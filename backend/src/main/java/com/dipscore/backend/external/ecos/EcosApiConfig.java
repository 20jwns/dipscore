package com.dipscore.backend.external.ecos;

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
 * 한국은행 ECOS 오픈API용 {@link RestClient} 구성.
 * (인증키가 URL 경로에 들어가므로 토스/DART 같은 인증 인터셉터는 없음 - 클라이언트에서 경로에 포함)
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EcosApiProperties.class)
public class EcosApiConfig {

    @Bean
    ClientHttpRequestFactory ecosClientHttpRequestFactory(EcosApiProperties props) {
        return ClientHttpRequestFactoryBuilder.detect()
                .build(ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(props.connectTimeout())
                        .withReadTimeout(props.readTimeout()));
    }

    @Bean
    RestClient ecosRestClient(EcosApiProperties props,
                              @Qualifier("ecosClientHttpRequestFactory") ClientHttpRequestFactory requestFactory) {
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        if (StringUtils.hasText(props.baseUrl())) {
            builder.baseUrl(props.baseUrl());
        }
        return builder.build();
    }
}
