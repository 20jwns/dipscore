package com.dipscore.backend.marketdata.instrument.sync;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InstrumentSyncProperties.class)
public class InstrumentSyncConfig {
}
