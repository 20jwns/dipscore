-- 저점 진입 스코어 계산 결과 (기획서 5장)
--   저점 진입 스코어 = (반등신호 × A) + (기술적지표 × B) + (매력도지수 × C)
--   반등신호/기술적지표/매력도component 는 0~1, entry_score 는 0~100.
--   filter_passed = 매력도 기본점수 ≥ 임계(기본 60) 통과 여부 (1차 필터). 불통과면 entry_score = 0.

CREATE TABLE entry_score (
    symbol                   VARCHAR(20)   NOT NULL,
    as_of                    TIMESTAMPTZ   NOT NULL,          -- 계산 기준시각
    rebound_signal           NUMERIC(6, 4) NOT NULL,          -- 0~1  (ATR 대비 상대구간 정규화)
    technical_indicator      NUMERIC(6, 4) NOT NULL,          -- 0~1  (RSI + 볼린저 조합)
    attractiveness_component NUMERIC(6, 4) NOT NULL,          -- 0~1  (매력도 기본점수 / 100)
    entry_score              NUMERIC(6, 2) NOT NULL,          -- 0~100
    filter_passed            BOOLEAN       NOT NULL,          -- 매력도 기본점수 ≥ 임계
    factor_breakdown         TEXT          NOT NULL,          -- 컴포넌트별 raw 지표/가중 상세 (JSON)
    engine_version           VARCHAR(16)   NOT NULL,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_entry_score PRIMARY KEY (symbol, as_of),
    CONSTRAINT fk_entry_score_instrument FOREIGN KEY (symbol) REFERENCES instrument (symbol)
);

CREATE INDEX ix_entry_score_symbol_asof ON entry_score (symbol, as_of DESC);
