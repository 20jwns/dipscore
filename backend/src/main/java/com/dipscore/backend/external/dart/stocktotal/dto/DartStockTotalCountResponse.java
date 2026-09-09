package com.dipscore.backend.external.dart.stocktotal.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 주식총수현황({@code /api/stockTotqySttus.json}) 응답. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DartStockTotalCountResponse(
        String status,
        String message,
        List<DartStockTotalCountItem> list
) {}
