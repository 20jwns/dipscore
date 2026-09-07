package com.dipscore.backend.entryscore;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 순수 기술적 지표 계산 (Wilder ATR / Wilder RSI / Bollinger %b). DB·설정 비의존.
 * 입력 봉은 시간 오름차순([0] 가장 과거, 마지막이 최신)이어야 한다.
 */
@Component
public class TechnicalIndicatorCalculator {

    /**
     * @param bars   시간 오름차순 OHLC
     * @param period 보통 14
     * @return 최신 ATR (Wilder). 봉이 {@code period+1} 미만이면 0.
     */
    public double atr(List<double[]> bars, int period) {
        int n = bars.size();
        if (n < period + 1) {
            return 0.0;
        }
        double[] tr = new double[n];
        for (int i = 1; i < n; i++) {
            double high = bars.get(i)[1];
            double low = bars.get(i)[2];
            double prevClose = bars.get(i - 1)[3];
            tr[i] = Math.max(high - low, Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
        }
        double atr = 0.0;
        for (int i = 1; i <= period; i++) {
            atr += tr[i];
        }
        atr /= period;
        for (int i = period + 1; i < n; i++) {
            atr = (atr * (period - 1) + tr[i]) / period;
        }
        return atr;
    }

    /**
     * Wilder RSI 스냅샷.
     *
     * @param closes 시간 오름차순 종가
     * @param period 보통 14
     * @param recent 최근 판정 창 (최근 recent 개 RSI 중 최소값을 함께 반환)
     */
    public Rsi rsi(double[] closes, int period, int recent) {
        int n = closes.length;
        if (n < period + 2) {
            throw new IllegalArgumentException("RSI 계산에 종가 %d개 필요 (있음: %d)".formatted(period + 2, n));
        }
        double avgGain = 0.0;
        double avgLoss = 0.0;
        for (int i = 1; i <= period; i++) {
            double ch = closes[i] - closes[i - 1];
            avgGain += Math.max(ch, 0.0);
            avgLoss += Math.max(-ch, 0.0);
        }
        avgGain /= period;
        avgLoss /= period;

        double[] rsi = new double[n];
        rsi[period] = rsiFrom(avgGain, avgLoss);
        for (int i = period + 1; i < n; i++) {
            double ch = closes[i] - closes[i - 1];
            avgGain = (avgGain * (period - 1) + Math.max(ch, 0.0)) / period;
            avgLoss = (avgLoss * (period - 1) + Math.max(-ch, 0.0)) / period;
            rsi[i] = rsiFrom(avgGain, avgLoss);
        }

        double current = rsi[n - 1];
        double previous = rsi[n - 2];
        double minRecent = current;
        for (int i = Math.max(period, n - recent); i < n; i++) {
            minRecent = Math.min(minRecent, rsi[i]);
        }
        return new Rsi(current, previous, minRecent);
    }

    private static double rsiFrom(double avgGain, double avgLoss) {
        if (avgLoss == 0.0) {
            return avgGain == 0.0 ? 50.0 : 100.0;
        }
        double rs = avgGain / avgLoss;
        return 100.0 - 100.0 / (1.0 + rs);
    }

    /**
     * Bollinger %b 스냅샷. %b = (close - lower) / (upper - lower). 0=하단선, 1=상단선, 음수=하단 이탈.
     *
     * @param closes 시간 오름차순 종가
     * @param period 보통 20
     * @param k      표준편차 배수 (보통 2.0), 모표준편차 사용
     * @param recent 최근 판정 창 (최근 recent 개 %b 중 최소값을 함께 반환)
     */
    public Bollinger bollinger(double[] closes, int period, double k, int recent) {
        int n = closes.length;
        if (n < period + 1) {
            throw new IllegalArgumentException("볼린저 계산에 종가 %d개 필요 (있음: %d)".formatted(period + 1, n));
        }
        double[] pctB = new double[n];
        double lastLower = 0.0;
        double lastMiddle = 0.0;
        double lastUpper = 0.0;
        for (int i = period - 1; i < n; i++) {
            double sum = 0.0;
            for (int j = i - period + 1; j <= i; j++) {
                sum += closes[j];
            }
            double mean = sum / period;
            double var = 0.0;
            for (int j = i - period + 1; j <= i; j++) {
                double d = closes[j] - mean;
                var += d * d;
            }
            double sd = Math.sqrt(var / period);
            double lower = mean - k * sd;
            double upper = mean + k * sd;
            pctB[i] = upper > lower ? (closes[i] - lower) / (upper - lower) : 0.5;
            if (i == n - 1) {
                lastLower = lower;
                lastMiddle = mean;
                lastUpper = upper;
            }
        }
        double current = pctB[n - 1];
        double minRecent = current;
        for (int i = Math.max(period - 1, n - recent); i < n; i++) {
            minRecent = Math.min(minRecent, pctB[i]);
        }
        return new Bollinger(current, minRecent, lastLower, lastMiddle, lastUpper);
    }

    /** @param minRecent 최근 창 내 RSI 최소값 (과매도 진입 여부 판정용) */
    public record Rsi(double current, double previous, double minRecent) {}

    /** @param minRecent 최근 창 내 %b 최소값 (하단선 터치 여부 판정용) */
    public record Bollinger(double currentPctB, double minRecentPctB, double lower, double middle, double upper) {}
}
