# DipScore

AI 모의투자 앱 모노레포. 종목 데이터 + 투자 성향/전략을 결합한 **매력도 지수** / **저점 진입 스코어**
공식을 설계하고 모의투자로 백테스트하며 개선한다. 상세 기획은 [`docs/`](docs/) 참고.

## 구조

| 디렉터리 | 스택 | 역할 |
|---|---|---|
| [`backend/`](backend/) | Spring Boot 3.4 · Java 21 · Gradle | 계산엔진(매력도지수/저점진입스코어), 리스크관리, 주문실행부, 백테스트·몬테카를로 엔진 |
| [`frontend/`](frontend/) | Flutter 3.44 · Dart 3.12 | 모바일 앱 (홈 / 스코어 랭킹 / 종목 상세 / 알림 / 전략 설정) |
| [`nlp-service/`](nlp-service/) | FastAPI · Python 3.11+ | 뉴스 감성분석 마이크로서비스 (백엔드가 내부 API 로 호출) |
| [`infra/`](infra/) | Docker Compose | 로컬 개발 인프라 (PostgreSQL + TimescaleDB) |
| [`docs/`](docs/) | — | 최종기획서, `CLAUDE.md` (루트 `CLAUDE.md` 는 이 파일로의 심볼릭 링크) |

> 1차 개발 범위: **직접 모의투자 + AI 모의투자** 2개 기능만. AI 실전투자는 범위 밖.
> ①② 는 실제 주문 API 를 호출하지 않고 내부 가상체결 로직으로만 시뮬레이션한다.

## 빠른 시작

```bash
# 1. 로컬 DB (PostgreSQL + TimescaleDB)
docker compose up -d
docker compose ps          # health: healthy 확인

# 2. 백엔드
cd backend
cp .env.example .env       # 토스증권 오픈API 자격증명(TOSS_CLIENT_ID/SECRET) 입력, .env 는 git 제외
./gradlew bootRun
curl localhost:8080/api/hello

# 3. NLP 서비스
cd nlp-service
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt
uvicorn app.main:app --reload --port 8000

# 4. 프론트엔드
cd frontend && flutter run
```

## DB 접속 (로컬 기본값)

```
host=localhost port=5432 db=dipscore user=dipscore password=dipscore
```

DB 초기화: `docker compose down -v` 로 볼륨 삭제 후 재기동하면 `infra/db/init/*.sql` 이 다시 실행된다.

## 각 프로젝트 상세

- 백엔드: [`backend/README.md`](backend/README.md)
- NLP 서비스: [`nlp-service/README.md`](nlp-service/README.md)
