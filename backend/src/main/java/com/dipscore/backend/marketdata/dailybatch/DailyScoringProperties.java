package com.dipscore.backend.marketdata.dailybatch;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 장 시작 전 일일 스코어링 배치 설정 ({@code daily-batch.scoring.*}).
 * 대상 종목은 {@link DailyBatchProperties#symbols()} 재사용. cron 은 {@code @Scheduled} 가
 * 플레이스홀더로 직접 읽는다 (Asia/Seoul).
 */
@ConfigurationProperties(prefix = "daily-batch.scoring")
public record DailyScoringProperties(

        @DefaultValue("false") boolean enabled,

        /** 실행 cron (Asia/Seoul). 기본: 평일 08:30 — 다른 배치(03/04/06시) 종료 후, 장 시작(09:00) 전. */
        @DefaultValue("0 30 8 * * MON-FRI") String cron
) {}
