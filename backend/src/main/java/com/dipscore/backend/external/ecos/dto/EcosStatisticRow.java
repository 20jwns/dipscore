package com.dipscore.backend.external.ecos.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ECOS {@code StatisticSearch} 응답의 {@code row} 1건.
 * 값은 모두 문자열로 내려오며, 응답에 따라 일부 필드가 생략될 수 있다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EcosStatisticRow(

        @JsonProperty("STAT_CODE") String statCode,
        @JsonProperty("STAT_NAME") String statName,
        @JsonProperty("ITEM_CODE1") String itemCode1,
        @JsonProperty("ITEM_NAME1") String itemName1,
        @JsonProperty("UNIT_NAME") String unitName,

        /** 시점. 주기에 따라 {@code YYYYMMDD}(일) / {@code YYYYMM}(월) / {@code YYYYQn}(분기) / {@code YYYY}(연). */
        @JsonProperty("TIME") String time,

        @JsonProperty("DATA_VALUE") String dataValue
) {

    /** 숫자 값. 빈 문자열이면 null. */
    public BigDecimal dataValueAsDecimal() {
        return (dataValue == null || dataValue.isBlank()) ? null : new BigDecimal(dataValue.trim());
    }
}
