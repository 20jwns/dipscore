package com.dipscore.backend.external.toss.candle;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

import com.dipscore.backend.external.toss.TossApiException;
import com.dipscore.backend.external.toss.candle.dto.TossCandlePage;
import com.dipscore.backend.external.toss.candle.dto.TossCandleResponse;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 토스증권 캔들(OHLCV) 조회 ({@code GET /api/v1/candles}).
 * Bearer 토큰은 {@code tossApiRestClient} 인터셉터가 자동 주입한다.
 *
 * <p>스펙(2026-09): {@code symbol}(필수), {@code interval}(필수, {@code 1m}|{@code 1d}),
 * {@code count}(1~200, 기본 100), {@code before}(ISO-8601, inclusive 상한, 페이지네이션),
 * {@code adjusted}(기본 true). 응답 {@code result.candles[]} + {@code result.nextBefore}.
 */
@Component
public class TossCandleClient {

    /** 스펙상 1회 조회 최대 봉 수. */
    public static final int MAX_COUNT = 200;

    private static final String INTERVAL_DAILY = "1d";

    private final RestClient apiRestClient;

    public TossCandleClient(@Qualifier("tossApiRestClient") RestClient apiRestClient) {
        this.apiRestClient = apiRestClient;
    }

    /**
     * 일봉 캔들 1페이지 조회.
     *
     * @param symbol 종목코드
     * @param count  1~200
     * @param before 이 시각과 같거나 이전인 봉만 반환 (null 이면 최신부터). 페이지네이션 시 직전 응답의 nextBefore.
     */
    public TossCandlePage getDailyCandles(String symbol, int count, Instant before) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol 은 필수입니다.");
        }
        if (count < 1 || count > MAX_COUNT) {
            throw new IllegalArgumentException("count 는 1~%d 범위여야 합니다: %d".formatted(MAX_COUNT, count));
        }
        try {
            TossCandleResponse res = apiRestClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/v1/candles")
                                .queryParam("symbol", symbol)
                                .queryParam("interval", INTERVAL_DAILY)
                                .queryParam("count", count);
                        if (before != null) {
                            // UTC 'Z' 형식으로 전달해 타임존 오프셋 '+' 의 %2B 인코딩 이슈를 피한다.
                            uriBuilder.queryParam("before", DateTimeFormatter.ISO_INSTANT.format(before));
                        }
                        return uriBuilder.build();
                    })
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw new TossApiException("캔들 조회 실패(%s): HTTP %d"
                                .formatted(symbol, resp.getStatusCode().value()));
                    })
                    .body(TossCandleResponse.class);

            if (res == null || res.result() == null) {
                throw new TossApiException("캔들 응답이 비어 있습니다: " + symbol);
            }
            return res.result();
        } catch (RestClientException e) {
            throw new TossApiException("캔들 조회 통신 오류: " + symbol, e);
        }
    }
}
