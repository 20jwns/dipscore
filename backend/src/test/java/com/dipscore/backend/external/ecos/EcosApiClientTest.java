package com.dipscore.backend.external.ecos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import com.dipscore.backend.external.ecos.EcosApiProperties.Indicator;
import com.dipscore.backend.external.ecos.EcosApiProperties.Indicators;
import com.dipscore.backend.external.ecos.dto.EcosStatisticRow;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class EcosApiClientTest {

    private static final String BASE = "https://ecos.example";
    private static final String KEY = "testkey123";
    // 2024-06-15 09:00 UTC → KST 로도 2024-06-15
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2024-06-15T09:00:00Z"), ZoneId.of("Asia/Seoul"));

    private static EcosApiProperties props(String apiKey) {
        return new EcosApiProperties(
                apiKey, BASE, Duration.ofSeconds(3), Duration.ofSeconds(10), "kr", "/api/StatisticSearch", 100,
                new Indicators(
                        new Indicator("722Y001", "0101000", "M"),
                        new Indicator("731Y001", "0000001", "D"),
                        new Indicator("901Y009", "0", "M")));
    }

    private record Fixture(EcosStatisticClient client, MockRestServiceServer server) {
    }

    private static Fixture fixture(String apiKey) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        EcosStatisticClient client = new EcosStatisticClient(builder.build(), props(apiKey), new ObjectMapper(), CLOCK);
        return new Fixture(client, server);
    }

    @Test
    void 기준금리_최근값을_조회한다() {
        Fixture f = fixture(KEY);
        // 월주기, 6개월 전 ~ 당월 → 202312 ~ 202406
        f.server().expect(requestTo(
                        BASE + "/api/StatisticSearch/testkey123/json/kr/1/100/722Y001/M/202312/202406/0101000"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"StatisticSearch":{"list_total_count":3,"row":[
                          {"STAT_CODE":"722Y001","STAT_NAME":"1.3.1. 한국은행 기준금리 및 여수신금리","ITEM_CODE1":"0101000","ITEM_NAME1":"한국은행 기준금리","UNIT_NAME":"연%","TIME":"202404","DATA_VALUE":"3.5"},
                          {"STAT_CODE":"722Y001","ITEM_CODE1":"0101000","UNIT_NAME":"연%","TIME":"202405","DATA_VALUE":"3.5"},
                          {"STAT_CODE":"722Y001","ITEM_CODE1":"0101000","UNIT_NAME":"연%","TIME":"202406","DATA_VALUE":"3.5"}
                        ]}}
                        """, MediaType.APPLICATION_JSON));

        EcosStatisticRow row = f.client().latestBaseRate();

        assertThat(row.time()).isEqualTo("202406");
        assertThat(row.dataValueAsDecimal()).isEqualByComparingTo("3.5");
        assertThat(row.unitName()).isEqualTo("연%");
        f.server().verify();
    }

    @Test
    void 원달러환율_최근값을_조회한다() {
        Fixture f = fixture(KEY);
        // 일주기, 10일 전 ~ 오늘 → 20240605 ~ 20240615
        f.server().expect(requestTo(
                        BASE + "/api/StatisticSearch/testkey123/json/kr/1/100/731Y001/D/20240605/20240615/0000001"))
                .andRespond(withSuccess("""
                        {"StatisticSearch":{"list_total_count":2,"row":[
                          {"STAT_NAME":"3.1.1.1. 주요국 통화의 대원화환율","ITEM_CODE1":"0000001","ITEM_NAME1":"원/미국달러(매매기준율)","UNIT_NAME":"원","TIME":"20240612","DATA_VALUE":"1373.1"},
                          {"STAT_NAME":"3.1.1.1. 주요국 통화의 대원화환율","ITEM_CODE1":"0000001","ITEM_NAME1":"원/미국달러(매매기준율)","UNIT_NAME":"원","TIME":"20240613","DATA_VALUE":"1377.0"}
                        ]}}
                        """, MediaType.APPLICATION_JSON));

        EcosStatisticRow row = f.client().latestExchangeRate();

        assertThat(row.time()).isEqualTo("20240613");
        assertThat(row.dataValueAsDecimal()).isEqualByComparingTo("1377.0");
        f.server().verify();
    }

    @Test
    void 가장_최근_TIME_행을_max로_선택한다() {
        Fixture f = fixture(KEY);
        f.server().expect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"StatisticSearch":{"list_total_count":3,"row":[
                          {"ITEM_CODE1":"0101000","TIME":"202405","DATA_VALUE":"3.50"},
                          {"ITEM_CODE1":"0101000","TIME":"202401","DATA_VALUE":"3.50"},
                          {"ITEM_CODE1":"0101000","TIME":"202403","DATA_VALUE":"3.50"}
                        ]}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(f.client().latestBaseRate().time()).isEqualTo("202405");
    }

    @Test
    void RESULT_오류응답이면_예외로_변환된다() {
        Fixture f = fixture(KEY);
        f.server().expect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"RESULT\":{\"CODE\":\"INFO-100\",\"MESSAGE\":\"인증키가 유효하지 않습니다.\"}}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> f.client().latestBaseRate())
                .isInstanceOf(EcosApiException.class)
                .hasMessageContaining("INFO-100");
    }

    @Test
    void 데이터없음_INFO_200도_예외로_변환된다() {
        Fixture f = fixture(KEY);
        f.server().expect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"RESULT\":{\"CODE\":\"INFO-200\",\"MESSAGE\":\"해당하는 데이터가 없습니다.\"}}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> f.client().latestCpi())
                .isInstanceOf(EcosApiException.class)
                .hasMessageContaining("INFO-200");
    }

    @Test
    void API키가_없으면_네트워크_호출_없이_예외를_던진다() {
        Fixture f = fixture("");
        // 기대 요청 없음 - 키 검사에서 먼저 예외
        assertThatThrownBy(() -> f.client().latestBaseRate())
                .isInstanceOf(EcosApiException.class)
                .hasMessageContaining("ECOS_API_KEY");
    }

    @Test
    void text_html_content_type_로_와도_파싱한다() {
        Fixture f = fixture(KEY);
        f.server().expect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"StatisticSearch":{"list_total_count":1,"row":[
                          {"ITEM_CODE1":"0","UNIT_NAME":"2020=100","TIME":"202405","DATA_VALUE":"114.09"}
                        ]}}
                        """, MediaType.TEXT_HTML));

        EcosStatisticRow row = f.client().latestCpi();
        assertThat(row.dataValueAsDecimal()).isEqualByComparingTo("114.09");
    }
}
