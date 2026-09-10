package com.dipscore.backend.trading;

/** 계좌/포지션/체결 처리 불가 (계좌 없음, 현금 부족, 중복 포지션, 매수신호 아님 등). */
public class TradingException extends RuntimeException {

    public TradingException(String message) {
        super(message);
    }
}
