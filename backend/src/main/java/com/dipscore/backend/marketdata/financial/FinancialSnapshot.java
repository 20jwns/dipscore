package com.dipscore.backend.marketdata.financial;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * DART 재무제표를 소화한 종목·회계연도별 핵심 계정 스냅샷. 금액 단위는 <b>백만원</b>.
 * 스키마는 Flyway {@code V4__attractiveness_base_score.sql} 로 관리 (ddl-auto=validate).
 */
@Entity
@Table(name = "financial_snapshot")
@IdClass(FinancialSnapshotId.class)
public class FinancialSnapshot {

    @Id
    @Column(name = "symbol", length = 20, nullable = false)
    private String symbol;

    @Id
    @Column(name = "fiscal_year", nullable = false)
    private Short fiscalYear;

    /** CFS(연결) / OFS(별도). */
    @Id
    @Column(name = "fs_div", length = 3, nullable = false)
    private String fsDiv;

    @Column(name = "revenue", precision = 20, scale = 2)
    private BigDecimal revenue;

    @Column(name = "operating_income", precision = 20, scale = 2)
    private BigDecimal operatingIncome;

    @Column(name = "net_income", precision = 20, scale = 2)
    private BigDecimal netIncome;

    @Column(name = "total_equity", precision = 20, scale = 2)
    private BigDecimal totalEquity;

    @Column(name = "total_liabilities", precision = 20, scale = 2)
    private BigDecimal totalLiabilities;

    /** 전기 매출액 (YoY 성장률 계산용). */
    @Column(name = "prior_revenue", precision = 20, scale = 2)
    private BigDecimal priorRevenue;

    @Column(name = "shares_outstanding")
    private Long sharesOutstanding;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "source", length = 32, nullable = false)
    private String source;

    @Column(name = "disclosed_at")
    private LocalDate disclosedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected FinancialSnapshot() {
    }

    /** 적재용 팩토리. 금액 인자는 백만원. {@code created_at} 은 DB 기본값(now())이 채운다. */
    public static FinancialSnapshot of(String symbol, short fiscalYear, String fsDiv,
                                       BigDecimal revenue, BigDecimal operatingIncome, BigDecimal netIncome,
                                       BigDecimal totalEquity, BigDecimal totalLiabilities, BigDecimal priorRevenue,
                                       Long sharesOutstanding, String currency, String source) {
        FinancialSnapshot s = new FinancialSnapshot();
        s.symbol = symbol;
        s.fiscalYear = fiscalYear;
        s.fsDiv = fsDiv;
        s.revenue = revenue;
        s.operatingIncome = operatingIncome;
        s.netIncome = netIncome;
        s.totalEquity = totalEquity;
        s.totalLiabilities = totalLiabilities;
        s.priorRevenue = priorRevenue;
        s.sharesOutstanding = sharesOutstanding;
        s.currency = currency;
        s.source = source;
        return s;
    }

    public String getSymbol() {
        return symbol;
    }

    public Short getFiscalYear() {
        return fiscalYear;
    }

    public String getFsDiv() {
        return fsDiv;
    }

    public BigDecimal getRevenue() {
        return revenue;
    }

    public BigDecimal getOperatingIncome() {
        return operatingIncome;
    }

    public BigDecimal getNetIncome() {
        return netIncome;
    }

    public BigDecimal getTotalEquity() {
        return totalEquity;
    }

    public BigDecimal getTotalLiabilities() {
        return totalLiabilities;
    }

    public BigDecimal getPriorRevenue() {
        return priorRevenue;
    }

    public Long getSharesOutstanding() {
        return sharesOutstanding;
    }

    public String getCurrency() {
        return currency;
    }

    public String getSource() {
        return source;
    }

    public LocalDate getDisclosedAt() {
        return disclosedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
