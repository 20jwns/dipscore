package com.dipscore.backend.attractiveness.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AttractivenessScoreRepository extends JpaRepository<AttractivenessScore, AttractivenessScoreId> {

    Optional<AttractivenessScore> findFirstBySymbolOrderByAsOfDesc(String symbol);
}
