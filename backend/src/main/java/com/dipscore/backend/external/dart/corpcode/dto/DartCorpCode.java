package com.dipscore.backend.external.dart.corpcode.dto;

/**
 * 고유번호 파일(CORPCODE.xml) 1행.
 *
 * @param corpCode   DART 고유번호 8자리
 * @param corpName   회사명
 * @param stockCode  상장 종목코드 6자리 (비상장이면 null)
 * @param modifyDate 최근 변경일자 YYYYMMDD
 */
public record DartCorpCode(String corpCode, String corpName, String stockCode, String modifyDate) {

    public boolean isListed() {
        return stockCode != null && !stockCode.isBlank();
    }
}
