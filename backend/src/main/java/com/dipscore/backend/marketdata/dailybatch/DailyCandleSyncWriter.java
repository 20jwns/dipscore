package com.dipscore.backend.marketdata.dailybatch;

import com.dipscore.backend.external.toss.candle.dto.TossCandle;
import com.dipscore.backend.marketdata.price.PriceHistoryRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일봉 1건을 {@code price_history} 에 OHLCV upsert (종목 단위 트랜잭션).
 * 종목별로 커밋되므로 뒤 종목이 실패해도 앞 종목 적재분은 남는다.
 * ({@code backfill} 의 {@code PriceHistoryBackfillWriter} 와 같은 패턴, upsert 라 재실행 안전)
 */
@Component
@ConditionalOnProperty(prefix = "daily-batch.daily-candle", name = "enabled", havingValue = "true")
public class DailyCandleSyncWriter {

    private final PriceHistoryRepository repository;

    public DailyCandleSyncWriter(PriceHistoryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void upsert(String symbol, String source, TossCandle c) {
        repository.upsertOhlcv(symbol, c.timestamp(), c.open(), c.high(), c.low(), c.close(),
                c.volume(), source);
    }
}
