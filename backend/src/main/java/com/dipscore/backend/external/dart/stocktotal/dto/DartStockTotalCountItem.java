package com.dipscore.backend.external.dart.stocktotal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 주식총수현황({@code /api/stockTotqySttus.json}) 응답의 {@code list[]} 원소.
 * 주식수는 DART 가 콤마 포함 문자열("5,969,782,550", 없으면 "-")로 내려주므로 String 으로 둔다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DartStockTotalCountItem(

        @JsonProperty("rcept_no") String receiptNo,
        @JsonProperty("corp_code") String corpCode,

        /** 구분: "보통주" / "우선주" / "합계" 등. */
        @JsonProperty("se") String kind,

        /** Ⅰ. 발행할 주식의 총수. */
        @JsonProperty("isu_stock_totqy") String issuableShares,

        /** Ⅱ. 현재까지 발행한 주식의 총수. */
        @JsonProperty("now_to_isu_stock_totqy") String issuedShares,

        /** Ⅳ. 발행주식의 총수 (= 발행 − 감소). 발행주식수로 사용. */
        @JsonProperty("istc_totqy") String outstandingShares,

        /** Ⅴ. 자기주식수. */
        @JsonProperty("tesstk_co") String treasuryShares,

        /** Ⅵ. 유통주식수 (= 발행주식총수 − 자기주식). */
        @JsonProperty("distb_stock_co") String distributedShares
) {}
