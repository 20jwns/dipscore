package com.dipscore.backend.attractiveness;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AttractivenessProperties.class)
public class AttractivenessConfig {
}
