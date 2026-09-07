package com.dipscore.backend.attractiveness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * {@link BaseScoreEngine#compute} 입력. 저장소 조회 결과를 이미 로딩한 순수 데이터 묶음
 * (엔진은 DB 를 모른다 - 단위 테스트 용이).
 *
 * @param target          대상 종목의 펀더멘털 지표
 * @param industryMetrics 같은 업종 종목들의 펀더멘털 지표 (대상 포함) - percentile 모집단
 * @param macroSeries     거시 요인별 최근값 리스트 ([0] = 현재), 내림차순
 */
public record BaseScoreInputs(
        String symbol,
        String industry,
        int fiscalYear,
        String fsDiv,
        BigDecimal closePrice,
        Instant asOf,
        ValuationMetrics target,
        List<ValuationMetrics> industryMetrics,
        Map<Factor, List<BigDecimal>> macroSeries
) {}
