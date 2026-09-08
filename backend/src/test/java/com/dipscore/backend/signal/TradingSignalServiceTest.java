package com.dipscore.backend.signal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import com.dipscore.backend.attractiveness.persistence.AttractivenessScore;
import com.dipscore.backend.attractiveness.persistence.AttractivenessScoreRepository;
import com.dipscore.backend.entryscore.persistence.EntryScore;
import com.dipscore.backend.entryscore.persistence.EntryScoreRepository;
import com.dipscore.backend.marketdata.instrument.Instrument;
import com.dipscore.backend.marketdata.instrument.InstrumentRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link TradingSignalService} 매수 신호 판정 (기획서 7-1) 검증.
 * 3종목(005930/000660/042700)으로 통과·미달 시나리오를 커버.
 */
@ExtendWith(MockitoExtension.class)
class TradingSignalServiceTest {

    private static final Instant T = Instant.parse("2026-09-08T00:00:00Z");

    @Mock
    InstrumentRepository instrumentRepository;
    @Mock
    EntryScoreRepository entryScoreRepository;
    @Mock
    AttractivenessScoreRepository attractivenessScoreRepository;

    private TradingSignalService service() {
        return new TradingSignalService(new SignalProperties(true, 70.0, 60.0, 0.9),
                instrumentRepository, entryScoreRepository, attractivenessScoreRepository);
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

    private static ConditionCheck cond(TradingSignalResult r, String namePart) {
        return r.conditions().stream().filter(c -> c.name().contains(namePart)).findFirst().orElseThrow();
    }

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
        // 보유현금 조건은 평가 대상 아님
        assertThat(cond(r, "보유현금").evaluated()).isFalse();
    }

    @Test
    void 저점진입스코어_미달이면_대기() {
        stubInstrument("000660");
        stubEntry("000660", 55.0);            // < 70
        stubAttractiveness("000660", 65.0, 1.0);

        TradingSignalResult r = service().evaluateBuySignal("000660");

        assertThat(r.decision()).isEqualTo(SignalDecision.WAIT);
        assertThat(r.label()).isEqualTo("대기");
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
        stubAttractiveness("042700", 50.0, 1.0);  // < 60

        TradingSignalResult r = service().evaluateBuySignal("042700");

        assertThat(r.decision()).isEqualTo(SignalDecision.WAIT);
        assertThat(cond(r, "저점진입스코어").passed()).isTrue();
        assertThat(cond(r, "기본점수").passed()).isFalse();
    }

    @Test
    void 이벤트조정계수_미달이면_대기() {
        // 조정계수는 현재 항상 1.0 이지만, 룰 자체는 동작해야 함
        stubInstrument("005930");
        stubEntry("005930", 90.0);
        stubAttractiveness("005930", 90.0, 0.8);  // < 0.9 (음수 이벤트 가정)

        TradingSignalResult r = service().evaluateBuySignal("005930");

        assertThat(r.decision()).isEqualTo(SignalDecision.WAIT);
        assertThat(cond(r, "이벤트조정계수").passed()).isFalse();
        assertThat(cond(r, "이벤트조정계수").actual()).isEqualTo(0.8);
    }

    @Test
    void 보유현금_조건은_평가에서_제외되고_결과에_영향_없다() {
        stubInstrument("005930");
        stubEntry("005930", 75.0);
        stubAttractiveness("005930", 65.0, 1.0);

        TradingSignalResult r = service().evaluateBuySignal("005930");

        // 나머지 3개 통과 → 보유현금 미평가여도 BUY
        assertThat(r.decision()).isEqualTo(SignalDecision.BUY);
        assertThat(r.conditions()).filteredOn(c -> !c.evaluated())
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.name()).contains("보유현금");
                    assertThat(c.note()).contains("TODO");
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
}
