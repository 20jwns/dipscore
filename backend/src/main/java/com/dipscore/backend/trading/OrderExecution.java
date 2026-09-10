package com.dipscore.backend.trading;

import java.math.BigDecimal;
import java.time.Instant;

/** 주문 체결 결과. */
public record OrderExecution(String symbol, long quantity, BigDecimal executedPrice, Instant executedAt) {}
