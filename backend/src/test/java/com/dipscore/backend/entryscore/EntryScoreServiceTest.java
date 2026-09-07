package com.dipscore.backend.entryscore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.dipscore.backend.attractiveness.persistence.AttractivenessScore;
import com.dipscore.backend.attractiveness.persistence.AttractivenessScoreRepository;
import com.dipscore.backend.entryscore.persistence.EntryScore;
import com.dipscore.backend.entryscore.persistence.EntryScoreRepository;
import com.dipscore.backend.marketdata.instrument.Instrument;
import com.dipscore.backend.marketdata.instrument.InstrumentRepository;
import com.dipscore.backend.marketdata.price.PriceHistory;
import com.dipscore.backend.marketdata.price.PriceHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link EntryScoreService} 가 매력도 점수를 <b>가장 최신 as_of 1건</b>으로만 가져오는지 검증.
 * (attractiveness_score 는 같은 symbol 에 재계산마다 행이 쌓이는 구조)
 */
@ExtendWith(MockitoExtension.class)
class EntryScoreServiceTest {

    private static final String SYMBOL = "005930";
    private static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");

    @Mock
    EntryScoreEngine engine;
    @Mock
    InstrumentRepository instrumentRepository;
    @Mock
    PriceHistoryRepository priceHistoryRepository;
    @Mock
    AttractivenessScoreRepository attractivenessScoreRepository;
    @Mock
    EntryScoreRepository entryScoreRepository;

    // 실제 앱의 ObjectMapper 처럼 JavaTimeModule(Instant 직렬화) 등록
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private EntryScoreService service() {
        return new EntryScoreService(engine, props(), instrumentRepository, priceHistoryRepository,
                attractivenessScoreRepository, entryScoreRepository, objectMapper);
    }

    private static EntryScoreProperties props() {
        return new EntryScoreProperties(true, "entry-v1",
                Map.of("rebound-signal", 0.3, "technical-indicator", 0.3, "attractiveness", 0.4),
                14, 14, 30.0, 20, 2.0, 10, 20, 0.5, 1.5, 3.0, 60.0, 70.0);
    }

    private static AttractivenessScore row(Instant asOf, double baseScore) {
        return new AttractivenessScore(SYMBOL, asOf, BigDecimal.valueOf(baseScore),
                BigDecimal.ONE, BigDecimal.valueOf(baseScore), "{}", "base-v1");
    }

    @BeforeEach
    void base() {
        when(instrumentRepository.findById(SYMBOL)).thenReturn(Optional.of(mock(Instrument.class)));
        when(priceHistoryRepository.findBySymbolAndOpenNotNullOrderByTsAsc(SYMBOL))
                .thenReturn(List.<PriceHistory>of());
        when(engine.minBarsRequired()).thenReturn(0); // 봉 개수 체크 통과
    }

    /** 같은 symbol 에 3행 (insert 순 ≠ as_of 순 ≠ 점수 순). "최신 as_of" 계약을 그대로 재현. */
    private void stubThreeAttractivenessRows() {
        AttractivenessScore twoDaysAgo = row(NOW.minus(2, ChronoUnit.DAYS), 30.0);  // 가장 오래됨
        AttractivenessScore oneDayAgo = row(NOW.minus(1, ChronoUnit.DAYS), 70.0);   // 중간, 최고 점수
        AttractivenessScore latest = row(NOW, 55.0);                                // 최신 as_of
        List<AttractivenessScore> rows = List.of(oneDayAgo, twoDaysAgo, latest);    // 삽입/PK 순서 섞음
        when(attractivenessScoreRepository.findFirstBySymbolOrderByAsOfDesc(SYMBOL))
                .thenAnswer(inv -> rows.stream()
                        .filter(r -> r.getSymbol().equals(inv.getArgument(0)))
                        .max(Comparator.comparing(AttractivenessScore::getAsOf)));
    }

    private void stubEngineAndSave() {
        when(engine.compute(any())).thenAnswer(inv -> {
            EntryScoreInputs in = inv.getArgument(0);
            double base = in.attractivenessBaseScore() == null ? 0.0 : in.attractivenessBaseScore().doubleValue();
            double component = base / 100.0;
            boolean passed = base >= 60.0;
            return new EntryScoreResult(in.symbol(), in.asOf(), "entry-v1",
                    0.5, 0.4, component, passed, passed ? 42.0 : 0.0, 70.0, Map.of());
        });
        when(entryScoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void 가장_최신_as_of_행의_base_score를_엔진에_넘기고_다른_조회메서드는_안_쓴다() {
        stubThreeAttractivenessRows();
        stubEngineAndSave();

        service().computeAndSave(SYMBOL);

        ArgumentCaptor<EntryScoreInputs> inputs = ArgumentCaptor.forClass(EntryScoreInputs.class);
        verify(engine).compute(inputs.capture());
        // 30(먼저 insert) 도 70(최고 점수) 도 아닌, 최신 as_of 행의 55
        assertThat(inputs.getValue().attractivenessBaseScore()).isEqualByComparingTo("55");

        // 매력도 조회는 findFirstBySymbolOrderByAsOfDesc 딱 한 번, 그 외 조회 없음
        verify(attractivenessScoreRepository).findFirstBySymbolOrderByAsOfDesc(SYMBOL);
        verifyNoMoreInteractions(attractivenessScoreRepository);
    }

    @Test
    void 저장되는_entry_score의_attractiveness_component가_최신_base_score_기반이다() {
        stubThreeAttractivenessRows();
        stubEngineAndSave();

        service().computeAndSave(SYMBOL);

        ArgumentCaptor<EntryScore> saved = ArgumentCaptor.forClass(EntryScore.class);
        verify(entryScoreRepository).save(saved.capture());
        assertThat(saved.getValue().getAttractivenessComponent()).isEqualByComparingTo("0.55"); // 55/100
        assertThat(saved.getValue().isFilterPassed()).isFalse();                                // 55 < 60
    }

    @Test
    void 매력도_점수가_하나도_없으면_예외이고_저장하지_않는다() {
        when(attractivenessScoreRepository.findFirstBySymbolOrderByAsOfDesc(SYMBOL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().computeAndSave(SYMBOL))
                .isInstanceOf(EntryScoreException.class)
                .hasMessageContaining("매력도 점수 없음");

        verify(entryScoreRepository, never()).save(any());
    }
}
