package com.dipscore.backend.trading;

import java.math.BigDecimal;

/**
 * 주문 실행부 인터페이스 (CLAUDE.md 아키텍처 원칙 — 모의/실전을 갈아끼우는 구조).
 * {@link MockOrderExecutor} 는 실제 주문 API 를 호출하지 않고 최신 시세로 즉시 체결을 시뮬레이션한다.
 * 실전투자(3차, 이번 범위 아님) 착수 시 같은 인터페이스로 {@code TossOrderExecutor} 를 구현해 갈아끼운다.
 */
public interface OrderExecutor {

    /** 참고 시세 (매수 수량 산정용). 체결가와 다를 수 있다(실전 슬리피지 등). */
    BigDecimal quote(String symbol);

    /** 시장가 매수 체결. */
    OrderExecution buyMarket(String symbol, long quantity);

    /** 시장가 매도 체결. */
    OrderExecution sellMarket(String symbol, long quantity);
}
