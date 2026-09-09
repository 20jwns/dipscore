-- DART 상장목록(corpCode.xml)에서 마지막으로 확인된 시각. 상장폐지 감지용.
-- instrument-sync 배치가 매 실행마다 현재 목록에 있는 종목의 last_seen_at 을 now() 로 갱신하고,
-- N일(기본 3일) 이상 목록에서 안 보인 active=true KR_STOCK 을 active=false 로 자동 전환한다
-- (일시적 DART 조회 실패로 인한 오탐 방지 유예기간).

ALTER TABLE instrument ADD COLUMN last_seen_at TIMESTAMPTZ;

COMMENT ON COLUMN instrument.last_seen_at IS 'DART 상장목록에서 마지막 확인 시각 (상장폐지 감지 유예 기준)';

-- 기존 종목은 방금 동기화된 상태로 간주 (유예기간 시작점)
UPDATE instrument SET last_seen_at = now();

CREATE INDEX ix_instrument_last_seen_at ON instrument (last_seen_at);
