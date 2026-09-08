-- 종목 활성 여부 (soft-delete). corp_cls 필터에서 코넥스(corp_cls='N')로 판정된 종목은
-- 삭제하지 않고 active=false 로 표시한다 (FK 자식행 보존, 가역).
-- 스코어링/랭킹 유니버스는 active=true 만 대상으로 한다.

ALTER TABLE instrument ADD COLUMN active BOOLEAN NOT NULL DEFAULT true;

COMMENT ON COLUMN instrument.active IS '활성 종목 여부. false = 코넥스 등 유니버스 제외 (soft-delete)';

CREATE INDEX ix_instrument_active ON instrument (active);
