package com.dipscore.backend.external.dart.stocktotal;

import com.dipscore.backend.external.dart.DartApiException;
import com.dipscore.backend.external.dart.DartApiProperties;
import com.dipscore.backend.external.dart.DartApiStatus;
import com.dipscore.backend.external.dart.stocktotal.dto.DartStockTotalCountResponse;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * DART 주식총수현황 조회 ({@code /api/stockTotqySttus.json}).
 * 발행주식수는 재무제표에 없어 별도 조회한다 ({@code reprtCode} 는 재무제표와 동일 코드 사용).
 *
 * <p>{@code reprtCode}: 11011(사업보고서) / 11012(반기) / 11013(1분기) / 11014(3분기).
 */
@Component
public class DartStockTotalCountClient {

    private final RestClient dartRestClient;
    private final DartApiProperties props;

    public DartStockTotalCountClient(@Qualifier("dartRestClient") RestClient dartRestClient,
                                     DartApiProperties props) {
        this.dartRestClient = dartRestClient;
        this.props = props;
    }

    public DartStockTotalCountResponse getStockTotalCount(String corpCode, int businessYear, String reprtCode) {
        if (corpCode == null || corpCode.isBlank()) {
            throw new IllegalArgumentException("corpCode 는 필수입니다.");
        }
        try {
            DartStockTotalCountResponse res = dartRestClient.get()
                    .uri(b -> b.path(props.stockTotalCountPath())
                            .queryParam("corp_code", corpCode)
                            .queryParam("bsns_year", businessYear)
                            .queryParam("reprt_code", reprtCode)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (rq, rp) -> {
                        throw new DartApiException("주식총수현황 조회 실패(%s/%d): HTTP %d"
                                .formatted(corpCode, businessYear, rp.getStatusCode().value()));
                    })
                    .body(DartStockTotalCountResponse.class);

            if (res == null) {
                throw new DartApiException("주식총수현황 응답이 비어 있습니다: " + corpCode);
            }
            DartApiStatus.verify(res.status(), res.message(), "주식총수현황 조회");
            return res;
        } catch (RestClientException e) {
            throw new DartApiException("주식총수현황 조회 통신 오류: " + corpCode, e);
        }
    }
}
