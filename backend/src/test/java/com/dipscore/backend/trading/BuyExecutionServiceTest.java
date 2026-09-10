package com.dipscore.backend.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.dipscore.backend.entryscore.EntryScoreProperties;
import com.dipscore.backend.entryscore.TechnicalIndicatorCalculator;
import com.dipscore.backend.marketdata.price.PriceHistory;
import com.dipscore.backend.marketdata.price.PriceHistoryRepository;
import com.dipscore.backend.signal.ConditionCheck;
import com.dipscore.backend.signal.SignalDecision;
import com.dipscore.backend.signal.SignalProperties;
import com.dipscore.backend.signal.TradingSignalResult;
import com.dipscore.backend.signal.TradingSignalService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 변동성 기반 포지션 사이징 검증: 수량 = (잔고 × riskPerTradePct) / (매수가 - 손절가),
 * 손절가 = 매수가 - entryAtr × stopLossAtrMultiple. ATR 은 {@link TechnicalIndicatorCalculator} 실계산을
 * 이용하도록 일봉을 "true range 일정(=range)" 하게 합성해 결과 ATR 을 정확히 알 수 있게 구성한다.
 */
@ExtendWith(MockitoExtension.class)
class BuyExecutionServiceTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final String SYMBOL = "005930";
    private static final int ATR_PERIOD = 5;
    /** signal.stop-loss-atr-multiple 테스트값. */
    private static final double STOP_LOSS_MULTIPLE = 1.25;

    @Mock TradingSignalService signalService;
    @Mock OrderExecutor orderExecutor;
    @Mock AccountRepository accountRepository;
    @Mock PositionRepository positionRepository;
    @Mock PriceHistoryRepository priceHistoryRepository;
    @Mock Account account;

    private final TechnicalIndicatorCalculator indicators = new TechnicalIndicatorCalculator();

    private BuyExecutionService service(BigDecimal riskPerTradePct) {
        EntryScoreProperties entryScoreProps = new EntryScoreProperties(
                true, "entry-v1", Map.of(), ATR_PERIOD, 14, 30, 20, 2.0, 10, 20, 0.5, 1.5, 3.0, 60, 70);
        AccountProperties accountProps = new AccountProperties(true, BigDecimal.valueOf(10_000_000),
                BigDecimal.valueOf(100_000), BigDecimal.valueOf(100_000), riskPerTradePct, "KRW");
        SignalProperties signalProps = new SignalProperties(true, 70.0, 60.0, 0.9, 0.025, STOP_LOSS_MULTIPLE, "15:20");
        return new BuyExecutionService(signalService, orderExecutor, accountRepository, positionRepository,
                priceHistoryRepository, indicators, entryScoreProps, accountProps, signalProps);
    }

    private static TradingSignalResult signal(SignalDecision decision) {
        return new TradingSignalResult(SYMBOL, Instant.now(), decision, decision.label(),
                75.0, 65.0, 1.0, Instant.now(), Instant.now(), List.of(
                        new ConditionCheck("저점진입스코어 ≥ 70", true, decision == SignalDecision.BUY, 75.0, 70.0, null)));
    }

    /** true range 가 항상 {@code range} 로 일정한 합성 일봉 — TechnicalIndicatorCalculator.atr(...) 결과가 정확히 range. */
    private static List<PriceHistory> constantAtrBars(int count, double range) {
        List<PriceHistory> bars = new ArrayList<>();
        double base = 8_000;
        for (int i = 0; i < count; i++) {
            double low = base + i * range;
            double high = low + range;
            bars.add(bar(low, high, low, high)); // open=low, close=high → 다음날 prevClose=오늘 low
        }
        return bars;
    }

    private static PriceHistory bar(double open, double high, double low, double close) {
        PriceHistory p = mock(PriceHistory.class);
        when(p.getOpen()).thenReturn(BigDecimal.valueOf(open));
        when(p.getHigh()).thenReturn(BigDecimal.valueOf(high));
        when(p.getLow()).thenReturn(BigDecimal.valueOf(low));
        when(p.getClose()).thenReturn(BigDecimal.valueOf(close));
        return p;
    }

    private void stubUpTo(BigDecimal cashBalance, BigDecimal quote, List<PriceHistory> priceBars) {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(signalService.evaluateBuySignal(SYMBOL, ACCOUNT_ID)).thenReturn(signal(SignalDecision.BUY));
        when(positionRepository.findByAccountIdAndSymbolAndStatus(ACCOUNT_ID, SYMBOL, PositionStatus.OPEN))
                .thenReturn(Optional.empty());
        when(account.getCashBalance()).thenReturn(cashBalance);
        when(orderExecutor.quote(SYMBOL)).thenReturn(quote);
        when(priceHistoryRepository.findBySymbolAndOpenNotNullOrderByTsAsc(SYMBOL)).thenReturn(priceBars);
        lenient().when(positionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void 리스크예산이_더_작으면_리스크기준으로_수량이_정해진다() {
        // ATR=500(=5% of 10,000), 손절폭 = 500×1.25 = 625. 잔고 1,000,000 × 1% = 리스크예산 10,000 → 16주
        // 현금기준 한도(100주)보다 훨씬 작음 → 리스크가 병목.
        stubUpTo(BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(10_000), constantAtrBars(8, 500));
        when(orderExecutor.buyMarket(SYMBOL, 16))
                .thenReturn(new OrderExecution(SYMBOL, 16, BigDecimal.valueOf(10_000), Instant.now()));

        Position position = service(BigDecimal.valueOf(0.01)).buyIfSignaled(ACCOUNT_ID, SYMBOL);

        assertThat(position.getQuantity()).isEqualTo(16);
        assertThat(position.getEntryAtr()).isEqualByComparingTo("500");
        ArgumentCaptor<BigDecimal> debitCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(account).debit(debitCaptor.capture());
        assertThat(debitCaptor.getValue()).isEqualByComparingTo("160000"); // 16주 × 10,000
    }

    @Test
    void 현금이_부족하면_리스크기준_수량이어도_현금한도로_캡된다() {
        // ATR=500 → 손절폭 625. 잔고 15,000, 현금기준 한도 = 15,000/10,000 = 1주.
        // 리스크예산 90% 로 크게 잡아도(13,500/625=21주) 현금이 병목 → 1주.
        stubUpTo(BigDecimal.valueOf(15_000), BigDecimal.valueOf(10_000), constantAtrBars(8, 500));
        when(orderExecutor.buyMarket(SYMBOL, 1))
                .thenReturn(new OrderExecution(SYMBOL, 1, BigDecimal.valueOf(10_000), Instant.now()));

        Position position = service(BigDecimal.valueOf(0.9)).buyIfSignaled(ACCOUNT_ID, SYMBOL);

        assertThat(position.getQuantity()).isEqualTo(1);
    }

    @Test
    void 일봉이_부족해_ATR_계산이_안되면_매수를_거부한다() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(signalService.evaluateBuySignal(SYMBOL, ACCOUNT_ID)).thenReturn(signal(SignalDecision.BUY));
        when(positionRepository.findByAccountIdAndSymbolAndStatus(ACCOUNT_ID, SYMBOL, PositionStatus.OPEN))
                .thenReturn(Optional.empty());
        List<PriceHistory> tooFewBars = constantAtrBars(3, 500); // ATR_PERIOD+1=6 개 필요한데 3개뿐
        when(priceHistoryRepository.findBySymbolAndOpenNotNullOrderByTsAsc(SYMBOL)).thenReturn(tooFewBars);

        assertThatThrownBy(() -> service(BigDecimal.valueOf(0.01)).buyIfSignaled(ACCOUNT_ID, SYMBOL))
                .isInstanceOf(TradingException.class)
                .hasMessageContaining("ATR 계산 불가");

        verify(orderExecutor, never()).buyMarket(any(), anyLong());
        verify(positionRepository, never()).save(any());
    }

    @Test
    void 사이징_결과_수량이_0이면_예외() {
        // 현금 5,000 < 시세 10,000 → 현금기준 수량 0
        stubUpTo(BigDecimal.valueOf(5_000), BigDecimal.valueOf(10_000), constantAtrBars(8, 500));

        assertThatThrownBy(() -> service(BigDecimal.valueOf(0.01)).buyIfSignaled(ACCOUNT_ID, SYMBOL))
                .isInstanceOf(TradingException.class)
                .hasMessageContaining("사이징 결과 수량이 0");

        verify(orderExecutor, never()).buyMarket(any(), anyLong());
        verify(account, never()).debit(any());
    }

    @Test
    void 신호가_BUY_아니면_예외이고_체결하지_않는다() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(signalService.evaluateBuySignal(SYMBOL, ACCOUNT_ID)).thenReturn(signal(SignalDecision.WAIT));

        assertThatThrownBy(() -> service(BigDecimal.valueOf(0.01)).buyIfSignaled(ACCOUNT_ID, SYMBOL))
                .isInstanceOf(TradingException.class)
                .hasMessageContaining("매수 신호가 아닙니다");

        verify(orderExecutor, never()).buyMarket(any(), anyLong());
        verify(positionRepository, never()).save(any());
    }

    @Test
    void 이미_보유중인_포지션이_있으면_예외() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(signalService.evaluateBuySignal(SYMBOL, ACCOUNT_ID)).thenReturn(signal(SignalDecision.BUY));
        when(positionRepository.findByAccountIdAndSymbolAndStatus(ACCOUNT_ID, SYMBOL, PositionStatus.OPEN))
                .thenReturn(Optional.of(mock(Position.class)));

        assertThatThrownBy(() -> service(BigDecimal.valueOf(0.01)).buyIfSignaled(ACCOUNT_ID, SYMBOL))
                .isInstanceOf(TradingException.class)
                .hasMessageContaining("이미 보유 중");

        verify(orderExecutor, never()).buyMarket(any(), anyLong());
    }

    @Test
    void 계좌가_없으면_예외() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(BigDecimal.valueOf(0.01)).buyIfSignaled(ACCOUNT_ID, SYMBOL))
                .isInstanceOf(TradingException.class)
                .hasMessageContaining("계좌 없음");
    }
}
