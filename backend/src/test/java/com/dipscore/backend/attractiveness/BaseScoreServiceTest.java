package com.dipscore.backend.attractiveness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.dipscore.backend.attractiveness.persistence.AttractivenessScore;
import com.dipscore.backend.attractiveness.persistence.AttractivenessScoreRepository;
import com.dipscore.backend.marketdata.financial.FinancialSnapshot;
import com.dipscore.backend.marketdata.financial.FinancialSnapshotRepository;
import com.dipscore.backend.marketdata.instrument.Instrument;
import com.dipscore.backend.marketdata.instrument.InstrumentRepository;
import com.dipscore.backend.marketdata.macro.MacroIndicatorRepository;
import com.dipscore.backend.marketdata.price.PriceHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link BaseScoreService} 가 {@code financial_snapshot} 에서 <b>어떤 fiscal_year 를 고르는지</b> 회귀 검증.
 * 여러 연도가 있으면 최신(fiscal_year DESC)을 쓰고, 설정으로 특정 연도를 고정할 수도 있어야 한다.
 */
@ExtendWith(MockitoExtension.class)
class BaseScoreServiceTest {

    @Mock BaseScoreEngine engine;
    @Mock FundamentalMetricsCalculator metricsCalculator;
    @Mock InstrumentRepository instrumentRepository;
    @Mock FinancialSnapshotRepository financialSnapshotRepository;
    @Mock PriceHistoryRepository priceHistoryRepository;
    @Mock MacroIndicatorRepository macroIndicatorRepository;
    @Mock AttractivenessScoreRepository attractivenessScoreRepository;
    @Mock Instrument instrument;

    private static final String SYMBOL = "005930";
    private static final String INDUSTRY = "반도체";

    private BaseScoreService service(int pinnedYear) {
        AttractivenessProperties props = new AttractivenessProperties(
                true, "base-v1", pinnedYear, "CFS", 24, Map.of("per", 1.0));
        return new BaseScoreService(props, engine, metricsCalculator, instrumentRepository,
                financialSnapshotRepository, priceHistoryRepository, macroIndicatorRepository,
                attractivenessScoreRepository, new ObjectMapper().findAndRegisterModules());
    }

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }

    private static FinancialSnapshot snap(int year) {
        return FinancialSnapshot.of(SYMBOL, (short) year, "CFS",
                bd(1000 + year), bd(100), bd(80), bd(500), bd(200), bd(900),
                10_000L, "KRW", "TEST_FY" + year);
    }

    private static AttractivenessResult result(int fiscalYear) {
        return new AttractivenessResult(SYMBOL, Instant.now(), "base-v1", INDUSTRY, 1, fiscalYear, "CFS",
                bd(70000), Map.of(), 55.0, 1.0, 55.0);
    }

    /** 스냅샷 조회를 제외한 나머지 협력자 스텁 (해피패스). */
    private void stubSurroundings() {
        lenient().when(instrumentRepository.findById(SYMBOL)).thenReturn(Optional.of(instrument));
        lenient().when(instrument.getIndustry()).thenReturn(INDUSTRY);
        lenient().when(instrument.getSymbol()).thenReturn(SYMBOL);
        lenient().when(instrumentRepository.findByIndustryAndActiveTrue(INDUSTRY)).thenReturn(List.of(instrument));
        lenient().when(priceHistoryRepository.findBySymbolOrderByTsDesc(anyString())).thenReturn(List.of());
        lenient().when(macroIndicatorRepository.findByIndicatorCodeOrderByTsDesc(anyString(), any()))
                .thenReturn(List.of());
        lenient().when(metricsCalculator.calculate(any(), any()))
                .thenReturn(new ValuationMetrics(SYMBOL, bd(10), bd(1), bd(40), bd(15), bd(20)));
        lenient().when(engine.compute(any())).thenReturn(result(0));
        lenient().when(attractivenessScoreRepository.save(any(AttractivenessScore.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void fiscal_year_설정이_0이면_최신연도_스냅샷을_사용한다() {
        stubSurroundings();
        when(financialSnapshotRepository.findFirstBySymbolAndFsDivOrderByFiscalYearDesc(SYMBOL, "CFS"))
                .thenReturn(Optional.of(snap(2025)));
        when(financialSnapshotRepository.findBySymbolInAndFiscalYearAndFsDiv(anyList(), eq((short) 2025), eq("CFS")))
                .thenReturn(List.of(snap(2025)));

        service(0).computeAndSave(SYMBOL);

        ArgumentCaptor<BaseScoreInputs> inputs = ArgumentCaptor.forClass(BaseScoreInputs.class);
        verify(engine).compute(inputs.capture());
        assertThat(inputs.getValue().fiscalYear()).isEqualTo(2025);
        verify(financialSnapshotRepository).findFirstBySymbolAndFsDivOrderByFiscalYearDesc(SYMBOL, "CFS");
        verify(financialSnapshotRepository, never()).findBySymbolAndFiscalYearAndFsDiv(any(), any(), any());
        // 업종 모집단도 같은 최신 연도로 조회
        verify(financialSnapshotRepository)
                .findBySymbolInAndFiscalYearAndFsDiv(anyList(), eq((short) 2025), eq("CFS"));
    }

    @Test
    void 여러_fiscal_year가_있으면_가장_최신_연도의_스냅샷을_엔진에_넘긴다() {
        stubSurroundings();
        // 삽입 순서 ≠ 연도 순서. 파생 쿼리 계약(fiscal_year DESC LIMIT 1)을 실제 의미로 구현.
        List<FinancialSnapshot> rows = List.of(snap(2024), snap(2023), snap(2025));
        when(financialSnapshotRepository.findFirstBySymbolAndFsDivOrderByFiscalYearDesc(SYMBOL, "CFS"))
                .thenAnswer(inv -> rows.stream()
                        .filter(s -> s.getSymbol().equals(SYMBOL) && s.getFsDiv().equals("CFS"))
                        .max(Comparator.comparing(FinancialSnapshot::getFiscalYear)));
        when(financialSnapshotRepository.findBySymbolInAndFiscalYearAndFsDiv(anyList(), any(), eq("CFS")))
                .thenAnswer(inv -> {
                    Short y = inv.getArgument(1);
                    return rows.stream().filter(s -> s.getFiscalYear().equals(y)).toList();
                });

        service(0).computeAndSave(SYMBOL);

        ArgumentCaptor<BaseScoreInputs> inputs = ArgumentCaptor.forClass(BaseScoreInputs.class);
        verify(engine).compute(inputs.capture());
        assertThat(inputs.getValue().fiscalYear()).isEqualTo(2025); // 2024/2023 아님

        ArgumentCaptor<FinancialSnapshot> used = ArgumentCaptor.forClass(FinancialSnapshot.class);
        verify(metricsCalculator, atLeastOnce()).calculate(used.capture(), any());
        assertThat(used.getAllValues()).allSatisfy(s -> {
            assertThat(s.getFiscalYear()).isEqualTo((short) 2025);
            assertThat(s.getSource()).isEqualTo("TEST_FY2025");
        });
    }

    @Test
    void targetFiscalYear_설정값이_있으면_그_연도로_고정한다() {
        stubSurroundings();
        when(financialSnapshotRepository.findBySymbolAndFiscalYearAndFsDiv(SYMBOL, (short) 2024, "CFS"))
                .thenReturn(Optional.of(snap(2024)));
        when(financialSnapshotRepository.findBySymbolInAndFiscalYearAndFsDiv(anyList(), eq((short) 2024), eq("CFS")))
                .thenReturn(List.of(snap(2024)));

        service(2024).computeAndSave(SYMBOL);

        ArgumentCaptor<BaseScoreInputs> inputs = ArgumentCaptor.forClass(BaseScoreInputs.class);
        verify(engine).compute(inputs.capture());
        assertThat(inputs.getValue().fiscalYear()).isEqualTo(2024);
        verify(financialSnapshotRepository, never())
                .findFirstBySymbolAndFsDivOrderByFiscalYearDesc(any(), any());
    }

    @Test
    void 어느_회계연도에도_스냅샷이_없으면_예외이고_저장하지_않는다() {
        lenient().when(instrumentRepository.findById(SYMBOL)).thenReturn(Optional.of(instrument));
        lenient().when(instrument.getIndustry()).thenReturn(INDUSTRY);
        when(financialSnapshotRepository.findFirstBySymbolAndFsDivOrderByFiscalYearDesc(SYMBOL, "CFS"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(0).computeAndSave(SYMBOL))
                .isInstanceOf(AttractivenessException.class)
                .hasMessageContaining("재무 스냅샷 없음");

        verifyNoInteractions(engine);
        verify(attractivenessScoreRepository, never()).save(any());
    }
}
