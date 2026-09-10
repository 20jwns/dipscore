-- 가상 계좌 / 포지션 (모의투자 매수·매도 체결 및 보유 관리, 기획서 7장)
-- MockOrderExecutor 가 체결을 시뮬레이션하고, 그 결과로 account.cash_balance 와 position 이 갱신된다.
-- (실제 주문 API 는 호출하지 않는다 — 1차 범위는 직접/AI 모의투자만.)

-- ─────────────────────────────────────────────
-- 가상 계좌: 초기자금 + 현재 현금잔고
-- ─────────────────────────────────────────────
CREATE TABLE account (
    id               BIGSERIAL      PRIMARY KEY,
    name             VARCHAR(100)   NOT NULL,
    initial_capital  NUMERIC(20, 2) NOT NULL,
    cash_balance     NUMERIC(20, 2) NOT NULL,
    currency         VARCHAR(3)     NOT NULL DEFAULT 'KRW',
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_account_initial_capital_positive CHECK (initial_capital > 0),
    CONSTRAINT ck_account_cash_balance_nonneg CHECK (cash_balance >= 0)
);

-- ─────────────────────────────────────────────
-- 보유 포지션: 진입가/진입시각/수량 + (청산 시) 청산가/청산시각/사유/실현손익
--   entry_atr 는 진입 시점 ATR — 손절가(entry_price - entry_atr × 배수) 계산 기준으로 고정 보관한다
--   (매도 판정마다 재계산하지 않고 진입 당시 값을 그대로 쓴다 — ATR 기반 스탑의 표준 방식).
-- ─────────────────────────────────────────────
CREATE TABLE position (
    id            BIGSERIAL      PRIMARY KEY,
    account_id    BIGINT         NOT NULL REFERENCES account (id),
    symbol        VARCHAR(20)    NOT NULL REFERENCES instrument (symbol),
    status        VARCHAR(10)    NOT NULL,
    quantity      BIGINT         NOT NULL,
    entry_price   NUMERIC(18, 4) NOT NULL,
    entry_at      TIMESTAMPTZ    NOT NULL,
    entry_atr     NUMERIC(18, 4),
    exit_price    NUMERIC(18, 4),
    exit_at       TIMESTAMPTZ,
    exit_reason   VARCHAR(24),
    realized_pnl  NUMERIC(20, 2),
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_position_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_position_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_position_exit_reason
        CHECK (exit_reason IS NULL OR exit_reason IN ('TARGET_PROFIT', 'STOP_LOSS', 'TIME_LIMIT', 'MANUAL'))
);

CREATE INDEX ix_position_account_status ON position (account_id, status);
CREATE INDEX ix_position_symbol_status ON position (symbol, status);

-- 계좌당 종목별 동시 보유(OPEN) 포지션은 1개로 제한 (중복매수 방지). CLOSED 는 여러 건 허용(재진입 이력).
CREATE UNIQUE INDEX ux_position_account_symbol_open ON position (account_id, symbol) WHERE status = 'OPEN';

COMMENT ON TABLE account IS '가상 계좌 (모의투자). 초기자금·현재 현금잔고.';
COMMENT ON TABLE position IS '보유/청산 포지션. entry_atr = 진입 시점 ATR (손절가 계산 기준).';
