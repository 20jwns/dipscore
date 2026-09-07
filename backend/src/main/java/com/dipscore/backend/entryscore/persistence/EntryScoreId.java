package com.dipscore.backend.entryscore.persistence;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** {@link EntryScore} 복합키 (종목 + 계산 기준시각). */
public class EntryScoreId implements Serializable {

    private String symbol;
    private Instant asOf;

    public EntryScoreId() {
    }

    public EntryScoreId(String symbol, Instant asOf) {
        this.symbol = symbol;
        this.asOf = asOf;
    }

    public String getSymbol() {
        return symbol;
    }

    public Instant getAsOf() {
        return asOf;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EntryScoreId that)) {
            return false;
        }
        return Objects.equals(symbol, that.symbol) && Objects.equals(asOf, that.asOf);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, asOf);
    }
}
