package com.dipscore.backend.trading;

import java.math.BigDecimal;
import java.time.Instant;

import com.dipscore.backend.signal.ExitReason;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 보유/청산 포지션. {@code entry_atr} 는 진입 시점 ATR 로 고정 보관 — 손절가
 * ({@code entry_price - entry_atr × signal.stop-loss-atr-multiple}) 계산 기준.
 * 스키마는 Flyway {@code V8__account_and_position.sql} 로 관리 (ddl-auto=validate).
 */
@Entity
@Table(name = "position")
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "symbol", length = 20, nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private PositionStatus status;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Column(name = "entry_price", precision = 18, scale = 4, nullable = false)
    private BigDecimal entryPrice;

    @Column(name = "entry_at", nullable = false)
    private Instant entryAt;

    /** 진입 시점 ATR. 계산에 필요한 일봉이 부족했으면 null (그 경우 손절 조건은 판정에서 제외). */
    @Column(name = "entry_atr", precision = 18, scale = 4)
    private BigDecimal entryAtr;

    @Column(name = "exit_price", precision = 18, scale = 4)
    private BigDecimal exitPrice;

    @Column(name = "exit_at")
    private Instant exitAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "exit_reason", length = 24)
    private ExitReason exitReason;

    @Column(name = "realized_pnl", precision = 20, scale = 2)
    private BigDecimal realizedPnl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Position() {
    }

    /** 매수 체결로 신규 포지션 개설. */
    public static Position open(Long accountId, String symbol, long quantity,
                                BigDecimal entryPrice, Instant entryAt, BigDecimal entryAtr) {
        Position p = new Position();
        p.accountId = accountId;
        p.symbol = symbol;
        p.status = PositionStatus.OPEN;
        p.quantity = quantity;
        p.entryPrice = entryPrice;
        p.entryAt = entryAt;
        p.entryAtr = entryAtr;
        p.createdAt = Instant.now();
        p.updatedAt = p.createdAt;
        return p;
    }

    /** 매도 체결로 청산. 실현손익 = (청산가-진입가)×수량. */
    public void close(BigDecimal exitPrice, Instant exitAt, ExitReason reason) {
        if (status != PositionStatus.OPEN) {
            throw new TradingException("이미 청산된 포지션: " + id);
        }
        this.exitPrice = exitPrice;
        this.exitAt = exitAt;
        this.exitReason = reason;
        this.realizedPnl = exitPrice.subtract(entryPrice).multiply(BigDecimal.valueOf(quantity));
        this.status = PositionStatus.CLOSED;
        this.updatedAt = Instant.now();
    }

    public boolean isOpen() {
        return status == PositionStatus.OPEN;
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public PositionStatus getStatus() {
        return status;
    }

    public long getQuantity() {
        return quantity;
    }

    public BigDecimal getEntryPrice() {
        return entryPrice;
    }

    public Instant getEntryAt() {
        return entryAt;
    }

    public BigDecimal getEntryAtr() {
        return entryAtr;
    }

    public BigDecimal getExitPrice() {
        return exitPrice;
    }

    public Instant getExitAt() {
        return exitAt;
    }

    public ExitReason getExitReason() {
        return exitReason;
    }

    public BigDecimal getRealizedPnl() {
        return realizedPnl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
