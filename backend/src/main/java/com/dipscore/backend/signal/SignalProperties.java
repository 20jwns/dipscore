package com.dipscore.backend.signal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 매매 신호 판정 설정 ({@code signal.*}). 기획서 7 매수/매도 룰의 임계값.
 * 값은 {@code entry-score.*} 와 겹치는 것도 있으나 매매 룰 고유 튜닝 대상이라 분리한다 (기획서 7-3, 백테스트로 조정).
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
        @DefaultValue("0.9") double eventCoefficientMin,

        /** 매도 — 목표수익률 (기획서 7-2 는 +2~3% 구간, 백테스트로 조정할 단일 운용값). */
        @DefaultValue("0.025") double targetProfitRate,

        /** 매도 — 손절 ATR 배수 (기획서 7-2 는 1~1.5배 구간, 백테스트로 조정할 단일 운용값). */
        @DefaultValue("1.25") double stopLossAtrMultiple,

        /** 매도 — 보유시간 초과 강제청산 시각 (HH:mm, Asia/Seoul). 이 시각 이후면 무조건 매도. */
        @DefaultValue("15:20") String forceCloseTime
) {}
