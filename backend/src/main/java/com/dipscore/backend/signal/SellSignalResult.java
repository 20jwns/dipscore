package com.dipscore.backend.signal;

import java.time.Instant;
import java.util.List;

/**
 * 매도 신호 판정 결과 (기획서 7-2). 포지션 하나에 대한 판정.
 *
 * @param sell           매도해야 하면 true (조건 중 하나라도 트리거)
 * @param reason         매도 사유. {@code sell=false} 면 null(HOLD)
 * @param currentPrice   판정에 쓴 현재가 (최신 price_history.close)
 * @param returnRate     (현재가-진입가)/진입가
 * @param stopLossPrice  손절가 (진입가 - 진입ATR×배수). 진입 시 ATR 기록이 없으면 null
 * @param targetPrice    목표가 (진입가 × (1+목표수익률))
 * @param conditions     조건별 판정 상세 (목표수익률/손절가/보유시간)
 */
public record SellSignalResult(
        Long positionId,
        String symbol,
        Instant evaluatedAt,
        boolean sell,
        ExitReason reason,
        double currentPrice,
        double returnRate,
        Double stopLossPrice,
        Double targetPrice,
        List<ConditionCheck> conditions
) {}
