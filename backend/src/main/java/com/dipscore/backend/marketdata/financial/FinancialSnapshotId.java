package com.dipscore.backend.marketdata.financial;

import java.io.Serializable;
import java.util.Objects;

/** {@link FinancialSnapshot} 복합키 (종목 + 회계연도 + 연결/별도). */
public class FinancialSnapshotId implements Serializable {

    private String symbol;
    private Short fiscalYear;
    private String fsDiv;

    public FinancialSnapshotId() {
    }

    public FinancialSnapshotId(String symbol, Short fiscalYear, String fsDiv) {
        this.symbol = symbol;
        this.fiscalYear = fiscalYear;
        this.fsDiv = fsDiv;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FinancialSnapshotId that)) {
            return false;
        }
        return Objects.equals(symbol, that.symbol)
                && Objects.equals(fiscalYear, that.fiscalYear)
                && Objects.equals(fsDiv, that.fsDiv);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, fiscalYear, fsDiv);
    }
}
