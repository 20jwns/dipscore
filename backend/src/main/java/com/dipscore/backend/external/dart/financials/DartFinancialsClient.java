package com.dipscore.backend.external.dart.financials;

import com.dipscore.backend.external.dart.DartApiException;
import com.dipscore.backend.external.dart.DartApiProperties;
import com.dipscore.backend.external.dart.DartApiStatus;
import com.dipscore.backend.external.dart.financials.dto.DartFinancialStatementResponse;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * DART 단일회사 전체 재무제표 조회 ({@code /api/fnlttSinglAcntAll.json}).
 *
 * <p>{@code reprtCode}: 11011(사업보고서) / 11012(반기) / 11013(1분기) / 11014(3분기).
 * <p>{@code fsDiv}: {@code CFS}(연결) / {@code OFS}(별도).
 */
@Component
public class DartFinancialsClient {

    private final RestClient dartRestClient;
    private final DartApiProperties props;

    public DartFinancialsClient(@Qualifier("dartRestClient") RestClient dartRestClient,
                                DartApiProperties props) {
        this.dartRestClient = dartRestClient;
        this.props = props;
    }

    public DartFinancialStatementResponse getSingleCompanyFullStatements(
            String corpCode, int businessYear, String reprtCode, String fsDiv) {

        if (corpCode == null || corpCode.isBlank()) {
            throw new IllegalArgumentException("corpCode 는 필수입니다.");
        }
        if (businessYear < 2015) {
            throw new IllegalArgumentException("businessYear 는 2015 이상이어야 합니다: " + businessYear);
        }
        try {
            DartFinancialStatementResponse res = dartRestClient.get()
                    .uri(b -> b.path(props.financialsPath())
                            .queryParam("corp_code", corpCode)
                            .queryParam("bsns_year", businessYear)
                            .queryParam("reprt_code", reprtCode)
                            .queryParam("fs_div", fsDiv)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (rq, rp) -> {
                        throw new DartApiException("재무제표 조회 실패(%s/%d): HTTP %d"
                                .formatted(corpCode, businessYear, rp.getStatusCode().value()));
                    })
                    .body(DartFinancialStatementResponse.class);

            if (res == null) {
                throw new DartApiException("재무제표 응답이 비어 있습니다: " + corpCode);
            }
            DartApiStatus.verify(res.status(), res.message(), "재무제표 조회");
            return res;
        } catch (RestClientException e) {
            throw new DartApiException("재무제표 조회 통신 오류: " + corpCode, e);
        }
    }
}
