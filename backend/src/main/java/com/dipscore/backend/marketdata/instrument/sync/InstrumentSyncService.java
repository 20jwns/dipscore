package com.dipscore.backend.marketdata.instrument.sync;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.dipscore.backend.external.dart.DartApiException;
import com.dipscore.backend.external.dart.company.DartCompanyClient;
import com.dipscore.backend.external.dart.corpcode.DartCorpCodeClient;
import com.dipscore.backend.external.dart.corpcode.dto.DartCorpCode;
import com.dipscore.backend.marketdata.instrument.InstrumentRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * DART 고유번호 파일(corpCode.xml) 전체 목록으로 {@code instrument} 를 대량 채우는 배치.
 * 상장 종목(stockCode 있음)만 대상, symbol 기준 upsert. 저장 없이 요약만 반환·로그.
 *
 * <p>{@code filterByCorpCls=true} 면 각 종목을 DART 기업개황으로 조회해 {@code corp_cls} 를 확인,
 * 코넥스({@code N})는 <b>{@code instrument.active=false} 로 soft-delete</b> 한다 (행·FK 자식행 보존, 가역).
 * 스코어링/랭킹 유니버스는 {@code active=true} 만 대상으로 한다({@code findByIndustryAndActiveTrue}).
 * (수십 분 소요 — 시작 시 로그로 안내)
 *
 * <p>{@code instrument-sync.enabled=true} 일 때만 빈 생성 (테스트 컨텍스트엔 JPA 가 없어 미생성).
 */
@Service
@ConditionalOnProperty(prefix = "instrument-sync", name = "enabled", havingValue = "true")
public class InstrumentSyncService {

    private static final Logger log = LoggerFactory.getLogger(InstrumentSyncService.class);
    private static final String KONEX = "N";
    private static final long ASSUMED_RTT_MS = 120; // 시간 추정용 대략적 왕복시간
    private static final int PROGRESS_EVERY = 500;

    private final DartCorpCodeClient dartCorpCodeClient;
    private final DartCompanyClient dartCompanyClient;
    private final InstrumentSyncWriter writer;
    private final InstrumentRepository instrumentRepository;
    private final InstrumentSyncProperties props;

    public InstrumentSyncService(DartCorpCodeClient dartCorpCodeClient,
                                 DartCompanyClient dartCompanyClient,
                                 InstrumentSyncWriter writer,
                                 InstrumentRepository instrumentRepository,
                                 InstrumentSyncProperties props) {
        this.dartCorpCodeClient = dartCorpCodeClient;
        this.dartCompanyClient = dartCompanyClient;
        this.writer = writer;
        this.instrumentRepository = instrumentRepository;
        this.props = props;
    }

    public InstrumentSyncReport sync(boolean filterByCorpCls) {
        return sync(filterByCorpCls, List.of());
    }

    /**
     * @param onlySymbols 비어 있지 않으면 이 종목코드들만 대상으로 한다 (소규모 검증·재동기화용).
     */
    public InstrumentSyncReport sync(boolean filterByCorpCls, List<String> onlySymbols) {
        List<DartCorpCode> all = dartCorpCodeClient.downloadAll();
        List<DartCorpCode> listed = all.stream().filter(DartCorpCode::isListed).toList();

        // stockCode 중복 제거 (희소하지만 우선주 등에서 발생 가능) — 먼저 나온 것 유지
        Map<String, DartCorpCode> byStockCode = new LinkedHashMap<>();
        for (DartCorpCode c : listed) {
            byStockCode.putIfAbsent(c.stockCode().trim(), c);
        }
        List<DartCorpCode> deduped = new ArrayList<>(byStockCode.values());

        if (onlySymbols != null && !onlySymbols.isEmpty()) {
            Set<String> want = new HashSet<>();
            for (String s : onlySymbols) {
                want.add(s.trim());
            }
            deduped = new ArrayList<>(deduped.stream().filter(c -> want.contains(c.stockCode().trim())).toList());
            log.info("[instrument-sync] symbols 지정 → {}건만 대상: {}", deduped.size(), want);
        }

        Classification cls = filterByCorpCls
                ? classifyByCorpCls(deduped)
                : new Classification(deduped, List.of(), 0, false);

        Set<String> existingSymbols = new HashSet<>(instrumentRepository.findAllSymbols());

        // corp_cls 필터 OFF → active 안 건드림(null). ON → 활성/비활성 명시.
        Boolean activeFlag = filterByCorpCls ? Boolean.TRUE : null;
        UpsertCounts active = upsertInChunks(cls.activeTargets(), activeFlag, existingSymbols);
        UpsertCounts konex = upsertInChunks(cls.konexTargets(), Boolean.FALSE, existingSymbols);

        int created = active.created() + konex.created();
        int updated = active.updated() + konex.updated();
        int failed = active.failed() + konex.failed();
        int konexDeactivated = konex.created() + konex.updated(); // 실제로 active=false 로 DB 반영된 건수

        InstrumentSyncReport report = new InstrumentSyncReport(
                all.size(), listed.size(), deduped.size(),
                filterByCorpCls, konexDeactivated, cls.lookupFailed(), cls.aborted(),
                created, updated, failed);
        log.info("[instrument-sync] 완료: 신규 {} / 갱신 {} / 실패 {}, 코넥스 비활성화(active=false) {} "
                        + "(DART 전체 {}, 상장 {}, 중복제거 후 {}, corp_cls필터 {}, 조회실패 {}{})",
                created, updated, failed, konexDeactivated, all.size(), listed.size(), deduped.size(),
                filterByCorpCls, cls.lookupFailed(), cls.aborted() ? ", 필터 중단됨" : "");
        return report;
    }

    private UpsertCounts upsertInChunks(List<DartCorpCode> targets, Boolean active, Set<String> existingSymbols) {
        int created = 0;
        int updated = 0;
        int failed = 0;
        int chunkSize = Math.max(1, props.chunkSize());
        for (int i = 0; i < targets.size(); i += chunkSize) {
            List<DartCorpCode> chunk = targets.subList(i, Math.min(i + chunkSize, targets.size()));
            try {
                writer.upsertChunk(chunk, active);
                for (DartCorpCode c : chunk) {
                    if (existingSymbols.contains(c.stockCode().trim())) {
                        updated++;
                    } else {
                        created++;
                    }
                }
            } catch (RuntimeException e) {
                failed += chunk.size();
                log.warn("[instrument-sync] 청크 {}건 실패, 건너뜀: {}", chunk.size(), e.toString());
            }
        }
        return new UpsertCounts(created, updated, failed);
    }

    /** 각 종목 기업개황 조회 → corp_cls='N'(코넥스)/그 외로 분류. 연속 실패 한계 초과 시 중단(나머지는 활성으로). */
    private Classification classifyByCorpCls(List<DartCorpCode> deduped) {
        long delay = props.companyLookupDelayMs();
        int maxConsecutive = props.companyLookupMaxConsecutiveFailures();
        long estMinutes = Math.round(deduped.size() * (delay + ASSUMED_RTT_MS) / 60_000.0);
        log.warn("[instrument-sync] corp_cls 필터 ON — 상장 {}건 각각 DART 기업개황 조회 "
                        + "(호출 간 {}ms 딜레이, 최소 약 {}분 소요 예상). DART 일일 요청 한도 소모에 유의.",
                deduped.size(), delay, estMinutes);

        List<DartCorpCode> activeTargets = new ArrayList<>(deduped.size());
        List<DartCorpCode> konexTargets = new ArrayList<>();
        int lookupFailed = 0;
        int consecutiveFailures = 0;
        boolean aborted = false;

        for (int idx = 0; idx < deduped.size(); idx++) {
            DartCorpCode c = deduped.get(idx);
            String corpCls = null;
            try {
                corpCls = dartCompanyClient.getCompany(c.corpCode()).corpCls();
                consecutiveFailures = 0;
            } catch (DartApiException e) {
                lookupFailed++;
                consecutiveFailures++;
                log.debug("[instrument-sync] {} 기업개황 조회 실패(활성 처리): {}", c.stockCode(), e.getMessage());
            }

            if (KONEX.equalsIgnoreCase(corpCls)) {
                konexTargets.add(c);
            } else {
                activeTargets.add(c); // Y/K/E/unknown/조회실패 → 활성
            }

            if (consecutiveFailures >= maxConsecutive) {
                aborted = true;
                List<DartCorpCode> remaining = deduped.subList(idx + 1, deduped.size());
                activeTargets.addAll(remaining); // 남은 종목은 필터 없이 활성으로
                log.warn("[instrument-sync] 기업개황 조회 {}회 연속 실패 — corp_cls 필터 중단. "
                        + "남은 {}건은 필터 없이 활성 처리. (재실행 권장)", maxConsecutive, remaining.size());
                break;
            }
            if ((idx + 1) % PROGRESS_EVERY == 0) {
                log.info("[instrument-sync] corp_cls 조회 진행 {}/{} (코넥스 {}, 조회실패 {})",
                        idx + 1, deduped.size(), konexTargets.size(), lookupFailed);
            }
            sleep(delay);
        }
        return new Classification(activeTargets, konexTargets, lookupFailed, aborted);
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

    private record Classification(List<DartCorpCode> activeTargets, List<DartCorpCode> konexTargets,
                                  int lookupFailed, boolean aborted) {}

    private record UpsertCounts(int created, int updated, int failed) {}

    /**
     * @param dartTotalCount        corpCode.xml 전체 회사 수
     * @param dartListedCount       그중 stockCode 있는(상장) 수
     * @param dedupedCount          stockCode 중복 제거 후 수 (corp_cls 필터 적용 전)
     * @param corpClsFilterApplied  corp_cls 필터를 수행했는지
     * @param konexDeactivated      코넥스로 판정되어 {@code instrument.active=false} 로 DB 반영된 건수
     *                              (신규를 비활성으로 insert 한 것 + 기존 활성행을 비활성으로 flip 한 것 모두 포함,
     *                              청크 실패로 반영 못 한 건 제외)
     * @param companyLookupFailed   기업개황 조회 실패로 분류 못 해 활성 처리한 수
     * @param aborted               연속 실패 한계 초과로 필터를 중단했는지
     * @param created               신규 insert 된 수 (활성+비활성 합)
     * @param updated               기존 종목 갱신 수 (활성+비활성 합)
     * @param failed                청크 실패로 건너뛴 수
     */
    public record InstrumentSyncReport(
            int dartTotalCount,
            int dartListedCount,
            int dedupedCount,
            boolean corpClsFilterApplied,
            int konexDeactivated,
            int companyLookupFailed,
            boolean aborted,
            int created,
            int updated,
            int failed
    ) {}
}
