package com.dipscore.backend.marketdata.dailybatch;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI 모의투자 자동매매 배치 설정 ({@code daily-batch.auto-trade.*}).
 * {@code daily-scoring}(08:30) 이 계산한 매력도·저점진입 점수로 만들어진 BUY 신호를,
 * 장 시작 후({@code cron} 기본 09:05) 별도로 {@link AutoTradeJob} 이 실제(가상) 체결한다.
 *
 * <p>{@code enabled} 기본값이 다른 배치와 달리 <b>false</b> 다 — 실제로 가상 계좌 현금을 움직이는
 * 자동매매라 명시적으로 켜야 한다. {@code accountId} 도 반드시 지정해야 스케줄 실행이 동작한다
 * (미지정이면 안전장치로 건너뛰고 경고 로그만 남긴다).
 */
@ConfigurationProperties(prefix = "daily-batch.auto-trade")
public record AutoTradeProperties(

        @DefaultValue("false") boolean enabled,

        /** 실행 cron (Asia/Seoul). 기본: 평일 09:05 — daily-scoring(08:30) 이후, 장 시작(09:00) 직후. */
        @DefaultValue("0 5 9 * * MON-FRI") String cron,

        /** 자동매매 체결에 쓸 계좌 ID. 미지정(null)이면 스케줄 실행을 건너뛴다(수동 트리거는 파라미터로 지정 가능). */
        Long accountId,

        /** 장중 시간대 시작 (HH:mm, Asia/Seoul). 이 시각 전이면 매수 체결을 건너뛴다. */
        @DefaultValue("09:00") String marketOpenTime,

        /** 장중 시간대 종료 (HH:mm, Asia/Seoul). 이 시각 이후면 매수 체결을 건너뛴다. */
        @DefaultValue("15:30") String marketCloseTime
) {}
