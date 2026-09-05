package com.dipscore.backend.external.toss.quote;

import java.util.List;

import com.dipscore.backend.external.toss.TossApiException;
import com.dipscore.backend.external.toss.TossApiProperties;
import com.dipscore.backend.external.toss.quote.dto.TossPriceQuote;
import com.dipscore.backend.external.toss.quote.dto.TossPricesResponse;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 토스증권 오픈API 종목 시세 조회 클라이언트 ({@code GET /api/v1/prices}).
 * Bearer 토큰은 {@code tossApiRestClient} 인터셉터가 자동 주입한다.
 */
@Component
public class TossQuoteClient {

    /** 스펙상 한 번에 조회 가능한 최대 종목 수. */
    private static final int MAX_SYMBOLS_PER_REQUEST = 200;

    private final RestClient apiRestClient;
    private final TossApiProperties props;

    public TossQuoteClient(@Qualifier("tossApiRestClient") RestClient apiRestClient,
                           TossApiProperties props) {
        this.apiRestClient = apiRestClient;
        this.props = props;
    }

    /**
     * 종목 현재가 1건 조회.
     *
     * @param symbol 종목코드 (예: 국내 {@code "005930"}, 미국 {@code "AAPL"})
     */
    public TossPriceQuote getQuote(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol 은 필수입니다.");
        }
        List<TossPriceQuote> quotes = getQuotes(List.of(symbol));
        if (quotes.isEmpty()) {
            throw new TossApiException("시세 응답에 결과가 없습니다: " + symbol);
        }
        return quotes.get(0);
    }

    /**
     * 종목 현재가 일괄 조회 (최대 {@value #MAX_SYMBOLS_PER_REQUEST}건, comma-separated 로 한 번에 전송).
     */
    public List<TossPriceQuote> getQuotes(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("symbols 는 최소 1개 이상이어야 합니다.");
        }
        if (symbols.size() > MAX_SYMBOLS_PER_REQUEST) {
            throw new IllegalArgumentException(
                    "symbols 는 최대 %d개까지 조회 가능합니다 (요청: %d개)".formatted(MAX_SYMBOLS_PER_REQUEST, symbols.size()));
        }
        String joined = String.join(",", symbols);
        try {
            TossPricesResponse res = apiRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path(props.quote().pricePath())
                            .queryParam("symbols", joined)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw new TossApiException(
                                "시세 조회 실패(%s): HTTP %d".formatted(joined, resp.getStatusCode().value()));
                    })
                    .body(TossPricesResponse.class);

            if (res == null || res.result() == null) {
                throw new TossApiException("시세 응답이 비어 있습니다: " + joined);
            }
            return res.result();
        } catch (RestClientException e) {
            throw new TossApiException("시세 조회 통신 오류: " + joined, e);
        }
    }
}
