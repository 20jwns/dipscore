package com.dipscore.backend.entryscore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.dipscore.backend.entryscore.EntryScoreInputs.Bar;

import org.junit.jupiter.api.Test;

class EntryScoreEngineTest {

    private static EntryScoreProperties props() {
        Map<String, Double> w = new LinkedHashMap<>();
        w.put("rebound-signal", 0.3);
        w.put("technical-indicator", 0.3);
        w.put("attractiveness", 0.4);
        return new EntryScoreProperties(true, "entry-v1", w,
                14, 14, 30.0, 20, 2.0, 10, 20, 0.5, 1.5, 3.0, 60.0, 70.0);
    }

    private static EntryScoreEngine engine() {
        return new EntryScoreEngine(props(), new TechnicalIndicatorCalculator());
    }

    // ── 시리즈 빌더 ──
    private static double[] ramp(double from, double to, int n) {
        double[] c = new double[n];
        for (int i = 0; i < n; i++) {
            c[i] = from + (to - from) * i / (n - 1);
        }
        return c;
    }

    private static double[] concat(double[]... arrs) {
        int len = Arrays.stream(arrs).mapToInt(a -> a.length).sum();
        double[] out = new double[len];
        int p = 0;
        for (double[] a : arrs) {
            System.arraycopy(a, 0, out, p, a.length);
            p += a.length;
        }
        return out;
    }

    /** 봉 생성: 장중 변동폭은 당일 종가 변화량에 비례 (평탄하면 도지 → ATR≈0). */
    private static List<Bar> bars(double[] closes) {
        List<Bar> out = new ArrayList<>(closes.length);
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < closes.length; i++) {
            double c = closes[i];
            double open = i == 0 ? c : closes[i - 1];
            double overshoot = Math.abs(c - open) * 0.35;
            out.add(new Bar(t0.plus(i, ChronoUnit.DAYS),
                    open, Math.max(open, c) + overshoot, Math.min(open, c) - overshoot, c));
        }
        return out;
    }

    private static List<Bar> flat(int n) {
        double[] c = new double[n];
        Arrays.fill(c, 100.0);
        return bars(c);
    }

    /** 40봉 상승 → 5봉 완만한 눌림(≈1×ATR) → 4봉 반등. RSI 과매도까지는 안 감. */
    private static List<Bar> mildDip() {
        return bars(concat(ramp(100, 140, 40), ramp(140, 134, 5), ramp(134, 138, 4)));
    }

    /** 완만한 상승만. */
    private static List<Bar> steadyUp() {
        return bars(ramp(100, 160, 60));
    }

    /** 30봉 횡보 → 12봉 급락(RSI 과매도 + 볼린저 하단 이탈) → 4봉 반등. */
    private static List<Bar> oversoldBounce() {
        return bars(concat(ramp(100, 100, 30), ramp(100, 70, 12), ramp(70, 78, 4)));
    }

    @Test
    void 공식_일관성_entryScore가_가중합과_일치한다() {
        EntryScoreResult r = engine().compute(
                new EntryScoreInputs("005930", Instant.now(), mildDip(), BigDecimal.valueOf(70)));

        double expected = 100.0 * (0.3 * r.reboundSignal() + 0.3 * r.technicalIndicator()
                + 0.4 * r.attractivenessComponent()) / (0.3 + 0.3 + 0.4);
        assertThat(r.entryScore()).isCloseTo(expected, within(0.1));

        assertThat(r.attractivenessComponent()).isEqualTo(0.7);
        assertThat(r.filterPassed()).isTrue();
        assertThat(r.components()).containsOnlyKeys("rebound-signal", "technical-indicator", "attractiveness");
        assertThat(r.reboundSignal()).isBetween(0.0, 1.0);
        assertThat(r.technicalIndicator()).isBetween(0.0, 1.0);
        assertThat(r.entryThreshold()).isEqualTo(70.0);
    }

    @Test
    void 매력도_1차필터_미통과면_entryScore_0이지만_컴포넌트는_계산된다() {
        EntryScoreResult r = engine().compute(
                new EntryScoreInputs("X", Instant.now(), mildDip(), BigDecimal.valueOf(45)));

        assertThat(r.filterPassed()).isFalse();
        assertThat(r.entryScore()).isEqualTo(0.0);
        assertThat(r.components()).hasSize(3);
        assertThat(r.reboundSignal()).isGreaterThan(0.0); // 눌림 있으니 반등신호는 계산됨
        assertThat(r.attractivenessComponent()).isEqualTo(0.45);
    }

    @Test
    void 매력도_null이면_필터_불통과_entryScore_0() {
        EntryScoreResult r = engine().compute(
                new EntryScoreInputs("X", Instant.now(), mildDip(), null));
        assertThat(r.filterPassed()).isFalse();
        assertThat(r.entryScore()).isEqualTo(0.0);
        assertThat(r.attractivenessComponent()).isEqualTo(0.0);
    }

    @Test
    void 눌림있는_시리즈가_평탄_상승_시리즈보다_반등신호가_높다() {
        double dip = engine().compute(inputs(mildDip())).reboundSignal();
        double flat = engine().compute(inputs(flat(60))).reboundSignal();
        double up = engine().compute(inputs(steadyUp())).reboundSignal();
        assertThat(dip).isGreaterThan(0.7);          // 0.5~1.5×ATR 눌림 → plateau 근처
        assertThat(flat).isLessThan(0.5).isLessThan(dip);
        assertThat(up).isLessThan(0.5).isLessThan(dip);
    }

    @Test
    void 과매도후반등_시리즈가_상승추세_시리즈보다_기술적지표가_높다() {
        double bounce = engine().compute(inputs(oversoldBounce())).technicalIndicator();
        double up = engine().compute(inputs(steadyUp())).technicalIndicator();
        assertThat(bounce).isGreaterThan(0.5);
        assertThat(bounce).isGreaterThan(up);
    }

    @Test
    void 일봉이_부족하면_예외() {
        assertThat(engine().minBarsRequired()).isEqualTo(31);
        assertThatThrownBy(() -> engine().compute(inputs(bars(ramp(100, 110, 20)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("31");
    }

    private static EntryScoreInputs inputs(List<Bar> bars) {
        return new EntryScoreInputs("A", Instant.now(), bars, BigDecimal.valueOf(65));
    }
}
