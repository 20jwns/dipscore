package com.dipscore.backend.external.ecos.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ECOS 응답. 정상이면 {@code StatisticSearch} 가, 오류면 {@code RESULT} 가 채워진다.
 * <pre>
 * 정상: {"StatisticSearch":{"list_total_count":N,"row":[...]}}
 * 오류: {"RESULT":{"CODE":"INFO-200","MESSAGE":"해당하는 데이터가 없습니다."}}
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EcosStatisticResponse(

        @JsonProperty("StatisticSearch") StatisticSearch statisticSearch,
        @JsonProperty("RESULT") Result result
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatisticSearch(
            @JsonProperty("list_total_count") Integer listTotalCount,
            @JsonProperty("row") List<EcosStatisticRow> row
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            @JsonProperty("CODE") String code,
            @JsonProperty("MESSAGE") String message
    ) {}
}
