package com.dipscore.backend.entryscore;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EntryScoreProperties.class)
public class EntryScoreConfig {
}
