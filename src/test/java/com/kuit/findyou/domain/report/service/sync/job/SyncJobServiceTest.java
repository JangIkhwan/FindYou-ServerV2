package com.kuit.findyou.domain.report.service.sync.job;

import com.kuit.findyou.domain.report.model.sync.SyncJob;
import com.kuit.findyou.domain.report.model.sync.SyncJobBatch;
import com.kuit.findyou.domain.report.model.sync.SyncJobBatchStatus;
import com.kuit.findyou.domain.report.model.sync.SyncJobStatus;
import com.kuit.findyou.domain.report.model.sync.SyncJobType;
import com.kuit.findyou.domain.report.repository.sync.SyncJobBatchRepository;
import com.kuit.findyou.domain.report.repository.sync.SyncJobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncJobServiceTest {

    @Mock
    SyncJobRepository syncJobRepository;

    @Mock
    SyncJobBatchRepository syncJobBatchRepository;

    @InjectMocks
    SyncJobService syncJobService;

    @Test
    @DisplayName("활성 job이 없으면 RUNNING 상태의 sync job을 생성한다")
    void should_StartJob_When_NoActiveJobExists() {
        // given
        SyncJobType jobType = SyncJobType.PROTECTING_REPORT_SYNC;
        when(syncJobRepository.save(any(SyncJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        SyncJob result = syncJobService.startJob(jobType);

        // then
        assertThat(result.getJobType()).isEqualTo(jobType);
        assertThat(result.getStatus()).isEqualTo(SyncJobStatus.RUNNING);
        assertThat(result.getStartedAt()).isNotNull();
        assertThat(result.getLastHeartbeatAt()).isNotNull();
        assertThat(result.getLeaseUntil()).isAfter(result.getLastHeartbeatAt());

        ArgumentCaptor<SyncJob> captor = ArgumentCaptor.forClass(SyncJob.class);
        verify(syncJobRepository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(result);
        verify(syncJobRepository).expireActiveJobs(
                eq(jobType),
                any(Collection.class),
                eq(SyncJobStatus.EXPIRED),
                any(LocalDateTime.class),
                eq("Sync job lease expired")
        );
    }

    @Test
    @DisplayName("활성 job 존재 여부를 조회한다")
    void should_CheckActiveJobExists() {
        // given
        SyncJobType jobType = SyncJobType.PROTECTING_REPORT_SYNC;
        when(syncJobRepository.existsByJobTypeAndStatusInAndLeaseUntilAfter(
                eq(jobType),
                any(Collection.class),
                any(LocalDateTime.class)
        ))
                .thenReturn(true);

        // when
        boolean result = syncJobService.hasActiveJob(jobType);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("유효한 active job이 있으면 새 job을 시작하지 않는다")
    void should_ThrowException_When_ActiveJobLeaseIsValid() {
        // given
        SyncJobType jobType = SyncJobType.PROTECTING_REPORT_SYNC;
        when(syncJobRepository.existsByJobTypeAndStatusInAndLeaseUntilAfter(
                eq(jobType),
                any(Collection.class),
                any(LocalDateTime.class)
        ))
                .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> syncJobService.startJob(jobType))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Active sync job already exists");

        verify(syncJobRepository).expireActiveJobs(
                eq(jobType),
                any(Collection.class),
                eq(SyncJobStatus.EXPIRED),
                any(LocalDateTime.class),
                eq("Sync job lease expired")
        );
    }

    @Test
    @DisplayName("batch 성공을 기록하면 batch 저장과 job staged count 누적을 수행한다")
    void should_RecordBatchSuccess() {
        // given
        Long jobId = 1L;
        SyncJob syncJob = SyncJob.start(SyncJobType.PROTECTING_REPORT_SYNC);
        when(syncJobRepository.findById(jobId)).thenReturn(Optional.of(syncJob));

        // when
        syncJobService.recordBatchSuccess(jobId, 2, 10, 100, 98);

        // then
        assertThat(syncJob.getTotalStagedCount()).isEqualTo(98);
        assertThat(syncJob.getLastHeartbeatAt()).isNotNull();
        assertThat(syncJob.getLeaseUntil()).isAfter(syncJob.getLastHeartbeatAt());

        ArgumentCaptor<SyncJobBatch> captor = ArgumentCaptor.forClass(SyncJobBatch.class);
        verify(syncJobBatchRepository).save(captor.capture());

        SyncJobBatch batch = captor.getValue();
        assertThat(batch.getSyncJob()).isSameAs(syncJob);
        assertThat(batch.getBatchNo()).isEqualTo(2);
        assertThat(batch.getRequestPageNo()).isEqualTo(10);
        assertThat(batch.getRequestedCount()).isEqualTo(100);
        assertThat(batch.getStagedCount()).isEqualTo(98);
        assertThat(batch.getStatus()).isEqualTo(SyncJobBatchStatus.SUCCESS);
        assertThat(batch.getFinishedAt()).isNotNull();
        assertThat(batch.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("batch 실패를 기록하면 batch 저장과 job failed batch count 누적을 수행한다")
    void should_RecordBatchFailure() {
        // given
        Long jobId = 1L;
        SyncJob syncJob = SyncJob.start(SyncJobType.PROTECTING_REPORT_SYNC);
        when(syncJobRepository.findById(jobId)).thenReturn(Optional.of(syncJob));

        // when
        syncJobService.recordBatchFailure(jobId, 3, 11, 100, "api timeout");

        // then
        assertThat(syncJob.getFailedBatchNo()).isEqualTo(3);

        ArgumentCaptor<SyncJobBatch> captor = ArgumentCaptor.forClass(SyncJobBatch.class);
        verify(syncJobBatchRepository).save(captor.capture());

        SyncJobBatch batch = captor.getValue();
        assertThat(batch.getSyncJob()).isSameAs(syncJob);
        assertThat(batch.getBatchNo()).isEqualTo(3);
        assertThat(batch.getRequestPageNo()).isEqualTo(11);
        assertThat(batch.getRequestedCount()).isEqualTo(100);
        assertThat(batch.getStatus()).isEqualTo(SyncJobBatchStatus.FAILED);
        assertThat(batch.getFinishedAt()).isNotNull();
        assertThat(batch.getErrorMessage()).isEqualTo("api timeout");
    }

    @Test
    @DisplayName("job 상태 변경 메서드는 SyncJob 상태와 집계값을 변경한다")
    void should_UpdateJobStatus() {
        // given
        Long jobId = 1L;
        SyncJob syncJob = SyncJob.start(SyncJobType.PROTECTING_REPORT_SYNC);
        when(syncJobRepository.findById(jobId)).thenReturn(Optional.of(syncJob));

        // when & then
        syncJobService.updateExpectedCount(jobId, 123);
        assertThat(syncJob.getTotalExpectedCount()).isEqualTo(123);

        syncJobService.markStagingCompleted(jobId);
        assertThat(syncJob.getStatus()).isEqualTo(SyncJobStatus.STAGING_COMPLETED);

        syncJobService.markMerging(jobId);
        assertThat(syncJob.getStatus()).isEqualTo(SyncJobStatus.MERGING);

        syncJobService.markSuccess(jobId, 120);
        assertThat(syncJob.getStatus()).isEqualTo(SyncJobStatus.SUCCESS);
        assertThat(syncJob.getTotalMergedCount()).isEqualTo(120);
        assertThat(syncJob.getFinishedAt()).isNotNull();
        assertThat(syncJob.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("validation 실패와 job 실패는 실패 상태와 에러 메시지를 기록한다")
    void should_RecordFailedStatus() {
        // given
        Long validationFailedJobId = 1L;
        SyncJob validationFailedJob = SyncJob.start(SyncJobType.PROTECTING_REPORT_SYNC);
        when(syncJobRepository.findById(validationFailedJobId)).thenReturn(Optional.of(validationFailedJob));

        Long failedJobId = 2L;
        SyncJob failedJob = SyncJob.start(SyncJobType.PROTECTING_REPORT_SYNC);
        when(syncJobRepository.findById(failedJobId)).thenReturn(Optional.of(failedJob));

        // when
        syncJobService.markValidationFailed(validationFailedJobId, "count mismatch");
        syncJobService.markFailed(failedJobId, new RuntimeException("merge failed"));

        // then
        assertThat(validationFailedJob.getStatus()).isEqualTo(SyncJobStatus.VALIDATION_FAILED);
        assertThat(validationFailedJob.getErrorMessage()).isEqualTo("count mismatch");
        assertThat(validationFailedJob.getFinishedAt()).isNotNull();

        assertThat(failedJob.getStatus()).isEqualTo(SyncJobStatus.FAILED);
        assertThat(failedJob.getErrorMessage()).isEqualTo("merge failed");
        assertThat(failedJob.getFinishedAt()).isNotNull();
    }
}
