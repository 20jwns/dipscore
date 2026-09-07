package com.dipscore.backend.entryscore.persistence;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * 저점 진입 스코어 계산 결과 (기획서 5장).
 * {@code entry_score = filter_passed ? 100 × Σ(가중치·component)/Σ가중치 : 0}
 */
@Entity
@Table(name = "entry_score")
@IdClass(EntryScoreId.class)
public class EntryScore {

    @Id
    @Column(name = "symbol", length = 20, nullable = false)
    private String symbol;

    @Id
    @Column(name = "as_of", nullable = false)
    private Instant asOf;

    /** 반등신호 0~1. */
    @Column(name = "rebound_signal", precision = 6, scale = 4, nullable = false)
    private BigDecimal reboundSignal;

    /** 기술적지표 0~1. */
    @Column(name = "technical_indicator", precision = 6, scale = 4, nullable = false)
    private BigDecimal technicalIndicator;

    /** 매력도 component 0~1 (base_score/100). */
    @Column(name = "attractiveness_component", precision = 6, scale = 4, nullable = false)
    private BigDecimal attractivenessComponent;

    /** 저점 진입 스코어 0~100. */
    @Column(name = "entry_score", precision = 6, scale = 2, nullable = false)
    private BigDecimal entryScore;

    /** 매력도 1차 필터 통과 여부. */
    @Column(name = "filter_passed", nullable = false)
    private boolean filterPassed;

    /** 컴포넌트별 raw 지표/가중 상세 (JSON 문자열). */
    @Column(name = "factor_breakdown", nullable = false)
    private String factorBreakdown;

    @Column(name = "engine_version", length = 16, nullable = false)
    private String engineVersion;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected EntryScore() {
    }

    public EntryScore(String symbol, Instant asOf, BigDecimal reboundSignal, BigDecimal technicalIndicator,
                      BigDecimal attractivenessComponent, BigDecimal entryScore, boolean filterPassed,
                      String factorBreakdown, String engineVersion) {
        this.symbol = symbol;
        this.asOf = asOf;
        this.reboundSignal = reboundSignal;
        this.technicalIndicator = technicalIndicator;
        this.attractivenessComponent = attractivenessComponent;
        this.entryScore = entryScore;
        this.filterPassed = filterPassed;
        this.factorBreakdown = factorBreakdown;
        this.engineVersion = engineVersion;
    }

    public String getSymbol() {
        return symbol;
    }

    public Instant getAsOf() {
        return asOf;
    }

    public BigDecimal getReboundSignal() {
        return reboundSignal;
    }

    public BigDecimal getTechnicalIndicator() {
        return technicalIndicator;
    }

    public BigDecimal getAttractivenessComponent() {
        return attractivenessComponent;
    }

    public BigDecimal getEntryScore() {
        return entryScore;
    }

    public boolean isFilterPassed() {
        return filterPassed;
    }

    public String getFactorBreakdown() {
        return factorBreakdown;
    }

    public String getEngineVersion() {
        return engineVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
