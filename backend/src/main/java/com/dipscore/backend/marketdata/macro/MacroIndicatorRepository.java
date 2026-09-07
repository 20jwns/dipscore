package com.dipscore.backend.marketdata.macro;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MacroIndicatorRepository extends JpaRepository<MacroIndicator, MacroIndicatorId> {

    /** 최근값부터 N개 ([0] = 가장 최근). Z-score 분포 창. */
    List<MacroIndicator> findByIndicatorCodeOrderByTsDesc(String indicatorCode, Limit limit);

    @Modifying
    @Query(value = """
            INSERT INTO macro_indicator (indicator_code, ts, value, unit, source)
            VALUES (:code, :ts, :value, :unit, :source)
            ON CONFLICT (indicator_code, ts) DO UPDATE SET value = EXCLUDED.value, source = EXCLUDED.source
            """, nativeQuery = true)
    void upsert(@Param("code") String indicatorCode,
                @Param("ts") Instant ts,
                @Param("value") BigDecimal value,
                @Param("unit") String unit,
                @Param("source") String source);
}
