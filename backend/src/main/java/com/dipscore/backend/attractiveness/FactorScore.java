package com.dipscore.backend.attractiveness;

/**
 * 한 요인의 계산 상세 (감사/화면 breakdown 용).
 *
 * @param kind       FUNDAMENTAL / MACRO
 * @param direction  HIGHER_BETTER / LOWER_BETTER
 * @param raw        원지표 값 (PER 배수, ROE 비율, 금리 % 등). 계산 불가 시 null
 * @param zScore     거시 요인의 표준화 점수 (펀더멘털이면 null)
 * @param normalized [0,1] 정규화 점수
 * @param weight     설정 가중치
 * @param weighted   weight × normalized (최종 정규화 전)
 * @param available  raw 값과 충분한 모집단/표본이 있었는지 (false 면 normalized=0.5 중립 처리)
 */
public record FactorScore(
        String kind,
        String direction,
        Double raw,
        Double zScore,
        double normalized,
        double weight,
        double weighted,
        boolean available
) {}
