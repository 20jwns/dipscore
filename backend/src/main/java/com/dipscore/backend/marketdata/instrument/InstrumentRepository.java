package com.dipscore.backend.marketdata.instrument;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InstrumentRepository extends JpaRepository<Instrument, String> {

    /** DART 고유번호로 종목 조회 (공시 이벤트 → 종목 매핑용). */
    Optional<Instrument> findByCorpCode(String corpCode);
}
