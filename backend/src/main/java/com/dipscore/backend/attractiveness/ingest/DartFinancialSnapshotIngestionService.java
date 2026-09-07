package com.dipscore.backend.attractiveness.ingest;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Set;

import com.dipscore.backend.external.dart.financials.DartFinancialsClient;
import com.dipscore.backend.external.dart.financials.dto.DartFinancialStatementItem;
import com.dipscore.backend.marketdata.financial.FinancialSnapshot;
import com.dipscore.backend.marketdata.financial.FinancialSnapshotRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DART 단일회사 전체 재무제표 → {@code financial_snapshot} 적재 (스켈레톤).
 *
 * <p><b>TODO</b>:
 * <ul>
 *   <li>계정 매칭이 {@code account_nm} 문자열 기반이라 취약함. 표준계정 {@code account_id}
 *       ({@code ifrs-full_Revenue} 등) 매핑 테이블로 교체 필요.</li>
 *   <li>발행주식수는 재무제표에 없음 → 별도 API(주식총수현황 {@code stockTotqySttus}) 연동 필요. 현재 null.</li>
 *   <li>disclosed_at(공시일)은 {@code rcept_no} 파싱 또는 공시목록 API 필요. 현재 null.</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(prefix = "attractiveness", name = "enabled", havingValue = "true")
public class DartFinancialSnapshotIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DartFinancialSnapshotIngestionService.class);
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000L);
    private static final String ANNUAL_REPORT = "11011";

    private final DartFinancialsClient dartFinancialsClient;
    private final FinancialSnapshotRepository repository;

    public DartFinancialSnapshotIngestionService(DartFinancialsClient dartFinancialsClient,
                                                 FinancialSnapshotRepository repository) {
        this.dartFinancialsClient = dartFinancialsClient;
        this.repository = repository;
    }

    @Transactional
    public FinancialSnapshot ingest(String symbol, String corpCode, int fiscalYear, String fsDiv) {
        List<DartFinancialStatementItem> items = dartFinancialsClient
                .getSingleCompanyFullStatements(corpCode, fiscalYear, ANNUAL_REPORT, fsDiv)
                .list();

        BigDecimal revenue = current(items, "IS", Set.of("매출액", "수익(매출액)", "영업수익"));
        BigDecimal operatingIncome = current(items, "IS", Set.of("영업이익", "영업이익(손실)"));
        BigDecimal netIncome = current(items, "IS", Set.of("당기순이익", "당기순이익(손실)"));
        BigDecimal equity = current(items, "BS", Set.of("자본총계"));
        BigDecimal liabilities = current(items, "BS", Set.of("부채총계"));
        BigDecimal priorRevenue = prior(items, "IS", Set.of("매출액", "수익(매출액)", "영업수익"));

        FinancialSnapshot snapshot = FinancialSnapshot.of(
                symbol, (short) fiscalYear, fsDiv,
                toMillions(revenue), toMillions(operatingIncome), toMillions(netIncome),
                toMillions(equity), toMillions(liabilities), toMillions(priorRevenue),
                null, "KRW", "DART_FNLTT_" + fiscalYear);

        FinancialSnapshot saved = repository.save(snapshot);
        log.info("[dart-financials] {} FY{} {} 적재 (매출 {}, 순이익 {} 백만원)",
                symbol, fiscalYear, fsDiv, saved.getRevenue(), saved.getNetIncome());
        return saved;
    }

    private static BigDecimal current(List<DartFinancialStatementItem> items, String sjDiv, Set<String> names) {
        return items.stream()
                .filter(i -> sjDiv.equals(i.statementDiv()) && names.contains(trim(i.accountName())))
                .map(DartFinancialStatementItem::currentTermAmount)
                .map(DartFinancialSnapshotIngestionService::parseAmount)
                .filter(v -> v != null)
                .findFirst()
                .orElse(null);
    }

    private static BigDecimal prior(List<DartFinancialStatementItem> items, String sjDiv, Set<String> names) {
        return items.stream()
                .filter(i -> sjDiv.equals(i.statementDiv()) && names.contains(trim(i.accountName())))
                .map(DartFinancialStatementItem::priorTermAmount)
                .map(DartFinancialSnapshotIngestionService::parseAmount)
                .filter(v -> v != null)
                .findFirst()
                .orElse(null);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    /** DART 금액 문자열("195936557000000", 음수는 "-" 접두, 빈 값 가능) → 원 단위 BigDecimal. */
    private static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 원 → 백만원. */
    private static BigDecimal toMillions(BigDecimal won) {
        return won == null ? null : won.divide(MILLION, MC);
    }
}
