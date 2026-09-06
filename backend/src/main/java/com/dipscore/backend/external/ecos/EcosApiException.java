package com.dipscore.backend.external.ecos;

/** 한국은행 ECOS 오픈API 연동 오류 (키 누락, RESULT 오류코드, 파싱 실패 등). */
public class EcosApiException extends RuntimeException {

    public EcosApiException(String message) {
        super(message);
    }

    public EcosApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
