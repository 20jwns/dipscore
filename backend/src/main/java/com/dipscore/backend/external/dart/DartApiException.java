package com.dipscore.backend.external.dart;

/** DART 전자공시 오픈API 연동 중 발생하는 오류 (키 누락, status != "000", 파일 파싱 실패 등). */
public class DartApiException extends RuntimeException {

    public DartApiException(String message) {
        super(message);
    }

    public DartApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
