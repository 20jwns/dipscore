package com.dipscore.backend.web;

import com.dipscore.backend.external.dart.company.DartCompanyClient;
import com.dipscore.backend.external.dart.company.dto.DartCompanyResponse;
import com.dipscore.backend.external.dart.corpcode.DartCorpCodeClient;
import com.dipscore.backend.external.dart.corpcode.dto.DartCorpCode;
import com.dipscore.backend.external.dart.financials.DartFinancialsClient;
import com.dipscore.backend.external.dart.financials.dto.DartFinancialStatementResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * DART 연동 스모크 테스트용 엔드포인트. (정식 스코어링 파이프라인으로 대체될 임시 창구)
 */
@RestController
public class DartController {

    private final DartFinancialsClient financialsClient;
    private final DartCompanyClient companyClient;
    private final DartCorpCodeClient corpCodeClient;

    public DartController(DartFinancialsClient financialsClient,
                          DartCompanyClient companyClient,
                          DartCorpCodeClient corpCodeClient) {
        this.financialsClient = financialsClient;
        this.companyClient = companyClient;
        this.corpCodeClient = corpCodeClient;
    }

    /** 예: {@code GET /api/financials/00126380?year=2024&reprtCode=11011&fsDiv=CFS} (삼성전자) */
    @GetMapping("/api/financials/{corpCode}")
    public DartFinancialStatementResponse financials(
            @PathVariable String corpCode,
            @RequestParam(defaultValue = "2024") int year,
            @RequestParam(defaultValue = "11011") String reprtCode,
            @RequestParam(defaultValue = "CFS") String fsDiv) {
        return financialsClient.getSingleCompanyFullStatements(corpCode, year, reprtCode, fsDiv);
    }

    /** 예: {@code GET /api/dart/company/00126380} */
    @GetMapping("/api/dart/company/{corpCode}")
    public DartCompanyResponse company(@PathVariable String corpCode) {
        return companyClient.getCompany(corpCode);
    }

    /** 종목코드 → DART 고유번호 조회. 예: {@code GET /api/dart/corp-code/005930} */
    @GetMapping("/api/dart/corp-code/{stockCode}")
    public ResponseEntity<DartCorpCode> corpCode(@PathVariable String stockCode) {
        return corpCodeClient.findByStockCode(stockCode)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
