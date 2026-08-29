package com.kuit.findyou.domain.report.service.sync.staging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuit.findyou.domain.report.model.sync.PublicAnimalStagingRow;
import com.kuit.findyou.domain.report.model.sync.SyncJob;
import com.kuit.findyou.domain.report.model.sync.SyncJobType;
import com.kuit.findyou.domain.report.repository.sync.PublicAnimalStagingJdbcRepository;
import com.kuit.findyou.domain.report.service.sync.job.SyncJobService;
import com.kuit.findyou.domain.report.service.sync.merge.ProtectingReportMergeService;
import com.kuit.findyou.global.external.client.KakaoCoordinateClient;
import com.kuit.findyou.global.external.dto.ProtectingAnimalItemDTO;
import com.kuit.findyou.global.external.dto.ProtectingAnimalPageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProtectingAnimalStagingServiceTest {

    @Mock
    ProtectingReportMergeService mergeService;

    @Mock
    SyncJobService syncJobService;

    @Mock
    PublicAnimalStagingJdbcRepository publicAnimalStagingRepository;

    @Mock
    ProtectingAnimalStagingWriter protectingAnimalStagingWriter;

    @Mock
    KakaoCoordinateClient kakaoCoordinateClient;

    ProtectingAnimalStagingService protectingAnimalStagingService;

    @BeforeEach
    void setUp() {
        protectingAnimalStagingService = new ProtectingAnimalStagingService(
                mergeService,
                syncJobService,
                protectingAnimalStagingWriter,
                publicAnimalStagingRepository,
                kakaoCoordinateClient,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("페이지 데이터를 스테이징 테이블에 저장한다")
    void should_SavePageSuccess() {
        // given
        Long syncJobId = 1L;
        int batchNo = 3;
        SyncJob syncJob = SyncJob.start(SyncJobType.PROTECTING_REPORT_SYNC);
        ProtectingAnimalItemDTO item = protectingAnimalItem(" NOTICE-1 ");
        ProtectingAnimalPageResult pageResult = new ProtectingAnimalPageResult(
                3,
                1000,
                2500,
                List.of(item)
        );
        when(kakaoCoordinateClient.requestCoordinateOrDefault("서울시 강남구"))
                .thenReturn(new KakaoCoordinateClient.Coordinate(
                        BigDecimal.valueOf(37.123456),
                        BigDecimal.valueOf(127.123456)
                ));
        when(protectingAnimalStagingWriter.saveRows(anyList()))
                .thenAnswer(invocation -> invocation.<List<PublicAnimalStagingRow>>getArgument(0).size());

        // when
        int savedCount = protectingAnimalStagingService.savePage(syncJobId, batchNo, pageResult);

        // then
        assertThat(savedCount).isEqualTo(1);

        ArgumentCaptor<List<PublicAnimalStagingRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(protectingAnimalStagingWriter).saveRows(captor.capture());

        List<PublicAnimalStagingRow> savedRows = captor.getValue();
        assertThat(savedRows).hasSize(1);

        PublicAnimalStagingRow saved = savedRows.get(0);
        assertThat(saved.syncJobId()).isSameAs(syncJobId);
        assertThat(saved.batchNo()).isEqualTo(batchNo);
        assertThat(saved.noticeNumber()).isEqualTo("NOTICE-1");
        assertThat(saved.species()).isEqualTo("강아지");
        assertThat(saved.breed()).isEqualTo("진돗개");
        assertThat(saved.happenDate()).hasToString("2026-05-01");
        assertThat(saved.address()).isEqualTo("서울시 강남구");
        assertThat(saved.sex()).isEqualTo("M");
        assertThat(saved.neutering()).isEqualTo("Y");
        assertThat(saved.age()).isEqualTo("6");
        assertThat(saved.weight()).isEqualTo("5.2");
        assertThat(saved.furColor()).isEqualTo("갈색,흰색");
        assertThat(saved.significant()).isEqualTo("순함");
        assertThat(saved.foundLocation()).isEqualTo("강남역");
        assertThat(saved.noticeStartDate()).hasToString("2026-05-01");
        assertThat(saved.noticeEndDate()).hasToString("2026-05-10");
        assertThat(saved.careName()).isEqualTo("강남보호소");
        assertThat(saved.careTel()).isEqualTo("02-123-4567");
        assertThat(saved.authority()).isEqualTo("강남구청");
        assertThat(saved.imageUrl1()).isEqualTo("https://example.com/1.jpg");
        assertThat(saved.imageUrl2()).isEqualTo("https://example.com/2.jpg");
        assertThat(saved.rawData()).contains("\"noticeNo\":\" NOTICE-1 \"");
        assertThat(saved.rawHash()).hasSize(64);
    }

    private ProtectingAnimalItemDTO protectingAnimalItem(String noticeNo) {
        return new ProtectingAnimalItemDTO(
                "20260501",
                "강남역",
                "개",
                "진돗개",
                "갈색&흰색",
                "2020(년생)",
                "5,2(Kg)",
                noticeNo,
                "20260501",
                "20260510",
                "https://example.com/1.jpg",
                "https://example.com/2.jpg",
                "M",
                "Y",
                " 순함 ",
                "강남보호소",
                "02-123-4567",
                "서울시 강남구",
                "홍길동",
                "강남구청"
        );
    }
}
