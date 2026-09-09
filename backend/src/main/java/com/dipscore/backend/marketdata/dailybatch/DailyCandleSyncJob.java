package com.dipscore.backend.marketdata.dailybatch;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.dipscore.backend.external.toss.candle.TossCandleClient;
import com.dipscore.backend.external.toss.candle.dto.TossCandle;
import com.dipscore.backend.external.toss.candle.dto.TossCandlePage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 장 마감 후 {@code daily-batch.symbols} 대상 종목의 <b>최신 일봉 1건</b>을 조회해
 * {@code price_history} 에 append 한다 ({@link TossCandleClient#getDailyCandles} count=1, upsert).
 *
 * <p>{@code PriceIngestionJob} 과 같은 스케줄러 패턴 —
 * {@code daily-batch.daily-candle.enabled=true} 일 때만 빈이 생성된다
 * (테스트 컨텍스트엔 이 키가 없어 스케줄러가 뜨지 않는다 → 외부 API 호출 방지).
 *
 * <p>한 종목 조회/적재가 실패해도 예외를 삼키고 다음 종목으로 넘어간다. 실행 결과(성공/실패 수)는 로그 + 반환.
 * 수동 트리거: {@code POST /api/admin/daily-candle-sync}.
 */
@Component
@ConditionalOnProperty(prefix = "daily-batch.daily-candle", name = "enabled", havingValue = "true")
public class DailyCandleSyncJob {

    private static final Logger log = LoggerFactory.getLogger(DailyCandleSyncJob.class);
    /** 백필과 동일 소스 태그 — 같은 일봉 OHLCV 데이터. */
    private static final String SOURCE = "TOSS_CANDLE_1D";

    private final TossCandleClient candleClient;
    private final DailyCandleSyncWriter writer;
    private final DailyBatchProperties batchProps;
    private final DailyCandleSyncProperties props;

    public DailyCandleSyncJob(TossCandleClient candleClient,
                              DailyCandleSyncWriter writer,
                              DailyBatchProperties batchProps,
                              DailyCandleSyncProperties props) {
        this.candleClient = candleClient;
        this.writer = writer;
        this.batchProps = batchProps;
        this.props = props;
    }

    @PostConstruct
    void logSchedule() {
        log.info("[daily-candle-sync] 스케줄 등록: cron='{}' (Asia/Seoul), 대상 {}종목 {}",
                props.cron(), batchProps.symbols().size(), batchProps.symbols());
    }

    /** 스케줄 실행 진입점. cron 은 Asia/Seoul 기준. */
    @Scheduled(cron = "${daily-batch.daily-candle.cron:0 0 16 * * MON-FRI}", zone = "Asia/Seoul")
    void scheduledRun() {
        sync(batchProps.symbols());
    }

    /** 지정 종목의 최신 일봉 1건씩 적재. 종목별 독립 — 실패해도 다음 종목 계속. */
    public DailyCandleSyncReport sync(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            log.warn("[daily-candle-sync] 대상 종목이 없어 건너뜀");
            return new DailyCandleSyncReport(0, 0, 0, List.of());
        }

        List<SymbolResult> results = new ArrayList<>(symbols.size());
        for (String symbol : symbols) {
            try {
                TossCandlePage page = candleClient.getDailyCandles(symbol, 1, null);
                TossCandle candle = latest(page);
                if (candle == null) {
                    results.add(SymbolResult.failure(symbol, "최신 일봉 없음(빈 응답)"));
                    log.warn("[daily-candle-sync] {}: 최신 일봉 없음, 건너뜀", symbol);
                    continue;
                }
                writer.upsert(symbol, SOURCE, candle);
                results.add(SymbolResult.success(symbol, candle.timestamp(), candle.close()));
                log.info("[daily-candle-sync] {}: {} 종가 {} 적재", symbol, candle.timestamp(), candle.close());
            } catch (RuntimeException e) {
                results.add(SymbolResult.failure(symbol, e.toString()));
                log.warn("[daily-candle-sync] {}: 조회/적재 실패, 다음 종목으로: {}", symbol, e.toString());
            }
        }

        int ok = (int) results.stream().filter(SymbolResult::ok).count();
        int failed = results.size() - ok;
        log.info("[daily-candle-sync] 완료: {}종목 중 성공 {} / 실패 {}", results.size(), ok, failed);
        return new DailyCandleSyncReport(symbols.size(), ok, failed, results);
    }

    /** count=1 이면 보통 1건이지만, 안전하게 ts/close 있는 것 중 가장 최신을 고른다. */
    private static TossCandle latest(TossCandlePage page) {
        if (page == null || page.candles() == null) {
            return null;
        }
        return page.candles().stream()
                .filter(c -> c.timestamp() != null && c.close() != null)
                .max(Comparator.comparing(TossCandle::timestamp))
                .orElse(null);
    }

    /**
     * @param symbol 종목코드
     * @param ok     적재 성공 여부
     * @param ts     적재한 봉 시각 (실패 시 null)
     * @param close  적재한 종가 (실패 시 null)
     * @param error  실패 사유 (성공 시 null)
     */
    public record SymbolResult(String symbol, boolean ok, Instant ts, BigDecimal close, String error) {
        static SymbolResult success(String symbol, Instant ts, BigDecimal close) {
            return new SymbolResult(symbol, true, ts, close, null);
        }

        static SymbolResult failure(String symbol, String error) {
            return new SymbolResult(symbol, false, null, null, error);
        }
    }

    /**
     * @param requested 요청 종목 수
     * @param succeeded 적재 성공 수
     * @param failed    조회/적재 실패 수
     * @param results   종목별 결과
     */
    public record DailyCandleSyncReport(int requested, int succeeded, int failed, List<SymbolResult> results) {}
}
