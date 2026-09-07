package com.dipscore.backend.external.toss.candle.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code GET /api/v1/candles} 응답 래퍼 (ApiResponse + result). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossCandleResponse(
        @JsonProperty("result") TossCandlePage result
) {}
