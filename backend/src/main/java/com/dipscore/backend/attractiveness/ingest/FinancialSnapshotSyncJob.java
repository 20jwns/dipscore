package com.dipscore.backend.attractiveness.ingest;

import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.dipscore.backend.attractiveness.AttractivenessProperties;
import com.dipscore.backend.external.dart.corpcode.DartCorpCodeClient;
import com.dipscore.backend.marketdata.dailybatch.DailyBatchProperties;
import com.dipscore.backend.marketdata.financial.FinancialSnapshot;
import com.dipscore.backend.marketdata.financial.FinancialSnapshotRepository;
import com.dipscore.backend.marketdata.instrument.Instrument;
import com.dipscore.backend.marketdata.instrument.InstrumentRepository;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매주 (기본: 월 03:00 Asia/Seoul) {@code daily-batch.symbols} 대상 종목의 최신 재무제표를
 * {@code financial_snapshot} 에 적재한다.
 *
 * <ul>
 *   <li>사업보고서(11011) 우선, 없으면 최근 분기보고서(11014→11012→11013) 폴백 — {@link DartFinancialSnapshotIngestionService#ingestLatest}.</li>
 *   <li>대상 회계연도에 이미 스냅샷이 있으면 <b>skip</b> (중복 계산 방지). 새 연도만 insert.</li>
 *   <li>종목별 독립 — 한 종목 실패해도 다음 종목 계속.</li>
 * </ul>
 *
 * <p>{@code PriceIngestionJob} 과 같은 스케줄러 패턴. {@code attractiveness.enabled} 와
 * {@code attractiveness.financial-sync.enabled} 가 모두 true 일 때만 빈 생성.
 * 수동 트리거: {@code POST /api/admin/financial-snapshot-sync}.
 */
@Component
@ConditionalOnProperty(
        name = {"attractiveness.enabled", "attractiveness.financial-sync.enabled"},
        havingValue = "true")
public class FinancialSnapshotSyncJob {

    private static final Logger log = LoggerFactory.getLogger(FinancialSnapshotSyncJob.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final DartFinancialSnapshotIngestionService ingestionService;
    private final FinancialSnapshotRepository repository;
    private final InstrumentRepository instrumentRepository;
    private final DartCorpCodeClient corpCodeClient;
    private final DailyBatchProperties batchProps;
    private final AttractivenessProperties attractivenessProps;
    private final FinancialSnapshotSyncProperties props;

    public FinancialSnapshotSyncJob(DartFinancialSnapshotIngestionService ingestionService,
                                    FinancialSnapshotRepository repository,
                                    InstrumentRepository instrumentRepository,
                                    DartCorpCodeClient corpCodeClient,
                                    DailyBatchProperties batchProps,
                                    AttractivenessProperties attractivenessProps,
                                    FinancialSnapshotSyncProperties props) {
        this.ingestionService = ingestionService;
        this.repository = repository;
        this.instrumentRepository = instrumentRepository;
        this.corpCodeClient = corpCodeClient;
        this.batchProps = batchProps;
        this.attractivenessProps = attractivenessProps;
        this.props = props;
    }

    @PostConstruct
    void logSchedule() {
        log.info("[financial-snapshot-sync] 스케줄 등록: cron='{}' (Asia/Seoul), 대상 {}종목, 기본 회계연도 {}",
                props.cron(), batchProps.symbols().size(), defaultFiscalYear());
    }

    @Scheduled(cron = "${attractiveness.financial-sync.cron:0 0 3 * * MON}", zone = "Asia/Seoul")
    void scheduledRun() {
        sync(batchProps.symbols(), defaultFiscalYear());
    }

    /** 설정값(>0)이면 그 연도, 아니면 작년(Asia/Seoul). */
    public int defaultFiscalYear() {
        int cfg = props.targetFiscalYear();
        return cfg > 0 ? cfg : Year.now(SEOUL).getValue() - 1;
    }

    /** 지정 종목의 {@code fiscalYear} 재무제표 적재. 이미 있으면 skip, 실패해도 다음 종목 계속. */
    public FinancialSnapshotSyncReport sync(List<String> symbols, int fiscalYear) {
        String fsDiv = attractivenessProps.fsDiv();
        if (symbols == null || symbols.isEmpty()) {
            log.warn("[financial-snapshot-sync] 대상 종목이 없어 건너뜀");
            return new FinancialSnapshotSyncReport(fiscalYear, fsDiv, 0, 0, 0, 0, List.of());
        }

        List<SymbolResult> results = new ArrayList<>(symbols.size());
        for (String symbol : symbols) {
            try {
                String corpCode = resolveCorpCode(symbol);
                if (corpCode == null) {
                    results.add(new SymbolResult(symbol, Status.SKIPPED_NO_CORP_CODE, null, null,
                            "corp_code 를 찾을 수 없음"));
                    log.warn("[financial-snapshot-sync] {}: corp_code 없음, skip", symbol);
                    continue;
                }
                if (repository.findBySymbolAndFiscalYearAndFsDiv(symbol, (short) fiscalYear, fsDiv).isPresent()) {
                    results.add(new SymbolResult(symbol, Status.SKIPPED_EXISTS, fiscalYear, null, null));
                    log.info("[financial-snapshot-sync] {}: FY{} {} 이미 존재, skip", symbol, fiscalYear, fsDiv);
                    continue;
                }
                FinancialSnapshot saved = ingestionService.ingestLatest(symbol, corpCode, fiscalYear, fsDiv);
                results.add(new SymbolResult(symbol, Status.INSERTED, fiscalYear, saved.getSource(), null));
            } catch (RuntimeException e) {
                results.add(new SymbolResult(symbol, Status.FAILED, fiscalYear, null, e.toString()));
                log.warn("[financial-snapshot-sync] {}: 적재 실패, 다음 종목으로: {}", symbol, e.toString());
            }
            sleep(props.requestDelayMs());
        }

        int inserted = (int) results.stream().filter(r -> r.status() == Status.INSERTED).count();
        int failed = (int) results.stream().filter(r -> r.status() == Status.FAILED).count();
        int skipped = results.size() - inserted - failed;
        log.info("[financial-snapshot-sync] 완료: FY{} {} — {}종목 중 신규 {} / skip {} / 실패 {}",
                fiscalYear, fsDiv, results.size(), inserted, skipped, failed);
        return new FinancialSnapshotSyncReport(fiscalYear, fsDiv, symbols.size(),
                inserted, skipped, failed, results);
    }

    /** instrument.corp_code 우선, 없으면 corpCode.xml 조회 폴백 (042700 등 시드에 corp_code 없는 종목 대응). */
    private String resolveCorpCode(String symbol) {
        String fromInstrument = instrumentRepository.findById(symbol)
                .map(Instrument::getCorpCode)
                .filter(c -> c != null && !c.isBlank())
                .orElse(null);
        if (fromInstrument != null) {
            return fromInstrument;
        }
        return corpCodeClient.findByStockCode(symbol)
                .map(c -> c.corpCode())
                .filter(c -> c != null && !c.isBlank())
                .orElse(null);
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public enum Status {
        /** 새로 적재됨. */
        INSERTED,
        /** 해당 회계연도 스냅샷이 이미 있어 건너뜀. */
        SKIPPED_EXISTS,
        /** corp_code 를 못 찾아 건너뜀. */
        SKIPPED_NO_CORP_CODE,
        /** DART 조회/적재 실패. */
        FAILED
    }

    /**
     * @param fiscalYear 적재 시도한 회계연도 (skip/실패면 null 가능)
     * @param source     적재된 스냅샷 source (예: {@code DART_11011_2025}), INSERTED 일 때만
     * @param error      실패/skip 사유
     */
    public record SymbolResult(String symbol, Status status, Integer fiscalYear, String source, String error) {}

    /**
     * @param requested 요청 종목 수
     * @param inserted  신규 적재 수
     * @param skipped   이미 존재/ corp_code 없음으로 건너뛴 수
     * @param failed    실패 수
     */
    public record FinancialSnapshotSyncReport(int fiscalYear, String fsDiv, int requested,
                                              int inserted, int skipped, int failed,
                                              List<SymbolResult> results) {}
}
