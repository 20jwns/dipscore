package com.dipscore.backend.marketdata.ingestion;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 시세 적재 스케줄러 설정 ({@code dipscore.ingestion.price.*}).
 * 주기(interval-ms)/초기지연은 {@code @Scheduled} 애노테이션이 플레이스홀더로 직접 읽는다.
 */
@ConfigurationProperties(prefix = "dipscore.ingestion.price")
public record PriceIngestionProperties(

        /** 스케줄러 활성화 여부. 미설정(테스트 등)이면 스케줄러 빈 자체가 생성되지 않는다. */
        @DefaultValue("false") boolean enabled,

        /** 적재 대상 종목코드. comma-separated 로 주입 (기본: 삼성전자 1종목). */
        @DefaultValue("005930") List<String> symbols
) {}
