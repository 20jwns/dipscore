package com.dipscore.backend.entryscore.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EntryScoreRepository extends JpaRepository<EntryScore, EntryScoreId> {

    Optional<EntryScore> findFirstBySymbolOrderByAsOfDesc(String symbol);
}
