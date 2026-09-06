package com.dipscore.backend.external.dart.company.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 기업개황({@code /api/company.json}) 응답. DART 는 모든 값을 문자열로 내려준다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DartCompanyResponse(

        String status,
        String message,

        @JsonProperty("corp_code") String corpCode,
        @JsonProperty("corp_name") String corpName,
        @JsonProperty("corp_name_eng") String corpNameEng,
        @JsonProperty("stock_name") String stockName,
        @JsonProperty("stock_code") String stockCode,

        /** Y(유가) / K(코스닥) / N(코넥스) / E(기타). */
        @JsonProperty("corp_cls") String corpCls,

        @JsonProperty("ceo_nm") String ceoName,
        @JsonProperty("jurir_no") String corporateRegistrationNumber,
        @JsonProperty("bizr_no") String businessRegistrationNumber,
        @JsonProperty("adres") String address,
        @JsonProperty("induty_code") String industryCode,

        /** 설립일 YYYYMMDD. */
        @JsonProperty("est_dt") String establishedDate,

        /** 결산월 MM. */
        @JsonProperty("acc_mt") String accountingMonth
) {}
