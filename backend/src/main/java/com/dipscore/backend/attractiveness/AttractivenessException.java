package com.dipscore.backend.attractiveness;

/** 매력도 지수 계산 불가 (재무 스냅샷/시세/거시데이터 부재 등). */
public class AttractivenessException extends RuntimeException {

    public AttractivenessException(String message) {
        super(message);
    }
}
