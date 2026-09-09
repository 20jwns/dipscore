package com.dipscore.backend.marketdata.financial;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialSnapshotRepository extends JpaRepository<FinancialSnapshot, FinancialSnapshotId> {

    Optional<FinancialSnapshot> findBySymbolAndFiscalYearAndFsDiv(String symbol, Short fiscalYear, String fsDiv);

    /**
     * 최신 회계연도 스냅샷 ({@code WHERE symbol=? AND fs_div=? ORDER BY fiscal_year DESC LIMIT 1}).
     * {@code entry_score} 가 {@code attractiveness_score} 를 {@code findFirstBySymbolOrderByAsOfDesc} 로
     * 최신 것만 쓰는 것과 같은 패턴.
     */
    Optional<FinancialSnapshot> findFirstBySymbolAndFsDivOrderByFiscalYearDesc(String symbol, String fsDiv);

    /** 특정 회계연도/연결구분의 여러 종목 스냅샷 (업종 percentile 모집단 구성용). */
    List<FinancialSnapshot> findBySymbolInAndFiscalYearAndFsDiv(List<String> symbols, Short fiscalYear, String fsDiv);
}
