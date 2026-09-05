package com.dipscore.backend.external.toss.auth;

import com.dipscore.backend.external.toss.TossApiException;
import com.dipscore.backend.external.toss.TossApiProperties;
import com.dipscore.backend.external.toss.auth.dto.TossTokenResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 토스증권 OAuth2.0 토큰 엔드포인트 호출부. 토큰 캐싱/갱신 판단은 {@link TossTokenManager} 담당.
 */
@Component
public class TossOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(TossOAuthClient.class);

    private final RestClient authRestClient;
    private final TossApiProperties props;

    public TossOAuthClient(@Qualifier("tossAuthRestClient") RestClient authRestClient,
                           TossApiProperties props) {
        this.authRestClient = authRestClient;
        this.props = props;
    }

    /** grant_type(기본 client_credentials) 으로 신규 토큰 발급. */
    public TossTokenResponse issue() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", props.auth().grantType());
        addClientCredentials(form);
        if (!props.auth().scope().isBlank()) {
            form.add("scope", props.auth().scope());
        }
        return exchange(form, "발급");
    }

    /**
     * refresh_token 으로 토큰 갱신.
     *
     * <p>2026-09 기준 토스증권 오픈API 는 응답에 refresh_token 을 내려주지 않으므로
     * ({@link TossTokenManager} 참고) 이 경로는 현재 호출되지 않는다. 향후 스펙 변경 대비 유지.
     */
    public TossTokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        addClientCredentials(form);
        return exchange(form, "갱신");
    }

    private void addClientCredentials(MultiValueMap<String, String> form) {
        if (!props.hasCredentials()) {
            throw new TossApiException(
                    "토스증권 오픈API 자격증명이 없습니다. backend/.env 에 TOSS_CLIENT_ID / TOSS_CLIENT_SECRET 를 설정하세요.");
        }
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());
    }

    private TossTokenResponse exchange(MultiValueMap<String, String> form, String action) {
        try {
            TossTokenResponse res = authRestClient.post()
                    .uri(props.auth().tokenPath())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw new TossApiException(
                                "토큰 %s 실패: HTTP %d".formatted(action, resp.getStatusCode().value()));
                    })
                    .body(TossTokenResponse.class);

            if (res == null || res.accessToken() == null || res.accessToken().isBlank()) {
                throw new TossApiException("토큰 %s 응답에 access_token 이 없습니다.".formatted(action));
            }
            log.debug("토스증권 토큰 {} 완료 (expires_in={}s)", action, res.expiresIn());
            return res;
        } catch (RestClientException e) {
            throw new TossApiException("토큰 %s 중 통신 오류".formatted(action), e);
        }
    }
}
