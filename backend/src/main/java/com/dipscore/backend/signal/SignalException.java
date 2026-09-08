package com.dipscore.backend.signal;

/** 매매 신호 판정 불가 (저점진입스코어/매력도 점수 부재 등). */
public class SignalException extends RuntimeException {

    public SignalException(String message) {
        super(message);
    }
}
