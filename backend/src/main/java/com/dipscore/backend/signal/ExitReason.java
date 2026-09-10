package com.dipscore.backend.signal;

/**
 * 매도 사유 (기획서 7-2). {@code position.exit_reason} 에도 그대로 저장된다.
 * {@link SellSignalResult#reason()} 은 매도 신호가 없으면(HOLD) {@code null}.
 */
public enum ExitReason {

    /** 목표수익률 도달. */
    TARGET_PROFIT,
    /** 손절가(ATR 배수) 도달. */
    STOP_LOSS,
    /** 보유시간 초과(강제청산 시각 경과). */
    TIME_LIMIT,
    /** 자동 판정 아닌 수동 청산. */
    MANUAL
}
