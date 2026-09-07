package com.dipscore.backend.attractiveness;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 매력도 지수 엔진 설정 ({@code attractiveness.*}).
 * 가중치는 하드코딩하지 않고 여기서 주입한다 (기획서 4-4).
 */
@ConfigurationProperties(prefix = "attractiveness")
public record AttractivenessProperties(

        /** 스케줄러/컨트롤러 등 DB 의존 빈 활성화 여부 (테스트 컨텍스트엔 이 키가 없어 미생성). */
        @DefaultValue("false") boolean enabled,

        @DefaultValue("base-v1") String engineVersion,

        /** 재무 스냅샷 조회 기준 회계연도. */
        @DefaultValue("2024") int targetFiscalYear,

        /** 재무 스냅샷 연결구분. */
        @DefaultValue("CFS") String fsDiv,

        /** 거시지표 Z-score 분포 창 크기 (macro_indicator 최근 N개). */
        @DefaultValue("24") int macroZscoreWindow,

        /**
         * 요인별 가중치 (키 = {@link Factor#configKey()}). 합이 1이 아니어도 됨 — 엔진이 정규화한다.
         * 가중치 0 이면 해당 요인은 계산에서 제외.
         */
        @DefaultValue Map<String, Double> weights
) {

    public AttractivenessProperties {
        weights = weights == null ? new LinkedHashMap<>() : weights;
    }

    public double weightOf(Factor factor) {
        return weights.getOrDefault(factor.configKey(), 0.0);
    }
}
