package com.dipscore.backend.marketdata.instrument;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstrumentRepository extends JpaRepository<Instrument, String> {

    /** DART 고유번호로 종목 조회 (공시 이벤트 → 종목 매핑용). */
    Optional<Instrument> findByCorpCode(String corpCode);

    /** 같은 업종의 <b>활성</b> 종목 (업종 percentile 모집단 구성용). 비활성(코넥스 등) 제외. */
    List<Instrument> findByIndustryAndActiveTrue(String industry);

    /** 전체 종목코드만 (instrument-sync 의 신규/갱신 분류용, active 무관). */
    @Query("select i.symbol from Instrument i")
    List<String> findAllSymbols();

    /**
     * DART 고유번호 파일 기준 종목 마스터 upsert (instrument-sync 배치).
     * 신규면 KR_STOCK 으로 insert, 기존이면 <b>name·corp_code(·active) 만</b> 갱신한다.
     * sector/industry/market_type/currency 는 SET 절에 없으므로 기존 값이 그대로 유지된다
     * (반도체 3사 시드처럼 이미 채워진 값을 null 로 덮어쓰지 않음).
     *
     * <p>{@code active} 파라미터:
     * <ul>
     *   <li>{@code null} — active 는 건드리지 않음 (신규는 기본값 true, 기존은 유지). corp_cls 필터 OFF.</li>
     *   <li>{@code true} — 활성으로 set (corp_cls 필터 ON, 비코넥스).</li>
     *   <li>{@code false} — 비활성으로 set (corp_cls 필터 ON, 코넥스 → soft-delete).</li>
     * </ul>
     * {@code last_seen_at} 은 항상 {@code now()} 로 갱신한다 (상장폐지 감지 유예 기준).
     * 호출자는 트랜잭션 컨텍스트 안이어야 한다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO instrument (symbol, name, market_type, corp_code, active, last_seen_at)
            VALUES (:symbol, :name, 'KR_STOCK', :corpCode, COALESCE(CAST(:active AS boolean), true), now())
            ON CONFLICT (symbol) DO UPDATE SET
              name = EXCLUDED.name,
              corp_code = EXCLUDED.corp_code,
              active = COALESCE(CAST(:active AS boolean), instrument.active),
              last_seen_at = now(),
              updated_at = now()
            """, nativeQuery = true)
    void upsertFromDart(@Param("symbol") String symbol,
                        @Param("name") String name,
                        @Param("corpCode") String corpCode,
                        @Param("active") Boolean active);

    /**
     * DART 상장목록에서 {@code cutoff} 이후로 확인되지 않은 활성 KR_STOCK 을 {@code active=false} 로 전환한다
     * (상장폐지 감지). {@code last_seen_at IS NULL} 이면 추적 이력이 없다는 뜻이라 건드리지 않는다.
     * 국내주식({@code KR_STOCK})만 대상 — 미국주식/ETF 는 instrument-sync 관리 밖.
     *
     * @return 비활성화된 행 수
     */
    @Modifying
    @Query(value = """
            UPDATE instrument SET active = false, updated_at = now()
            WHERE active = true
              AND market_type = 'KR_STOCK'
              AND last_seen_at IS NOT NULL
              AND last_seen_at < :cutoff
            """, nativeQuery = true)
    int deactivateStaleKrStocks(@Param("cutoff") Instant cutoff);
}
