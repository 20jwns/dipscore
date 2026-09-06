package com.dipscore.backend.external.dart.company;

import com.dipscore.backend.external.dart.DartApiException;
import com.dipscore.backend.external.dart.DartApiProperties;
import com.dipscore.backend.external.dart.DartApiStatus;
import com.dipscore.backend.external.dart.company.dto.DartCompanyResponse;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** DART 기업개황 조회. */
@Component
public class DartCompanyClient {

    private final RestClient dartRestClient;
    private final DartApiProperties props;

    public DartCompanyClient(@Qualifier("dartRestClient") RestClient dartRestClient,
                             DartApiProperties props) {
        this.dartRestClient = dartRestClient;
        this.props = props;
    }

    /**
     * @param corpCode DART 고유번호 8자리 (예: 삼성전자 {@code "00126380"})
     */
    public DartCompanyResponse getCompany(String corpCode) {
        if (corpCode == null || corpCode.isBlank()) {
            throw new IllegalArgumentException("corpCode 는 필수입니다.");
        }
        try {
            DartCompanyResponse res = dartRestClient.get()
                    .uri(b -> b.path(props.companyPath())
                            .queryParam("corp_code", corpCode)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (rq, rp) -> {
                        throw new DartApiException("기업개황 조회 실패(%s): HTTP %d"
                                .formatted(corpCode, rp.getStatusCode().value()));
                    })
                    .body(DartCompanyResponse.class);

            if (res == null) {
                throw new DartApiException("기업개황 응답이 비어 있습니다: " + corpCode);
            }
            DartApiStatus.verify(res.status(), res.message(), "기업개황 조회");
            return res;
        } catch (RestClientException e) {
            throw new DartApiException("기업개황 조회 통신 오류: " + corpCode, e);
        }
    }
}
