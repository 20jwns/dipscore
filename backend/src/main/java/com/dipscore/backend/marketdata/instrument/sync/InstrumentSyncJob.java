package com.dipscore.backend.marketdata.instrument.sync;

import java.util.List;

import com.dipscore.backend.marketdata.instrument.sync.InstrumentSyncService.InstrumentSyncReport;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 새벽(기본 04:00 Asia/Seoul) DART 상장목록으로 {@code instrument} 를 전체 동기화한다
 * ({@code filterByCorpCls=true}, 상장폐지 감지 ON). 15분+ 소요하므로 재무(월 03:00)·거시(06:00) 배치와
 * 겹치지 않는 시간대로 잡는다.
 *
 * <p>{@code PriceIngestionJob} 과 같은 스케줄러 패턴. {@code instrument-sync.enabled} 와
 * {@code instrument-sync.scheduled} 가 모두 true 일 때만 빈 생성.
 * 수동 트리거는 {@code POST /api/admin/instrument-sync} (컨트롤러).
 */
@Component
@ConditionalOnProperty(
        name = {"instrument-sync.enabled", "instrument-sync.scheduled"},
        havingValue = "true")
public class InstrumentSyncJob {

    private static final Logger log = LoggerFactory.getLogger(InstrumentSyncJob.class);

    private final InstrumentSyncService service;
    private final InstrumentSyncProperties props;

    public InstrumentSyncJob(InstrumentSyncService service, InstrumentSyncProperties props) {
        this.service = service;
        this.props = props;
    }

    @PostConstruct
    void logSchedule() {
        log.info("[instrument-sync] 스케줄 등록: cron='{}' (Asia/Seoul), corp_cls 필터 ON, 상장폐지 감지 {} (유예 {}일)",
                props.cron(), props.detectDelistings(), props.staleDaysBeforeDeactivate());
    }

    @Scheduled(cron = "${instrument-sync.cron:0 0 4 * * *}", zone = "Asia/Seoul")
    void scheduledRun() {
        try {
            InstrumentSyncReport r = service.sync(true, List.of(), props.detectDelistings());
            log.info("[instrument-sync] 스케줄 실행 완료: 신규 {} / 갱신 {} / 코넥스비활성 {} / 상장폐지비활성 {} / 실패 {}",
                    r.created(), r.updated(), r.konexDeactivated(), r.delistingsDeactivated(), r.failed());
        } catch (Exception e) {
            log.error("[instrument-sync] 스케줄 실행 실패 (다음 주기 재시도): {}", e.toString(), e);
        }
    }
}
