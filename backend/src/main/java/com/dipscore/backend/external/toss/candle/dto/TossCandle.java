package com.dipscore.backend.external.toss.candle.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 캔들 1건. {@code GET /api/v1/candles} 응답의 {@code result.candles[]} 원소
 * (2026-09 공개 스펙: https://openapi.tossinvest.com/openapi-docs/latest/openapi.json).
 * 가격/거래량은 문자열로 내려온다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossCandle(

        /** 봉 기준 시각 (ISO-8601, 일봉은 장 시작 09:00 KST). */
        @JsonProperty("timestamp") Instant timestamp,

        @JsonProperty("openPrice") BigDecimal open,
        @JsonProperty("highPrice") BigDecimal high,
        @JsonProperty("lowPrice") BigDecimal low,
        @JsonProperty("closePrice") BigDecimal close,
        @JsonProperty("volume") Long volume,
        @JsonProperty("currency") String currency
) {}
