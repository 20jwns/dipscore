package com.dipscore.backend.attractiveness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.dipscore.backend.attractiveness.normalize.PercentileNormalizer;
import com.dipscore.backend.attractiveness.normalize.ZScoreNormalizer;
import com.dipscore.backend.marketdata.financial.FinancialSnapshot;

import org.junit.jupiter.api.Test;

/**
 * 삼성전자 기본점수 스모크. 재무값은 FY2024 연결 공개 공시치, peer 는 SK하이닉스·한미반도체,
 * 거시는 검증된 ECOS 관측치. 엔진이 percentile + Z-score + 가중합으로 사람이 검산 가능한
 * 값(≈47/100)을 내는지 확인한다.
 */
class BaseScoreEngineTest {

    private static final FundamentalMetricsCalculator CALC = new FundamentalMetricsCalculator();

    private static AttractivenessProperties props() {
        Map<String, Double> w = new LinkedHashMap<>();
        w.put("per", 0.20);
        w.put("pbr", 0.15);
        w.put("debt-to-equity", 0.15);
        w.put("roe", 0.20);
        w.put("revenue-growth", 0.15);
        w.put("base-rate", 0.075);
        w.put("usd-krw", 0.075);
        return new AttractivenessProperties(true, "base-v1", 2024, "CFS", 24, w);
    }

    private static BaseScoreEngine engine() {
        return new BaseScoreEngine(props(), new PercentileNormalizer(), new ZScoreNormalizer());
    }

    // 단위: 백만원 (financial_snapshot 규약)
    private static FinancialSnapshot snap(String symbol, long revenue, long opInc, long netInc,
                                          long equity, long liab, long priorRev, long shares) {
        return FinancialSnapshot.of(symbol, (short) 2024, "CFS",
                BigDecimal.valueOf(revenue), BigDecimal.valueOf(opInc), BigDecimal.valueOf(netInc),
                BigDecimal.valueOf(equity), BigDecimal.valueOf(liab), BigDecimal.valueOf(priorRev),
                shares, "KRW", "TEST");
    }

    private static List<BigDecimal> series(double... values) {
        return Arrays.stream(values).mapToObj(BigDecimal::valueOf).toList();
    }

    @Test
    void 삼성전자_기본점수_스모크() {
        ValuationMetrics samsung = CALC.calculate(
                snap("005930", 300870903, 32725961, 33621363, 402192200, 112339700, 258935494, 5969782550L),
                BigDecimal.valueOf(257000));
        ValuationMetrics skhynix = CALC.calculate(
                snap("000660", 66192960, 23467319, 19788681, 73895700, 45939400, 32765719, 728002365L),
                BigDecimal.valueOf(300000));
        ValuationMetrics hanmi = CALC.calculate(
                snap("042700", 558917, 255392, 152615, 556381, 174871, 159009, 97126000L),
                BigDecimal.valueOf(222000));

        // 거시 시계열: [0] = 최근 (macro_indicator OrderByTsDesc 규약)
        Map<Factor, List<BigDecimal>> macro = new java.util.EnumMap<>(Factor.class);
        macro.put(Factor.BASE_RATE, series(2.75, 2.75, 2.75, 3.0, 3.0, 3.0, 3.25, 3.5, 3.5, 3.5));
        macro.put(Factor.USD_KRW, series(1376.5, 1380.3, 1384.6, 1383.1, 1380.6, 1383.6, 1393.0, 1402.5, 1411.0, 1415.2));

        BaseScoreInputs in = new BaseScoreInputs("005930", "반도체", 2024, "CFS",
                BigDecimal.valueOf(257000), Instant.parse("2026-09-07T00:00:00Z"),
                samsung, List.of(samsung, skhynix, hanmi), macro);

        AttractivenessResult r = engine().compute(in);

        // 7개 요인 모두 계산됨
        assertThat(r.factors()).containsOnlyKeys(
                "per", "pbr", "debt-to-equity", "roe", "revenue-growth", "base-rate", "usd-krw");
        assertThat(r.factors().values()).allMatch(FactorScore::available);

        // 사람 검산: PER/PBR 은 3사 중 중앙(자기 포함 mid-rank) → 0.5,
        //           부채비율은 삼성전자가 최저 → ≈0.833, ROE·매출성장률은 최저 → ≈0.167
        assertThat(r.factors().get("per").normalized()).isCloseTo(0.5, within(0.01));
        assertThat(r.factors().get("pbr").normalized()).isCloseTo(0.5, within(0.01));
        assertThat(r.factors().get("debt-to-equity").normalized()).isCloseTo(0.8333, within(0.01));
        assertThat(r.factors().get("roe").normalized()).isCloseTo(0.1667, within(0.01));
        assertThat(r.factors().get("revenue-growth").normalized()).isCloseTo(0.1667, within(0.01));

        // 거시: 최근 기준금리·환율이 창 평균보다 낮음(LOWER_BETTER) → z<0, 정규화 > 0.5
        assertThat(r.factors().get("base-rate").zScore()).isNotNull().isNegative();
        assertThat(r.factors().get("base-rate").normalized()).isGreaterThan(0.6);
        assertThat(r.factors().get("usd-krw").normalized()).isGreaterThan(0.6);

        // 종합: ≈47점 (60점 매수 임계 미달 - 두 반도체 peer 대비 밸류·성장 열위)
        assertThat(r.baseScore()).isCloseTo(47.0, within(1.0));
        assertThat(r.eventCoefficient()).isEqualTo(1.0);
        assertThat(r.attractiveness()).isEqualTo(r.baseScore());
        assertThat(r.industryPeerCount()).isEqualTo(3);
        assertThat(r.engineVersion()).isEqualTo("base-v1");
    }

    @Test
    void 순이익_적자면_PER은_null_ROE는_음수() {
        ValuationMetrics loss = CALC.calculate(
                snap("A", 1000, -50, -100, 500, 300, 900, 1_000_000L), BigDecimal.valueOf(1000));

        assertThat(loss.per()).isNull();                                  // PER 은 손실이면 정의 불가
        assertThat(loss.pbr()).isNotNull().isPositive();                  // PBR 은 자본 양수면 계산됨
        assertThat(loss.roe()).isNotNull().isNegative();                  // ROE = -100/500 = -0.2
        assertThat(loss.debtToEquity()).isEqualByComparingTo("0.6");      // 300/500
        assertThat(loss.revenueGrowth().doubleValue()).isCloseTo(0.1111, within(0.001)); // (1000-900)/900
    }

    @Test
    void 가중치_0인_요인은_계산에서_제외된다() {
        Map<String, Double> w = new LinkedHashMap<>();
        w.put("per", 1.0); // per 만
        AttractivenessProperties onlyPer = new AttractivenessProperties(true, "base-v1", 2024, "CFS", 24, w);

        ValuationMetrics a = CALC.calculate(snap("A", 100, 10, 10, 100, 10, 90, 1_000L), BigDecimal.valueOf(100));
        ValuationMetrics b = CALC.calculate(snap("B", 100, 10, 20, 100, 10, 90, 1_000L), BigDecimal.valueOf(100));

        AttractivenessResult r = new BaseScoreEngine(onlyPer, new PercentileNormalizer(), new ZScoreNormalizer())
                .compute(new BaseScoreInputs("A", "x", 2024, "CFS", BigDecimal.valueOf(100),
                        Instant.now(), a, List.of(a, b), Map.of()));

        assertThat(r.factors()).containsOnlyKeys("per");
    }
}
