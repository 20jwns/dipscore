-- 컨테이너 최초 기동 시 1회 실행된다 (데이터 볼륨이 비어 있을 때만).
-- timescale/timescaledb 이미지는 template1 에 확장을 미리 넣어두므로
-- POSTGRES_DB(dipscore) 에도 보통 상속되지만, 멱등하게 명시해 둔다.

CREATE EXTENSION IF NOT EXISTS timescaledb;

SELECT extname, extversion FROM pg_extension WHERE extname = 'timescaledb';
