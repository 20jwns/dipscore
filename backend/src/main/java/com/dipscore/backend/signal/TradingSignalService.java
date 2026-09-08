package com.dipscore.backend.signal;

import java.util.ArrayList;
import java.util.List;

import com.dipscore.backend.attractiveness.persistence.AttractivenessScore;
import com.dipscore.backend.attractiveness.persistence.AttractivenessScoreRepository;
import com.dipscore.backend.entryscore.persistence.EntryScore;
import com.dipscore.backend.entryscore.persistence.EntryScoreRepository;
import com.dipscore.backend.marketdata.instrument.InstrumentRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 매매 신호 판정 (기획서 7). 현재는 <b>매수 신호</b> 판정만 구현한다.
 *
 * <p>기획서 7-1 매수 룰:
 * <pre>
 * 매수 = 저점진입스코어 ≥ 70
 *      AND 기본점수 ≥ 60
 *      AND 이벤트조정계수 ≥ 0.9 (음수 이벤트 없음)
 *      AND 보유현금 > 최소매수단위
 * </pre>
 *
 * <ul>
 *   <li>이벤트조정계수는 현재 항상 1.0 (기획서 4-5 감성분석 미구현) → 이 조건은 사실상 항상 통과.</li>
 *   <li>보유현금/최소매수단위는 계좌·포지션 개념이 없어 <b>판정에서 제외</b>({@code evaluated=false}, TODO).</li>
 * </ul>
 *
 * <p><b>TODO — 매도 신호</b> (기획서 7-2): 포지션(보유종목의 진입가·진입시각·수량) 개념이 필요.
 * 목표수익률(+2~3%) 도달 OR 손절(ATR×1~1.5배) 도달 OR 보유시간(15:20) 초과. 이번 범위 밖.
 *
 * <p>{@code signal.enabled=true} 일 때만 빈 생성 (테스트 컨텍스트엔 JPA 가 없어 미생성).
 */
@Service
@ConditionalOnProperty(prefix = "signal", name = "enabled", havingValue = "true")
public class TradingSignalService {

    private static final Logger log = LoggerFactory.getLogger(TradingSignalService.class);

    private final SignalProperties props;
    private final InstrumentRepository instrumentRepository;
    private final EntryScoreRepository entryScoreRepository;
    private final AttractivenessScoreRepository attractivenessScoreRepository;

    public TradingSignalService(SignalProperties props,
                                InstrumentRepository instrumentRepository,
                                EntryScoreRepository entryScoreRepository,
                                AttractivenessScoreRepository attractivenessScoreRepository) {
        this.props = props;
        this.instrumentRepository = instrumentRepository;
        this.entryScoreRepository = entryScoreRepository;
        this.attractivenessScoreRepository = attractivenessScoreRepository;
    }

    /** 매수 신호 판정. 저장 없이 계산·반환만 한다. */
    public TradingSignalResult evaluateBuySignal(String symbol) {
        if (instrumentRepository.findById(symbol).isEmpty()) {
            throw new SignalException("종목 없음: " + symbol);
        }

        EntryScore entry = entryScoreRepository.findFirstBySymbolOrderByAsOfDesc(symbol)
                .orElseThrow(() -> new SignalException(
                        "저점 진입 스코어 없음: %s (POST /api/entry-score/%s 먼저 실행)".formatted(symbol, symbol)));
        AttractivenessScore attractiveness = attractivenessScoreRepository.findFirstBySymbolOrderByAsOfDesc(symbol)
                .orElseThrow(() -> new SignalException(
                        "매력도 점수 없음: %s (POST /api/attractiveness/%s 먼저 실행)".formatted(symbol, symbol)));

        double entryScore = entry.getEntryScore().doubleValue();
        double baseScore = attractiveness.getBaseScore().doubleValue();
        double eventCoefficient = attractiveness.getEventCoefficient().doubleValue();

        List<ConditionCheck> conditions = new ArrayList<>();
        conditions.add(ConditionCheck.evaluated(
                "저점진입스코어 ≥ " + fmt(props.entryThreshold()),
                entryScore >= props.entryThreshold(), entryScore, props.entryThreshold(), null));
        conditions.add(ConditionCheck.evaluated(
                "기본점수 ≥ " + fmt(props.minBaseScore()),
                baseScore >= props.minBaseScore(), baseScore, props.minBaseScore(), null));
        conditions.add(ConditionCheck.evaluated(
                "이벤트조정계수 ≥ " + fmt(props.eventCoefficientMin()) + " (음수 이벤트 없음)",
                eventCoefficient >= props.eventCoefficientMin(), eventCoefficient, props.eventCoefficientMin(),
                "이벤트 조정계수는 현재 1.0 고정 — 기획서 4-5 감성분석 미구현 (TODO)"));
        conditions.add(ConditionCheck.skipped(
                "보유현금 > 최소매수단위",
                "계좌/포지션 개념 미구현 — 판정에서 제외 (TODO)"));

        boolean buy = conditions.stream()
                .filter(ConditionCheck::evaluated)
                .allMatch(ConditionCheck::passed);
        SignalDecision decision = buy ? SignalDecision.BUY : SignalDecision.WAIT;

        log.info("[trading-signal] {}: {} (entry={}, base={}, evtCoef={})",
                symbol, decision, entryScore, baseScore, eventCoefficient);

        return new TradingSignalResult(symbol, java.time.Instant.now(), decision, decision.label(),
                round2(entryScore), round2(baseScore), round3(eventCoefficient),
                entry.getAsOf(), attractiveness.getAsOf(), conditions);
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
