package com.dipscore.backend.external.toss.quote.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 종목 현재가 1건. {@code GET /api/v1/prices} 응답의 {@code result} 배열 원소 형태
 * (2026-09 기준 공개 스펙: https://openapi.tossinvest.com/openapi-docs/latest/openapi.json).
 *
 * <p>API 는 시세/등락률/거래량 등 부가 정보 없이 이 4개 필드만 제공한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPriceQuote(

        @JsonProperty("symbol") String symbol,

        /** 시세 기준 시각 (ISO-8601, 예: {@code 2026-03-25T09:30:00.123+09:00}). */
        @JsonProperty("timestamp") Instant timestamp,

        /** 현재가. 응답에서는 문자열로 내려오나 숫자 비교/연산을 위해 BigDecimal 로 매핑. */
        @JsonProperty("lastPrice") BigDecimal lastPrice,

        /** 통화 (KRW/USD). */
        @JsonProperty("currency") String currency
) {}
