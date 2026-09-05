package com.dipscore.backend.external.toss;

/** 토스증권 오픈API 연동 중 발생하는 오류 (인증 실패, 응답 오류, 설정 누락 등). */
public class TossApiException extends RuntimeException {

    public TossApiException(String message) {
        super(message);
    }

    public TossApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
