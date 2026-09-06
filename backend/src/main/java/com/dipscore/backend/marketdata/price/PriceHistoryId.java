package com.dipscore.backend.marketdata.price;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** {@link PriceHistory} 복합키 (종목코드 + 시각). */
public class PriceHistoryId implements Serializable {

    private String symbol;
    private Instant ts;

    public PriceHistoryId() {
    }

    public PriceHistoryId(String symbol, Instant ts) {
        this.symbol = symbol;
        this.ts = ts;
    }

    public String getSymbol() {
        return symbol;
    }

    public Instant getTs() {
        return ts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PriceHistoryId that)) {
            return false;
        }
        return Objects.equals(symbol, that.symbol) && Objects.equals(ts, that.ts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, ts);
    }
}
