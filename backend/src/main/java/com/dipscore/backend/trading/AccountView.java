package com.dipscore.backend.trading;

import java.math.BigDecimal;
import java.time.Instant;

/** {@link Account} REST 응답용 뷰. */
public record AccountView(
        Long id,
        String name,
        BigDecimal initialCapital,
        BigDecimal cashBalance,
        String currency,
        Instant createdAt
) {
    public static AccountView of(Account a) {
        return new AccountView(a.getId(), a.getName(), a.getInitialCapital(), a.getCashBalance(),
                a.getCurrency(), a.getCreatedAt());
    }
}
