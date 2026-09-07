package com.dipscore.backend.entryscore;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 저점 진입 스코어 엔진 설정 ({@code entry-score.*}).
 * 가중치 A/B/C 와 지표 파라미터는 하드코딩하지 않고 여기서 주입한다 (기획서 5-3, 4단계 방법론으로 확정 예정).
 */
@ConfigurationProperties(prefix = "entry-score")
public record EntryScoreProperties(

        /** DB 의존 빈(EntryScoreService/컨트롤러) 활성화 여부. 테스트 컨텍스트엔 이 키가 없어 미생성. */
        @DefaultValue("false") boolean enabled,

        @DefaultValue("entry-v1") String engineVersion,

        /**
         * 구성요소 가중치 (키 = {@link EntryComponent#configKey()}, 초안 A/B/C = 0.3/0.3/0.4).
         * 합이 1이 아니어도 됨 — 엔진이 정규화. 0 이면 해당 요소 제외.
         */
        @DefaultValue Map<String, Double> weights,

        // --- 지표 파라미터 ---
        @DefaultValue("14") int atrPeriod,
        @DefaultValue("14") int rsiPeriod,
        @DefaultValue("30") double rsiOversold,
        @DefaultValue("20") int bollingerPeriod,
        @DefaultValue("2.0") double bollingerK,

        /** "최근" 판정 창 (최근 N봉 안에 과매도/하단터치가 있었는지). */
        @DefaultValue("10") int recentWindow,

        // --- 반등신호: 최근 고점 대비 낙폭을 ATR 배수로 환산해 정규화 ---
        /** 낙폭 기준이 되는 최근 고점 계산 창. */
        @DefaultValue("20") int reboundLookback,
        /** 이 배수 미만은 아직 눌림 아님 (0→1 램프). */
        @DefaultValue("0.5") double reboundBandLowMultiple,
        /** low~high 배수 구간이 최고점(plateau=1.0). */
        @DefaultValue("1.5") double reboundBandHighMultiple,
        /** high~far 배수에서 1→0 감소, far 이상은 0 (낙폭 과다 = 추세이탈 의심). */
        @DefaultValue("3.0") double reboundBandFarMultiple,

        // --- 매력도지수 ---
        /** 1차 필터: 매력도 기본점수 이 값 미만이면 진입 대상 아님 (entry_score=0). */
        @DefaultValue("60") double attractivenessMinBaseScore,

        /** 진입 임계값 (참고/로그용, 백테스트로 조정 — 기획서 5-2). */
        @DefaultValue("70") double entryThreshold
) {

    public EntryScoreProperties {
        weights = weights == null ? new LinkedHashMap<>() : weights;
    }

    public double weightOf(EntryComponent component) {
        return weights.getOrDefault(component.configKey(), 0.0);
    }
}
