package com.dipscore.backend.external.toss.candle.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 캔들 페이지. 다음 페이지가 있으면 {@code nextBefore} 를 다음 요청의 {@code before} 로 전달한다
 * (없으면 null).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossCandlePage(
        @JsonProperty("candles") List<TossCandle> candles,
        @JsonProperty("nextBefore") Instant nextBefore
) {}
