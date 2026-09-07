package com.dipscore.backend.entryscore;

/** 저점 진입 스코어 계산 불가 (시세 봉 부족, 매력도 점수 부재 등). */
public class EntryScoreException extends RuntimeException {

    public EntryScoreException(String message) {
        super(message);
    }
}
