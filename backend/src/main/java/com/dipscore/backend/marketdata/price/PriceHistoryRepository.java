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

    List<PriceHistory> findBySymbolOrderByTsDesc(String symbol);
}
