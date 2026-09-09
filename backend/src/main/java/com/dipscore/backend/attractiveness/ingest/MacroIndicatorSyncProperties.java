package com.dipscore.backend.attractiveness.ingest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 거시지표(ECOS) 자동 갱신 스케줄러 설정 ({@code attractiveness.macro-sync.*}).
 * cron 은 {@code @Scheduled} 애노테이션이 플레이스홀더로 직접 읽는다 (Asia/Seoul).
 * 잡/컨트롤러는 {@code attractiveness.enabled} 와 이 {@code enabled} 가 <b>모두</b> true 여야 생성된다.
 */
@ConfigurationProperties(prefix = "attractiveness.macro-sync")
public record MacroIndicatorSyncProperties(

        @DefaultValue("false") boolean enabled,

        /** 환율(USD_KRW) 갱신 cron. 기본: 매일 06:00. */
        @DefaultValue("0 0 6 * * *") String exchangeRateCron,

        /** 기준금리·CPI 갱신 cron. 기본: 매월 1일 06:00. */
        @DefaultValue("0 0 6 1 * *") String monthlyCron
) {}
