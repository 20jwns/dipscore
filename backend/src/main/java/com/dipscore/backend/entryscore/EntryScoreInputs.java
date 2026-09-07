package com.dipscore.backend.entryscore;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * {@link EntryScoreEngine#compute} 입력. 저장소 조회 결과를 이미 로딩한 순수 데이터 묶음.
 *
 * @param dailyBars               일봉 OHLC, <b>시간 오름차순</b> (마지막이 최신). full OHLC 만 (스냅샷 행 제외).
 * @param attractivenessBaseScore 최신 attractiveness_score.base_score (0~100). null 이면 매력도 미계산.
 */
public record EntryScoreInputs(
        String symbol,
        Instant asOf,
        List<Bar> dailyBars,
        BigDecimal attractivenessBaseScore
) {

    public record Bar(Instant ts, double open, double high, double low, double close) {}
}
