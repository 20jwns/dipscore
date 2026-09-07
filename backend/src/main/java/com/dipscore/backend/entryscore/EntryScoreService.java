package com.dipscore.backend.entryscore;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.dipscore.backend.attractiveness.persistence.AttractivenessScore;
import com.dipscore.backend.attractiveness.persistence.AttractivenessScoreRepository;
import com.dipscore.backend.entryscore.EntryScoreInputs.Bar;
import com.dipscore.backend.entryscore.persistence.EntryScore;
import com.dipscore.backend.entryscore.persistence.EntryScoreRepository;
import com.dipscore.backend.marketdata.instrument.InstrumentRepository;
import com.dipscore.backend.marketdata.price.PriceHistory;
import com.dipscore.backend.marketdata.price.PriceHistoryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저점 진입 스코어의 저장소 연동 오케스트레이션: 일봉 시세 + 최신 매력도 기본점수를 로딩해
 * {@link EntryScoreEngine} 로 계산하고 {@code entry_score} 에 저장한다.
 *
 * <p>{@code entry-score.enabled=true} 일 때만 빈 생성 (테스트 컨텍스트엔 JPA 가 없어 미생성).
 */
@Service
@ConditionalOnProperty(prefix = "entry-score", name = "enabled", havingValue = "true")
public class EntryScoreService {

    private static final Logger log = LoggerFactory.getLogger(EntryScoreService.class);

    private final EntryScoreEngine engine;
    private final EntryScoreProperties props;
    private final InstrumentRepository instrumentRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final AttractivenessScoreRepository attractivenessScoreRepository;
    private final EntryScoreRepository entryScoreRepository;
    private final ObjectMapper objectMapper;

    public EntryScoreService(EntryScoreEngine engine,
                             EntryScoreProperties props,
                             InstrumentRepository instrumentRepository,
                             PriceHistoryRepository priceHistoryRepository,
                             AttractivenessScoreRepository attractivenessScoreRepository,
                             EntryScoreRepository entryScoreRepository,
                             ObjectMapper objectMapper) {
        this.engine = engine;
        this.props = props;
        this.instrumentRepository = instrumentRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.attractivenessScoreRepository = attractivenessScoreRepository;
        this.entryScoreRepository = entryScoreRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EntryScoreResult computeAndSave(String symbol) {
        if (instrumentRepository.findById(symbol).isEmpty()) {
            throw new EntryScoreException("종목 없음: " + symbol);
        }

        List<Bar> bars = priceHistoryRepository.findBySymbolAndOpenNotNullOrderByTsAsc(symbol).stream()
                .map(EntryScoreService::toBar)
                .toList();
        if (bars.size() < engine.minBarsRequired()) {
            throw new EntryScoreException(
                    "일봉 부족: %s 최소 %d개 필요, 있음 %d개 (POST /api/admin/price-backfill 로 백필 필요)"
                            .formatted(symbol, engine.minBarsRequired(), bars.size()));
        }

        AttractivenessScore attractiveness = attractivenessScoreRepository
                .findFirstBySymbolOrderByAsOfDesc(symbol)
                .orElseThrow(() -> new EntryScoreException(
                        "매력도 점수 없음: %s (POST /api/attractiveness/%s 먼저 실행)".formatted(symbol, symbol)));

        Instant asOf = Instant.now();
        EntryScoreResult result = engine.compute(
                new EntryScoreInputs(symbol, asOf, bars, attractiveness.getBaseScore()));

        entryScoreRepository.save(new EntryScore(
                symbol, asOf,
                BigDecimal.valueOf(result.reboundSignal()),
                BigDecimal.valueOf(result.technicalIndicator()),
                BigDecimal.valueOf(result.attractivenessComponent()),
                BigDecimal.valueOf(result.entryScore()),
                result.filterPassed(),
                toJson(result), result.engineVersion()));

        log.info("[entry-score] {} entry_score={} (rebound={}, tech={}, attr={}, filter={})",
                symbol, result.entryScore(), result.reboundSignal(), result.technicalIndicator(),
                result.attractivenessComponent(), result.filterPassed());
        return result;
    }

    private static Bar toBar(PriceHistory p) {
        return new Bar(p.getTs(),
                p.getOpen().doubleValue(), p.getHigh().doubleValue(),
                p.getLow().doubleValue(), p.getClose().doubleValue());
    }

    private String toJson(EntryScoreResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new EntryScoreException("factor_breakdown 직렬화 실패: " + e.getMessage());
        }
    }
}
