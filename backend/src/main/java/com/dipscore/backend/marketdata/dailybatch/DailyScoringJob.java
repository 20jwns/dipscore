package com.dipscore.backend.marketdata.dailybatch;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.dipscore.backend.attractiveness.AttractivenessResult;
import com.dipscore.backend.attractiveness.BaseScoreService;
import com.dipscore.backend.entryscore.EntryScoreResult;
import com.dipscore.backend.entryscore.EntryScoreService;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 장 시작 전(기본 평일 08:30 Asia/Seoul) {@code daily-batch.symbols} 대상 종목의
 * <b>매력도 지수 → 저점 진입 스코어</b> 를 순서대로 자동 계산·저장한다.
 *
 * <ul>
 *   <li>종목별 독립 — 한 종목이 실패해도(재무 스냅샷 부재 등) 다음 종목 계속.</li>
 *   <li>매력도 계산이 실패하면 그 종목의 저점 진입 스코어는 건너뛴다 (선행 입력이라).</li>
 * </ul>
 *
 * <p>{@code PriceIngestionJob} 과 같은 스케줄러 패턴. {@code attractiveness.enabled} +
 * {@code entry-score.enabled} + {@code daily-batch.scoring.enabled} 가 모두 true 일 때만 빈 생성.
 * 수동 트리거: {@code POST /api/admin/daily-scoring}.
 */
@Component
@ConditionalOnProperty(
        name = {"attractiveness.enabled", "entry-score.enabled", "daily-batch.scoring.enabled"},
        havingValue = "true")
public class DailyScoringJob {

    private static final Logger log = LoggerFactory.getLogger(DailyScoringJob.class);
    private static final int ERROR_MSG_MAX = 120;

    private final BaseScoreService baseScoreService;
    private final EntryScoreService entryScoreService;
    private final DailyBatchProperties batchProps;
    private final DailyScoringProperties props;

    public DailyScoringJob(BaseScoreService baseScoreService,
                           EntryScoreService entryScoreService,
                           DailyBatchProperties batchProps,
                           DailyScoringProperties props) {
        this.baseScoreService = baseScoreService;
        this.entryScoreService = entryScoreService;
        this.batchProps = batchProps;
        this.props = props;
    }

    @PostConstruct
    void logSchedule() {
        log.info("[daily-scoring] 스케줄 등록: cron='{}' (Asia/Seoul), 대상 {}종목 {}",
                props.cron(), batchProps.symbols().size(), batchProps.symbols());
    }

    @Scheduled(cron = "${daily-batch.scoring.cron:0 30 8 * * MON-FRI}", zone = "Asia/Seoul")
    void scheduledRun() {
        run(batchProps.symbols());
    }

    /** 지정 종목의 매력도 → 저점진입 스코어를 순서대로 계산. 종목별 독립(실패해도 계속). */
    public DailyScoringReport run(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            log.warn("[daily-scoring] 대상 종목이 없어 건너뜀");
            return new DailyScoringReport(0, 0, 0, List.of());
        }

        List<SymbolScore> results = new ArrayList<>(symbols.size());
        for (String symbol : symbols) {
            AttractivenessResult attractiveness;
            try {
                attractiveness = baseScoreService.computeAndSave(symbol);
            } catch (RuntimeException e) {
                results.add(SymbolScore.failed(symbol, Status.ATTRACTIVENESS_FAILED, e));
                log.warn("[daily-scoring] {}: 매력도 계산 실패, 저점진입 건너뜀: {}", symbol, e.toString());
                continue;
            }

            try {
                EntryScoreResult entry = entryScoreService.computeAndSave(symbol);
                results.add(new SymbolScore(symbol, Status.OK,
                        attractiveness.baseScore(), entry.entryScore(), entry.filterPassed(), null));
                log.info("[daily-scoring] {}: 매력도 {} / 저점진입 {} (filterPassed={})",
                        symbol, round(attractiveness.baseScore()), round(entry.entryScore()), entry.filterPassed());
            } catch (RuntimeException e) {
                results.add(new SymbolScore(symbol, Status.ENTRY_SCORE_FAILED,
                        attractiveness.baseScore(), null, null, truncate(e.toString())));
                log.warn("[daily-scoring] {}: 매력도 OK({}) 이나 저점진입 계산 실패: {}",
                        symbol, round(attractiveness.baseScore()), e.toString());
            }
        }

        int succeeded = (int) results.stream().filter(r -> r.status() == Status.OK).count();
        int failed = results.size() - succeeded;
        if (failed > 0) {
            String summary = results.stream()
                    .filter(r -> r.status() != Status.OK)
                    .map(r -> "%s=%s".formatted(r.symbol(), r.error()))
                    .collect(Collectors.joining("; "));
            log.warn("[daily-scoring] 실패 {}종목 사유: {}", failed, summary);
        }
        log.info("[daily-scoring] 완료: {}종목 중 성공 {} / 실패 {}", results.size(), succeeded, failed);
        return new DailyScoringReport(symbols.size(), succeeded, failed, results);
    }

    private static Double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > ERROR_MSG_MAX ? s.substring(0, ERROR_MSG_MAX) + "…" : s;
    }

    public enum Status {
        /** 매력도·저점진입 모두 계산됨. */
        OK,
        /** 매력도 계산 실패 (저점진입 미시도). */
        ATTRACTIVENESS_FAILED,
        /** 매력도는 OK, 저점진입 계산 실패. */
        ENTRY_SCORE_FAILED
    }

    /**
     * @param baseScore     매력도 기본점수 (매력도 실패면 null)
     * @param entryScore    저점 진입 스코어 (OK 일 때만)
     * @param filterPassed  저점진입 1차 필터 통과 여부 (OK 일 때만)
     * @param error         실패 사유 (OK 면 null)
     */
    public record SymbolScore(String symbol, Status status, Double baseScore, Double entryScore,
                              Boolean filterPassed, String error) {
        static SymbolScore failed(String symbol, Status status, RuntimeException e) {
            String msg = e.toString();
            return new SymbolScore(symbol, status, null, null, null,
                    msg.length() > ERROR_MSG_MAX ? msg.substring(0, ERROR_MSG_MAX) + "…" : msg);
        }
    }

    /**
     * @param requested 요청 종목 수
     * @param succeeded 매력도·저점진입 모두 성공한 수
     * @param failed    한 단계라도 실패한 수
     */
    public record DailyScoringReport(int requested, int succeeded, int failed, List<SymbolScore> results) {}
}
