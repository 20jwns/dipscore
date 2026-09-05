from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health():
    res = client.get("/health")
    assert res.status_code == 200
    assert res.json()["status"] == "ok"


def test_sentiment_stub():
    res = client.post("/api/sentiment", json={"text": "삼성전자 어닝 서프라이즈"})
    assert res.status_code == 200
    body = res.json()
    assert body["label"] == "neutral"
    assert body["score"] == 0.0
