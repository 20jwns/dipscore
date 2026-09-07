package com.dipscore.backend.marketdata.financial;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialSnapshotRepository extends JpaRepository<FinancialSnapshot, FinancialSnapshotId> {

    Optional<FinancialSnapshot> findBySymbolAndFiscalYearAndFsDiv(String symbol, Short fiscalYear, String fsDiv);

    /** 특정 회계연도/연결구분의 여러 종목 스냅샷 (업종 percentile 모집단 구성용). */
    List<FinancialSnapshot> findBySymbolInAndFiscalYearAndFsDiv(List<String> symbols, Short fiscalYear, String fsDiv);
}
