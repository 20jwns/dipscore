package com.dipscore.backend.external.dart.financials.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 단일회사 전체 재무제표({@code /api/fnlttSinglAcntAll.json}) 응답. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DartFinancialStatementResponse(
        String status,
        String message,
        List<DartFinancialStatementItem> list
) {}
