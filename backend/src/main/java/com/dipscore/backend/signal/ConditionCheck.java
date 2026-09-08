package com.dipscore.backend.signal;

/**
 * 매수 룰 개별 조건의 판정 상세.
 *
 * @param name      조건 설명 (예: "저점진입스코어 ≥ 70")
 * @param evaluated 실제로 평가된 조건인지 (false = 데이터/기능 부재로 판정에서 제외)
 * @param passed    통과 여부 (evaluated=false 면 의미 없음)
 * @param actual    실제 값 (evaluated=false 면 null)
 * @param threshold 기준 값 (evaluated=false 면 null)
 * @param note      비고 (nullable)
 */
public record ConditionCheck(
        String name,
        boolean evaluated,
        boolean passed,
        Double actual,
        Double threshold,
        String note
) {

    static ConditionCheck evaluated(String name, boolean passed, double actual, double threshold, String note) {
        return new ConditionCheck(name, true, passed, actual, threshold, note);
    }

    static ConditionCheck skipped(String name, String note) {
        return new ConditionCheck(name, false, false, null, null, note);
    }
}
