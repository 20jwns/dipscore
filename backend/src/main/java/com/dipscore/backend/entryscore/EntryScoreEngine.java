package com.dipscore.backend.entryscore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.dipscore.backend.entryscore.EntryScoreInputs.Bar;
import com.dipscore.backend.entryscore.TechnicalIndicatorCalculator.Bollinger;
import com.dipscore.backend.entryscore.TechnicalIndicatorCalculator.Rsi;

import org.springframework.stereotype.Component;

/**
 * 저점 진입 스코어 계산 엔진 (순수 로직, DB 비의존 — 기획서 5장).
 *
 * <pre>
 * 저점 진입 스코어 = (반등신호 × A) + (기술적지표 × B) + (매력도지수 × C)
 *   반등신호   : 최근 고점 대비 낙폭을 ATR 배수로 환산 → 0.5~1.5×ATR 구간에서 최고점(plateau)
 *   기술적지표 : RSI(과매도 30 이하 후 상향돌파) + 볼린저(20일 하단 터치 후 복귀) 두 서브점수 평균
 *   매력도지수 : attractiveness_score.base_score/100 (1차 필터 base_score≥60 + 2차 가중요소 C)
 * entryScore = filterPassed ? 100 × Σ(가중치·component) / Σ가중치 : 0
 * </pre>
 *
 * 서브 공식의 세부 상수는 1차 초안이며 백테스트(기획서 5-3 4단계 방법론)로 튜닝 대상이다.
 */
@Component
public class EntryScoreEngine {

    private final EntryScoreProperties props;
    private final TechnicalIndicatorCalculator indicators;

    public EntryScoreEngine(EntryScoreProperties props, TechnicalIndicatorCalculator indicators) {
        this.props = props;
        this.indicators = indicators;
    }

    /** 지표 계산에 필요한 최소 일봉 수. */
    public int minBarsRequired() {
        int longest = Math.max(Math.max(props.atrPeriod() + 1, props.rsiPeriod() + 2), props.bollingerPeriod() + 1);
        return Math.max(longest, props.reboundLookback()) + props.recentWindow();
    }

    public EntryScoreResult compute(EntryScoreInputs in) {
        List<Bar> bars = in.dailyBars();
        if (bars == null || bars.size() < minBarsRequired()) {
            throw new IllegalArgumentException(
                    "일봉이 부족합니다: 최소 %d개 필요, 있음 %d개".formatted(minBarsRequired(), bars == null ? 0 : bars.size()));
        }
        int n = bars.size();
        double[] closes = new double[n];
        List<double[]> ohlc = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Bar b = bars.get(i);
            closes[i] = b.close();
            ohlc.add(new double[] {b.open(), b.high(), b.low(), b.close()});
        }

        // ── 반등신호 (A) ──
        double atr = indicators.atr(ohlc, props.atrPeriod());
        double recentHigh = Double.NEGATIVE_INFINITY;
        for (int i = n - props.reboundLookback(); i < n; i++) {
            recentHigh = Math.max(recentHigh, bars.get(i).high());
        }
        double lastClose = closes[n - 1];
        double drawdownAtr = atr > 0 ? (recentHigh - lastClose) / atr : 0.0;
        double reboundSignal = reboundNormalize(drawdownAtr);

        // ── 기술적지표 (B) ──
        Rsi rsi = indicators.rsi(closes, props.rsiPeriod(), props.recentWindow());
        Bollinger bb = indicators.bollinger(closes, props.bollingerPeriod(), props.bollingerK(), props.recentWindow());
        double rsiSub = rsiScore(rsi);
        double bbSub = bollingerScore(bb);
        double technicalIndicator = (rsiSub + bbSub) / 2.0;

        // ── 매력도지수 (C) ──
        Double baseScoreBoxed = in.attractivenessBaseScore() == null ? null : in.attractivenessBaseScore().doubleValue();
        double baseScore = baseScoreBoxed == null ? 0.0 : baseScoreBoxed;
        double attractivenessComponent = clamp(baseScore / 100.0, 0.0, 1.0);
        boolean filterPassed = baseScore >= props.attractivenessMinBaseScore();

        // ── 가중합 ──
        double wA = props.weightOf(EntryComponent.REBOUND_SIGNAL);
        double wB = props.weightOf(EntryComponent.TECHNICAL_INDICATOR);
        double wC = props.weightOf(EntryComponent.ATTRACTIVENESS);
        double wSum = wA + wB + wC;
        double raw = wSum > 0
                ? (reboundSignal * wA + technicalIndicator * wB + attractivenessComponent * wC) / wSum
                : 0.0;
        double entryScore = filterPassed ? round2(100.0 * raw) : 0.0;

        Map<String, ComponentScore> components = new LinkedHashMap<>();
        components.put(EntryComponent.REBOUND_SIGNAL.configKey(), new ComponentScore(
                wA, reboundSignal, wA * reboundSignal,
                ordered("atr", atr, "recentHigh", recentHigh, "lastClose", lastClose, "drawdownAtr", drawdownAtr)));
        components.put(EntryComponent.TECHNICAL_INDICATOR.configKey(), new ComponentScore(
                wB, technicalIndicator, wB * technicalIndicator,
                ordered("rsi", rsi.current(), "rsiPrev", rsi.previous(), "rsiMinRecent", rsi.minRecent(),
                        "rsiSub", rsiSub, "pctB", bb.currentPctB(), "pctBMinRecent", bb.minRecentPctB(),
                        "bbLower", bb.lower(), "bbSub", bbSub)));
        components.put(EntryComponent.ATTRACTIVENESS.configKey(), new ComponentScore(
                wC, attractivenessComponent, wC * attractivenessComponent,
                ordered("baseScore", baseScore, "minBaseScore", props.attractivenessMinBaseScore())));

        return new EntryScoreResult(
                in.symbol(), in.asOf(), props.engineVersion(),
                round4(reboundSignal), round4(technicalIndicator), round4(attractivenessComponent),
                filterPassed, entryScore, props.entryThreshold(), components);
    }

    /** 낙폭(ATR배수) → [0,1]. low 미만 램프업 / low~high plateau 1.0 / high~far 램프다운 / far 이상 0. */
    private double reboundNormalize(double drawdownAtr) {
        double lo = props.reboundBandLowMultiple();
        double hi = props.reboundBandHighMultiple();
        double far = props.reboundBandFarMultiple();
        if (drawdownAtr <= 0) {
            return 0.0;
        }
        if (drawdownAtr < lo) {
            return drawdownAtr / lo;
        }
        if (drawdownAtr <= hi) {
            return 1.0;
        }
        if (drawdownAtr < far) {
            return 1.0 - (drawdownAtr - hi) / (far - hi);
        }
        return 0.0;
    }

    /** RSI 서브점수: 최근 과매도(≤oversold) 후 상향돌파일 때 최고. */
    private double rsiScore(Rsi rsi) {
        double oversold = props.rsiOversold();
        double curr = rsi.current();
        double prev = rsi.previous();
        double minRecent = rsi.minRecent();
        double depth = clamp((oversold - minRecent) / oversold, 0.0, 1.0); // 0=RSI30, 1=RSI0

        if (minRecent <= oversold) {
            if (curr > oversold && curr >= prev) {
                return clamp(0.6 + 0.4 * depth, 0.0, 1.0);              // 과매도 후 상향돌파 = 최강
            }
            if (curr <= oversold) {
                return clamp(0.35 + 0.25 * ((oversold - curr) / oversold), 0.0, 1.0); // 아직 과매도, 반등 대기
            }
            return clamp(0.5 - (curr - oversold) / 40.0, 0.0, 1.0);     // 돌파 후 다시 하락 / 과회복
        }
        return clamp((45.0 - curr) / 45.0 * 0.3, 0.0, 1.0);            // 과매도 없었음 — RSI 낮으면 소폭 가점
    }

    /** 볼린저 서브점수: 최근 하단선 터치(%b≤0.05) 후 복귀일 때 최고. */
    private double bollingerScore(Bollinger bb) {
        double curr = bb.currentPctB();
        double minRecent = bb.minRecentPctB();
        if (minRecent <= 0.05) {
            if (curr > minRecent && curr <= 0.5) {
                return clamp(1.0 - curr, 0.5, 1.0);                     // 하단 근처에서 복귀 중 = 최강
            }
            if (curr <= 0.05) {
                return 0.5;                                             // 아직 하단선
            }
            return clamp(0.8 - curr, 0.0, 1.0);                         // 중앙 넘어 회복 — 약화
        }
        return clamp((0.25 - curr) / 0.25 * 0.4, 0.0, 1.0);            // 하단 미터치 — 하위권이면 소폭 가점
    }

    private static Map<String, Double> ordered(Object... kv) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], round4(((Number) kv[i + 1]).doubleValue()));
        }
        return m;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round4(double v) {
        if (!Double.isFinite(v)) {
            return v;
        }
        return Math.round(v * 10_000.0) / 10_000.0;
    }
}
