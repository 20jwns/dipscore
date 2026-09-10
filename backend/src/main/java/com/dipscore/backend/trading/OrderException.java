package com.dipscore.backend.trading;

/** 주문 체결 불가 (시세 없음 등). {@link TradingException} 과 같은 422 로 처리된다. */
public class OrderException extends TradingException {

    public OrderException(String message) {
        super(message);
    }
}
