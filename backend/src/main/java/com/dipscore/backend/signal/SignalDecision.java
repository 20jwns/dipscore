package com.dipscore.backend.signal;

/** 매수 신호 판정 결과. */
public enum SignalDecision {

    BUY("매수신호"),
    WAIT("대기");

    private final String label;

    SignalDecision(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
