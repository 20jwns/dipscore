package com.dipscore.backend.marketdata.macro;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** {@link MacroIndicator} 복합키 (지표코드 + 시각). */
public class MacroIndicatorId implements Serializable {

    private String indicatorCode;
    private Instant ts;

    public MacroIndicatorId() {
    }

    public MacroIndicatorId(String indicatorCode, Instant ts) {
        this.indicatorCode = indicatorCode;
        this.ts = ts;
    }

    public String getIndicatorCode() {
        return indicatorCode;
    }

    public Instant getTs() {
        return ts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MacroIndicatorId that)) {
            return false;
        }
        return Objects.equals(indicatorCode, that.indicatorCode) && Objects.equals(ts, that.ts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(indicatorCode, ts);
    }
}
