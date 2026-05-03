package com.kuit.findyou.domain.report.service.sync.staging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuit.findyou.domain.report.model.sync.PublicAnimalStaging;
import com.kuit.findyou.domain.report.model.sync.SyncJob;
import com.kuit.findyou.domain.report.model.sync.SyncJobType;
import com.kuit.findyou.domain.report.repository.sync.PublicAnimalStagingRepository;
import com.kuit.findyou.domain.report.repository.sync.SyncJobRepository;
import com.kuit.findyou.global.external.dto.ProtectingAnimalItemDTO;
import com.kuit.findyou.global.external.dto.ProtectingAnimalPageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProtectingAnimalStagingServiceTest {

    @Mock
    PublicAnimalStagingRepository publicAnimalStagingRepository;

    @Mock
    SyncJobRepository syncJobRepository;

    ProtectingAnimalStagingService protectingAnimalStagingService;

    @BeforeEach
    void setUp() {
        protectingAnimalStagingService = new ProtectingAnimalStagingService(
                publicAnimalStagingRepository,
                syncJobRepository,
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

        when(syncJobRepository.getReferenceById(syncJobId)).thenReturn(syncJob);

        // when
        int savedCount = protectingAnimalStagingService.savePage(syncJobId, batchNo, pageResult);

        // then
        assertThat(savedCount).isEqualTo(1);

        ArgumentCaptor<List<PublicAnimalStaging>> captor = ArgumentCaptor.forClass(List.class);
        verify(publicAnimalStagingRepository).saveAll(captor.capture());
        verify(publicAnimalStagingRepository).flush();

        List<PublicAnimalStaging> savedRows = captor.getValue();
        assertThat(savedRows).hasSize(1);

        PublicAnimalStaging saved = savedRows.get(0);
        assertThat(saved.getSyncJob()).isSameAs(syncJob);
        assertThat(saved.getBatchNo()).isEqualTo(batchNo);
        assertThat(saved.getNoticeNumber()).isEqualTo("NOTICE-1");
        assertThat(saved.getSpecies()).isEqualTo("강아지");
        assertThat(saved.getBreed()).isEqualTo("진돗개");
        assertThat(saved.getHappenDate()).hasToString("2026-05-01");
        assertThat(saved.getAddress()).isEqualTo("서울시 강남구");
        assertThat(saved.getSex()).isEqualTo("M");
        assertThat(saved.getNeutering()).isEqualTo("Y");
        assertThat(saved.getAge()).isEqualTo("6");
        assertThat(saved.getWeight()).isEqualTo("5.2");
        assertThat(saved.getFurColor()).isEqualTo("갈색,흰색");
        assertThat(saved.getSignificant()).isEqualTo("순함");
        assertThat(saved.getFoundLocation()).isEqualTo("강남역");
        assertThat(saved.getNoticeStartDate()).hasToString("2026-05-01");
        assertThat(saved.getNoticeEndDate()).hasToString("2026-05-10");
        assertThat(saved.getCareName()).isEqualTo("강남보호소");
        assertThat(saved.getCareTel()).isEqualTo("02-123-4567");
        assertThat(saved.getAuthority()).isEqualTo("강남구청");
        assertThat(saved.getImageUrl1()).isEqualTo("https://example.com/1.jpg");
        assertThat(saved.getImageUrl2()).isEqualTo("https://example.com/2.jpg");
        assertThat(saved.getRawData()).contains("\"noticeNo\":\" NOTICE-1 \"");
        assertThat(saved.getRawHash()).hasSize(64);
    }

    @Test
    @DisplayName("staging 데이터 수와 expectedTotalCount가 일치하면 검증에 성공한다")
    void should_ValidateSuccess_When_StagedCountMatchesExpectedTotalCount() {
        // given
        Long syncJobId = 1L;
        int expectedTotalCount = 10;
        when(publicAnimalStagingRepository.countBySyncJobId(syncJobId)).thenReturn(10L);

        // when
        StagingValidationResult result = protectingAnimalStagingService.validate(syncJobId, expectedTotalCount);

        // then
        assertThat(result.valid()).isTrue();
        assertThat(result.message()).isNull();
    }

    @Test
    @DisplayName("staging 데이터 수와 expectedTotalCount가 일치하지 않으면 검증에 실패한다")
    void should_ValidateFailure_When_StagedCountDoesNotMatchExpectedTotalCount() {
        // given
        Long syncJobId = 1L;
        int expectedTotalCount = 10;
        when(publicAnimalStagingRepository.countBySyncJobId(syncJobId)).thenReturn(8L);

        // when
        StagingValidationResult result = protectingAnimalStagingService.validate(syncJobId, expectedTotalCount);

        // then
        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("expectedTotalCount=10");
        assertThat(result.message()).contains("stagedCount=8");
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
