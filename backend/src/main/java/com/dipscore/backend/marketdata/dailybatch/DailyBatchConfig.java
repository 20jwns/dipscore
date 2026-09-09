package com.dipscore.backend.marketdata.dailybatch;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 일별 배치 인프라. 프로퍼티 바인딩({@link DailyBatchProperties}, {@link DailyCandleSyncProperties})은 항상,
 * 실제 스케줄 잡({@link DailyCandleSyncJob})은 {@code daily-batch.daily-candle.enabled=true} 일 때만 생성된다.
 *
 * <p>{@code @EnableScheduling} 은 {@code marketdata.ingestion.IngestionConfig} 에서 이미 활성화되어 있어 생략.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({DailyBatchProperties.class, DailyCandleSyncProperties.class})
public class DailyBatchConfig {
}
