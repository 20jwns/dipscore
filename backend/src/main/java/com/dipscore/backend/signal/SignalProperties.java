package com.dipscore.backend.signal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 매매 신호 판정 설정 ({@code signal.*}). 기획서 7-1 매수 룰의 임계값.
 * 값은 {@code entry-score.*} 와 동일하나 매수 룰 고유 튜닝 대상이라 분리한다 (기획서 7-3, 백테스트로 조정).
 */
@ConfigurationProperties(prefix = "signal")
public record SignalProperties(

        /** DB 의존 빈(TradingSignalService/컨트롤러) 활성화 여부. 테스트 컨텍스트엔 이 키가 없어 미생성. */
        @DefaultValue("false") boolean enabled,

        /** 저점 진입 스코어 ≥ 이 값. */
        @DefaultValue("70") double entryThreshold,

        /** 매력도 기본점수 ≥ 이 값. */
        @DefaultValue("60") double minBaseScore,

        /** 이벤트 조정계수 ≥ 이 값 (= 음수 이벤트 없음). 현재 조정계수는 항상 1.0 이라 항상 통과. */
        @DefaultValue("0.9") double eventCoefficientMin
) {}
