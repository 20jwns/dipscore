package com.dipscore.backend.external.toss;

import java.io.IOException;

import com.dipscore.backend.external.toss.auth.TossTokenManager;

import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * 토스증권 REST API 호출에 {@code Authorization: Bearer <access_token>} 를 주입한다.
 * 401 응답을 받으면 토큰을 무효화하고 1회 재시도한다.
 */
class BearerTokenInterceptor implements ClientHttpRequestInterceptor {

    private final TossTokenManager tokenManager;

    BearerTokenInterceptor(TossTokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {

        request.getHeaders().setBearerAuth(tokenManager.getAccessToken());
        ClientHttpResponse response = execution.execute(request, body);

        if (response.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
            response.close();
            tokenManager.invalidate();
            request.getHeaders().setBearerAuth(tokenManager.getAccessToken());
            response = execution.execute(request, body);
        }
        return response;
    }
}
