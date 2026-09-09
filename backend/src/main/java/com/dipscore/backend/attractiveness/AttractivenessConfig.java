package com.dipscore.backend.attractiveness;

import com.dipscore.backend.attractiveness.ingest.FinancialSnapshotSyncProperties;
import com.dipscore.backend.attractiveness.ingest.MacroIndicatorSyncProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        AttractivenessProperties.class,
        FinancialSnapshotSyncProperties.class,
        MacroIndicatorSyncProperties.class})
public class AttractivenessConfig {
}
