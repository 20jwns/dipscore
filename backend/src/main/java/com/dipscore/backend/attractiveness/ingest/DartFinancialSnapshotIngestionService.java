package com.dipscore.backend.attractiveness.ingest;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import com.dipscore.backend.external.dart.DartApiException;
import com.dipscore.backend.external.dart.financials.DartFinancialsClient;
import com.dipscore.backend.external.dart.financials.dto.DartFinancialStatementItem;
import com.dipscore.backend.external.dart.stocktotal.DartStockTotalCountClient;
import com.dipscore.backend.external.dart.stocktotal.dto.DartStockTotalCountItem;
import com.dipscore.backend.marketdata.financial.FinancialSnapshot;
import com.dipscore.backend.marketdata.financial.FinancialSnapshotRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DART 단일회사 전체 재무제표 + 주식총수현황 → {@code financial_snapshot} 적재.
 *
 * <p>계정 매칭은 <b>표준계정 {@code account_id}</b>(IFRS/DART 확장 태그) 우선, 없으면 {@code account_nm} 폴백.
 * 발행주식수는 재무제표에 없어 {@link DartStockTotalCountClient}(주식총수현황)로 별도 조회한다.
 * {@code disclosed_at} 은 {@code rcept_no} 앞 8자리(접수일)에서 파싱한다.
 */
@Service
@ConditionalOnProperty(prefix = "attractiveness", name = "enabled", havingValue = "true")
public class DartFinancialSnapshotIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DartFinancialSnapshotIngestionService.class);
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000L);
    private static final String ANNUAL_REPORT = "11011";

    /** 사업보고서 우선, 없으면 최신 분기부터: 3분기 → 반기 → 1분기. */
    private static final List<String> REPORT_PREFERENCE = List.of("11011", "11014", "11012", "11013");

    // ── 표준계정 매칭: account_id 우선, account_nm 폴백 ─────────────────
    /** 손익 계정이 실릴 수 있는 sj_div. 단일 포괄손익계산서만 내는 회사(SK하이닉스 등)는 IS 없이 CIS 만 있다. */
    private static final Set<String> INCOME_DIVS = Set.of("IS", "CIS");
    private static final Set<String> BALANCE_DIVS = Set.of("BS");

    private static final Set<String> REVENUE_IDS = Set.of("ifrs-full_Revenue", "ifrs_Revenue");
    private static final Set<String> REVENUE_NAMES = Set.of("매출액", "수익(매출액)", "영업수익");
    private static final Set<String> OPERATING_INCOME_IDS =
            Set.of("dart_OperatingIncomeLoss", "ifrs-full_ProfitLossFromOperatingActivities");
    private static final Set<String> OPERATING_INCOME_NAMES = Set.of("영업이익", "영업이익(손실)");
    private static final Set<String> NET_INCOME_IDS = Set.of("ifrs-full_ProfitLoss", "ifrs_ProfitLoss");
    private static final Set<String> NET_INCOME_NAMES = Set.of("당기순이익", "당기순이익(손실)");
    private static final Set<String> EQUITY_IDS = Set.of("ifrs-full_Equity", "ifrs_Equity");
    private static final Set<String> EQUITY_NAMES = Set.of("자본총계");
    private static final Set<String> LIABILITIES_IDS = Set.of("ifrs-full_Liabilities", "ifrs_Liabilities");
    private static final Set<String> LIABILITIES_NAMES = Set.of("부채총계");

    private final DartFinancialsClient dartFinancialsClient;
    private final DartStockTotalCountClient stockTotalCountClient;
    private final FinancialSnapshotRepository repository;

    public DartFinancialSnapshotIngestionService(DartFinancialsClient dartFinancialsClient,
                                                 DartStockTotalCountClient stockTotalCountClient,
                                                 FinancialSnapshotRepository repository) {
        this.dartFinancialsClient = dartFinancialsClient;
        this.stockTotalCountClient = stockTotalCountClient;
        this.repository = repository;
    }

    /**
     * 사업보고서 우선, 없으면 최근 분기보고서로 폴백하여 해당 연도 최신 재무제표를 적재한다.
     *
     * @throws DartApiException 모든 보고서 코드에서 데이터를 못 찾은 경우
     */
    @Transactional
    public FinancialSnapshot ingestLatest(String symbol, String corpCode, int fiscalYear, String fsDiv) {
        DartApiException last = null;
        for (String reprtCode : REPORT_PREFERENCE) {
            try {
                return ingest(symbol, corpCode, fiscalYear, reprtCode, fsDiv);
            } catch (DartApiException e) {
                last = e;
                log.debug("[dart-financials] {} FY{} 보고서 {} 데이터 없음 → 다음 코드 시도: {}",
                        symbol, fiscalYear, reprtCode, e.getMessage());
            }
        }
        throw last != null ? last
                : new DartApiException("재무제표 없음: %s FY%d".formatted(symbol, fiscalYear));
    }

    /** 사업보고서(11011) 고정 적재 (기존 시그니처 유지). */
    @Transactional
    public FinancialSnapshot ingest(String symbol, String corpCode, int fiscalYear, String fsDiv) {
        return ingest(symbol, corpCode, fiscalYear, ANNUAL_REPORT, fsDiv);
    }

    @Transactional
    public FinancialSnapshot ingest(String symbol, String corpCode, int fiscalYear, String reprtCode, String fsDiv) {
        List<DartFinancialStatementItem> items = dartFinancialsClient
                .getSingleCompanyFullStatements(corpCode, fiscalYear, reprtCode, fsDiv)
                .list();
        if (items == null || items.isEmpty()) {
            throw new DartApiException(
                    "재무제표 항목이 비어 있음: %s FY%d %s".formatted(symbol, fiscalYear, reprtCode));
        }

        BigDecimal revenue = pick(items, INCOME_DIVS, REVENUE_IDS, REVENUE_NAMES,
                DartFinancialStatementItem::currentTermAmount);
        BigDecimal operatingIncome = pick(items, INCOME_DIVS, OPERATING_INCOME_IDS, OPERATING_INCOME_NAMES,
                DartFinancialStatementItem::currentTermAmount);
        BigDecimal netIncome = pick(items, INCOME_DIVS, NET_INCOME_IDS, NET_INCOME_NAMES,
                DartFinancialStatementItem::currentTermAmount);
        BigDecimal equity = pick(items, BALANCE_DIVS, EQUITY_IDS, EQUITY_NAMES,
                DartFinancialStatementItem::currentTermAmount);
        BigDecimal liabilities = pick(items, BALANCE_DIVS, LIABILITIES_IDS, LIABILITIES_NAMES,
                DartFinancialStatementItem::currentTermAmount);
        BigDecimal priorRevenue = pick(items, INCOME_DIVS, REVENUE_IDS, REVENUE_NAMES,
                DartFinancialStatementItem::priorTermAmount);

        Long shares = fetchCommonShares(symbol, corpCode, fiscalYear, reprtCode);
        LocalDate disclosedAt = disclosedAt(items);

        FinancialSnapshot snapshot = FinancialSnapshot.of(
                        symbol, (short) fiscalYear, fsDiv,
                        toMillions(revenue), toMillions(operatingIncome), toMillions(netIncome),
                        toMillions(equity), toMillions(liabilities), toMillions(priorRevenue),
                        shares, "KRW", "DART_" + reprtCode + "_" + fiscalYear)
                .withDisclosedAt(disclosedAt);

        FinancialSnapshot saved = repository.save(snapshot);
        log.info("[dart-financials] {} FY{} {} {} 적재 (매출 {}, 순이익 {} 백만원, 주식수 {}, 공시일 {})",
                symbol, fiscalYear, reprtCode, fsDiv, saved.getRevenue(), saved.getNetIncome(), shares, disclosedAt);
        return saved;
    }

    /** {@code sj_div} ∈ sjDivs 안에서 account_id(표준계정) → account_nm 순으로 첫 유효 금액을 고른다. */
    private static BigDecimal pick(List<DartFinancialStatementItem> items, Set<String> sjDivs,
                                   Set<String> accountIds, Set<String> accountNames,
                                   Function<DartFinancialStatementItem, String> amount) {
        BigDecimal byId = items.stream()
                .filter(i -> sjDivs.contains(i.statementDiv())
                        && i.accountId() != null && accountIds.contains(i.accountId().trim()))
                .map(amount).map(DartFinancialSnapshotIngestionService::parseAmount)
                .filter(Objects::nonNull).findFirst().orElse(null);
        if (byId != null) {
            return byId;
        }
        return items.stream()
                .filter(i -> sjDivs.contains(i.statementDiv()) && accountNames.contains(trim(i.accountName())))
                .map(amount).map(DartFinancialSnapshotIngestionService::parseAmount)
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    /** 주식총수현황에서 보통주 발행주식총수(istc_totqy). 조회 실패는 무시하고 null. */
    private Long fetchCommonShares(String symbol, String corpCode, int fiscalYear, String reprtCode) {
        try {
            var response = stockTotalCountClient.getStockTotalCount(corpCode, fiscalYear, reprtCode);
            List<DartStockTotalCountItem> rows = response == null ? null : response.list();
            if (rows == null || rows.isEmpty()) {
                return null;
            }
            Long common = shareCountOf(rows, "보통주");
            return common != null ? common : shareCountOf(rows, "합계"); // 보통주 행 없으면 합계
        } catch (DartApiException e) {
            log.warn("[dart-financials] {} 주식총수현황 조회 실패(주식수 null 로 진행): {}", symbol, e.getMessage());
            return null;
        }
    }

    private static Long shareCountOf(List<DartStockTotalCountItem> rows, String kindKeyword) {
        return rows.stream()
                .filter(r -> r.kind() != null && r.kind().replace(" ", "").contains(kindKeyword))
                .map(DartStockTotalCountItem::outstandingShares)
                .map(DartFinancialSnapshotIngestionService::parseShareCount)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /** items 중 첫 유효 rcept_no(YYYYMMDD…) 앞 8자리 → 공시일. */
    private static LocalDate disclosedAt(List<DartFinancialStatementItem> items) {
        return items.stream()
                .map(DartFinancialStatementItem::receiptNo)
                .filter(r -> r != null && r.length() >= 8)
                .map(r -> {
                    try {
                        return LocalDate.parse(r.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
                    } catch (DateTimeParseException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    /** DART 금액 문자열("195936557000000", 음수는 "-" 접두, 빈 값 가능) → 원 단위 BigDecimal. */
    private static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank() || "-".equals(raw.trim())) {
            return null;
        }
        try {
            return new BigDecimal(raw.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 주식수 문자열("5,969,782,550", 없으면 "-") → Long. */
    private static Long parseShareCount(String raw) {
        if (raw == null || raw.isBlank() || "-".equals(raw.trim())) {
            return null;
        }
        try {
            return Long.parseLong(raw.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 원 → 백만원. */
    private static BigDecimal toMillions(BigDecimal won) {
        return won == null ? null : won.divide(MILLION, MC);
    }
}
