package com.dipscore.backend.entryscore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;

import com.dipscore.backend.entryscore.TechnicalIndicatorCalculator.Bollinger;
import com.dipscore.backend.entryscore.TechnicalIndicatorCalculator.Rsi;

import org.junit.jupiter.api.Test;

class TechnicalIndicatorCalculatorTest {

    private final TechnicalIndicatorCalculator calc = new TechnicalIndicatorCalculator();

    private static List<double[]> ohlc(double open, double high, double low, double close, int bars) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < bars; i++) {
            out.add(new double[] {open, high, low, close});
        }
        return out;
    }

    private static double[] ramp(double from, double step, int n) {
        double[] c = new double[n];
        for (int i = 0; i < n; i++) {
            c[i] = from + step * i;
        }
        return c;
    }

    private static double[] flat(double v, int n) {
        return ramp(v, 0.0, n);
    }

    @Test
    void atr_모든봉_TR이_같으면_ATR도_같다() {
        // open=close=100, high=105, low=95 → 매 봉 TR = max(10, 5, 5) = 10
        assertThat(calc.atr(ohlc(100, 105, 95, 100, 30), 14)).isEqualTo(10.0);
    }

    @Test
    void rsi_상승만_하면_100_하락만_하면_0_변동없으면_50() {
        assertThat(calc.rsi(ramp(100, 1, 20), 14, 10).current()).isEqualTo(100.0);
        assertThat(calc.rsi(ramp(120, -1, 20), 14, 10).current()).isEqualTo(0.0);
        assertThat(calc.rsi(flat(100, 20), 14, 10).current()).isEqualTo(50.0);
    }

    @Test
    void rsi_봉_부족하면_예외() {
        assertThatThrownBy(() -> calc.rsi(flat(100, 10), 14, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bollinger_변동_없으면_pctB_05() {
        Bollinger b = calc.bollinger(flat(100, 25), 20, 2.0, 10);
        assertThat(b.currentPctB()).isEqualTo(0.5);
        assertThat(b.minRecentPctB()).isEqualTo(0.5);
        assertThat(b.lower()).isEqualTo(100.0);
        assertThat(b.upper()).isEqualTo(100.0);
    }

    @Test
    void bollinger_마지막_급락은_하단선_아래_pctB_음수() {
        double[] c = new double[25];
        for (int i = 0; i < 24; i++) {
            c[i] = 100.0;
        }
        c[24] = 88.0; // 급락
        Bollinger b = calc.bollinger(c, 20, 2.0, 10);
        assertThat(b.currentPctB()).isNegative();
        assertThat(b.minRecentPctB()).isEqualTo(b.currentPctB());
    }

    @Test
    void rsi_과매도_후_반등이면_current가_prev보다_크다() {
        // 15개 하락 후 5개 반등
        double[] down = ramp(100, -3, 16);      // 100 → 55
        double[] c = new double[21];
        System.arraycopy(down, 0, c, 0, 16);
        for (int i = 16; i < 21; i++) {
            c[i] = c[i - 1] + 2.0;               // 반등
        }
        Rsi r = calc.rsi(c, 14, 10);
        assertThat(r.minRecent()).isLessThan(30.0);       // 과매도 도달
        assertThat(r.current()).isGreaterThan(r.previous()); // 반등 중
    }
}
