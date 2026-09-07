package com.dipscore.backend.attractiveness.persistence;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * 매력도 지수 계산 결과. 현재는 기본점수만 구현 — {@code attractiveness = base_score × event_coefficient},
 * {@code event_coefficient} 는 1.0 고정 (기획서 4-5 이벤트 조정계수 미구현).
 */
@Entity
@Table(name = "attractiveness_score")
@IdClass(AttractivenessScoreId.class)
public class AttractivenessScore {

    @Id
    @Column(name = "symbol", length = 20, nullable = false)
    private String symbol;

    @Id
    @Column(name = "as_of", nullable = false)
    private Instant asOf;

    @Column(name = "base_score", precision = 6, scale = 2, nullable = false)
    private BigDecimal baseScore;

    @Column(name = "event_coefficient", precision = 6, scale = 3, nullable = false)
    private BigDecimal eventCoefficient;

    @Column(name = "attractiveness", precision = 6, scale = 2, nullable = false)
    private BigDecimal attractiveness;

    /** 요인별 raw/정규화/가중 상세 (JSON 문자열). */
    @Column(name = "factor_breakdown", nullable = false)
    private String factorBreakdown;

    @Column(name = "engine_version", length = 16, nullable = false)
    private String engineVersion;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected AttractivenessScore() {
    }

    public AttractivenessScore(String symbol, Instant asOf, BigDecimal baseScore, BigDecimal eventCoefficient,
                               BigDecimal attractiveness, String factorBreakdown, String engineVersion) {
        this.symbol = symbol;
        this.asOf = asOf;
        this.baseScore = baseScore;
        this.eventCoefficient = eventCoefficient;
        this.attractiveness = attractiveness;
        this.factorBreakdown = factorBreakdown;
        this.engineVersion = engineVersion;
    }

    public String getSymbol() {
        return symbol;
    }

    public Instant getAsOf() {
        return asOf;
    }

    public BigDecimal getBaseScore() {
        return baseScore;
    }

    public BigDecimal getEventCoefficient() {
        return eventCoefficient;
    }

    public BigDecimal getAttractiveness() {
        return attractiveness;
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
