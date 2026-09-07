package com.dipscore.backend.attractiveness;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.dipscore.backend.attractiveness.normalize.PercentileNormalizer;
import com.dipscore.backend.attractiveness.normalize.ZScoreNormalizer;

import org.springframework.stereotype.Component;

/**
 * 매력도 지수 "기본점수" 계산 엔진 (순수 로직, DB 비의존).
 *
 * <pre>
 * 기본점수 = 100 × Σ(가중치_i × 정규화_i) / Σ(가중치_i)
 *   - 펀더멘털 요인: 업종 percentile 정규화
 *   - 거시 요인: Z-score 정규화
 * 매력도 지수 = 기본점수 × 이벤트 조정계수
 * </pre>
 */
@Component
public class BaseScoreEngine {

    /** TODO: 이벤트 조정계수 미구현 (기획서 4-5). 감성분석 서비스 연동 전까지 중립 고정. */
    public static final double EVENT_COEFFICIENT_NEUTRAL = 1.0;

    private final AttractivenessProperties props;
    private final PercentileNormalizer percentileNormalizer;
    private final ZScoreNormalizer zScoreNormalizer;

    public BaseScoreEngine(AttractivenessProperties props,
                           PercentileNormalizer percentileNormalizer,
                           ZScoreNormalizer zScoreNormalizer) {
        this.props = props;
        this.percentileNormalizer = percentileNormalizer;
        this.zScoreNormalizer = zScoreNormalizer;
    }

    public AttractivenessResult compute(BaseScoreInputs in) {
        Map<String, FactorScore> factors = new LinkedHashMap<>();
        double weightSum = 0.0;
        double weightedScoreSum = 0.0;

        for (Factor factor : Factor.values()) {
            double weight = props.weightOf(factor);
            if (weight <= 0.0) {
                continue; // 가중치 0 → 요인 제외
            }
            FactorScore score = switch (factor.kind()) {
                case FUNDAMENTAL -> fundamental(factor, weight, in);
                case MACRO -> macro(factor, weight, in);
            };
            factors.put(factor.configKey(), score);
            weightSum += weight;
            weightedScoreSum += score.weighted();
        }

        double baseScore = weightSum > 0.0 ? 100.0 * weightedScoreSum / weightSum : 0.0;
        double attractiveness = baseScore * EVENT_COEFFICIENT_NEUTRAL;

        return new AttractivenessResult(
                in.symbol(), in.asOf(), props.engineVersion(),
                in.industry(), in.industryMetrics().size(),
                in.fiscalYear(), in.fsDiv(), in.closePrice(),
                factors, round2(baseScore), EVENT_COEFFICIENT_NEUTRAL, round2(attractiveness));
    }

    private FactorScore fundamental(Factor factor, double weight, BaseScoreInputs in) {
        BigDecimal raw = in.target().valueOf(factor);
        List<BigDecimal> population = in.industryMetrics().stream()
                .map(m -> m.valueOf(factor))
                .filter(Objects::nonNull)
                .toList();
        boolean available = raw != null && population.size() >= 2;
        double normalized = available
                ? percentileNormalizer.normalize(raw, population, factor.direction())
                : 0.5;
        return new FactorScore("FUNDAMENTAL", factor.direction().name(),
                raw == null ? null : raw.doubleValue(), null,
                normalized, weight, weight * normalized, available);
    }

    private FactorScore macro(Factor factor, double weight, BaseScoreInputs in) {
        List<BigDecimal> series = in.macroSeries().getOrDefault(factor, List.of());
        ZScoreNormalizer.Result r = zScoreNormalizer.normalize(series, factor.direction());
        boolean available = series.size() >= 2 && r.zScore() != null;
        return new FactorScore("MACRO", factor.direction().name(),
                r.current() == null ? null : r.current().doubleValue(), r.zScore(),
                r.normalized(), weight, weight * r.normalized(), available);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
