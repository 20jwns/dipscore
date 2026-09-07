package com.dipscore.backend.attractiveness.normalize;

import java.math.BigDecimal;
import java.util.List;

import com.dipscore.backend.attractiveness.Factor.Direction;

import org.springframework.stereotype.Component;

/**
 * 거시경제 변수 Z-score 정규화 (기획서 4-3). 최근 분포 창 대비 현재값의 표준화 점수를 구하고
 * {@code 0.5·(1 + tanh(z/2))} 로 (0,1) 에 매끄럽게 매핑한다 (z=0 → 0.5, z=±2 → ≈0.88/0.12).
 *
 * <p>{@code LOWER_BETTER} 요인은 z 부호를 반전한다 (기준금리가 낮을수록 점수 높음).
 * 표본이 2개 미만이거나 표준편차 0 이면 → 0.5(중립).
 */
@Component
public class ZScoreNormalizer {

    /**
     * @param recentValuesDesc 최근값부터 정렬된 관측치 ([0] = 현재). 분포(평균/표준편차)도 이 창으로 계산.
     */
    public Result normalize(List<BigDecimal> recentValuesDesc, Direction direction) {
        if (recentValuesDesc == null || recentValuesDesc.size() < 2) {
            BigDecimal current = (recentValuesDesc == null || recentValuesDesc.isEmpty())
                    ? null : recentValuesDesc.get(0);
            return new Result(current, null, 0.5);
        }
        double[] xs = recentValuesDesc.stream().mapToDouble(BigDecimal::doubleValue).toArray();
        double current = xs[0];

        double mean = 0.0;
        for (double x : xs) {
            mean += x;
        }
        mean /= xs.length;

        double sumSq = 0.0;
        for (double x : xs) {
            sumSq += (x - mean) * (x - mean);
        }
        double sd = Math.sqrt(sumSq / (xs.length - 1)); // 표본표준편차

        if (sd == 0.0) {
            return new Result(BigDecimal.valueOf(current), 0.0, 0.5);
        }
        double z = (current - mean) / sd;
        double directed = direction == Direction.LOWER_BETTER ? -z : z;
        double normalized = 0.5 * (1.0 + Math.tanh(directed / 2.0));
        return new Result(BigDecimal.valueOf(current), z, normalized);
    }

    /**
     * @param current    분포 창의 현재값 (null 가능)
     * @param zScore     표준화 점수 (표본 부족 시 null)
     * @param normalized [0,1] 정규화 점수
     */
    public record Result(BigDecimal current, Double zScore, double normalized) {
    }
}
