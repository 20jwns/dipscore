package com.dipscore.backend.signal;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.dipscore.backend.attractiveness.persistence.AttractivenessScore;
import com.dipscore.backend.attractiveness.persistence.AttractivenessScoreRepository;
import com.dipscore.backend.entryscore.persistence.EntryScore;
import com.dipscore.backend.entryscore.persistence.EntryScoreRepository;
import com.dipscore.backend.marketdata.instrument.InstrumentRepository;
import com.dipscore.backend.marketdata.price.PriceHistory;
import com.dipscore.backend.marketdata.price.PriceHistoryRepository;
import com.dipscore.backend.trading.Account;
import com.dipscore.backend.trading.AccountProperties;
import com.dipscore.backend.trading.AccountRepository;
import com.dipscore.backend.trading.Position;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 매매 신호 판정 (기획서 7). 매수·매도 신호 둘 다 판정한다.
 *
 * <p>기획서 7-1 매수 룰:
 * <pre>
 * 매수 = 저점진입스코어 ≥ 70
 *      AND 기본점수 ≥ 60
 *      AND 이벤트조정계수 ≥ 0.9 (음수 이벤트 없음)
 *      AND 보유현금 > 최소매수단위
 * </pre>
 * 이벤트조정계수는 현재 항상 1.0 (기획서 4-5 감성분석 미구현) → 이 조건은 사실상 항상 통과.
 * 보유현금 조건은 {@code accountId} 를 줬을 때만 실제 계좌 잔고로 평가한다(안 주면 평가 제외 — 하위호환).
 *
 * <p>기획서 7-2 매도 룰 ({@link #evaluateSellSignal}):
 * <pre>
 * 매도 = 목표수익률(+2~3%) 도달 OR 손절(ATR×1~1.5배) 도달 OR 보유시간(15:20) 초과
 * </pre>
 *
 * <p>{@code signal.enabled=true} 일 때만 빈 생성 (테스트 컨텍스트엔 JPA 가 없어 미생성).
 */
@Service
@ConditionalOnProperty(prefix = "signal", name = "enabled", havingValue = "true")
public class TradingSignalService {

    private static final Logger log = LoggerFactory.getLogger(TradingSignalService.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final SignalProperties props;
    private final InstrumentRepository instrumentRepository;
    private final EntryScoreRepository entryScoreRepository;
    private final AttractivenessScoreRepository attractivenessScoreRepository;
    private final AccountRepository accountRepository;
    private final AccountProperties accountProps;
    private final PriceHistoryRepository priceHistoryRepository;
    private final Clock clock;

    @Autowired
    public TradingSignalService(SignalProperties props,
                                InstrumentRepository instrumentRepository,
                                EntryScoreRepository entryScoreRepository,
                                AttractivenessScoreRepository attractivenessScoreRepository,
                                AccountRepository accountRepository,
                                AccountProperties accountProps,
                                PriceHistoryRepository priceHistoryRepository) {
        this(props, instrumentRepository, entryScoreRepository, attractivenessScoreRepository,
                accountRepository, accountProps, priceHistoryRepository, Clock.system(SEOUL));
    }

    /** 커스텀 {@link Clock} 주입용 (보유시간 초과 판정 테스트). */
    public TradingSignalService(SignalProperties props,
                                InstrumentRepository instrumentRepository,
                                EntryScoreRepository entryScoreRepository,
                                AttractivenessScoreRepository attractivenessScoreRepository,
                                AccountRepository accountRepository,
                                AccountProperties accountProps,
                                PriceHistoryRepository priceHistoryRepository,
                                Clock clock) {
        this.props = props;
        this.instrumentRepository = instrumentRepository;
        this.entryScoreRepository = entryScoreRepository;
        this.attractivenessScoreRepository = attractivenessScoreRepository;
        this.accountRepository = accountRepository;
        this.accountProps = accountProps;
        this.priceHistoryRepository = priceHistoryRepository;
        this.clock = clock;
    }

    /** 매수 신호 판정. 계좌 미지정 — 보유현금 조건은 평가 제외. 저장 없이 계산·반환만. */
    public TradingSignalResult evaluateBuySignal(String symbol) {
        return evaluateBuySignal(symbol, null);
    }

    /**
     * 매수 신호 판정.
     *
     * @param accountId 지정하면 그 계좌의 실제 현금잔고로 "보유현금 > 최소매수단위" 조건을 평가한다.
     *                  null 이면 그 조건은 판정에서 제외(하위호환).
     */
    public TradingSignalResult evaluateBuySignal(String symbol, Long accountId) {
        if (instrumentRepository.findById(symbol).isEmpty()) {
            throw new SignalException("종목 없음: " + symbol);
        }

        EntryScore entry = entryScoreRepository.findFirstBySymbolOrderByAsOfDesc(symbol)
                .orElseThrow(() -> new SignalException(
                        "저점 진입 스코어 없음: %s (POST /api/entry-score/%s 먼저 실행)".formatted(symbol, symbol)));
        AttractivenessScore attractiveness = attractivenessScoreRepository.findFirstBySymbolOrderByAsOfDesc(symbol)
                .orElseThrow(() -> new SignalException(
                        "매력도 점수 없음: %s (POST /api/attractiveness/%s 먼저 실행)".formatted(symbol, symbol)));

        double entryScore = entry.getEntryScore().doubleValue();
        double baseScore = attractiveness.getBaseScore().doubleValue();
        double eventCoefficient = attractiveness.getEventCoefficient().doubleValue();

        List<ConditionCheck> conditions = new ArrayList<>();
        conditions.add(ConditionCheck.evaluated(
                "저점진입스코어 ≥ " + fmt(props.entryThreshold()),
                entryScore >= props.entryThreshold(), entryScore, props.entryThreshold(), null));
        conditions.add(ConditionCheck.evaluated(
                "기본점수 ≥ " + fmt(props.minBaseScore()),
                baseScore >= props.minBaseScore(), baseScore, props.minBaseScore(), null));
        conditions.add(ConditionCheck.evaluated(
                "이벤트조정계수 ≥ " + fmt(props.eventCoefficientMin()) + " (음수 이벤트 없음)",
                eventCoefficient >= props.eventCoefficientMin(), eventCoefficient, props.eventCoefficientMin(),
                "이벤트 조정계수는 현재 1.0 고정 — 기획서 4-5 감성분석 미구현 (TODO)"));
        conditions.add(cashCondition(accountId));

        boolean buy = conditions.stream()
                .filter(ConditionCheck::evaluated)
                .allMatch(ConditionCheck::passed);
        SignalDecision decision = buy ? SignalDecision.BUY : SignalDecision.WAIT;

        log.info("[trading-signal] {}: {} (entry={}, base={}, evtCoef={}, accountId={})",
                symbol, decision, entryScore, baseScore, eventCoefficient, accountId);

        return new TradingSignalResult(symbol, Instant.now(), decision, decision.label(),
                round2(entryScore), round2(baseScore), round3(eventCoefficient),
                entry.getAsOf(), attractiveness.getAsOf(), conditions);
    }

    private ConditionCheck cashCondition(Long accountId) {
        if (accountId == null) {
            return ConditionCheck.skipped("보유현금 > 최소매수단위", "accountId 미지정 — 판정에서 제외");
        }
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new SignalException("계좌 없음: " + accountId));
        double cash = account.getCashBalance().doubleValue();
        double minBuyUnit = accountProps.minBuyUnit().doubleValue();
        return ConditionCheck.evaluated("보유현금 > 최소매수단위", cash > minBuyUnit, cash, minBuyUnit, null);
    }

    /**
     * 매도 신호 판정 (기획서 7-2). {@code position} 은 OPEN 상태여야 한다.
     * 목표수익률 도달 → 손절가 도달 → 보유시간 초과 순으로 첫 트리거를 사유로 삼는다
     * (목표수익률·손절은 가격 방향이 반대라 동시 트리거 불가, 보유시간 초과는 둘과 독립적으로 겹칠 수 있음).
     */
    public SellSignalResult evaluateSellSignal(Position position) {
        if (!position.isOpen()) {
            throw new SignalException("이미 청산된 포지션: " + position.getId());
        }

        double entryPrice = position.getEntryPrice().doubleValue();
        double currentPrice = latestClose(position.getSymbol()).doubleValue();
        double returnRate = (currentPrice - entryPrice) / entryPrice;

        boolean targetHit = returnRate >= props.targetProfitRate();
        ConditionCheck targetCond = ConditionCheck.evaluated(
                "목표수익률 ≥ " + pct(props.targetProfitRate()),
                targetHit, returnRate, props.targetProfitRate(), null);

        BigDecimal entryAtr = position.getEntryAtr();
        ConditionCheck stopCond;
        boolean stopHit = false;
        Double stopLossPrice = null;
        if (entryAtr != null && entryAtr.signum() > 0) {
            double stopPrice = entryPrice - entryAtr.doubleValue() * props.stopLossAtrMultiple();
            stopLossPrice = stopPrice;
            stopHit = currentPrice <= stopPrice;
            stopCond = ConditionCheck.evaluated(
                    "현재가 ≤ 손절가(진입가-ATR×" + fmt(props.stopLossAtrMultiple()) + ")",
                    stopHit, currentPrice, stopPrice, null);
        } else {
            stopCond = ConditionCheck.skipped("손절가 도달", "진입 시 ATR 기록 없음 — 판정에서 제외");
        }

        LocalTime forceClose = LocalTime.parse(props.forceCloseTime());
        LocalTime now = LocalTime.now(clock);
        boolean timeUp = !now.isBefore(forceClose);
        ConditionCheck timeCond = ConditionCheck.evaluated(
                "보유시간 초과 (" + props.forceCloseTime() + " 이후, Asia/Seoul)",
                timeUp, hourFraction(now), hourFraction(forceClose), null);

        List<ConditionCheck> conditions = List.of(targetCond, stopCond, timeCond);
        ExitReason reason = targetHit ? ExitReason.TARGET_PROFIT
                : stopHit ? ExitReason.STOP_LOSS
                : timeUp ? ExitReason.TIME_LIMIT
                : null;
        boolean sell = reason != null;

        double targetPrice = entryPrice * (1 + props.targetProfitRate());

        log.info("[trading-signal] SELL {} position={}: {} (return={}, current={}, entry={})",
                position.getSymbol(), position.getId(), sell ? reason : "HOLD",
                round4(returnRate), currentPrice, entryPrice);

        return new SellSignalResult(position.getId(), position.getSymbol(), Instant.now(),
                sell, reason, round2(currentPrice), round4(returnRate),
                stopLossPrice == null ? null : round2(stopLossPrice), round2(targetPrice), conditions);
    }

    private BigDecimal latestClose(String symbol) {
        return priceHistoryRepository.findBySymbolOrderByTsDesc(symbol).stream()
                .findFirst()
                .map(PriceHistory::getClose)
                .orElseThrow(() -> new SignalException("시세 없음: " + symbol));
    }

    private static double hourFraction(LocalTime t) {
        return t.getHour() + t.getMinute() / 60.0;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }

    private static String pct(double v) {
        return fmt(v * 100.0) + "%";
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }
}
