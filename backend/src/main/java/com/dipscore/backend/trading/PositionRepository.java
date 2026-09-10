package com.dipscore.backend.trading;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<Position, Long> {

    List<Position> findByAccountId(Long accountId);

    List<Position> findByAccountIdAndStatus(Long accountId, PositionStatus status);

    /** 중복매수 방지 — 계좌·종목당 OPEN 포지션은 최대 1개(DB 부분 unique 인덱스로도 보장). */
    Optional<Position> findByAccountIdAndSymbolAndStatus(Long accountId, String symbol, PositionStatus status);

    List<Position> findByStatus(PositionStatus status);
}
