package com.dipscore.backend.marketdata.backfill;

import java.util.Collection;

import com.dipscore.backend.external.toss.candle.dto.TossCandle;
import com.dipscore.backend.marketdata.price.PriceHistoryRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 한 종목분 캔들을 {@code price_history} 에 OHLCV upsert (종목 단위 트랜잭션).
 * 종목별로 커밋되므로 뒤 종목이 실패해도 앞 종목 적재분은 남는다.
 */
@Component
@ConditionalOnProperty(prefix = "backfill.prices", name = "enabled", havingValue = "true")
public class PriceHistoryBackfillWriter {

    private final PriceHistoryRepository repository;

    public PriceHistoryBackfillWriter(PriceHistoryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public int upsertAll(String symbol, String source, Collection<TossCandle> candles) {
        int n = 0;
        for (TossCandle c : candles) {
            if (c.timestamp() == null || c.close() == null) {
                continue;
            }
            repository.upsertOhlcv(symbol, c.timestamp(), c.open(), c.high(), c.low(), c.close(),
                    c.volume(), source);
            n++;
        }
        return n;
    }
}
