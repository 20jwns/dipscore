package com.dipscore.backend.external.toss.quote.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code GET /api/v1/prices} 응답 래퍼. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPricesResponse(
        @JsonProperty("result") List<TossPriceQuote> result
) {}
