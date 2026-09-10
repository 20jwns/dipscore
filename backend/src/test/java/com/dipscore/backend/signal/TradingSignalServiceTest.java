package com.dipscore.backend.signal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import com.dipscore.backend.attractiveness.persistence.AttractivenessScore;
import com.dipscore.backend.attractiveness.persistence.AttractivenessScoreRepository;
import com.dipscore.backend.entryscore.persistence.EntryScore;
import com.dipscore.backend.entryscore.persistence.EntryScoreRepository;
import com.dipscore.backend.marketdata.instrument.Instrument;
import com.dipscore.backend.marketdata.instrument.InstrumentRepository;
import com.dipscore.backend.marketdata.price.PriceHistory;
import com.dipscore.backend.marketdata.price.PriceHistoryRepository;
import com.dipscore.backend.trading.Account;
import com.dipscore.backend.trading.AccountProperties;
import com.dipscore.backend.trading.AccountRepository;
import com.dipscore.backend.trading.Position;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link TradingSignalService} 매수(기획서 7-1)·매도(기획서 7-2) 신호 판정 검증.
 * 3종목(005930/000660/042700)으로 통과·미달 시나리오를 커버.
 */
@ExtendWith(MockitoExtension.class)
class TradingSignalServiceTest {

    private static final Instant T = Instant.parse("2026-09-08T00:00:00Z");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 14:00 KST — 강제청산 시각(15:20) 전. */
    private static final Clock CLOCK_1400 = Clock.fixed(Instant.parse("2026-09-08T05:00:00Z"), SEOUL);
    /** 15:30 KST — 강제청산 시각 이후. */
    private static final Clock CLOCK_1530 = Clock.fixed(Instant.parse("2026-09-08T06:30:00Z"), SEOUL);

    @Mock
    InstrumentRepository instrumentRepository;
    @Mock
    EntryScoreRepository entryScoreRepository;
    @Mock
    AttractivenessScoreRepository attractivenessScoreRepository;
    @Mock
    AccountRepository accountRepository;
    @Mock
    PriceHistoryRepository priceHistoryRepository;

    private static SignalProperties signalProps() {
        return new SignalProperties(true, 70.0, 60.0, 0.9, 0.025, 1.25, "15:20");
    }

    private static AccountProperties accountProps() {
        return new AccountProperties(true, BigDecimal.valueOf(10_000_000),
                BigDecimal.valueOf(100_000), BigDecimal.valueOf(100_000), BigDecimal.valueOf(0.01), "KRW");
    }

    private TradingSignalService service() {
        return service(CLOCK_1400);
    }

    private TradingSignalService service(Clock clock) {
        return new TradingSignalService(signalProps(),
                instrumentRepository, entryScoreRepository, attractivenessScoreRepository,
                accountRepository, accountProps(), priceHistoryRepository, clock);
    }

    private void stubInstrument(String symbol) {
        when(instrumentRepository.findById(symbol)).thenReturn(Optional.of(mock(Instrument.class)));
    }

    private void stubEntry(String symbol, double entryScore) {
        when(entryScoreRepository.findFirstBySymbolOrderByAsOfDesc(symbol)).thenReturn(Optional.of(
                new EntryScore(symbol, T, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.valueOf(entryScore), true, "{}", "entry-v1")));
    }

    private void stubAttractiveness(String symbol, double baseScore, double eventCoefficient) {
        when(attractivenessScoreRepository.findFirstBySymbolOrderByAsOfDesc(symbol)).thenReturn(Optional.of(
                new AttractivenessScore(symbol, T, BigDecimal.valueOf(baseScore),
                        BigDecimal.valueOf(eventCoefficient), BigDecimal.valueOf(baseScore), "{}", "base-v1")));
    }

    private void stubAccount(Long accountId, double cashBalance) {
        Account account = mock(Account.class);
        when(account.getCashBalance()).thenReturn(BigDecimal.valueOf(cashBalance));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
    }

    private void stubPrice(String symbol, double close) {
        PriceHistory ph = mock(PriceHistory.class);
        when(ph.getClose()).thenReturn(BigDecimal.valueOf(close));
        when(priceHistoryRepository.findBySymbolOrderByTsDesc(symbol)).thenReturn(java.util.List.of(ph));
    }

    private static ConditionCheck cond(TradingSignalResult r, String namePart) {
        return r.conditions().stream().filter(c -> c.name().contains(namePart)).findFirst().orElseThrow();
    }

    private static ConditionCheck cond(SellSignalResult r, String namePart) {
        return r.conditions().stream().filter(c -> c.name().contains(namePart)).findFirst().orElseThrow();
    }

    // ── 매수 신호 (기존) ──────────────────────────────────────────

    @Test
    void 모든_조건_충족시_매수신호() {
        stubInstrument("005930");
        stubEntry("005930", 75.0);
        stubAttractiveness("005930", 65.0, 1.0);

        TradingSignalResult r = service().evaluateBuySignal("005930");

        assertThat(r.decision()).isEqualTo(SignalDecision.BUY);
        assertThat(r.label()).isEqualTo("매수신호");
        assertThat(cond(r, "저점진입스코어").passed()).isTrue();
        assertThat(cond(r, "기본점수").passed()).isTrue();
        assertThat(cond(r, "이벤트조정계수").passed()).isTrue();
        assertThat(cond(r, "이벤트조정계수").note()).contains("1.0 고정");
        assertThat(cond(r, "보유현금").evaluated()).isFalse();
    }

    @Test
    void 저점진입스코어_미달이면_대기() {
        stubInstrument("000660");
        stubEntry("000660", 55.0);
        stubAttractiveness("000660", 65.0, 1.0);

        TradingSignalResult r = service().evaluateBuySignal("000660");

        assertThat(r.decision()).isEqualTo(SignalDecision.WAIT);
        ConditionCheck c = cond(r, "저점진입스코어");
        assertThat(c.passed()).isFalse();
        assertThat(c.actual()).isEqualTo(55.0);
        assertThat(c.threshold()).isEqualTo(70.0);
        assertThat(cond(r, "기본점수").passed()).isTrue();
    }

    @Test
    void 기본점수_미달이면_대기() {
        stubInstrument("042700");
        stubEntry("042700", 80.0);
        stubAttractiveness("042700", 50.0, 1.0);

        TradingSignalResult r = service().evaluateBuySignal("042700");

        assertThat(r.decision()).isEqualTo(SignalDecision.WAIT);
        assertThat(cond(r, "저점진입스코어").passed()).isTrue();
        assertThat(cond(r, "기본점수").passed()).isFalse();
    }

    @Test
    void 이벤트조정계수_미달이면_대기() {
        stubInstrument("005930");
        stubEntry("005930", 90.0);
        stubAttractiveness("005930", 90.0, 0.8);

        TradingSignalResult r = service().evaluateBuySignal("005930");

        assertThat(r.decision()).isEqualTo(SignalDecision.WAIT);
        assertThat(cond(r, "이벤트조정계수").passed()).isFalse();
        assertThat(cond(r, "이벤트조정계수").actual()).isEqualTo(0.8);
    }

    @Test
    void 계좌_미지정이면_보유현금_조건은_평가에서_제외되고_결과에_영향_없다() {
        stubInstrument("005930");
        stubEntry("005930", 75.0);
        stubAttractiveness("005930", 65.0, 1.0);

        TradingSignalResult r = service().evaluateBuySignal("005930");

        assertThat(r.decision()).isEqualTo(SignalDecision.BUY);
        assertThat(r.conditions()).filteredOn(c -> !c.evaluated())
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.name()).contains("보유현금");
                    assertThat(c.note()).contains("accountId 미지정");
                });
    }

    @Test
    void 저점진입스코어_없으면_예외() {
        stubInstrument("005930");
        when(entryScoreRepository.findFirstBySymbolOrderByAsOfDesc("005930")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().evaluateBuySignal("005930"))
                .isInstanceOf(SignalException.class)
                .hasMessageContaining("저점 진입 스코어 없음");
    }

    @Test
    void 매력도_점수_없으면_예외() {
        stubInstrument("005930");
        stubEntry("005930", 75.0);
        when(attractivenessScoreRepository.findFirstBySymbolOrderByAsOfDesc("005930")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().evaluateBuySignal("005930"))
                .isInstanceOf(SignalException.class)
                .hasMessageContaining("매력도 점수 없음");
    }

    @Test
    void 종목_없으면_예외() {
        when(instrumentRepository.findById("999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().evaluateBuySignal("999999"))
                .isInstanceOf(SignalException.class)
                .hasMessageContaining("종목 없음");
    }

    // ── 매수 신호 — 보유현금(계좌 연동) ────────────────────────────

    @Test
    void 계좌지정시_현금이_충분하면_보유현금_조건도_충족되어_매수신호() {
        stubInstrument("005930");
        stubEntry("005930", 75.0);
        stubAttractiveness("005930", 65.0, 1.0);
        stubAccount(1L, 200_000.0); // > 최소매수단위 100,000

        TradingSignalResult r = service().evaluateBuySignal("005930", 1L);

        assertThat(r.decision()).isEqualTo(SignalDecision.BUY);
        ConditionCheck c = cond(r, "보유현금");
        assertThat(c.evaluated()).isTrue();
        assertThat(c.passed()).isTrue();
        assertThat(c.actual()).isEqualTo(200_000.0);
        assertThat(c.threshold()).isEqualTo(100_000.0);
    }

    @Test
    void 계좌지정시_현금이_최소매수단위_이하면_대기() {
        stubInstrument("005930");
        stubEntry("005930", 75.0);
        stubAttractiveness("005930", 65.0, 1.0);
        stubAccount(1L, 50_000.0); // ≤ 최소매수단위

        TradingSignalResult r = service().evaluateBuySignal("005930", 1L);

        assertThat(r.decision()).isEqualTo(SignalDecision.WAIT);
        assertThat(cond(r, "보유현금").passed()).isFalse();
    }

    @Test
    void 계좌ID_지정했는데_계좌없으면_예외() {
        stubInstrument("005930");
        stubEntry("005930", 75.0);
        stubAttractiveness("005930", 65.0, 1.0);
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().evaluateBuySignal("005930", 99L))
                .isInstanceOf(SignalException.class)
                .hasMessageContaining("계좌 없음");
    }

    // ── 매도 신호 (기획서 7-2) ────────────────────────────────────

    @Test
    void 목표수익률_도달하면_TARGET_PROFIT_매도신호() {
        stubPrice("005930", 10_300.0); // (10300-10000)/10000 = 3% ≥ 2.5%
        Position position = Position.open(1L, "005930", 10, BigDecimal.valueOf(10_000), T, null);

        SellSignalResult r = service().evaluateSellSignal(position);

        assertThat(r.sell()).isTrue();
        assertThat(r.reason()).isEqualTo(ExitReason.TARGET_PROFIT);
        assertThat(r.returnRate()).isEqualTo(0.03);
        assertThat(cond(r, "목표수익률").passed()).isTrue();
    }

    @Test
    void 손절가_도달하면_STOP_LOSS_매도신호() {
        // 진입가 10000, ATR 100 → 손절가 = 10000 - 100*1.25 = 9875
        stubPrice("005930", 9_800.0);
        Position position = Position.open(1L, "005930", 10, BigDecimal.valueOf(10_000), T, BigDecimal.valueOf(100));

        SellSignalResult r = service().evaluateSellSignal(position);

        assertThat(r.sell()).isTrue();
        assertThat(r.reason()).isEqualTo(ExitReason.STOP_LOSS);
        assertThat(r.stopLossPrice()).isEqualTo(9_875.0);
        assertThat(cond(r, "손절가").passed()).isTrue();
    }

    @Test
    void 보유시간_초과하면_TIME_LIMIT_매도신호() {
        stubPrice("005930", 10_000.0); // 목표수익/손절 둘 다 미해당
        Position position = Position.open(1L, "005930", 10, BigDecimal.valueOf(10_000), T, null);

        SellSignalResult r = service(CLOCK_1530).evaluateSellSignal(position); // 15:30 KST

        assertThat(r.sell()).isTrue();
        assertThat(r.reason()).isEqualTo(ExitReason.TIME_LIMIT);
        assertThat(cond(r, "보유시간").passed()).isTrue();
    }

    @Test
    void 아무_조건도_안맞으면_HOLD() {
        // 진입가 10000, ATR 100 → 손절가 9875. 현재가 10050(0.5% 수익, 손절 미도달), 14:00(시간 전)
        stubPrice("005930", 10_050.0);
        Position position = Position.open(1L, "005930", 10, BigDecimal.valueOf(10_000), T, BigDecimal.valueOf(100));

        SellSignalResult r = service().evaluateSellSignal(position);

        assertThat(r.sell()).isFalse();
        assertThat(r.reason()).isNull();
        assertThat(cond(r, "목표수익률").passed()).isFalse();
        assertThat(cond(r, "손절가").passed()).isFalse();
        assertThat(cond(r, "보유시간").passed()).isFalse();
    }

    @Test
    void 진입시_ATR_기록이_없으면_손절조건은_판정에서_제외() {
        stubPrice("005930", 10_050.0);
        Position position = Position.open(1L, "005930", 10, BigDecimal.valueOf(10_000), T, null);

        SellSignalResult r = service().evaluateSellSignal(position);

        ConditionCheck stop = cond(r, "손절가");
        assertThat(stop.evaluated()).isFalse();
        assertThat(r.stopLossPrice()).isNull();
    }

    @Test
    void 이미_청산된_포지션은_매도판정_예외() {
        Position position = Position.open(1L, "005930", 10, BigDecimal.valueOf(10_000), T, null);
        position.close(BigDecimal.valueOf(10_100), Instant.now(), ExitReason.MANUAL);

        assertThatThrownBy(() -> service().evaluateSellSignal(position))
                .isInstanceOf(SignalException.class)
                .hasMessageContaining("이미 청산된 포지션");
    }
}
