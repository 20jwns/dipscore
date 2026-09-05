# dipscore-nlp-service

DipScore 뉴스 감성분석 마이크로서비스 (FastAPI / Python 3.11+).

Spring Boot 백엔드가 내부 API 로 호출한다. 현재는 스캐폴딩이며 실제 파인튜닝 모델은 미연동
(`/api/sentiment` 는 중립값 스텁).

## 실행

```bash
cd nlp-service
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt

uvicorn app.main:app --reload --port 8000
```

- Health: `curl localhost:8000/health`
- Docs (Swagger UI): http://localhost:8000/docs

## 테스트

```bash
pytest
```
