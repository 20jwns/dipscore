package com.dipscore.backend.marketdata.backfill;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;

import com.dipscore.backend.external.toss.candle.TossCandleClient;
import com.dipscore.backend.external.toss.candle.dto.TossCandle;
import com.dipscore.backend.external.toss.candle.dto.TossCandlePage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 토스 일봉 캔들로 {@code price_history} 를 과거로 백필 (일회성, 수동 트리거).
 *
 * <p>스케줄러와 무관하며 앱 기동 시 자동 실행되지 않는다 — {@code POST /api/admin/price-backfill} 로만 호출.
 * 종목별로 최신부터 {@code before}/{@code nextBefore} 페이지네이션하며 cutoff(=now-days) 까지 수집 후
 * 종목 단위로 커밋한다. 호출 사이에 {@code request-delay-ms} 딜레이를 둔다.
 */
@Service
@ConditionalOnProperty(prefix = "backfill.prices", name = "enabled", havingValue = "true")
public class PriceBackfillService {

    private static final Logger log = LoggerFactory.getLogger(PriceBackfillService.class);
    private static final String SOURCE = "TOSS_CANDLE_1D";
    /** 안전장치: 종목당 최대 페이지 (200봉 × 8 = 1600봉 ≈ 6년). */
    private static final int MAX_PAGES = 8;

    private final TossCandleClient candleClient;
    private final PriceHistoryBackfillWriter writer;
    private final PriceBackfillProperties props;

    public PriceBackfillService(TossCandleClient candleClient,
                                PriceHistoryBackfillWriter writer,
                                PriceBackfillProperties props) {
        this.candleClient = candleClient;
        this.writer = writer;
        this.props = props;
    }

    public PriceBackfillReport backfill(List<String> symbols, int days) {
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        List<SymbolBackfillResult> results = new ArrayList<>();
        int total = 0;

        for (String symbol : symbols) {
            Collection<TossCandle> candles = fetchSince(symbol, cutoff);
            int upserted = writer.upsertAll(symbol, SOURCE, candles);
            total += upserted;

            Instant earliest = candles.stream().map(TossCandle::timestamp).min(Instant::compareTo).orElse(null);
            Instant latest = candles.stream().map(TossCandle::timestamp).max(Instant::compareTo).orElse(null);
            results.add(new SymbolBackfillResult(symbol, upserted, earliest, latest));
            log.info("[price-backfill] {}: {}건 적재 (기간 {} ~ {})", symbol, upserted, earliest, latest);

            sleep();
        }

        log.info("[price-backfill] 완료: {}종목 총 {}건 (cutoff={})", symbols.size(), total, cutoff);
        return new PriceBackfillReport(cutoff, total, results);
    }

    /** 최신부터 페이지네이션하며 cutoff 이상인 봉만 모은다 (ts 오름차순, 중복 제거). */
    private Collection<TossCandle> fetchSince(String symbol, Instant cutoff) {
        TreeMap<Instant, TossCandle> byTs = new TreeMap<>();
        Instant before = null;

        for (int page = 0; page < MAX_PAGES; page++) {
            TossCandlePage p = candleClient.getDailyCandles(symbol, TossCandleClient.MAX_COUNT, before);
            List<TossCandle> pageCandles = p.candles() == null ? List.of() : p.candles();
            if (pageCandles.isEmpty()) {
                break;
            }
            Instant oldestInPage = null;
            for (TossCandle c : pageCandles) {
                if (c.timestamp() == null) {
                    continue;
                }
                byTs.putIfAbsent(c.timestamp(), c);
                if (oldestInPage == null || c.timestamp().isBefore(oldestInPage)) {
                    oldestInPage = c.timestamp();
                }
            }
            if (p.nextBefore() == null || (oldestInPage != null && !oldestInPage.isAfter(cutoff))) {
                break; // 더 없거나 cutoff 도달
            }
            before = p.nextBefore();
            sleep();
        }

        return byTs.tailMap(cutoff, true).values();
    }

    private void sleep() {
        long ms = props.requestDelayMs();
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record SymbolBackfillResult(String symbol, int upserted, Instant earliest, Instant latest) {}

    public record PriceBackfillReport(Instant cutoff, int totalUpserted, List<SymbolBackfillResult> results) {}
}
