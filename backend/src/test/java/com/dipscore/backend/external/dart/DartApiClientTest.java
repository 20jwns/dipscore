package com.dipscore.backend.external.dart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.dipscore.backend.external.dart.company.DartCompanyClient;
import com.dipscore.backend.external.dart.company.dto.DartCompanyResponse;
import com.dipscore.backend.external.dart.corpcode.DartCorpCodeClient;
import com.dipscore.backend.external.dart.corpcode.dto.DartCorpCode;
import com.dipscore.backend.external.dart.financials.DartFinancialsClient;
import com.dipscore.backend.external.dart.financials.dto.DartFinancialStatementResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DartApiClientTest {

    private static final String BASE = "https://opendart.example";
    private static final String KEY = "test-key-0123456789";

    private static DartApiProperties props(String apiKey) {
        return new DartApiProperties(
                apiKey, BASE, Duration.ofSeconds(3), Duration.ofSeconds(10),
                "/api/company.json", "/api/fnlttSinglAcntAll.json", "/api/corpCode.xml");
    }

    private record Fixture(RestClient client, MockRestServiceServer server) {
    }

    private static Fixture fixture(String apiKey) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE)
                .requestInterceptor(new DartApiKeyInterceptor(apiKey));
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(builder.build(), server);
    }

    @Test
    void 기업개황을_조회하고_crtfc_key가_주입된다() {
        Fixture f = fixture(KEY);
        f.server().expect(method(HttpMethod.GET))
                .andExpect(queryParam("crtfc_key", KEY))
                .andExpect(queryParam("corp_code", "00126380"))
                .andRespond(withSuccess("""
                        {"status":"000","message":"정상","corp_code":"00126380","corp_name":"삼성전자",
                         "stock_name":"삼성전자","stock_code":"005930","corp_cls":"Y","induty_code":"264"}
                        """, MediaType.APPLICATION_JSON));

        DartCompanyResponse res = new DartCompanyClient(f.client(), props(KEY)).getCompany("00126380");

        assertThat(res.corpName()).isEqualTo("삼성전자");
        assertThat(res.stockCode()).isEqualTo("005930");
        assertThat(res.corpCls()).isEqualTo("Y");
        f.server().verify();
    }

    @Test
    void 단일회사_전체재무제표를_조회한다() {
        Fixture f = fixture(KEY);
        f.server().expect(method(HttpMethod.GET))
                .andExpect(queryParam("corp_code", "00126380"))
                .andExpect(queryParam("bsns_year", "2024"))
                .andExpect(queryParam("reprt_code", "11011"))
                .andExpect(queryParam("fs_div", "CFS"))
                .andExpect(queryParam("crtfc_key", KEY))
                .andRespond(withSuccess("""
                        {"status":"000","message":"정상","list":[
                          {"corp_code":"00126380","sj_div":"BS","sj_nm":"재무상태표",
                           "account_nm":"유동자산","thstrm_nm":"제 55 기","thstrm_amount":"195936557000000","currency":"KRW"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        DartFinancialStatementResponse res = new DartFinancialsClient(f.client(), props(KEY))
                .getSingleCompanyFullStatements("00126380", 2024, "11011", "CFS");

        assertThat(res.list()).hasSize(1);
        assertThat(res.list().get(0).accountName()).isEqualTo("유동자산");
        assertThat(res.list().get(0).statementDiv()).isEqualTo("BS");
        f.server().verify();
    }

    @Test
    void status가_000이_아니면_예외로_변환된다() {
        Fixture f = fixture(KEY);
        f.server().expect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"status\":\"013\",\"message\":\"조회된 데이타가 없습니다.\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new DartCompanyClient(f.client(), props(KEY)).getCompany("00126380"))
                .isInstanceOf(DartApiException.class)
                .hasMessageContaining("013");
    }

    @Test
    void API키가_없으면_명확한_예외를_던진다() {
        Fixture f = fixture("");
        assertThatThrownBy(() -> new DartCompanyClient(f.client(), props("")).getCompany("00126380"))
                .isInstanceOf(DartApiException.class)
                .hasMessageContaining("DART_API_KEY");
    }

    @Test
    void 고유번호_ZIP을_받아_종목코드로_corp_code를_찾는다() throws Exception {
        String xml = """
                <result>
                  <list><corp_code>00126380</corp_code><corp_name>삼성전자</corp_name><stock_code>005930</stock_code><modify_date>20170630</modify_date></list>
                  <list><corp_code>00164779</corp_code><corp_name>SK하이닉스</corp_name><stock_code>000660</stock_code><modify_date>20170630</modify_date></list>
                  <list><corp_code>00999999</corp_code><corp_name>비상장회사</corp_name><stock_code> </stock_code><modify_date>20200101</modify_date></list>
                </result>
                """;
        byte[] zip;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("CORPCODE.xml"));
            zos.write(xml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.finish();
            zip = bos.toByteArray();
        }

        Fixture f = fixture(KEY);
        f.server().expect(method(HttpMethod.GET))
                .andExpect(queryParam("crtfc_key", KEY))
                .andRespond(withSuccess(zip, MediaType.APPLICATION_OCTET_STREAM));

        DartCorpCodeClient client = new DartCorpCodeClient(f.client(), props(KEY), new ObjectMapper());
        Optional<DartCorpCode> samsung = client.findByStockCode("005930");

        assertThat(samsung).isPresent();
        assertThat(samsung.get().corpCode()).isEqualTo("00126380");
        assertThat(samsung.get().isListed()).isTrue();
        f.server().verify();
    }

    @Test
    void 고유번호_요청이_에러JSON이면_예외로_변환된다() {
        Fixture f = fixture(KEY);
        f.server().expect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"status\":\"010\",\"message\":\"등록되지 않은 인증키입니다.\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new DartCorpCodeClient(f.client(), props(KEY), new ObjectMapper()).downloadAll())
                .isInstanceOf(DartApiException.class)
                .hasMessageContaining("010");
    }
}
