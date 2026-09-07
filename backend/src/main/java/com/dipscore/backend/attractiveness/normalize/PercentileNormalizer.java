package com.dipscore.backend.attractiveness.normalize;

import java.math.BigDecimal;
import java.util.List;

import com.dipscore.backend.attractiveness.Factor.Direction;

import org.springframework.stereotype.Component;

/**
 * 업종 percentile 정규화 (기획서 4-3). 모집단(같은 업종 종목들의 해당 지표값) 대비
 * 대상값의 순위를 [0,1] 로 매핑한다. 이상치에 강함.
 *
 * <p>{@code LOWER_BETTER} 요인은 순위를 반전한다 (PER 이 낮을수록 점수 높음).
 * 모집단이 2개 미만이거나 대상값이 null 이면 정보 없음 → 0.5(중립).
 */
@Component
public class PercentileNormalizer {

    /**
     * @param value      대상 종목의 지표값 (모집단에 포함되어 있어야 함)
     * @param population  같은 업종의 지표값들 (null 제외해서 전달)
     */
    public double normalize(BigDecimal value, List<BigDecimal> population, Direction direction) {
        if (value == null || population == null || population.size() < 2) {
            return 0.5;
        }
        double rank = 0.0;
        for (BigDecimal p : population) {
            int c = p.compareTo(value);
            if (c < 0) {
                rank += 1.0;
            } else if (c == 0) {
                rank += 0.5;
            }
        }
        double percentile = rank / population.size(); // (0,1), 높을수록 모집단 상위
        return direction == Direction.LOWER_BETTER ? 1.0 - percentile : percentile;
    }
}
