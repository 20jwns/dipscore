package com.dipscore.backend.marketdata.backfill;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PriceBackfillProperties.class)
public class PriceBackfillConfig {
}
