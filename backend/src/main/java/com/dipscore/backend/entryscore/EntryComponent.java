package com.dipscore.backend.entryscore;

/**
 * 저점 진입 스코어의 3개 구성요소 (기획서 5-1).
 * 가중치(A/B/C)는 {@code application.yml} {@code entry-score.weights.*} 로 분리한다 (하드코딩 금지).
 */
public enum EntryComponent {

    /** 반등신호 (A) — ATR 대비 상대 구간 정규화. */
    REBOUND_SIGNAL("rebound-signal"),

    /** 기술적지표 (B) — RSI 과매도 반등 + 볼린저 하단 복귀 조합. */
    TECHNICAL_INDICATOR("technical-indicator"),

    /** 매력도지수 (C) — attractiveness_score 기본점수 재사용 (1차 필터 + 2차 가중요소). */
    ATTRACTIVENESS("attractiveness");

    private final String configKey;

    EntryComponent(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }
}
