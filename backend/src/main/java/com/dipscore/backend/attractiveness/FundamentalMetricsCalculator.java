package com.dipscore.backend.attractiveness;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import com.dipscore.backend.marketdata.financial.FinancialSnapshot;

import org.springframework.stereotype.Component;

/**
 * 재무 스냅샷(백만원 단위) + 현재가(원)로 밸류에이션/재무 지표를 계산한다.
 *
 * <ul>
 *   <li>PER = 시가총액 / 당기순이익  (순이익 ≤ 0 → null)</li>
 *   <li>PBR = 시가총액 / 자본총계</li>
 *   <li>부채비율(D/E) = 부채총계 / 자본총계</li>
 *   <li>ROE = 당기순이익 / 자본총계</li>
 *   <li>매출성장률 = (당기 매출 − 전기 매출) / 전기 매출</li>
 * </ul>
 */
@Component
public class FundamentalMetricsCalculator {

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000L);

    /**
     * @param snapshot     재무 스냅샷 (금액 백만원)
     * @param latestClose  최근 종가 (원). null 이면 PER/PBR null.
     */
    public ValuationMetrics calculate(FinancialSnapshot snapshot, BigDecimal latestClose) {
        BigDecimal equity = snapshot.getTotalEquity();
        BigDecimal netIncome = snapshot.getNetIncome();
        BigDecimal liabilities = snapshot.getTotalLiabilities();
        BigDecimal revenue = snapshot.getRevenue();
        BigDecimal priorRevenue = snapshot.getPriorRevenue();
        Long shares = snapshot.getSharesOutstanding();

        BigDecimal marketCapMillions = null;
        if (latestClose != null && latestClose.signum() > 0 && shares != null && shares > 0) {
            // 원 × 주식수 → 백만원
            marketCapMillions = latestClose.multiply(BigDecimal.valueOf(shares))
                    .divide(MILLION, MC);
        }

        return new ValuationMetrics(
                snapshot.getSymbol(),
                ratio(marketCapMillions, positiveOrNull(netIncome)),
                ratio(marketCapMillions, positiveOrNull(equity)),
                ratio(liabilities, positiveOrNull(equity)),
                ratio(netIncome, positiveOrNull(equity)),
                growth(revenue, priorRevenue)
        );
    }

    private static BigDecimal positiveOrNull(BigDecimal v) {
        return (v != null && v.signum() > 0) ? v : null;
    }

    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() == 0) {
            return null;
        }
        return numerator.divide(denominator, MC);
    }

    private static BigDecimal growth(BigDecimal current, BigDecimal prior) {
        if (current == null || prior == null || prior.signum() <= 0) {
            return null;
        }
        return current.subtract(prior).divide(prior, MC);
    }
}
