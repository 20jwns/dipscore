package com.dipscore.backend.marketdata.dailybatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.dipscore.backend.external.toss.TossApiException;
import com.dipscore.backend.external.toss.candle.TossCandleClient;
import com.dipscore.backend.external.toss.candle.dto.TossCandle;
import com.dipscore.backend.external.toss.candle.dto.TossCandlePage;
import com.dipscore.backend.marketdata.dailybatch.DailyCandleSyncJob.DailyCandleSyncReport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyCandleSyncJobTest {

    @Mock
    TossCandleClient candleClient;
    @Mock
    DailyCandleSyncWriter writer;

    private static final Instant TS = Instant.parse("2026-09-08T00:00:00Z");

    private DailyCandleSyncJob job() {
        return new DailyCandleSyncJob(candleClient, writer,
                new DailyBatchProperties(List.of("005930", "000660")),
                new DailyCandleSyncProperties(true, "0 0 16 * * MON-FRI"));
    }

    private static TossCandlePage page(String close) {
        return new TossCandlePage(List.of(new TossCandle(TS,
                new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("90"),
                new BigDecimal(close), 1_000L, "KRW")), null);
    }

    @Test
    void 각_종목_최신_일봉을_upsert하고_성공수를_집계한다() {
        when(candleClient.getDailyCandles("005930", 1, null)).thenReturn(page("257000"));
        when(candleClient.getDailyCandles("000660", 1, null)).thenReturn(page("180000"));

        DailyCandleSyncReport report = job().sync(List.of("005930", "000660"));

        verify(writer).upsert(eq("005930"), eq("TOSS_CANDLE_1D"), any(TossCandle.class));
        verify(writer).upsert(eq("000660"), eq("TOSS_CANDLE_1D"), any(TossCandle.class));
        assertThat(report.requested()).isEqualTo(2);
        assertThat(report.succeeded()).isEqualTo(2);
        assertThat(report.failed()).isZero();
        assertThat(report.results()).allMatch(DailyCandleSyncJob.SymbolResult::ok);
    }

    @Test
    void 한_종목_API_실패해도_나머지_종목은_계속_처리한다() {
        when(candleClient.getDailyCandles("005930", 1, null))
                .thenThrow(new TossApiException("캔들 조회 실패(005930): HTTP 500"));
        when(candleClient.getDailyCandles("000660", 1, null)).thenReturn(page("180000"));

        DailyCandleSyncReport report = job().sync(List.of("005930", "000660"));

        verify(writer, never()).upsert(eq("005930"), any(), any());
        verify(writer).upsert(eq("000660"), eq("TOSS_CANDLE_1D"), any());
        assertThat(report.succeeded()).isEqualTo(1);
        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.results()).anySatisfy(r -> {
            assertThat(r.symbol()).isEqualTo("005930");
            assertThat(r.ok()).isFalse();
            assertThat(r.error()).contains("HTTP 500");
        });
    }

    @Test
    void writer_예외도_삼키고_다음_종목으로_넘어간다() {
        when(candleClient.getDailyCandles(any(), eq(1), isNull())).thenReturn(page("100"));
        doThrow(new RuntimeException("db down")).when(writer).upsert(eq("005930"), any(), any());

        DailyCandleSyncReport report = job().sync(List.of("005930", "000660"));

        assertThat(report.succeeded()).isEqualTo(1);
        assertThat(report.failed()).isEqualTo(1);
    }

    @Test
    void 최신_일봉이_없으면_실패로_집계하고_upsert하지_않는다() {
        when(candleClient.getDailyCandles("005930", 1, null))
                .thenReturn(new TossCandlePage(List.of(), null));

        DailyCandleSyncReport report = job().sync(List.of("005930"));

        verify(writer, never()).upsert(any(), any(), any());
        assertThat(report.succeeded()).isZero();
        assertThat(report.failed()).isEqualTo(1);
    }

    @Test
    void 대상_종목이_없으면_아무것도_하지_않는다() {
        DailyCandleSyncReport report = job().sync(List.of());

        assertThat(report.requested()).isZero();
        verifyNoInteractions(candleClient, writer);
    }
}
