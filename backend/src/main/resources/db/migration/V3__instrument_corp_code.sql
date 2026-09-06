-- 종목 마스터에 DART 고유번호(corp_code) 매핑 추가

ALTER TABLE instrument ADD COLUMN corp_code VARCHAR(8);

COMMENT ON COLUMN instrument.corp_code IS 'DART 공시대상회사 고유번호 (opendart corpCode). 국내 종목만, 미상장/해외는 null';

-- corp_code 는 있으면 유일. (Postgres 는 NULL 다중 허용 → 미매핑 종목은 제약 대상 아님)
CREATE UNIQUE INDEX ux_instrument_corp_code ON instrument (corp_code);

-- 시드: 삼성전자 DART 고유번호
UPDATE instrument SET corp_code = '00126380', updated_at = now() WHERE symbol = '005930';
