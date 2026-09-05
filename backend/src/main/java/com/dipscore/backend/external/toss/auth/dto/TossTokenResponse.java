package com.dipscore.backend.external.toss.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OAuth2.0 토큰 응답 (RFC 6749 형식 가정).
 * 실제 토스증권 응답 필드명이 다르면 {@code @JsonProperty} 를 조정한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossTokenResponse(

        @JsonProperty("access_token") String accessToken,

        @JsonProperty("token_type") String tokenType,

        /** 만료까지 남은 초. */
        @JsonProperty("expires_in") Long expiresIn,

        /** refresh_token grant 미지원 시 null 일 수 있음. */
        @JsonProperty("refresh_token") String refreshToken,

        @JsonProperty("scope") String scope
) {}
