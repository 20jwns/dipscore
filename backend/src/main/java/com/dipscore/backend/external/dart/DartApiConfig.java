package com.dipscore.backend.external.dart;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * DART 오픈API용 {@link RestClient} 구성. 모든 요청에 {@code crtfc_key} 를 자동 주입한다
 * ({@link DartApiKeyInterceptor}).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DartApiProperties.class)
public class DartApiConfig {

    @Bean
    ClientHttpRequestFactory dartClientHttpRequestFactory(DartApiProperties props) {
        return ClientHttpRequestFactoryBuilder.detect()
                .build(ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(props.connectTimeout())
                        .withReadTimeout(props.readTimeout()));
    }

    @Bean
    RestClient dartRestClient(DartApiProperties props,
                              @Qualifier("dartClientHttpRequestFactory") ClientHttpRequestFactory requestFactory) {
        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor(new DartApiKeyInterceptor(props.apiKey()));
        if (StringUtils.hasText(props.baseUrl())) {
            builder.baseUrl(props.baseUrl());
        }
        return builder.build();
    }
}
