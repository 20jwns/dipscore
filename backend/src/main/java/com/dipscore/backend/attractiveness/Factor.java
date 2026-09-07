package com.dipscore.backend.attractiveness;

/**
 * 매력도 지수 "기본점수" 1차 핵심 요인 (기획서 4-2에서 선별, 7개).
 *
 * <ul>
 *   <li>펀더멘털(업종 percentile 정규화): PER, PBR, 부채비율, ROE, 매출성장률</li>
 *   <li>거시(Z-score 정규화): 기준금리, 원/달러 환율</li>
 * </ul>
 *
 * <p>방향({@link Direction})은 요인의 <b>구조적 의미</b>라 코드에 고정한다.
 * 가중치는 튜닝 대상이라 {@code application.yml} 로 분리한다 (기획서 4-4의 4단계 방법론으로 확정 예정).
 * <p>{@code USD_KRW} 방향은 잠정적으로 LOWER_BETTER(원화 강세=안정) — 수출주 영향과 상충하므로 검증 대상.
 */
public enum Factor {

    PER("per", Kind.FUNDAMENTAL, Direction.LOWER_BETTER),
    PBR("pbr", Kind.FUNDAMENTAL, Direction.LOWER_BETTER),
    DEBT_TO_EQUITY("debt-to-equity", Kind.FUNDAMENTAL, Direction.LOWER_BETTER),
    ROE("roe", Kind.FUNDAMENTAL, Direction.HIGHER_BETTER),
    REVENUE_GROWTH("revenue-growth", Kind.FUNDAMENTAL, Direction.HIGHER_BETTER),
    BASE_RATE("base-rate", Kind.MACRO, Direction.LOWER_BETTER),
    USD_KRW("usd-krw", Kind.MACRO, Direction.LOWER_BETTER);

    private final String configKey;
    private final Kind kind;
    private final Direction direction;

    Factor(String configKey, Kind kind, Direction direction) {
        this.configKey = configKey;
        this.kind = kind;
        this.direction = direction;
    }

    public String configKey() {
        return configKey;
    }

    public Kind kind() {
        return kind;
    }

    public Direction direction() {
        return direction;
    }

    public enum Kind {
        FUNDAMENTAL, MACRO
    }

    public enum Direction {
        /** 값이 높을수록 매력적. */
        HIGHER_BETTER,
        /** 값이 낮을수록 매력적 (정규화 시 반전). */
        LOWER_BETTER
    }
}
