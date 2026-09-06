package com.dipscore.backend.external.dart;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 모든 DART 요청 URL 에 인증 쿼리 파라미터 {@code crtfc_key} 를 주입한다.
 * (토스의 {@code BearerTokenInterceptor} 와 같은 역할 - 인증을 중앙에서 처리)
 */
class DartApiKeyInterceptor implements ClientHttpRequestInterceptor {

    private final String apiKey;

    DartApiKeyInterceptor(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {

        if (!StringUtils.hasText(apiKey)) {
            throw new DartApiException("DART API 키가 없습니다. backend/.env 에 DART_API_KEY 를 설정하세요.");
        }

        URI withKey = UriComponentsBuilder.fromUri(request.getURI())
                .replaceQueryParam("crtfc_key", apiKey)
                .build(true)
                .toUri();

        HttpRequestWrapper wrapped = new HttpRequestWrapper(request) {
            @Override
            public URI getURI() {
                return withKey;
            }
        };
        return execution.execute(wrapped, body);
    }
}
