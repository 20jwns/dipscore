package com.dipscore.backend.attractiveness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.List;

import com.dipscore.backend.attractiveness.Factor.Direction;
import com.dipscore.backend.attractiveness.normalize.PercentileNormalizer;
import com.dipscore.backend.attractiveness.normalize.ZScoreNormalizer;

import org.junit.jupiter.api.Test;

class NormalizerTest {

    private final PercentileNormalizer percentile = new PercentileNormalizer();
    private final ZScoreNormalizer zscore = new ZScoreNormalizer();

    private static List<BigDecimal> nums(double... v) {
        return java.util.Arrays.stream(v).mapToObj(BigDecimal::valueOf).toList();
    }

    @Test
    void percentile_HIGHER_BETTER_는_모집단_상위일수록_1에_가깝다() {
        List<BigDecimal> pop = nums(10, 20, 30, 40, 50);
        assertThat(percentile.normalize(BigDecimal.valueOf(50), pop, Direction.HIGHER_BETTER))
                .isCloseTo(0.9, within(0.001)); // (4 below + 0.5 self)/5
        assertThat(percentile.normalize(BigDecimal.valueOf(10), pop, Direction.HIGHER_BETTER))
                .isCloseTo(0.1, within(0.001));
    }

    @Test
    void percentile_LOWER_BETTER_는_순위를_반전한다() {
        List<BigDecimal> pop = nums(10, 20, 30, 40, 50);
        assertThat(percentile.normalize(BigDecimal.valueOf(10), pop, Direction.LOWER_BETTER))
                .isCloseTo(0.9, within(0.001)); // 낮은 PER = 매력적
    }

    @Test
    void percentile_모집단_2개미만이거나_null이면_중립() {
        assertThat(percentile.normalize(BigDecimal.ONE, nums(1), Direction.HIGHER_BETTER)).isEqualTo(0.5);
        assertThat(percentile.normalize(null, nums(1, 2, 3), Direction.HIGHER_BETTER)).isEqualTo(0.5);
    }

    @Test
    void zscore_현재값이_평균이면_05_HIGHER_BETTER면_고평가일수록_1쪽() {
        // 평균 30, 현재값(=[0]) 30 → z=0 → 0.5
        assertThat(zscore.normalize(nums(30, 10, 20, 30, 40, 50), Direction.HIGHER_BETTER).normalized())
                .isCloseTo(0.5, within(0.001));
        // 현재값 50 (평균 위) → HIGHER_BETTER → > 0.5
        ZScoreNormalizer.Result high = zscore.normalize(nums(50, 10, 20, 30, 40), Direction.HIGHER_BETTER);
        assertThat(high.zScore()).isPositive();
        assertThat(high.normalized()).isGreaterThan(0.5);
        // 같은 상황 LOWER_BETTER → < 0.5
        assertThat(zscore.normalize(nums(50, 10, 20, 30, 40), Direction.LOWER_BETTER).normalized())
                .isLessThan(0.5);
    }

    @Test
    void zscore_표본_1개면_중립() {
        assertThat(zscore.normalize(nums(3.5), Direction.LOWER_BETTER).normalized()).isEqualTo(0.5);
    }
}
