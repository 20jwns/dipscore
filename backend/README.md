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

## DART 전자공시 오픈API 연동 구조

토스와 같은 패턴. 인증만 다르다 — OAuth 가 아니라 쿼리 파라미터 `crtfc_key` (`DartApiKeyInterceptor` 가
모든 요청에 자동 주입). 키는 `backend/.env` 의 `DART_API_KEY` (opendart.fss.or.kr 가입 후 즉시 발급, 40자리).

| 구성요소 | 위치 | 역할 |
|---|---|---|
| `DartApiProperties` / `DartApiConfig` | `external/dart` | `dart.api.*` 바인딩, `dartRestClient` 빈(`crtfc_key` 자동주입) |
| `DartCompanyClient` | `external/dart/company` | 기업개황 `GET /api/company.json?corp_code=` |
| `DartFinancialsClient` | `external/dart/financials` | 단일회사 전체 재무제표 `GET /api/fnlttSinglAcntAll.json` (`bsns_year`, `reprt_code`, `fs_div`) |
| `DartCorpCodeClient` | `external/dart/corpcode` | 고유번호 `GET /api/corpCode.xml` → ZIP(CORPCODE.xml) 다운로드·StAX 파싱, `findByStockCode()` |

- 응답 `status != "000"` 및 키 누락 → `DartApiException` → `DartApiExceptionHandler` 가 **502**(`dart_api_error`)로 변환 (토스와 동일).
- 고유번호 파일은 매 호출 수 MB 다운로드+파싱 → 실제로는 캐시/DB 적재해야 함 (`DartCorpCodeClient` TODO).
- 스모크 엔드포인트: `GET /api/financials/{corpCode}?year=&reprtCode=&fsDiv=`, `GET /api/dart/company/{corpCode}`, `GET /api/dart/corp-code/{stockCode}` (예: `005930` → `00126380`).

## 한국은행 ECOS 오픈API 연동 구조

토스/DART 와 같은 패턴. 인증키는 **URL 경로 세그먼트**로 전달되므로 인증 인터셉터는 없다.
키는 `backend/.env` 의 `ECOS_API_KEY` (ecos.bok.or.kr/api 발급). 시험용 공개키 `sample` 은 1회 10건 제한.

`external/ecos` 의 `EcosStatisticClient` — `GET /api/StatisticSearch/{KEY}/json/{lang}/1/{maxRows}/{statCode}/{cycle}/{startTime}/{endTime}/{itemCode}`.
ECOS 가 `text/html` content-type 으로 JSON 을 주는 경우가 있어 문자열로 받아 `ObjectMapper` 로 파싱.
`latest*()` 는 최근 창을 조회해 `TIME` 최대 행을 고른다.

| 지표 | 통계표코드 / 항목코드 / 주기 | 단위 | 스모크 |
|---|---|---|---|
| 기준금리 | `722Y001` / `0101000` / `M` | 연% | `GET /api/ecos/base-rate` |
| 원/달러 매매기준율 | `731Y001` / `0000001` / `D` | 원 | `GET /api/ecos/exchange-rate` |
| 소비자물가지수(총지수) | `901Y009` / `0` / `M` | 2020=100 | `GET /api/ecos/cpi` |

코드는 2026-09 ECOS 실데이터로 검증. `application.yml` 의 `ecos.api.indicators.*` 로 교체 가능.
정상 응답은 `{"StatisticSearch":{"row":[...]}}`, 오류는 `{"RESULT":{"CODE":"INFO-200",...}}` →
`EcosApiException` → `EcosApiExceptionHandler` 가 **502**(`ecos_api_error`).

## DB 스키마 (Flyway, `src/main/resources/db/migration`)

`ddl-auto: validate` — 스키마는 마이그레이션으로만 관리하고 JPA 엔티티는 검증만 한다.

| 마이그레이션 | 내용 |
|---|---|
| `V1__init.sql` | TimescaleDB 확장 활성화 |
| `V2__instrument_and_price_history.sql` | `instrument`(종목 마스터) + `price_history`(시세, 하이퍼테이블) + 삼성전자 시드 |
| `V3__instrument_corp_code.sql` | `instrument.corp_code`(DART 고유번호) 컬럼 + unique 인덱스 + 삼성전자 `00126380` 매핑 |
| `V4__attractiveness_base_score.sql` | `financial_snapshot` + `macro_indicator`(하이퍼테이블) + `attractiveness_score` + 반도체 peer(삼성전자·SK하이닉스·한미반도체) 시드 |
| `V5__entry_score.sql` | `entry_score`(저점 진입 스코어 계산 결과) |
| `V6__instrument_active.sql` | `instrument.active` BOOLEAN(soft-delete). 코넥스 등 유니버스 제외용 |

- **`instrument`**: `symbol`(PK), `name`, `market_type`(`KR_STOCK`/`US_STOCK`/`ETF` CHECK), `sector`, `industry`, `currency`, `corp_code`(DART 고유번호 8자리, nullable·unique), `active`(soft-delete, 코넥스 등 제외) — 엔티티 `marketdata.instrument.Instrument`
- **`price_history`**: PK `(symbol, ts)`, `ts` 기준 하이퍼테이블(청크 7일), OHLCV(`open/high/low/volume` 은 nullable), `close` NOT NULL, `source` 출처 태그, `instrument` 로 FK — 엔티티 `marketdata.price.PriceHistory` (복합키 `PriceHistoryId`)
  - 현재 적재기는 토스 `/api/v1/prices` 의 현재가만 얻으므로 `close` 만 채운다. 정규 분봉/일봉 소스 연동 시 같은 테이블에 전체 필드 적재.
- **`financial_snapshot`**: PK `(symbol, fiscal_year, fs_div)`, DART 재무제표를 소화한 핵심 계정(매출/영업이익/순이익/자본총계/부채총계/전기매출/발행주식수), **금액 단위 백만원** — 엔티티 `marketdata.financial.FinancialSnapshot`
- **`macro_indicator`**: PK `(indicator_code, ts)`, `ts` 하이퍼테이블. 거시지표 시계열(BASE_RATE/USD_KRW…), Z-score 분포 창 — 엔티티 `marketdata.macro.MacroIndicator`
- **`attractiveness_score`**: PK `(symbol, as_of)`, `base_score`·`event_coefficient`·`attractiveness` + `factor_breakdown`(JSON, 요인별 raw/정규화/가중) — 엔티티 `attractiveness.persistence.AttractivenessScore`
- **`entry_score`**: PK `(symbol, as_of)`, `rebound_signal`·`technical_indicator`·`attractiveness_component`(각 0~1) + `entry_score`(0~100) + `filter_passed` + `factor_breakdown`(JSON) — 엔티티 `entryscore.persistence.EntryScore`

## 매력도 지수 "기본점수" 엔진 (기획서 4장)

```
기본점수 = 100 × Σ(가중치_i × 정규화_i) / Σ(가중치_i)      (가중치는 application.yml, 합≠1 허용)
매력도 지수 = 기본점수 × 이벤트 조정계수                    (조정계수 현재 1.0 고정 - 기획서 4-5 미구현, TODO)
```

1차 핵심 요인 7개 (`attractiveness.Factor`):

| 요인 | 종류 | 정규화 | 방향 |
|---|---|---|---|
| PER · PBR · 부채비율 | FUNDAMENTAL | 업종(`instrument.industry`) percentile | 낮을수록 유리 |
| ROE · 매출성장률 | FUNDAMENTAL | 업종 percentile | 높을수록 유리 |
| 기준금리 · 원/달러환율 | MACRO | Z-score(`0.5·(1+tanh(z/2))`) | 낮을수록 유리(잠정) |

- 순수 계산: `attractiveness.BaseScoreEngine`(DB 비의존) + `PercentileNormalizer` / `ZScoreNormalizer` / `FundamentalMetricsCalculator`
- 저장소 연동: `attractiveness.BaseScoreService`(`@ConditionalOnProperty attractiveness.enabled`) — 재무 스냅샷·시세·거시데이터 로딩 → 계산 → `attractiveness_score` 저장
  - **회계연도 선택**: `financial_snapshot` 에서 `findFirstBySymbolAndFsDivOrderByFiscalYearDesc` 로 **최신 fiscal_year** 사용
    (`entry_score` 가 `attractiveness_score` 를 `findFirstBySymbolOrderByAsOfDesc` 로 쓰는 것과 같은 패턴).
    `attractiveness.target-fiscal-year` 를 `>0` 으로 두면 그 연도로 고정(백테스트 재현용), `0`(기본)이면 자동.
- 적재: `attractiveness.ingest.DartFinancialSnapshotIngestionService`(DART→`financial_snapshot`) + `MacroIndicatorRefreshService`(ECOS→`macro_indicator`). 아래 "주간 배치 — 재무제표 적재" 참고.
- 스모크: `POST /api/attractiveness/{symbol}` → 계산·저장. 3종목 FY2025 실측(2026-09): 기본점수 **삼성전자 52.0 / SK하이닉스 67.0 / 한미반도체 42.0**
  (FY2024 대비 각 58.7→52.0 / 48.7→67.0 / 53.7→42.0 — SK하이닉스 ROE 26.8%→35.6%·PER 65.7→31.1 로 급개선, 업종 percentile 이 상대평가라 나머지 둘은 하락)
- 가중치는 `application.yml` `attractiveness.weights.*` (전문가 초안, 기획서 4-4 4단계 방법론으로 확정 예정)

## 저점 진입 스코어 엔진 (기획서 5장)

```
저점 진입 스코어 = 100 × [ (반등신호 × A) + (기술적지표 × B) + (매력도지수 × C) ] / (A+B+C)
                  단, 매력도 1차 필터(base_score ≥ 60) 미통과면 0
```

| 구성요소 | 계산 | 정규화 |
|---|---|---|
| 반등신호 (A) | 최근 `rebound-lookback`봉 고점 대비 낙폭 ÷ ATR(14) | 0.5~1.5×ATR 구간 plateau=1.0, 밖으로 램프다운 (`reboundNormalize`) |
| 기술적지표 (B) | RSI(14) 과매도(≤30) 후 상향돌파 + 볼린저(20,2σ) 하단 터치 후 복귀 두 서브점수 **평균** | 각 0~1 |
| 매력도지수 (C) | 최신 `attractiveness_score.base_score` / 100 | 0~1. base_score ≥ 60 이 1차 필터 |

- 순수 계산: `entryscore.EntryScoreEngine`(DB 비의존) + `TechnicalIndicatorCalculator`(Wilder ATR/RSI, Bollinger %b)
- 저장소 연동: `entryscore.EntryScoreService`(`@ConditionalOnProperty entry-score.enabled`) — 일봉 시세(`price_history` OHLC 만) + 최신 매력도 점수 로딩 → 계산 → `entry_score` 저장. 매력도 점수 없으면 422 (먼저 `POST /api/attractiveness/{symbol}`).
- 가중치 A/B/C·지표 파라미터는 `application.yml` `entry-score.*` (초안 0.3/0.3/0.4, 하드코딩 금지 — 기획서 5-3 4단계 방법론으로 확정 예정). `entry-threshold` 70 은 참고/로그용.
- 스모크: `POST /api/entry-score/{symbol}`. 3종목(005930/000660/042700) 실측: 매력도 기본점수 58.7/48.7/53.7 로 **셋 다 1차 필터 미통과 → entry_score 0** (반등신호 1.00/0.07/0.78, 기술적지표는 셋 다 0 — 현재 과매도·하단터치 없음). 필터 임계를 45 로 낮추면 53.5/21.7/44.7 (모두 진입임계 70 미달).

## 매매 신호 판정 (기획서 7)

`signal.TradingSignalService`(`@ConditionalOnProperty signal.enabled`) — **매수 신호 판정만** 구현.
저장 없이 계산·반환. `GET /api/trading-signal/{symbol}` → `decision` `BUY`(매수신호)/`WAIT`(대기) + 조건별 상세.

기획서 7-1 매수 룰:

| 조건 | 소스 | 현재 상태 |
|---|---|---|
| 저점진입스코어 ≥ `signal.entry-threshold`(70) | 최신 `entry_score.entry_score` | 평가 |
| 기본점수 ≥ `signal.min-base-score`(60) | 최신 `attractiveness_score.base_score` | 평가 |
| 이벤트조정계수 ≥ `signal.event-coefficient-min`(0.9) | 최신 `attractiveness_score.event_coefficient` | 평가 (계수 항상 1.0 → 사실상 항상 통과, 기획서 4-5 TODO) |
| 보유현금 > 최소매수단위 | — | **미평가**(`evaluated:false`) — 계좌/포지션 개념 없음, TODO |

- 전제 데이터 없으면 422 (`entry_score` 없으면 `POST /api/entry-score/{symbol}` 먼저, `attractiveness_score` 없으면 `POST /api/attractiveness/{symbol}` 먼저).
- **매도 신호(기획서 7-2, 목표수익률/손절/보유시간)는 이번 범위 밖** — 포지션(진입가·진입시각·수량) 개념 필요, `TradingSignalService` 클래스 주석에 TODO.
- 스모크: 3종목 모두 매력도 기본점수 < 60 (58.7/48.7/53.7) 이고 entry_score 0 → **셋 다 `WAIT`**(저점진입스코어·기본점수 조건 미충족). `conditions` 배열에 조건별 actual/threshold/pass 표시.

## 시세 적재 스케줄러

`marketdata.ingestion.PriceIngestionJob` — `@Scheduled` 로 주기 실행, `TossQuoteClient.getQuotes()` →
`price_history` upsert(`ON CONFLICT (symbol, ts) DO UPDATE`). 토스 조회 실패는 삼켜서 다음 주기 재시도.

```yaml
dipscore.ingestion.price:
  enabled: true          # false 면 스케줄러 빈 자체가 안 뜸 (테스트 컨텍스트가 이 상태 → 외부 호출 없음)
  symbols: 005930        # comma-separated, 최대 200
  interval-ms: 60000
  initial-delay-ms: 10000
```

로그: `[price-ingestion] N건 저장 (요청 M종목: [...])` / 실패 시 `... 토스 시세 조회 실패, 이번 주기 건너뜀: ...`

## 과거 시세 백필 (일봉 캔들)

토스 `GET /api/v1/candles`(`symbol`, `interval=1d`, `count≤200`, `before` 페이지네이션, `nextBefore` 응답 —
[공개 스펙](https://openapi.tossinvest.com/openapi-docs/latest/openapi.json), 2026-09 확인)로 `price_history` 를
과거로 채운다. `TossCandleClient` → `PriceBackfillService`(종목별 페이지네이션·cutoff·딜레이) →
`PriceHistoryBackfillWriter`(종목 단위 트랜잭션, `upsertOhlcv` 로 **OHLCV 전체** 채움, `source=TOSS_CANDLE_1D`).

- **수동 트리거만**: `POST /api/admin/price-backfill` — 스케줄러와 무관, 앱 기동 시 자동 실행 안 함.
  파라미터 없으면 설정 기본값(3종목, 365일). `?symbols=005930,000660&days=90` 로 지정 가능.
- 종목별 로그: `[price-backfill] 005930: N건 적재 (기간 <최초> ~ <최근>)`
- rate limit(`MARKET_DATA_CHART`) 대비: 페이지 간·종목 간 `request-delay-ms`(기본 300ms) 딜레이.

```yaml
backfill.prices:
  enabled: true          # false/미설정 → 엔드포인트·서비스 빈 미생성 (테스트 컨텍스트가 이 상태)
  symbols: 005930,000660,042700
  days: 365               # ≈ 250 영업일. 365일 → 종목당 ~243봉, 2페이지
  request-delay-ms: 300
```

## 일별 배치 — 최신 일봉 적재 (`daily-candle-sync`)

장 마감 후 대상 종목의 **최신 일봉 1건**을 `price_history` 에 append 한다.
`marketdata.dailybatch.DailyCandleSyncJob` — `PriceIngestionJob` 과 같은 스케줄러 패턴
(`@Scheduled(cron, zone="Asia/Seoul")` + `@ConditionalOnProperty`), `TossCandleClient.getDailyCandles(count=1)` →
`DailyCandleSyncWriter`(종목 단위 트랜잭션, `upsertOhlcv`, `source=TOSS_CANDLE_1D` — 백필과 동일).

- `daily-batch.symbols` 는 **여러 일별 배치가 공유**하는 대상 종목 (이 잡 전용 아님).
- 한 종목 조회/적재 실패는 삼키고 다음 종목 계속. 실행 결과 로그: `[daily-candle-sync] 완료: N종목 중 성공 x / 실패 y`.
- **수동 트리거**: `POST /api/admin/daily-candle-sync` (스케줄과 별개로 즉시 1회). `?symbols=005930,000660` 로 대상 지정 가능.
  응답: `{ requested, succeeded, failed, results:[{symbol, ok, ts, close, error}] }`.
- 소규모 실측(2026-09, 3종목): 성공 3/3, `?symbols=005930,999999` → 성공 1 / 실패 1(존재 안 하는 종목 `HTTP 404` 를 error 로 집계, 나머지 계속).

```yaml
daily-batch:
  symbols: 005930,000660,042700   # 공유 대상 종목
  daily-candle:
    enabled: true                 # false/미설정 → 스케줄러·컨트롤러·writer 빈 미생성 (테스트 컨텍스트가 이 상태)
    cron: "0 0 16 * * MON-FRI"     # Asia/Seoul — 평일 16:00, 장 마감(15:30) 후
```

## 주간 배치 — 재무제표 적재 (`financial-snapshot-sync`)

`daily-batch.symbols` 대상 종목의 최신 재무제표를 `financial_snapshot` 에 적재한다 (기본: 매주 월 03:00 Asia/Seoul).
`attractiveness.ingest.FinancialSnapshotSyncJob` — `DailyCandleSyncJob` 과 같은 스케줄러 패턴.

- **계정 매칭**: 표준계정 `account_id`(예: `ifrs-full_Revenue`, `dart_OperatingIncomeLoss`, `ifrs-full_ProfitLoss`,
  `ifrs-full_Equity`, `ifrs-full_Liabilities`) 우선, 없으면 `account_nm` 문자열 폴백. 손익 계정은 `sj_div` 가
  `IS` 뿐 아니라 **`CIS`(포괄손익계산서)** 도 본다 — 별도 IS 없이 CIS 만 내는 회사(SK하이닉스 등) 대응.
- **발행주식수**: 재무제표엔 없어 `DartStockTotalCountClient`(주식총수현황 `/api/stockTotqySttus.json`)로 별도 조회 —
  보통주 `istc_totqy`(발행주식총수), 없으면 합계 행. 조회 실패해도 스냅샷은 저장(주식수 null).
- **공시일**: `rcept_no` 앞 8자리 → `disclosed_at`.
- **보고서 폴백**: 사업보고서(`11011`) → 3분기(`11014`) → 반기(`11012`) → 1분기(`11013`) 순으로 첫 성공분 적재.
  `source` 에 실제 코드 기록 (`DART_11011_2025` 등).
- **중복 방지**: 대상 `fiscal_year` 스냅샷이 이미 있으면 skip. 새 연도만 insert.
- **corp_code**: `instrument.corp_code` 우선, 없으면 `corpCode.xml` 조회 폴백(042700 등). 그래도 없으면 skip.
- 종목별 독립 — 실패해도 다음 종목 계속. 로그: `[financial-snapshot-sync] 완료: FYxxxx CFS — N종목 중 신규 x / skip y / 실패 z`.
- **수동 트리거**: `POST /api/admin/financial-snapshot-sync` (`?symbols=…&fiscalYear=2024`). 기본 회계연도 = 작년.
  응답: `{ fiscalYear, fsDiv, requested, inserted, skipped, failed, results:[{symbol, status, fiscalYear, source, error}] }`
  (status: `INSERTED` / `SKIPPED_EXISTS` / `SKIPPED_NO_CORP_CODE` / `FAILED`).
- 실측(2026-09, FY2025): 3종목 전부 `DART_11011_2025` 적재 — 삼성전자/SK하이닉스/한미반도체 매출·영업이익·순이익·자본·부채·전기매출·주식수·공시일 모두 채워짐(SK하이닉스는 CIS, 한미반도체는 corp_code 폴백).

```yaml
attractiveness:
  enabled: true                     # 이 값도 true 여야 함 (ingestion 서비스가 여기 걸림)
  financial-sync:
    enabled: true                   # false/미설정 → 스케줄러·컨트롤러 빈 미생성
    cron: "0 0 3 * * MON"           # Asia/Seoul — 매주 월 03:00
    target-fiscal-year: 0           # 0 = 자동(작년)
    request-delay-ms: 300
```

## 종목 마스터 대량 채우기 (DART 고유번호)

`DartCorpCodeClient.downloadAll()`(corpCode.xml, ~12만 회사) → 상장(`stockCode` 있음)만 필터 →
`stockCode` 중복 제거 → (선택) corp_cls 분류 → `InstrumentSyncWriter`(청크 단위 트랜잭션)로 `instrument` upsert.

- **수동 트리거만**: `POST /api/admin/instrument-sync` — 자동 실행 안 함.
- `?symbols=086220,224810,005930` — 지정 종목코드만 대상 (소규모 검증·재동기화). 미지정 시 DART 상장 전체.
- upsert(`InstrumentRepository.upsertFromDart`): `INSERT ... ON CONFLICT (symbol) DO UPDATE SET name, corp_code, active, updated_at` — **`name`·`corp_code`(·`active`) 만 갱신**.
  `sector`/`industry`/`market_type`/`currency` 는 SET 절에 없어 **기존 값 유지** (반도체 3사 시드의 `sector=IT, industry=반도체` 안 지워짐). 신규는 `market_type=KR_STOCK` 고정.
- 청크(기본 500) 하나가 실패해도 나머지 진행, `failed` 로 집계. 멱등(재실행 안전).

**corp_cls 시장구분 필터** (`filter-by-corp-cls`, 기본 on. `?filterByCorpCls=false` 로 호출별 off):

- 각 상장 종목을 `DartCompanyClient.getCompany(corpCode)` 로 조회해 `corp_cls` 확인.
- `corp_cls='N'`(코넥스) → **삭제하지 않고 `instrument.active=false` 로 soft-delete** (V6, FK 자식행 보존·가역).
  신규 코넥스는 `active=false` 로 insert, 기존 활성행은 `active=false` 로 flip. `Y`/`K`/`E`/불명/조회실패 → `active=true`.
- 스코어링/랭킹 유니버스는 `active=true` 만 대상 (`findByIndustryAndActiveTrue`).
- `konexDeactivated` = **실제로 `active=false` 로 DB 반영된 코넥스 건수** (청크 실패분 제외). "이번에 안 넣은 건수"가 아님.
- rate limit: 호출 간 `company-lookup-delay-ms`(기본 100ms). `company-lookup-max-consecutive-failures`(기본 20)회 연속 실패 시 필터 **중단**(나머지는 활성 처리, `aborted=true`) — 일일 한도 소진 대비.
- 3,989건 개별 호출 → **수십 분 소요**. 시작 시 경고 로그: `corp_cls 필터 ON — 상장 N건 … 최소 약 M분 소요 예상. DART 일일 요청 한도 소모에 유의.`
- corp_cls off 실측(2026-09): DART 118,942사 → 상장 3,989 → 신규 3,986 / 갱신 3(시드 3사, `042700` corp_code `00161383` 채워짐) / 실패 0, ~18초.
- corp_cls on 소규모 실측(2026-09, `?symbols=086220,224810,005930,247540`): 광동헬스바이오·엄지하우스(코넥스 `N`) → `active=false` flip, 삼성전자(`Y`)·에코프로비엠(`K`) → `active=true` 유지, `konexDeactivated=2`.

```yaml
instrument-sync:
  enabled: true          # false/미설정 → 엔드포인트·서비스 빈 미생성
  chunk-size: 500
  filter-by-corp-cls: true            # 코넥스(N) → active=false. ?filterByCorpCls=false 로 끄면 수 초
  company-lookup-delay-ms: 100
  company-lookup-max-consecutive-failures: 20
```

## 빌드 / 테스트

```bash
./gradlew build      # 테스트 포함, DB 없이도 통과 (테스트 스코프에서 DB 자동설정 + 스케줄러 제외)
```

## DB 접속 정보 (기본값, 환경변수로 오버라이드)

| 변수 | 기본값 |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/dipscore` |
| `DB_USERNAME` | `dipscore` |
| `DB_PASSWORD` | `dipscore` |
