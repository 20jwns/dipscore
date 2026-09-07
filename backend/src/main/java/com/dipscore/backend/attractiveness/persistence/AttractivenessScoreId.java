package com.dipscore.backend.attractiveness.persistence;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** {@link AttractivenessScore} 복합키 (종목 + 계산 기준시각). */
public class AttractivenessScoreId implements Serializable {

    private String symbol;
    private Instant asOf;

    public AttractivenessScoreId() {
    }

    public AttractivenessScoreId(String symbol, Instant asOf) {
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
        if (!(o instanceof AttractivenessScoreId that)) {
            return false;
        }
        return Objects.equals(symbol, that.symbol) && Objects.equals(asOf, that.asOf);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, asOf);
    }
}
