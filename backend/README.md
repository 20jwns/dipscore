# dipscore-backend

AI 모의투자 앱 백엔드 (Spring Boot 3.4 / Java 21 / Gradle).

담당 범위 (기획서 기준): 매력도지수·저점진입스코어 계산엔진, 리스크관리, 주문실행부
(`MockOrderExecutor` / `TossOrderExecutor` 교체 구조), 백테스트·몬테카를로 엔진.

## 실행

```bash
# 1. 로컬 DB 기동 (레포 루트에서)
docker compose up -d

# 2. 환경변수 준비 (토스증권 오픈API 자격증명)
cd backend
cp .env.example .env      # 이후 TOSS_CLIENT_ID / TOSS_CLIENT_SECRET 채우기

# 3. 앱 실행
./gradlew bootRun

# 4. 확인
curl localhost:8080/api/hello
curl localhost:8080/actuator/health
curl localhost:8080/api/quotes/005930   # 토스증권 시세 (자격증명 필요, 실패 시 502 — 아래 트러블슈팅 참고)
```

`.env` 는 `me.paulschwarz:spring-dotenv` 가 기동 시 자동 로드해 `application.yml` 의
`${TOSS_CLIENT_ID}` 등에 주입한다 (working directory = `backend/`). `.env` 는 git 제외,
`.env.example` 에 필요한 키 목록이 있다. `.env` 없이도 앱은 뜨며, 토스증권 호출 시에만 자격증명이 필요하다.

## 토스증권 오픈API 연동 구조

| 구성요소 | 위치 | 역할 |
|---|---|---|
| `TossApiProperties` | `external/toss` | `toss.api.*` 설정 바인딩 (엔드포인트/타임아웃/자격증명) |
| `TossApiConfig` | `external/toss` | `tossAuthRestClient`(토큰 전용) / `tossApiRestClient`(Bearer 자동주입) 빈 |
| `TossOAuthClient` | `external/toss/auth` | OAuth2.0 토큰 엔드포인트 호출 (`client_credentials` 발급, `refresh_token` 갱신은 스펙상 현재 미사용) |
| `TossTokenManager` | `external/toss/auth` | 토큰 메모리 캐시 + 만료 임박(`refresh-skew`) 시 자동 재발급, 동시성 1회 보장, 401 시 무효화 |
| `TossQuoteClient` | `external/toss/quote` | 종목 현재가 조회 (`GET /api/v1/prices?symbols=...`, 최대 200개 일괄 조회 `getQuotes()` 지원) |

설정된 엔드포인트는 [공개 OpenAPI 스펙](https://openapi.tossinvest.com/openapi-docs/latest/openapi.json)
(2026-09 확인) 기준: 토큰 발급 `POST /oauth2/token` (form body: `grant_type=client_credentials`,
`client_id`, `client_secret` — HTTP Basic 아님), 시세 조회 `GET /api/v1/prices?symbols=005930,AAPL`.
스펙이 바뀌면 `application.yml`/`.env` 값만 바꾸면 되고 코드 변경은 불필요하도록 설계했다.

> **토스증권 오픈API 는 refresh_token 을 내려주지 않고, client 당 유효 토큰이 1개뿐이며 재발급 시
> 이전 토큰을 즉시 무효화한다.** 단일 인스턴스 기동(현재 범위)에서는 문제 없지만, 백엔드를
> 여러 인스턴스로 수평 확장하면 서로 토큰을 무효화시킬 수 있어 그때는 토큰 캐시를 Redis 등
> 공유 저장소로 옮겨야 한다 (`TossTokenManager` 클래스 주석 참고).

### 자격증명 등록했는데 403(`IP address not allowed`)이 뜬다면

**자격증명 문제가 아니라 IP 허용 목록 미등록 문제다.** 토스증권 WTS 로그인 → 설정 → Open API →
허용 IP 관리에서 현재 발신 IP(사무실/집 공인 IP, 또는 배포 서버 IP)를 등록해야 토큰 발급이 통과한다.
401 이면 `client_id`/`client_secret` 오타를 먼저 의심할 것.

## 빌드 / 테스트

```bash
./gradlew build      # 테스트 포함, DB 없이도 통과 (테스트 스코프에서 DB 자동설정 제외)
```

## DB 접속 정보 (기본값, 환경변수로 오버라이드)

| 변수 | 기본값 |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/dipscore` |
| `DB_USERNAME` | `dipscore` |
| `DB_PASSWORD` | `dipscore` |

마이그레이션은 Flyway (`src/main/resources/db/migration`). `V1__init.sql` 은 TimescaleDB 확장 활성화만 수행한다.
