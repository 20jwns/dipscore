package com.dipscore.backend.marketdata.macro;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * 거시지표 관측치 시계열. TimescaleDB 하이퍼테이블({@code macro_indicator}, 파티션 {@code ts}).
 * Z-score 정규화의 분포 창으로 쓰인다.
 */
@Entity
@Table(name = "macro_indicator")
@IdClass(MacroIndicatorId.class)
public class MacroIndicator {

    /** BASE_RATE / USD_KRW / CPI ... */
    @Id
    @Column(name = "indicator_code", length = 32, nullable = false)
    private String indicatorCode;

    @Id
    @Column(name = "ts", nullable = false)
    private Instant ts;

    @Column(name = "value", precision = 20, scale = 6, nullable = false)
    private BigDecimal value;

    @Column(name = "unit", length = 16)
    private String unit;

    @Column(name = "source", length = 32, nullable = false)
    private String source;

    protected MacroIndicator() {
    }

    public String getIndicatorCode() {
        return indicatorCode;
    }

    public Instant getTs() {
        return ts;
    }

    public BigDecimal getValue() {
        return value;
    }

    public String getUnit() {
        return unit;
    }

    public String getSource() {
        return source;
    }
}
