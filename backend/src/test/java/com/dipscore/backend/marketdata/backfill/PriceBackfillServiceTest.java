package com.dipscore.backend.marketdata.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.dipscore.backend.external.toss.candle.TossCandleClient;
import com.dipscore.backend.external.toss.candle.dto.TossCandle;
import com.dipscore.backend.external.toss.candle.dto.TossCandlePage;
import com.dipscore.backend.marketdata.backfill.PriceBackfillService.PriceBackfillReport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceBackfillServiceTest {

    @Mock
    TossCandleClient candleClient;
    @Mock
    PriceHistoryBackfillWriter writer;

    private static final Instant DAY0 = Instant.now().truncatedTo(ChronoUnit.DAYS);

    private PriceBackfillService service() {
        return new PriceBackfillService(candleClient, writer,
                new PriceBackfillProperties(true, List.of("005930"), 250, 0L)); // delay 0
    }

    /** DAY0 에서 daysBack 만큼 과거부터 count 개 일봉 (최신 → 과거 순). */
    private static List<TossCandle> candles(int daysBack, int count) {
        List<TossCandle> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Instant ts = DAY0.minus((long) daysBack + i, ChronoUnit.DAYS);
            out.add(new TossCandle(ts, new BigDecimal("100"), new BigDecimal("110"),
                    new BigDecimal("90"), new BigDecimal("105"), 1_000L, "KRW"));
        }
        return out;
    }

    @Test
    void 페이지네이션으로_모아_cutoff_이후만_upsert하고_기간을_보고한다() {
        // page1: 0 ~ -199, nextBefore = -200일
        Instant nextBefore = DAY0.minus(200, ChronoUnit.DAYS);
        when(candleClient.getDailyCandles(eq("005930"), eq(200), isNull()))
                .thenReturn(new TossCandlePage(candles(0, 200), nextBefore));
        // page2: -200 ~ -299, nextBefore = null
        when(candleClient.getDailyCandles(eq("005930"), eq(200), eq(nextBefore)))
                .thenReturn(new TossCandlePage(candles(200, 100), null));

        when(writer.upsertAll(eq("005930"), eq("TOSS_CANDLE_1D"), anyCollection()))
                .thenAnswer(inv -> ((Collection<?>) inv.getArgument(2)).size());

        PriceBackfillReport report = service().backfill(List.of("005930"), 250);

        // 캔들 클라이언트는 정확히 2회 (페이지네이션)
        verify(candleClient).getDailyCandles("005930", 200, null);
        verify(candleClient).getDailyCandles("005930", 200, nextBefore);

        // writer 로 넘어간 컬렉션: cutoff(=now-250d) 이후만, 중복 없음, ~250건
        ArgumentCaptor<Collection<TossCandle>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(writer).upsertAll(eq("005930"), eq("TOSS_CANDLE_1D"), captor.capture());
        Collection<TossCandle> saved = captor.getValue();
        assertThat(saved).hasSizeBetween(249, 252);
        assertThat(saved.stream().map(TossCandle::timestamp))
                .allMatch(ts -> !ts.isBefore(report.cutoff()))
                .doesNotHaveDuplicates();

        var r = report.results().get(0);
        assertThat(r.symbol()).isEqualTo("005930");
        assertThat(r.upserted()).isEqualTo(saved.size());
        assertThat(r.latest()).isEqualTo(DAY0);
        assertThat(r.earliest()).isAfterOrEqualTo(report.cutoff())
                .isBefore(report.cutoff().plus(3, ChronoUnit.DAYS));
        assertThat(report.totalUpserted()).isEqualTo(r.upserted());
    }

    @Test
    void nextBefore가_null이면_한_페이지만_조회한다() {
        when(candleClient.getDailyCandles(eq("005930"), eq(200), isNull()))
                .thenReturn(new TossCandlePage(candles(0, 30), null));
        when(writer.upsertAll(any(), any(), anyCollection())).thenReturn(30);

        PriceBackfillReport report = service().backfill(List.of("005930"), 250);

        verify(candleClient).getDailyCandles("005930", 200, null);
        assertThat(report.results().get(0).upserted()).isEqualTo(30);
    }

    @Test
    void 빈_응답이면_0건_적재() {
        when(candleClient.getDailyCandles(eq("005930"), eq(200), isNull()))
                .thenReturn(new TossCandlePage(List.of(), null));
        when(writer.upsertAll(any(), any(), anyCollection())).thenReturn(0);

        PriceBackfillReport report = service().backfill(List.of("005930"), 250);

        assertThat(report.totalUpserted()).isZero();
        assertThat(report.results().get(0).earliest()).isNull();
    }

    @Test
    void 종목이_없으면_아무것도_하지_않는다() {
        PriceBackfillReport report = service().backfill(List.of(), 250);
        assertThat(report.results()).isEmpty();
        verifyNoInteractions(candleClient, writer);
    }
}
