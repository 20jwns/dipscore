-- 매력도 지수 "기본점수" 엔진: 재무 스냅샷 + 거시지표 시계열 + 계산결과 저장 + 반도체 peer 시드
-- (기획서 4장. 1차 핵심요인 7개: PER/PBR/부채비율/ROE/매출성장률[업종 percentile] + 기준금리/원달러[Z-score])

-- ─────────────────────────────────────────────
-- 재무 스냅샷 (DART 재무제표를 소화한 종목·연도별 핵심 계정. 금액 단위: 백만원)
-- ─────────────────────────────────────────────
CREATE TABLE financial_snapshot (
    symbol             VARCHAR(20)   NOT NULL,
    fiscal_year        SMALLINT      NOT NULL,
    fs_div             VARCHAR(3)     NOT NULL,          -- CFS(연결) / OFS(별도)
    revenue            NUMERIC(20, 2),                   -- 매출액
    operating_income   NUMERIC(20, 2),                   -- 영업이익
    net_income         NUMERIC(20, 2),                   -- 당기순이익
    total_equity       NUMERIC(20, 2),                   -- 자본총계
    total_liabilities  NUMERIC(20, 2),                   -- 부채총계
    prior_revenue      NUMERIC(20, 2),                   -- 전기 매출액 (YoY 성장률 계산용)
    shares_outstanding BIGINT,                           -- 발행 보통주 수
    currency           VARCHAR(3)     NOT NULL DEFAULT 'KRW',
    source             VARCHAR(32)    NOT NULL,          -- SEED_DART_FY2024 / DART_FNLTT ...
    disclosed_at       DATE,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT pk_financial_snapshot PRIMARY KEY (symbol, fiscal_year, fs_div),
    CONSTRAINT fk_financial_snapshot_instrument FOREIGN KEY (symbol) REFERENCES instrument (symbol),
    CONSTRAINT ck_financial_snapshot_fs_div CHECK (fs_div IN ('CFS', 'OFS'))
);

-- ─────────────────────────────────────────────
-- 거시지표 시계열 (하이퍼테이블). ECOS 등에서 적재. Z-score 정규화의 분포 창.
-- ─────────────────────────────────────────────
CREATE TABLE macro_indicator (
    indicator_code VARCHAR(32)   NOT NULL,               -- BASE_RATE / USD_KRW / CPI ...
    ts             TIMESTAMPTZ   NOT NULL,               -- 관측 시점 (월지표는 해당 월 1일 UTC)
    value          NUMERIC(20, 6) NOT NULL,
    unit           VARCHAR(16),
    source         VARCHAR(32)   NOT NULL,               -- SEED_ECOS / ECOS_722Y001 ...
    CONSTRAINT pk_macro_indicator PRIMARY KEY (indicator_code, ts)
);
SELECT create_hypertable('macro_indicator', 'ts', chunk_time_interval => INTERVAL '365 days', if_not_exists => TRUE);

-- ─────────────────────────────────────────────
-- 매력도 지수 계산 결과 (현재는 기본점수만. attractiveness = base_score × event_coefficient)
-- ─────────────────────────────────────────────
CREATE TABLE attractiveness_score (
    symbol            VARCHAR(20)   NOT NULL,
    as_of             TIMESTAMPTZ   NOT NULL,            -- 계산 기준시각
    base_score        NUMERIC(6, 2) NOT NULL,           -- 0~100
    event_coefficient NUMERIC(6, 3) NOT NULL DEFAULT 1.000,  -- 이벤트 조정계수 (현재 1.0 고정, 기획서 4-5 미구현)
    attractiveness    NUMERIC(6, 2) NOT NULL,           -- base_score × event_coefficient
    factor_breakdown  TEXT          NOT NULL,            -- 요인별 raw/정규화/가중 상세 (JSON). 추후 jsonb 승격 가능
    engine_version    VARCHAR(16)   NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_attractiveness_score PRIMARY KEY (symbol, as_of),
    CONSTRAINT fk_attractiveness_score_instrument FOREIGN KEY (symbol) REFERENCES instrument (symbol)
);
CREATE INDEX ix_attractiveness_score_symbol_asof ON attractiveness_score (symbol, as_of DESC);

-- ─────────────────────────────────────────────
-- 시드: 반도체 업종 peer 유니버스 (업종 percentile 정규화가 동작하도록 최소 3종목)
--   재무값 = 검증된 공개 공시치 (FY2024 연결, 단위 백만원). 출처: valueline / hankyung.
--   삼성전자/SK하이닉스 자본·부채총계는 재무상태표 실측, 한미반도체는 ROE·부채비율에서 역산(근사).
--   주가·주식수는 스냅샷 (실제 운영 시 price_history 적재 + 주식총수 API 로 대체).
-- ─────────────────────────────────────────────
INSERT INTO instrument (symbol, name, market_type, sector, industry, currency, corp_code) VALUES
  ('000660', 'SK하이닉스',  'KR_STOCK', 'IT', '반도체', 'KRW', '00164779'),
  ('042700', '한미반도체',  'KR_STOCK', 'IT', '반도체', 'KRW', NULL)
ON CONFLICT (symbol) DO NOTHING;

-- peer 주가 스냅샷 (삼성전자 005930 은 TOSS_QUOTE 로 이미 존재: 257000 @ 2026-09-04)
INSERT INTO price_history (symbol, ts, close, source) VALUES
  ('000660', '2026-09-04T00:00:00Z', 300000, 'SEED'),
  ('042700', '2026-09-04T00:00:00Z', 222000, 'SEED')
ON CONFLICT (symbol, ts) DO NOTHING;

INSERT INTO financial_snapshot
  (symbol, fiscal_year, fs_div, revenue, operating_income, net_income, total_equity, total_liabilities, prior_revenue, shares_outstanding, source)
VALUES
  ('005930', 2024, 'CFS', 300870903, 32725961, 33621363, 402192200, 112339700, 258935494, 5969782550, 'SEED_DART_FY2024'),
  ('000660', 2024, 'CFS',  66192960, 23467319, 19788681,  73895700,  45939400,  32765719,  728002365, 'SEED_DART_FY2024'),
  ('042700', 2024, 'CFS',    558917,   255392,   152615,    556381,    174871,    159009,   97126000, 'SEED_DART_FY2024')
ON CONFLICT (symbol, fiscal_year, fs_div) DO NOTHING;

-- 거시지표 시드 (각 지표의 검증된 실제 관측치. 지표 간 기간은 무관 - 독립 Z-score.
--   실제 운영 시 MacroIndicatorRefreshService 가 ECOS 에서 최신 창으로 채운다.)
INSERT INTO macro_indicator (indicator_code, ts, value, unit, source) VALUES
  ('BASE_RATE', '2024-07-01T00:00:00Z', 3.50,  '%', 'SEED_ECOS'),
  ('BASE_RATE', '2024-08-01T00:00:00Z', 3.50,  '%', 'SEED_ECOS'),
  ('BASE_RATE', '2024-09-01T00:00:00Z', 3.50,  '%', 'SEED_ECOS'),
  ('BASE_RATE', '2024-10-01T00:00:00Z', 3.25,  '%', 'SEED_ECOS'),
  ('BASE_RATE', '2024-11-01T00:00:00Z', 3.00,  '%', 'SEED_ECOS'),
  ('BASE_RATE', '2024-12-01T00:00:00Z', 3.00,  '%', 'SEED_ECOS'),
  ('BASE_RATE', '2025-01-01T00:00:00Z', 3.00,  '%', 'SEED_ECOS'),
  ('BASE_RATE', '2025-02-01T00:00:00Z', 2.75,  '%', 'SEED_ECOS'),
  ('BASE_RATE', '2025-03-01T00:00:00Z', 2.75,  '%', 'SEED_ECOS'),
  ('BASE_RATE', '2025-04-01T00:00:00Z', 2.75,  '%', 'SEED_ECOS'),
  ('USD_KRW',   '2026-08-18T00:00:00Z', 1415.2, 'KRW', 'SEED_ECOS'),
  ('USD_KRW',   '2026-08-19T00:00:00Z', 1411.0, 'KRW', 'SEED_ECOS'),
  ('USD_KRW',   '2026-08-20T00:00:00Z', 1402.5, 'KRW', 'SEED_ECOS'),
  ('USD_KRW',   '2026-08-21T00:00:00Z', 1393.0, 'KRW', 'SEED_ECOS'),
  ('USD_KRW',   '2026-08-24T00:00:00Z', 1383.6, 'KRW', 'SEED_ECOS'),
  ('USD_KRW',   '2026-08-25T00:00:00Z', 1380.6, 'KRW', 'SEED_ECOS'),
  ('USD_KRW',   '2026-08-26T00:00:00Z', 1383.1, 'KRW', 'SEED_ECOS'),
  ('USD_KRW',   '2026-08-27T00:00:00Z', 1384.6, 'KRW', 'SEED_ECOS'),
  ('USD_KRW',   '2026-08-28T00:00:00Z', 1380.3, 'KRW', 'SEED_ECOS'),
  ('USD_KRW',   '2026-08-31T00:00:00Z', 1376.5, 'KRW', 'SEED_ECOS')
ON CONFLICT (indicator_code, ts) DO NOTHING;
