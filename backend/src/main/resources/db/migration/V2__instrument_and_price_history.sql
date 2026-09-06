-- 종목 마스터 + 시세 히스토리(TimescaleDB 하이퍼테이블)
-- (V1 의 임시 스모크 테이블 제거)

DROP TABLE IF EXISTS app_bootstrap;

-- ─────────────────────────────────────────────
-- 종목 마스터
-- ─────────────────────────────────────────────
CREATE TABLE instrument (
    symbol       VARCHAR(20)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    market_type  VARCHAR(16)  NOT NULL,
    sector       VARCHAR(100),
    industry     VARCHAR(100),
    currency     VARCHAR(3)   NOT NULL DEFAULT 'KRW',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_instrument PRIMARY KEY (symbol),
    CONSTRAINT ck_instrument_market_type CHECK (market_type IN ('KR_STOCK', 'US_STOCK', 'ETF'))
);

COMMENT ON TABLE instrument IS '스코어링/랭킹/시세적재 기준 유니버스';

-- ─────────────────────────────────────────────
-- 시세 히스토리 (하이퍼테이블)
--   OHLCV 스키마이나 현재 적재기는 현재가(close)만 채운다. open/high/low/volume 은 정규 분봉/일봉
--   소스 연동 시 채워진다. source 로 출처 구분.
-- ─────────────────────────────────────────────
CREATE TABLE price_history (
    symbol   VARCHAR(20)   NOT NULL,
    ts       TIMESTAMPTZ   NOT NULL,
    open     NUMERIC(18, 4),
    high     NUMERIC(18, 4),
    low      NUMERIC(18, 4),
    close    NUMERIC(18, 4) NOT NULL,
    volume   BIGINT,
    source   VARCHAR(32)   NOT NULL DEFAULT 'TOSS_QUOTE',
    CONSTRAINT pk_price_history PRIMARY KEY (symbol, ts),
    CONSTRAINT fk_price_history_instrument FOREIGN KEY (symbol) REFERENCES instrument (symbol)
);

-- ts 기준 range 파티셔닝. 청크 7일.
SELECT create_hypertable('price_history', 'ts', chunk_time_interval => INTERVAL '7 days', if_not_exists => TRUE);

-- 종목별 최신순 조회 최적화
CREATE INDEX ix_price_history_symbol_ts ON price_history (symbol, ts DESC);

-- ─────────────────────────────────────────────
-- 시드: 스케줄러 검증용 삼성전자
-- ─────────────────────────────────────────────
INSERT INTO instrument (symbol, name, market_type, sector, industry, currency)
VALUES ('005930', '삼성전자', 'KR_STOCK', 'IT', '반도체', 'KRW')
ON CONFLICT (symbol) DO NOTHING;
