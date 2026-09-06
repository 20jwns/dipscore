package com.dipscore.backend.marketdata.instrument;

/** 종목 시장/자산군 구분 (기획서 자산군: 국내 주식 / 미국 주식 / ETF). */
public enum MarketType {

    /** 국내 주식 (KRX). */
    KR_STOCK,

    /** 미국 주식 (NYSE/NASDAQ). */
    US_STOCK,

    /** ETF (국내·해외 공통). */
    ETF
}
