package com.dipscore.backend.signal;

import java.time.Instant;
import java.util.List;

/**
 * 매수 신호 판정 결과.
 *
 * @param decision                BUY(매수신호) / WAIT(대기)
 * @param label                   {@code decision.label()} — 클라이언트 편의용 한글 라벨
 * @param entryScoreAsOf          판정에 쓴 entry_score 행의 계산 시각
 * @param attractivenessScoreAsOf 판정에 쓴 attractiveness_score 행의 계산 시각
 * @param conditions              기획서 7-1 매수 룰의 조건별 판정 상세
 */
public record TradingSignalResult(
        String symbol,
        Instant evaluatedAt,
        SignalDecision decision,
        String label,
        double entryScore,
        double baseScore,
        double eventCoefficient,
        Instant entryScoreAsOf,
        Instant attractivenessScoreAsOf,
        List<ConditionCheck> conditions
) {}
