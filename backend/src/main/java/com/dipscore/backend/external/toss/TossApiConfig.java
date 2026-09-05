package com.dipscore.backend.external.toss;

import com.dipscore.backend.external.toss.auth.TossTokenManager;

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
 * 토스증권 오픈API용 {@link RestClient} 구성.
 *
 * <ul>
 *   <li>{@code tossAuthRestClient} - OAuth2.0 토큰 엔드포인트 전용 (인증 헤더 없음)</li>
 *   <li>{@code tossApiRestClient}  - 시세/계좌 조회 등 REST API 용 (Bearer 토큰 자동 주입)</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TossApiProperties.class)
public class TossApiConfig {

    @Bean
    ClientHttpRequestFactory tossClientHttpRequestFactory(TossApiProperties props) {
        return ClientHttpRequestFactoryBuilder.detect()
                .build(ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(props.connectTimeout())
                        .withReadTimeout(props.readTimeout()));
    }

    @Bean
    RestClient tossAuthRestClient(TossApiProperties props,
                                  @Qualifier("tossClientHttpRequestFactory") ClientHttpRequestFactory requestFactory) {
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        if (StringUtils.hasText(props.auth().baseUrl())) {
            builder.baseUrl(props.auth().baseUrl());
        }
        return builder.build();
    }

    @Bean
    RestClient tossApiRestClient(TossApiProperties props,
                                 @Qualifier("tossClientHttpRequestFactory") ClientHttpRequestFactory requestFactory,
                                 TossTokenManager tokenManager) {
        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor(new BearerTokenInterceptor(tokenManager));
        if (StringUtils.hasText(props.baseUrl())) {
            builder.baseUrl(props.baseUrl());
        }
        return builder.build();
    }
}
