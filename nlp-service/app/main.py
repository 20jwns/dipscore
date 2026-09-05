"""DipScore NLP 마이크로서비스 (FastAPI).

역할 (기획서 기준): 뉴스 감성분석(직접 파인튜닝 모델) 서빙.
Spring Boot 백엔드가 내부 API 로 호출한다. 지금은 스캐폴딩 - 실제 모델 없음.
"""

from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(
    title="dipscore-nlp-service",
    description="뉴스 감성분석 마이크로서비스",
    version="0.0.1",
)


class SentimentRequest(BaseModel):
    text: str


class SentimentResponse(BaseModel):
    label: str
    score: float


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "dipscore-nlp-service"}


@app.post("/api/sentiment", response_model=SentimentResponse)
def sentiment(req: SentimentRequest) -> SentimentResponse:
    """스텁 구현. 파인튜닝 모델 연동 전까지 중립값을 반환한다."""
    return SentimentResponse(label="neutral", score=0.0)
