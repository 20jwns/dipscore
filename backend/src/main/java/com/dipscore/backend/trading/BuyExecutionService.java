package com.dipscore.backend.trading;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.dipscore.backend.entryscore.EntryScoreProperties;
import com.dipscore.backend.entryscore.TechnicalIndicatorCalculator;
import com.dipscore.backend.marketdata.price.PriceHistory;
import com.dipscore.backend.marketdata.price.PriceHistoryRepository;
import com.dipscore.backend.signal.SignalDecision;
import com.dipscore.backend.signal.SignalProperties;
import com.dipscore.backend.signal.TradingSignalResult;
import com.dipscore.backend.signal.TradingSignalService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매수 신호(BUY)가 뜨면 실제로 가상 매수 체결하는 로직 (기획서 7-1).
 * {@link TradingSignalService#evaluateBuySignal} 로 계좌 현금까지 반영한 매수 신호를 판정하고,
 * BUY 일 때만 {@link OrderExecutor}(모의 체결) 로 주문을 실행해 {@link Account}·{@link Position} 을 갱신한다.
 *
 * <p><b>포지션 사이징(변동성 기반)</b>:
 * <pre>
 * 매수 수량 = (계좌 현재 잔고 × account.risk-per-trade-pct) / (매수가 - 손절가)
 * 손절가   = 매수가 - 진입 ATR × signal.stop-loss-atr-multiple
 * </pre>
 * 즉 "이 거래에서 손절까지 잃어도 되는 최대 금액 = 잔고 × 리스크비율" 이 되도록 수량을 역산한다
 * (변동성이 큰 종목일수록 손절폭이 커 수량이 줄어듦 — 고정 예산 방식보다 종목별 리스크가 균등해짐).
 * ATR 계산에 필요한 일봉이 부족하면(entryAtr 산출 불가) 사이징 자체가 불가능해 매수를 거부한다.
 * 현금 한도도 넘지 않도록 별도로 캡을 건다.
 *
 * <p>{@code account.enabled} + {@code signal.enabled} 가 모두 true 일 때만 빈 생성.
 */
@Service
@ConditionalOnProperty(name = {"account.enabled", "signal.enabled"}, havingValue = "true")
public class BuyExecutionService {

    private static final Logger log = LoggerFactory.getLogger(BuyExecutionService.class);

    private final TradingSignalService signalService;
    private final OrderExecutor orderExecutor;
    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final TechnicalIndicatorCalculator indicators;
    private final EntryScoreProperties entryScoreProps;
    private final AccountProperties accountProps;
    private final SignalProperties signalProps;

    public BuyExecutionService(TradingSignalService signalService,
                               OrderExecutor orderExecutor,
                               AccountRepository accountRepository,
                               PositionRepository positionRepository,
                               PriceHistoryRepository priceHistoryRepository,
                               TechnicalIndicatorCalculator indicators,
                               EntryScoreProperties entryScoreProps,
                               AccountProperties accountProps,
                               SignalProperties signalProps) {
        this.signalService = signalService;
        this.orderExecutor = orderExecutor;
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.indicators = indicators;
        this.entryScoreProps = entryScoreProps;
        this.accountProps = accountProps;
        this.signalProps = signalProps;
    }

    /**
     * 매수 신호를 판정하고, BUY 일 때만 체결한다.
     *
     * @throws TradingException 계좌 없음 / 신호가 BUY 아님 / 이미 보유 중 / ATR 산출 불가(일봉 부족) / 사이징 결과 수량 0
     */
    @Transactional
    public Position buyIfSignaled(Long accountId, String symbol) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new TradingException("계좌 없음: " + accountId));

        TradingSignalResult signal = signalService.evaluateBuySignal(symbol, accountId);
        if (signal.decision() != SignalDecision.BUY) {
            throw new TradingException("매수 신호가 아닙니다(%s): %s — 조건: %s"
                    .formatted(signal.decision().label(), symbol, summarize(signal)));
        }

        if (positionRepository.findByAccountIdAndSymbolAndStatus(accountId, symbol, PositionStatus.OPEN).isPresent()) {
            throw new TradingException("이미 보유 중인 포지션: " + symbol);
        }

        BigDecimal entryAtr = computeAtr(symbol);
        if (entryAtr == null || entryAtr.signum() <= 0) {
            throw new TradingException(
                    "ATR 계산 불가(일봉 부족) — 변동성 기반 사이징을 할 수 없습니다: " + symbol);
        }

        BigDecimal quote = orderExecutor.quote(symbol);
        long quantity = sizePosition(account, quote, entryAtr);
        if (quantity < 1) {
            throw new TradingException(
                    "포지션 사이징 결과 수량이 0입니다: 잔고 %s × 리스크 %s = 리스크예산, 주당위험(ATR×%s) %s, 현재가 %s"
                            .formatted(account.getCashBalance(), accountProps.riskPerTradePct(),
                                    signalProps.stopLossAtrMultiple(),
                                    entryAtr.multiply(BigDecimal.valueOf(signalProps.stopLossAtrMultiple())), quote));
        }

        OrderExecution exec = orderExecutor.buyMarket(symbol, quantity);
        BigDecimal cost = exec.executedPrice().multiply(BigDecimal.valueOf(quantity));

        account.debit(cost);
        Position position = Position.open(accountId, symbol, quantity, exec.executedPrice(), exec.executedAt(), entryAtr);
        positionRepository.save(position);

        log.info("[buy-execution] 계좌 {} {}: {}주 @ {} 체결 (금액 {}, entryAtr {}, 잔고 {})",
                accountId, symbol, quantity, exec.executedPrice(), cost, entryAtr, account.getCashBalance());
        return position;
    }

    /**
     * 변동성 기반 사이징: 수량 = (잔고 × riskPerTradePct) / (매수가 - 손절가). 현금 한도로도 캡.
     * 손절폭(riskPerShare) 이 0 이하면(ATR 이상치) 사이징 불가 → 0 반환.
     */
    private long sizePosition(Account account, BigDecimal quote, BigDecimal entryAtr) {
        BigDecimal riskPerShare = entryAtr.multiply(BigDecimal.valueOf(signalProps.stopLossAtrMultiple()));
        if (riskPerShare.signum() <= 0 || quote.signum() <= 0) {
            return 0;
        }
        BigDecimal riskBudget = account.getCashBalance().multiply(accountProps.riskPerTradePct());
        long quantityByRisk = riskBudget.divideToIntegralValue(riskPerShare).longValueExact();
        long maxAffordable = account.getCashBalance().divideToIntegralValue(quote).longValueExact();
        return Math.min(quantityByRisk, maxAffordable);
    }

    /** 진입 시점 ATR. 일봉이 부족하면 null. */
    private BigDecimal computeAtr(String symbol) {
        List<double[]> ohlc = priceHistoryRepository.findBySymbolAndOpenNotNullOrderByTsAsc(symbol).stream()
                .map(BuyExecutionService::toOhlc)
                .toList();
        int need = entryScoreProps.atrPeriod() + 1;
        if (ohlc.size() < need) {
            return null;
        }
        double atr = indicators.atr(ohlc, entryScoreProps.atrPeriod());
        return BigDecimal.valueOf(atr).setScale(4, RoundingMode.HALF_UP);
    }

    private static double[] toOhlc(PriceHistory p) {
        return new double[] {p.getOpen().doubleValue(), p.getHigh().doubleValue(),
                p.getLow().doubleValue(), p.getClose().doubleValue()};
    }

    private static String summarize(TradingSignalResult signal) {
        return signal.conditions().stream()
                .filter(c -> c.evaluated() && !c.passed())
                .map(c -> c.name())
                .reduce((a, b) -> a + ", " + b)
                .orElse("(모두 통과 — 재확인 필요)");
    }
}
