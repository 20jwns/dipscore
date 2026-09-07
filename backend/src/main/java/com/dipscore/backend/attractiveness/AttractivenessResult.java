package com.dipscore.backend.attractiveness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * 매력도 지수 계산 결과. {@code attractiveness = baseScore × eventCoefficient}.
 *
 * @param factors 요인별 상세, 키 = {@link Factor#configKey()}
 */
public record AttractivenessResult(
        String symbol,
        Instant asOf,
        String engineVersion,
        String industry,
        int industryPeerCount,
        int fiscalYear,
        String fsDiv,
        BigDecimal closePrice,
        Map<String, FactorScore> factors,
        double baseScore,
        double eventCoefficient,
        double attractiveness
) {}
