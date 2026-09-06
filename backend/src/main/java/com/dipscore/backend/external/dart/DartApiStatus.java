package com.dipscore.backend.external.dart;

/** DART 응답 {@code status} 코드 처리. "000" 이 정상, 그 외는 {@link DartApiException}. */
public final class DartApiStatus {

    public static final String OK = "000";

    private DartApiStatus() {
    }

    /**
     * @param action 로그/메시지용 동작명 (예: "재무제표 조회")
     */
    public static void verify(String status, String message, String action) {
        if (!OK.equals(status)) {
            throw new DartApiException(
                    "%s 실패 (status=%s): %s".formatted(action, status, message == null ? "" : message));
        }
    }
}
