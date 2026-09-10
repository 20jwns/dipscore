package com.dipscore.backend.trading;

import java.math.BigDecimal;
import java.time.Instant;

import com.dipscore.backend.marketdata.price.PriceHistory;
import com.dipscore.backend.marketdata.price.PriceHistoryRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 모의투자용 {@link OrderExecutor}. 실제 주문 API 는 절대 호출하지 않는다 — 최신 {@code price_history.close}
 * 를 체결가로 삼아 즉시(슬리피지·수수료 없이) 체결된 것으로 시뮬레이션한다.
 *
 * <p>{@code account.enabled=true} 일 때만 빈 생성.
 */
@Component
@ConditionalOnProperty(prefix = "account", name = "enabled", havingValue = "true")
public class MockOrderExecutor implements OrderExecutor {

    private final PriceHistoryRepository priceHistoryRepository;

    public MockOrderExecutor(PriceHistoryRepository priceHistoryRepository) {
        this.priceHistoryRepository = priceHistoryRepository;
    }

    @Override
    public BigDecimal quote(String symbol) {
        return latestClose(symbol);
    }

    @Override
    public OrderExecution buyMarket(String symbol, long quantity) {
        requirePositiveQuantity(quantity);
        return new OrderExecution(symbol, quantity, latestClose(symbol), Instant.now());
    }

    @Override
    public OrderExecution sellMarket(String symbol, long quantity) {
        requirePositiveQuantity(quantity);
        return new OrderExecution(symbol, quantity, latestClose(symbol), Instant.now());
    }

    private BigDecimal latestClose(String symbol) {
        return priceHistoryRepository.findBySymbolOrderByTsDesc(symbol).stream()
                .findFirst()
                .map(PriceHistory::getClose)
                .orElseThrow(() -> new OrderException("시세 없음(체결 불가): " + symbol));
    }

    private static void requirePositiveQuantity(long quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다: " + quantity);
        }
    }
}
