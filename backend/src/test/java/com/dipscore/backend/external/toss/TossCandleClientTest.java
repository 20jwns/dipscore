package com.dipscore.backend.external.toss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;

import com.dipscore.backend.external.toss.candle.TossCandleClient;
import com.dipscore.backend.external.toss.candle.dto.TossCandlePage;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TossCandleClientTest {

    private static final String BASE = "https://api.example";

    private record Fixture(TossCandleClient client, MockRestServiceServer server) {
    }

    private static Fixture fixture() {
        RestClient.Builder b = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        return new Fixture(new TossCandleClient(b.build()), server);
    }

    @Test
    void 일봉_첫페이지_요청과_파싱() {
        Fixture f = fixture();
        f.server().expect(method(HttpMethod.GET))
                .andExpect(queryParam("symbol", "005930"))
                .andExpect(queryParam("interval", "1d"))
                .andExpect(queryParam("count", "200"))
                .andRespond(withSuccess("""
                        {"result":{"candles":[
                          {"timestamp":"2026-03-25T09:00:00+09:00","openPrice":"71600","highPrice":"72300","lowPrice":"71500","closePrice":"72000","volume":"3521000","currency":"KRW"}
                        ],"nextBefore":"2026-03-24T09:00:00+09:00"}}
                        """, MediaType.APPLICATION_JSON));

        TossCandlePage page = f.client().getDailyCandles("005930", 200, null);

        assertThat(page.candles()).hasSize(1);
        var c = page.candles().get(0);
        assertThat(c.timestamp()).isEqualTo(Instant.parse("2026-03-25T00:00:00Z")); // 09:00+09:00 → 00:00Z
        assertThat(c.open()).isEqualByComparingTo("71600");
        assertThat(c.high()).isEqualByComparingTo("72300");
        assertThat(c.low()).isEqualByComparingTo("71500");
        assertThat(c.close()).isEqualByComparingTo("72000");
        assertThat(c.volume()).isEqualTo(3_521_000L);
        assertThat(page.nextBefore()).isEqualTo(Instant.parse("2026-03-24T00:00:00Z"));
        f.server().verify();
    }

    @Test
    void 페이지네이션_before는_UTC_Z형식으로_전달_nextBefore_null이면_마지막() {
        Fixture f = fixture();
        f.server().expect(method(HttpMethod.GET))
                .andExpect(queryParam("before", "2026-03-24T00:00:00Z"))
                .andRespond(withSuccess("""
                        {"result":{"candles":[
                          {"timestamp":"2026-03-23T09:00:00+09:00","openPrice":"70000","highPrice":"70500","lowPrice":"69800","closePrice":"70100","volume":"1000000","currency":"KRW"}
                        ],"nextBefore":null}}
                        """, MediaType.APPLICATION_JSON));

        TossCandlePage page = f.client().getDailyCandles("005930", 200, Instant.parse("2026-03-24T00:00:00Z"));

        assertThat(page.candles()).hasSize(1);
        assertThat(page.nextBefore()).isNull();
        f.server().verify();
    }

    @Test
    void HTTP_에러는_TossApiException으로_변환된다() {
        Fixture f = fixture();
        f.server().expect(method(HttpMethod.GET)).andRespond(withResourceNotFound());

        assertThatThrownBy(() -> f.client().getDailyCandles("BADCODE", 200, null))
                .isInstanceOf(TossApiException.class)
                .hasMessageContaining("404");
    }

    @Test
    void count_범위밖이면_예외() {
        assertThatThrownBy(() -> fixture().client().getDailyCandles("005930", 201, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("200");
    }
}
