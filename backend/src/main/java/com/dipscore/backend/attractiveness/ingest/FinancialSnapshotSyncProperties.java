package com.dipscore.backend.attractiveness.ingest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 주간 재무제표 적재 스케줄러 설정 ({@code attractiveness.financial-sync.*}).
 * {@code cron} 은 {@code @Scheduled} 애노테이션이 플레이스홀더로 직접 읽는다 (Asia/Seoul).
 * 잡/컨트롤러는 {@code attractiveness.enabled} 와 {@code attractiveness.financial-sync.enabled} 가
 * <b>모두</b> true 일 때만 생성된다 ({@link DartFinancialSnapshotIngestionService} 가 전자에 걸려 있어서).
 */
@ConfigurationProperties(prefix = "attractiveness.financial-sync")
public record FinancialSnapshotSyncProperties(

        @DefaultValue("false") boolean enabled,

        /** 실행 cron (Asia/Seoul). 기본: 매주 월요일 03:00. */
        @DefaultValue("0 0 3 * * MON") String cron,

        /** 적재 대상 회계연도. 0 이면 자동(작년, Asia/Seoul 기준). */
        @DefaultValue("0") int targetFiscalYear,

        /** 종목 간 DART 호출 딜레이 (rate limit 완화). */
        @DefaultValue("300") long requestDelayMs
) {}
