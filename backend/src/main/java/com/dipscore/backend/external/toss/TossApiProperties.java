package com.dipscore.backend.external.toss;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 토스증권 오픈API 연동 설정. 값은 {@code application.yml} 의 {@code toss.api.*} 에서 바인딩되며,
 * 실제 값은 {@code backend/.env} 또는 배포 환경변수로 주입한다 (커밋 금지).
 *
 * <p>base-url / *-path 는 실제 토스증권 오픈API 스펙 확정 후 조정 대상인 플레이스홀더다.
 */
@ConfigurationProperties(prefix = "toss.api")
public record TossApiProperties(

        /** 발급받은 앱 클라이언트 ID. 미설정 시 연동 호출 시점에 예외. */
        @DefaultValue("") String clientId,

        /** 발급받은 앱 클라이언트 시크릿. */
        @DefaultValue("") String clientSecret,

        /** REST API 기본 URL. */
        @DefaultValue("https://openapi.tossinvest.com") String baseUrl,

        @DefaultValue("3s") Duration connectTimeout,

        @DefaultValue("5s") Duration readTimeout,

        @DefaultValue Auth auth,

        @DefaultValue Quote quote
) {

    /** 자격증명이 채워져 있는지. */
    public boolean hasCredentials() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    /** OAuth2.0 토큰 발급/갱신 설정. */
    public record Auth(

            /** 인증 서버 기본 URL (REST API 와 다를 수 있어 분리). */
            @DefaultValue("https://openapi.tossinvest.com") String baseUrl,

            /** 토큰 엔드포인트 경로. */
            @DefaultValue("/oauth2/token") String tokenPath,

            /** grant_type (기본 client_credentials). */
            @DefaultValue("client_credentials") String grantType,

            /** 요청 scope (선택). 비어 있으면 파라미터 생략. */
            @DefaultValue("") String scope,

            /** 액세스 토큰 만료 이 시간 전이면 미리 갱신한다. */
            @DefaultValue("60s") Duration refreshSkew
    ) {}

    /** 시세 조회 설정. */
    public record Quote(

            /** 현재가 조회 경로. {@code GET {pricePath}?symbols=005930,AAPL} 형태로 호출 (최대 200개, comma-separated). */
            @DefaultValue("/api/v1/prices") String pricePath
    ) {}
}
