package com.dipscore.backend.entryscore;

import java.time.Instant;
import java.util.Map;

/**
 * 저점 진입 스코어 계산 결과.
 * {@code entryScore = filterPassed ? 100 × Σ(가중치·component) / Σ가중치 : 0}
 *
 * @param components 키 = {@link EntryComponent#configKey()}
 */
public record EntryScoreResult(
        String symbol,
        Instant asOf,
        String engineVersion,
        double reboundSignal,
        double technicalIndicator,
        double attractivenessComponent,
        boolean filterPassed,
        double entryScore,
        double entryThreshold,
        Map<String, ComponentScore> components
) {}
