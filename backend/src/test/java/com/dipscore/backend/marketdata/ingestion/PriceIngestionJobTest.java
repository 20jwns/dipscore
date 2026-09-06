package com.dipscore.backend.marketdata.ingestion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.dipscore.backend.external.toss.TossApiException;
import com.dipscore.backend.external.toss.quote.TossQuoteClient;
import com.dipscore.backend.external.toss.quote.dto.TossPriceQuote;
import com.dipscore.backend.marketdata.price.PriceHistoryRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceIngestionJobTest {

    @Mock
    TossQuoteClient tossQuoteClient;

    @Mock
    PriceHistoryRepository priceHistoryRepository;

    private PriceIngestionJob job(String... symbols) {
        return new PriceIngestionJob(tossQuoteClient, priceHistoryRepository,
                new PriceIngestionProperties(true, List.of(symbols)));
    }

    @Test
    void 조회한_현재가를_close로_upsert한다() {
        Instant ts = Instant.parse("2026-09-04T10:59:59Z");
        when(tossQuoteClient.getQuotes(List.of("005930")))
                .thenReturn(List.of(new TossPriceQuote("005930", ts, new BigDecimal("257000"), "KRW")));

        job("005930").runOnce();

        verify(priceHistoryRepository).upsertClose("005930", ts, new BigDecimal("257000"), "TOSS_QUOTE");
    }

    @Test
    void timestamp가_없는_응답은_저장하지_않는다() {
        when(tossQuoteClient.getQuotes(List.of("005930")))
                .thenReturn(List.of(new TossPriceQuote("005930", null, new BigDecimal("257000"), "KRW")));

        job("005930").runOnce();

        verify(priceHistoryRepository, never()).upsertClose(any(), any(), any(), any());
    }

    @Test
    void 토스_조회_실패시_예외를_삼키고_넘어간다() {
        when(tossQuoteClient.getQuotes(any())).thenThrow(new TossApiException("토큰 발급 실패: HTTP 403"));

        job("005930").runOnce(); // 예외 전파 없음

        verify(priceHistoryRepository, never()).upsertClose(any(), any(), any(), any());
    }

    @Test
    void 대상_종목이_없으면_조회도_하지_않는다() {
        job().runOnce();

        verifyNoInteractions(tossQuoteClient, priceHistoryRepository);
    }
}
