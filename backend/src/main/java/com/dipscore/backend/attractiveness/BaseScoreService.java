package com.dipscore.backend.attractiveness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.dipscore.backend.attractiveness.persistence.AttractivenessScore;
import com.dipscore.backend.attractiveness.persistence.AttractivenessScoreRepository;
import com.dipscore.backend.marketdata.financial.FinancialSnapshot;
import com.dipscore.backend.marketdata.financial.FinancialSnapshotRepository;
import com.dipscore.backend.marketdata.instrument.Instrument;
import com.dipscore.backend.marketdata.instrument.InstrumentRepository;
import com.dipscore.backend.marketdata.macro.MacroIndicator;
import com.dipscore.backend.marketdata.macro.MacroIndicatorRepository;
import com.dipscore.backend.marketdata.price.PriceHistory;
import com.dipscore.backend.marketdata.price.PriceHistoryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매력도 기본점수 계산의 저장소 연동 오케스트레이션: 재무 스냅샷·시세·거시데이터를 로딩해
 * {@link BaseScoreEngine} 로 계산하고 {@code attractiveness_score} 에 저장한다.
 *
 * <p>{@code attractiveness.enabled=true} 일 때만 빈 생성 (테스트 컨텍스트엔 JPA 가 없어 미생성).
 */
@Service
@ConditionalOnProperty(prefix = "attractiveness", name = "enabled", havingValue = "true")
public class BaseScoreService {

    private static final Logger log = LoggerFactory.getLogger(BaseScoreService.class);

    /** 거시 요인 → macro_indicator.indicator_code 매핑. */
    private static final Map<Factor, String> MACRO_CODE = Map.of(
            Factor.BASE_RATE, "BASE_RATE",
            Factor.USD_KRW, "USD_KRW");

    private final AttractivenessProperties props;
    private final BaseScoreEngine engine;
    private final FundamentalMetricsCalculator metricsCalculator;
    private final InstrumentRepository instrumentRepository;
    private final FinancialSnapshotRepository financialSnapshotRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final MacroIndicatorRepository macroIndicatorRepository;
    private final AttractivenessScoreRepository attractivenessScoreRepository;
    private final ObjectMapper objectMapper;

    public BaseScoreService(AttractivenessProperties props,
                            BaseScoreEngine engine,
                            FundamentalMetricsCalculator metricsCalculator,
                            InstrumentRepository instrumentRepository,
                            FinancialSnapshotRepository financialSnapshotRepository,
                            PriceHistoryRepository priceHistoryRepository,
                            MacroIndicatorRepository macroIndicatorRepository,
                            AttractivenessScoreRepository attractivenessScoreRepository,
                            ObjectMapper objectMapper) {
        this.props = props;
        this.engine = engine;
        this.metricsCalculator = metricsCalculator;
        this.instrumentRepository = instrumentRepository;
        this.financialSnapshotRepository = financialSnapshotRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.macroIndicatorRepository = macroIndicatorRepository;
        this.attractivenessScoreRepository = attractivenessScoreRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AttractivenessResult computeAndSave(String symbol) {
        String fsDiv = props.fsDiv();
        int pinnedYear = props.targetFiscalYear(); // 0 = 자동(최신 fiscal_year), >0 = 고정

        Instrument instrument = instrumentRepository.findById(symbol)
                .orElseThrow(() -> new AttractivenessException("종목 없음: " + symbol));
        String industry = instrument.getIndustry();
        if (industry == null || industry.isBlank()) {
            throw new AttractivenessException("업종(industry) 미지정 종목이라 percentile 정규화 불가: " + symbol);
        }

        FinancialSnapshot targetSnapshot = (pinnedYear > 0
                ? financialSnapshotRepository.findBySymbolAndFiscalYearAndFsDiv(symbol, (short) pinnedYear, fsDiv)
                : financialSnapshotRepository.findFirstBySymbolAndFsDivOrderByFiscalYearDesc(symbol, fsDiv))
                .orElseThrow(() -> new AttractivenessException(pinnedYear > 0
                        ? "재무 스냅샷 없음: %s FY%d %s".formatted(symbol, pinnedYear, fsDiv)
                        : "재무 스냅샷 없음: %s %s (어느 회계연도에도)".formatted(symbol, fsDiv)));
        short fiscalYear = targetSnapshot.getFiscalYear();

        BigDecimal targetClose = latestClose(symbol);

        // 업종 모집단: 같은 industry 의 종목들 중 해당 연도 스냅샷이 있는 것
        List<String> peerSymbols = instrumentRepository.findByIndustryAndActiveTrue(industry).stream()
                .map(Instrument::getSymbol)
                .toList();
        List<ValuationMetrics> industryMetrics = new ArrayList<>();
        for (FinancialSnapshot fs : financialSnapshotRepository
                .findBySymbolInAndFiscalYearAndFsDiv(peerSymbols, fiscalYear, fsDiv)) {
            industryMetrics.add(metricsCalculator.calculate(fs, latestClose(fs.getSymbol())));
        }

        ValuationMetrics target = metricsCalculator.calculate(targetSnapshot, targetClose);

        Map<Factor, List<BigDecimal>> macroSeries = new EnumMap<>(Factor.class);
        Limit window = Limit.of(props.macroZscoreWindow());
        MACRO_CODE.forEach((factor, code) -> macroSeries.put(factor,
                macroIndicatorRepository.findByIndicatorCodeOrderByTsDesc(code, window).stream()
                        .map(MacroIndicator::getValue)
                        .toList()));

        Instant asOf = Instant.now();
        BaseScoreInputs inputs = new BaseScoreInputs(
                symbol, industry, fiscalYear, fsDiv, targetClose, asOf,
                target, industryMetrics, macroSeries);

        AttractivenessResult result = engine.compute(inputs);

        attractivenessScoreRepository.save(new AttractivenessScore(
                symbol, asOf,
                BigDecimal.valueOf(result.baseScore()),
                BigDecimal.valueOf(result.eventCoefficient()),
                BigDecimal.valueOf(result.attractiveness()),
                toJson(result), result.engineVersion()));

        log.info("[attractiveness] {} 기본점수 {} (peers={}, FY{})",
                symbol, result.baseScore(), result.industryPeerCount(), fiscalYear);
        return result;
    }

    private BigDecimal latestClose(String symbol) {
        return priceHistoryRepository.findBySymbolOrderByTsDesc(symbol).stream()
                .findFirst()
                .map(PriceHistory::getClose)
                .orElse(null);
    }

    private String toJson(AttractivenessResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new AttractivenessException("factor_breakdown 직렬화 실패: " + e.getMessage());
        }
    }
}
