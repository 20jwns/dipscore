package com.dipscore.backend.external.toss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.dipscore.backend.external.toss.auth.TossOAuthClient;
import com.dipscore.backend.external.toss.auth.TossTokenManager;
import com.dipscore.backend.external.toss.quote.TossQuoteClient;
import com.dipscore.backend.external.toss.quote.dto.TossPriceQuote;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TossApiClientTest {

    private static final String AUTH_BASE = "https://auth.example";
    private static final String API_BASE = "https://api.example";

    private static TossApiProperties props(String clientId, String clientSecret) {
        return new TossApiProperties(
                clientId, clientSecret, API_BASE,
                Duration.ofSeconds(3), Duration.ofSeconds(5),
                new TossApiProperties.Auth(AUTH_BASE, "/oauth2/token", "client_credentials", "",
                        Duration.ofSeconds(60)),
                new TossApiProperties.Quote("/api/v1/prices"));
    }

    /** 진행 중 임의로 시간을 앞당길 수 있는 테스트용 Clock. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override public Instant instant() {
            return now;
        }
    }

    @Test
    void 토큰_발급_후_시세조회에_Bearer_토큰이_주입된다() {
        TossApiProperties props = props("cid", "secret");

        RestClient.Builder authBuilder = RestClient.builder().baseUrl(AUTH_BASE);
        MockRestServiceServer authServer = MockRestServiceServer.bindTo(authBuilder).build();
        authServer.expect(once(), requestTo(AUTH_BASE + "/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("grant_type=client_credentials")))
                .andExpect(content().string(containsString("client_id=cid")))
                .andRespond(withSuccess("""
                        {"access_token":"tok-1","token_type":"Bearer","expires_in":3600}
                        """, MediaType.APPLICATION_JSON));

        TossOAuthClient oauthClient = new TossOAuthClient(authBuilder.build(), props);
        TossTokenManager tokenManager = new TossTokenManager(oauthClient, props);

        RestClient.Builder apiBuilder = RestClient.builder().baseUrl(API_BASE)
                .requestInterceptor(new BearerTokenInterceptor(tokenManager));
        MockRestServiceServer apiServer = MockRestServiceServer.bindTo(apiBuilder).build();
        apiServer.expect(once(), requestTo(API_BASE + "/api/v1/prices?symbols=005930"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok-1"))
                .andRespond(withSuccess("""
                        {"result":[{"symbol":"005930","timestamp":"2026-03-25T09:30:00.123+09:00","lastPrice":"73500","currency":"KRW"}]}
                        """, MediaType.APPLICATION_JSON));

        TossQuoteClient quoteClient = new TossQuoteClient(apiBuilder.build(), props);

        TossPriceQuote quote = quoteClient.getQuote("005930");

        assertThat(quote.symbol()).isEqualTo("005930");
        assertThat(quote.lastPrice()).isEqualByComparingTo("73500");
        assertThat(quote.currency()).isEqualTo("KRW");
        authServer.verify();
        apiServer.verify();
    }

    @Test
    void 유효한_토큰은_캐시되어_재발급하지_않는다() {
        TossApiProperties props = props("cid", "secret");

        RestClient.Builder authBuilder = RestClient.builder().baseUrl(AUTH_BASE);
        MockRestServiceServer authServer = MockRestServiceServer.bindTo(authBuilder).build();
        authServer.expect(once(), requestTo(AUTH_BASE + "/oauth2/token"))
                .andRespond(withSuccess("""
                        {"access_token":"tok-1","expires_in":3600}
                        """, MediaType.APPLICATION_JSON));

        TossOAuthClient oauthClient = new TossOAuthClient(authBuilder.build(), props);
        TossTokenManager tokenManager = new TossTokenManager(oauthClient, props);

        RestClient.Builder apiBuilder = RestClient.builder().baseUrl(API_BASE)
                .requestInterceptor(new BearerTokenInterceptor(tokenManager));
        MockRestServiceServer apiServer = MockRestServiceServer.bindTo(apiBuilder).build();
        apiServer.expect(times(2), requestTo(API_BASE + "/api/v1/prices?symbols=AAPL"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok-1"))
                .andRespond(withSuccess("""
                        {"result":[{"symbol":"AAPL","timestamp":"2026-03-25T22:30:00.456+09:00","lastPrice":"231.4","currency":"USD"}]}
                        """, MediaType.APPLICATION_JSON));

        TossQuoteClient quoteClient = new TossQuoteClient(apiBuilder.build(), props);

        quoteClient.getQuote("AAPL");
        quoteClient.getQuote("AAPL");

        authServer.verify(); // 토큰 엔드포인트는 정확히 1회만 호출되어야 한다
        apiServer.verify();
    }

    @Test
    void 만료가_임박하면_refresh_token으로_갱신한다() {
        TossApiProperties props = props("cid", "secret");
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

        RestClient.Builder authBuilder = RestClient.builder().baseUrl(AUTH_BASE);
        MockRestServiceServer authServer = MockRestServiceServer.bindTo(authBuilder).build();
        // 1차: 신규 발급 (refresh_token 포함, 120초 만료)
        authServer.expect(once(), requestTo(AUTH_BASE + "/oauth2/token"))
                .andExpect(content().string(containsString("grant_type=client_credentials")))
                .andRespond(withSuccess("""
                        {"access_token":"tok-1","expires_in":120,"refresh_token":"rt-1"}
                        """, MediaType.APPLICATION_JSON));
        // 2차: refresh_token 으로 갱신
        authServer.expect(once(), requestTo(AUTH_BASE + "/oauth2/token"))
                .andExpect(content().string(containsString("grant_type=refresh_token")))
                .andExpect(content().string(containsString("refresh_token=rt-1")))
                .andRespond(withSuccess("""
                        {"access_token":"tok-2","expires_in":120}
                        """, MediaType.APPLICATION_JSON));

        TossOAuthClient oauthClient = new TossOAuthClient(authBuilder.build(), props);
        TossTokenManager tokenManager = new TossTokenManager(oauthClient, props, clock);

        RestClient.Builder apiBuilder = RestClient.builder().baseUrl(API_BASE)
                .requestInterceptor(new BearerTokenInterceptor(tokenManager));
        MockRestServiceServer apiServer = MockRestServiceServer.bindTo(apiBuilder).build();
        apiServer.expect(once(), requestTo(API_BASE + "/api/v1/prices?symbols=005930"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok-1"))
                .andRespond(withSuccess(
                        "{\"result\":[{\"symbol\":\"005930\",\"timestamp\":\"2026-01-01T00:00:00Z\",\"lastPrice\":\"100\",\"currency\":\"KRW\"}]}",
                        MediaType.APPLICATION_JSON));
        apiServer.expect(once(), requestTo(API_BASE + "/api/v1/prices?symbols=005930"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok-2"))
                .andRespond(withSuccess(
                        "{\"result\":[{\"symbol\":\"005930\",\"timestamp\":\"2026-01-01T00:01:30Z\",\"lastPrice\":\"101\",\"currency\":\"KRW\"}]}",
                        MediaType.APPLICATION_JSON));

        TossQuoteClient quoteClient = new TossQuoteClient(apiBuilder.build(), props);

        quoteClient.getQuote("005930");        // tok-1
        clock.advance(Duration.ofSeconds(90)); // 만료(120s) - skew(60s) = 60s 지점 초과 → 갱신 필요
        quoteClient.getQuote("005930");        // tok-2

        authServer.verify();
        apiServer.verify();
    }

    @Test
    void 자격증명이_없으면_명확한_예외를_던진다() {
        TossApiProperties props = props("", "");
        RestClient.Builder authBuilder = RestClient.builder().baseUrl(AUTH_BASE);
        MockRestServiceServer.bindTo(authBuilder).build();
        TossOAuthClient oauthClient = new TossOAuthClient(authBuilder.build(), props);

        assertThatThrownBy(oauthClient::issue)
                .isInstanceOf(TossApiException.class)
                .hasMessageContaining("TOSS_CLIENT_ID");
    }

    @Test
    void 여러_종목을_한번에_조회한다() {
        TossApiProperties props = props("cid", "secret");

        RestClient.Builder authBuilder = RestClient.builder().baseUrl(AUTH_BASE);
        MockRestServiceServer authServer = MockRestServiceServer.bindTo(authBuilder).build();
        authServer.expect(once(), requestTo(AUTH_BASE + "/oauth2/token"))
                .andRespond(withSuccess("""
                        {"access_token":"tok-1","expires_in":3600}
                        """, MediaType.APPLICATION_JSON));

        TossOAuthClient oauthClient = new TossOAuthClient(authBuilder.build(), props);
        TossTokenManager tokenManager = new TossTokenManager(oauthClient, props);

        RestClient.Builder apiBuilder = RestClient.builder().baseUrl(API_BASE)
                .requestInterceptor(new BearerTokenInterceptor(tokenManager));
        MockRestServiceServer apiServer = MockRestServiceServer.bindTo(apiBuilder).build();
        apiServer.expect(once(), requestTo(API_BASE + "/api/v1/prices?symbols=005930,AAPL"))
                .andRespond(withSuccess("""
                        {"result":[
                          {"symbol":"005930","timestamp":"2026-03-25T09:30:00+09:00","lastPrice":"73500","currency":"KRW"},
                          {"symbol":"AAPL","timestamp":"2026-03-25T22:30:00+09:00","lastPrice":"185.7","currency":"USD"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        TossQuoteClient quoteClient = new TossQuoteClient(apiBuilder.build(), props);

        var quotes = quoteClient.getQuotes(java.util.List.of("005930", "AAPL"));

        assertThat(quotes).hasSize(2);
        assertThat(quotes.get(0).symbol()).isEqualTo("005930");
        assertThat(quotes.get(1).symbol()).isEqualTo("AAPL");
        apiServer.verify();
    }

    @Test
    void symbols가_최대개수를_초과하면_네트워크_호출_없이_예외를_던진다() {
        TossApiProperties props = props("cid", "secret");
        RestClient.Builder apiBuilder = RestClient.builder().baseUrl(API_BASE);
        MockRestServiceServer.bindTo(apiBuilder).build(); // 어떤 요청도 기대하지 않음

        TossQuoteClient quoteClient = new TossQuoteClient(apiBuilder.build(), props);
        java.util.List<String> tooMany = java.util.Collections.nCopies(201, "005930");

        assertThatThrownBy(() -> quoteClient.getQuotes(tooMany))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("200");
    }
}
