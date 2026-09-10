package com.dipscore.backend.trading;

import java.math.BigDecimal;
import java.time.Instant;

import com.dipscore.backend.signal.ExitReason;

/** {@link Position} REST 응답용 뷰. */
public record PositionView(
        Long id,
        Long accountId,
        String symbol,
        PositionStatus status,
        long quantity,
        BigDecimal entryPrice,
        Instant entryAt,
        BigDecimal entryAtr,
        BigDecimal exitPrice,
        Instant exitAt,
        ExitReason exitReason,
        BigDecimal realizedPnl
) {
    public static PositionView of(Position p) {
        return new PositionView(p.getId(), p.getAccountId(), p.getSymbol(), p.getStatus(), p.getQuantity(),
                p.getEntryPrice(), p.getEntryAt(), p.getEntryAtr(),
                p.getExitPrice(), p.getExitAt(), p.getExitReason(), p.getRealizedPnl());
    }
}
