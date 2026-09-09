package com.dipscore.backend.attractiveness.ingest;

import java.util.ArrayList;
import java.util.List;

import com.dipscore.backend.attractiveness.ingest.MacroIndicatorRefreshService.MacroRefreshResult;
import com.dipscore.backend.attractiveness.ingest.MacroIndicatorRefreshService.RefreshStatus;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 거시지표(ECOS) 자동 갱신 스케줄러. {@link MacroIndicatorRefreshService#refreshLatest} 로 최신 관측치를
 * {@code macro_indicator} 에 채운다 (이미 있는 시점은 skip).
 *
 * <ul>
 *   <li><b>환율(USD_KRW)</b> — 매일 1회 (기본 06:00 Asia/Seoul)</li>
 *   <li><b>기준금리·CPI</b> — 월 1회 (기본 매월 1일 06:00 Asia/Seoul)</li>
 * </ul>
 *
 * <p>{@code PriceIngestionJob} 과 같은 패턴. {@code attractiveness.enabled} 와
 * {@code attractiveness.macro-sync.enabled} 가 모두 true 일 때만 빈 생성.
 * 지표별 독립 — 하나 실패해도 다음 지표 계속. 수동 트리거: {@code POST /api/admin/macro-sync}.
 */
@Component
@ConditionalOnProperty(
        name = {"attractiveness.enabled", "attractiveness.macro-sync.enabled"},
        havingValue = "true")
public class MacroIndicatorRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(MacroIndicatorRefreshJob.class);

    private final MacroIndicatorRefreshService service;
    private final MacroIndicatorSyncProperties props;

    public MacroIndicatorRefreshJob(MacroIndicatorRefreshService service, MacroIndicatorSyncProperties props) {
        this.service = service;
        this.props = props;
    }

    @PostConstruct
    void logSchedule() {
        log.info("[macro-sync] 스케줄 등록: 환율 cron='{}' (매일), 기준금리·CPI cron='{}' (월 1회), Asia/Seoul",
                props.exchangeRateCron(), props.monthlyCron());
    }

    /** 환율: 매일. */
    @Scheduled(cron = "${attractiveness.macro-sync.exchange-rate-cron:0 0 6 * * *}", zone = "Asia/Seoul")
    void refreshExchangeRate() {
        sync(List.of(MacroIndicatorRefreshService.USD_KRW));
    }

    /** 기준금리·CPI: 월 1회. */
    @Scheduled(cron = "${attractiveness.macro-sync.monthly-cron:0 0 6 1 * *}", zone = "Asia/Seoul")
    void refreshMonthlyIndicators() {
        sync(List.of(MacroIndicatorRefreshService.BASE_RATE, MacroIndicatorRefreshService.CPI));
    }

    /** 지정 지표들의 최신값을 갱신. 지표별 독립(실패해도 계속). */
    public MacroSyncReport sync(List<String> indicatorCodes) {
        if (indicatorCodes == null || indicatorCodes.isEmpty()) {
            log.warn("[macro-sync] 대상 지표가 없어 건너뜀");
            return new MacroSyncReport(0, 0, 0, 0, List.of());
        }

        List<MacroRefreshResult> results = new ArrayList<>(indicatorCodes.size());
        for (String code : indicatorCodes) {
            try {
                results.add(service.refreshLatest(code));
            } catch (RuntimeException e) {
                results.add(new MacroRefreshResult(code, null, null, RefreshStatus.FAILED, e.toString()));
                log.warn("[macro-sync] {}: 갱신 실패, 다음 지표로: {}", code, e.toString());
            }
        }

        int inserted = (int) results.stream().filter(r -> r.status() == RefreshStatus.INSERTED).count();
        int skipped = (int) results.stream().filter(r -> r.status() == RefreshStatus.SKIPPED_EXISTS).count();
        int failed = (int) results.stream().filter(r -> r.status() == RefreshStatus.FAILED).count();
        log.info("[macro-sync] 완료: {}개 지표 중 신규 {} / skip {} / 실패 {}",
                results.size(), inserted, skipped, failed);
        return new MacroSyncReport(indicatorCodes.size(), inserted, skipped, failed, results);
    }

    /**
     * @param requested 요청 지표 수
     * @param inserted  새 시점 적재 수
     * @param skipped   이미 존재해 건너뛴 수
     * @param failed    실패 수
     */
    public record MacroSyncReport(int requested, int inserted, int skipped, int failed,
                                  List<MacroRefreshResult> results) {}
}
