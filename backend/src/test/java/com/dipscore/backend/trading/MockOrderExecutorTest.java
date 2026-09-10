package com.dipscore.backend.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import com.dipscore.backend.marketdata.price.PriceHistory;
import com.dipscore.backend.marketdata.price.PriceHistoryRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 실제 주문 API 호출 없이 최신 price_history.close 로 체결을 시뮬레이션하는지 검증. */
@ExtendWith(MockitoExtension.class)
class MockOrderExecutorTest {

    @Mock PriceHistoryRepository priceHistoryRepository;

    private MockOrderExecutor executor() {
        return new MockOrderExecutor(priceHistoryRepository);
    }

    private void stubPrice(String symbol, double close) {
        PriceHistory p = mock(PriceHistory.class);
        when(p.getClose()).thenReturn(BigDecimal.valueOf(close));
        when(priceHistoryRepository.findBySymbolOrderByTsDesc(symbol)).thenReturn(List.of(p));
    }

    @Test
    void quote는_최신_종가를_반환한다() {
        stubPrice("005930", 70_000.0);

        assertThat(executor().quote("005930")).isEqualByComparingTo("70000");
    }

    @Test
    void 매수체결가는_최신_종가이고_수량이_그대로_반영된다() {
        stubPrice("005930", 70_000.0);

        OrderExecution exec = executor().buyMarket("005930", 10);

        assertThat(exec.executedPrice()).isEqualByComparingTo("70000");
        assertThat(exec.quantity()).isEqualTo(10);
        assertThat(exec.symbol()).isEqualTo("005930");
    }

    @Test
    void 매도체결가도_최신_종가다() {
        stubPrice("005930", 68_500.0);

        OrderExecution exec = executor().sellMarket("005930", 5);

        assertThat(exec.executedPrice()).isEqualByComparingTo("68500");
    }

    @Test
    void 시세가_없으면_체결불가_예외() {
        when(priceHistoryRepository.findBySymbolOrderByTsDesc("999999")).thenReturn(List.of());

        assertThatThrownBy(() -> executor().buyMarket("999999", 1))
                .isInstanceOf(OrderException.class)
                .hasMessageContaining("시세 없음");
    }

    @Test
    void 수량이_1미만이면_예외() {
        assertThatThrownBy(() -> executor().buyMarket("005930", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
