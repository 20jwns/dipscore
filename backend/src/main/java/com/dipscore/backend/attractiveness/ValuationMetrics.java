package com.dipscore.backend.attractiveness;

import java.math.BigDecimal;

/**
 * 한 종목의 펀더멘털 지표 묶음. 계산 불가한 값은 null (예: 순이익 ≤ 0 이면 PER null).
 */
public record ValuationMetrics(
        String symbol,
        BigDecimal per,
        BigDecimal pbr,
        BigDecimal debtToEquity,
        BigDecimal roe,
        BigDecimal revenueGrowth
) {

    /** {@link Factor} 에 대응하는 raw 값. 펀더멘털 요인만 유효. */
    public BigDecimal valueOf(Factor factor) {
        return switch (factor) {
            case PER -> per;
            case PBR -> pbr;
            case DEBT_TO_EQUITY -> debtToEquity;
            case ROE -> roe;
            case REVENUE_GROWTH -> revenueGrowth;
            default -> throw new IllegalArgumentException("펀더멘털 요인이 아님: " + factor);
        };
    }
}
