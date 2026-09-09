package com.dipscore.backend.marketdata.dailybatch;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 장 마감 후 최신 일봉 적재 스케줄러 설정 ({@code daily-batch.daily-candle.*}).
 * {@code cron} 은 {@code @Scheduled} 애노테이션이 플레이스홀더로 직접 읽는다 (Asia/Seoul).
 */
@ConfigurationProperties(prefix = "daily-batch.daily-candle")
public record DailyCandleSyncProperties(

        /** 스케줄러/컨트롤러/writer 빈 생성 여부. 미설정(테스트 등)이면 빈 자체가 뜨지 않는다. */
        @DefaultValue("false") boolean enabled,

        /** 실행 cron (Asia/Seoul). 기본: 평일 16:00 — 장 마감(15:30) 후. */
        @DefaultValue("0 0 16 * * MON-FRI") String cron
) {}
