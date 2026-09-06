package com.dipscore.backend.marketdata.instrument;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 종목 마스터. 스코어링/랭킹/시세 적재의 기준 유니버스.
 * 스키마는 Flyway {@code V2__instrument_and_price_history.sql} 로 관리한다 (ddl-auto=validate).
 */
@Entity
@Table(name = "instrument")
public class Instrument {

    /** 종목코드 (국내 6자리 숫자, 미국 티커). */
    @Id
    @Column(name = "symbol", length = 20, nullable = false)
    private String symbol;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_type", length = 16, nullable = false)
    private MarketType marketType;

    /** 섹터 (대분류). nullable. */
    @Column(name = "sector", length = 100)
    private String sector;

    /** 업종 (세분류). nullable. */
    @Column(name = "industry", length = 100)
    private String industry;

    /** 호가 통화 (KRW/USD). */
    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    /** DART 공시대상회사 고유번호 (opendart corp_code, 8자리). 국내 종목만, 미상장/해외는 null. */
    @Column(name = "corp_code", length = 8)
    private String corpCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Instrument() {
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public MarketType getMarketType() {
        return marketType;
    }

    public String getSector() {
        return sector;
    }

    public String getIndustry() {
        return industry;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCorpCode() {
        return corpCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
