package com.dipscore.backend.marketdata.price;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, PriceHistoryId> {

    /**
     * 현재가 스냅샷 upsert. 같은 (symbol, ts) 재수집 시 close 만 갱신한다.
     * 호출자는 트랜잭션 컨텍스트 안이어야 한다 ({@code PriceIngestionJob#runOnce} 가 {@code @Transactional}).
     */
    @Modifying
    @Query(value = """
            INSERT INTO price_history (symbol, ts, close, source)
            VALUES (:symbol, :ts, :close, :source)
            ON CONFLICT (symbol, ts) DO UPDATE SET close = EXCLUDED.close
            """, nativeQuery = true)
    void upsertClose(@Param("symbol") String symbol,
                     @Param("ts") Instant ts,
                     @Param("close") BigDecimal close,
                     @Param("source") String source);

    /**
     * OHLCV 전체 upsert (일봉 백필용). 같은 (symbol, ts) 면 O/H/L/C/V/source 를 모두 갱신한다.
     * 호출자는 트랜잭션 컨텍스트 안이어야 한다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO price_history (symbol, ts, open, high, low, close, volume, source)
            VALUES (:symbol, :ts, :open, :high, :low, :close, :volume, :source)
            ON CONFLICT (symbol, ts) DO UPDATE SET
              open = EXCLUDED.open, high = EXCLUDED.high, low = EXCLUDED.low,
              close = EXCLUDED.close, volume = EXCLUDED.volume, source = EXCLUDED.source
            """, nativeQuery = true)
    void upsertOhlcv(@Param("symbol") String symbol,
                     @Param("ts") Instant ts,
                     @Param("open") BigDecimal open,
                     @Param("high") BigDecimal high,
                     @Param("low") BigDecimal low,
                     @Param("close") BigDecimal close,
                     @Param("volume") Long volume,
                     @Param("source") String source);

    List<PriceHistory> findBySymbolOrderByTsDesc(String symbol);

    /** full OHLC 를 가진 일봉만 시간 오름차순 (스냅샷/close-only 행 제외). 지표 계산용. */
    List<PriceHistory> findBySymbolAndOpenNotNullOrderByTsAsc(String symbol);
}
