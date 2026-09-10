package com.dipscore.backend.trading;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 가상 계좌 (모의투자). {@code cash_balance} 는 {@link MockOrderExecutor} 체결 결과에 따라
 * {@link #debit}/{@link #credit} 으로만 변경한다 — 직접 setter 없음(잔고 정합성 보호).
 * 스키마는 Flyway {@code V8__account_and_position.sql} 로 관리 (ddl-auto=validate).
 */
@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "initial_capital", precision = 20, scale = 2, nullable = false)
    private BigDecimal initialCapital;

    @Column(name = "cash_balance", precision = 20, scale = 2, nullable = false)
    private BigDecimal cashBalance;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Account() {
    }

    /** 신규 계좌 개설. 현금잔고 = 초기자금. */
    public static Account open(String name, BigDecimal initialCapital, String currency) {
        if (initialCapital == null || initialCapital.signum() <= 0) {
            throw new TradingException("초기자금은 0보다 커야 합니다: " + initialCapital);
        }
        Account a = new Account();
        a.name = name;
        a.initialCapital = initialCapital;
        a.cashBalance = initialCapital;
        a.currency = currency;
        a.createdAt = Instant.now();
        a.updatedAt = a.createdAt;
        return a;
    }

    /** 매수 체결 — 현금 차감. 잔고 부족이면 예외(음수 잔고 방지). */
    public void debit(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new TradingException("체결금액이 올바르지 않습니다: " + amount);
        }
        if (amount.compareTo(cashBalance) > 0) {
            throw new TradingException("현금 부족: 필요 %s, 잔고 %s".formatted(amount, cashBalance));
        }
        this.cashBalance = this.cashBalance.subtract(amount);
        this.updatedAt = Instant.now();
    }

    /** 매도 체결 — 현금 가산. */
    public void credit(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new TradingException("체결금액이 올바르지 않습니다: " + amount);
        }
        this.cashBalance = this.cashBalance.add(amount);
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getInitialCapital() {
        return initialCapital;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
