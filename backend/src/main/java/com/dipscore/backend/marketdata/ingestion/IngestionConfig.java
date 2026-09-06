package com.dipscore.backend.marketdata.ingestion;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 시세 적재 스케줄러 인프라. {@link PriceIngestionProperties} 바인딩은 항상,
 * 실제 스케줄 잡({@link PriceIngestionJob})은 {@code dipscore.ingestion.price.enabled=true} 일 때만 생성된다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(PriceIngestionProperties.class)
public class IngestionConfig {
}
