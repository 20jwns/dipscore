package com.dipscore.backend.marketdata.instrument.sync;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.dipscore.backend.external.dart.DartApiException;
import com.dipscore.backend.marketdata.instrument.sync.InstrumentSyncService.InstrumentSyncReport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstrumentSyncJobTest {

    @Mock
    InstrumentSyncService service;

    private InstrumentSyncJob job() {
        return new InstrumentSyncJob(service,
                new InstrumentSyncProperties(true, true, "0 0 4 * * *", 500, true, true, 3, 0L, 20));
    }

    private static InstrumentSyncReport report() {
        return new InstrumentSyncReport(100, 90, 88, true, 1, 0, false, 5, 80, 0, 2);
    }

    @Test
    void 스케줄_실행은_corp_cls필터_ON_상장폐지감지_ON으로_전체동기화한다() {
        when(service.sync(true, List.of(), true)).thenReturn(report());

        job().scheduledRun();

        verify(service).sync(true, List.of(), true);
    }

    @Test
    void 서비스가_예외를_던져도_스케줄_스레드로_전파하지_않는다() {
        when(service.sync(anyBoolean(), any(), anyBoolean()))
                .thenThrow(new DartApiException("corpCode.xml 다운로드 실패: HTTP 503"));

        assertThatCode(() -> job().scheduledRun()).doesNotThrowAnyException();
    }
}
