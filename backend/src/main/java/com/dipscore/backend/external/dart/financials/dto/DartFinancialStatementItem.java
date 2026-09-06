package com.dipscore.backend.external.dart.financials.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 재무제표 계정 1행. 금액은 DART 가 문자열(음수는 "-" 접두, 빈 값 가능)로 내려주므로 String 으로 둔다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DartFinancialStatementItem(

        @JsonProperty("rcept_no") String receiptNo,
        @JsonProperty("bsns_year") String businessYear,
        @JsonProperty("corp_code") String corpCode,

        /** BS(재무상태표) / IS(손익) / CIS(포괄손익) / CF(현금흐름) / SCE(자본변동). */
        @JsonProperty("sj_div") String statementDiv,
        @JsonProperty("sj_nm") String statementName,

        @JsonProperty("account_id") String accountId,
        @JsonProperty("account_nm") String accountName,
        @JsonProperty("account_detail") String accountDetail,

        /** 당기. */
        @JsonProperty("thstrm_nm") String currentTermName,
        @JsonProperty("thstrm_amount") String currentTermAmount,

        /** 전기. */
        @JsonProperty("frmtrm_nm") String priorTermName,
        @JsonProperty("frmtrm_amount") String priorTermAmount,

        /** 전전기. */
        @JsonProperty("bfefrmtrm_nm") String priorPriorTermName,
        @JsonProperty("bfefrmtrm_amount") String priorPriorTermAmount,

        @JsonProperty("ord") String ord,
        @JsonProperty("currency") String currency
) {}
