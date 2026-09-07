package com.dipscore.backend.entryscore;

import java.util.Map;

/**
 * 저점 진입 스코어 한 구성요소의 계산 상세 (감사/화면 breakdown 용).
 *
 * @param weight   설정 가중치
 * @param value    [0,1] 정규화 값 (반등신호 / 기술적지표 / 매력도component)
 * @param weighted weight × value (최종 정규화 전)
 * @param detail   raw 지표 값 (ATR, drawdownAtr, RSI, %b, baseScore 등)
 */
public record ComponentScore(
        double weight,
        double value,
        double weighted,
        Map<String, Double> detail
) {}
