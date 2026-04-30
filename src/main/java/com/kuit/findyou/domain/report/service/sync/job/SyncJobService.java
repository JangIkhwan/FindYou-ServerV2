package com.kuit.findyou.domain.report.service.sync.job;

import com.kuit.findyou.domain.report.model.sync.SyncJob;
import com.kuit.findyou.domain.report.model.sync.SyncJobBatch;
import com.kuit.findyou.domain.report.model.sync.SyncJobStatus;
import com.kuit.findyou.domain.report.model.sync.SyncJobType;
import com.kuit.findyou.domain.report.repository.sync.SyncJobBatchRepository;
import com.kuit.findyou.domain.report.repository.sync.SyncJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SyncJobService {

    private static final Set<SyncJobStatus> ACTIVE_STATUSES = EnumSet.of(
            SyncJobStatus.RUNNING,
            SyncJobStatus.STAGING_COMPLETED,
            SyncJobStatus.MERGING
    );

    private final SyncJobRepository syncJobRepository;
    private final SyncJobBatchRepository syncJobBatchRepository;

    @Transactional
    public SyncJob startJob(SyncJobType jobType) {
        if (hasActiveJob(jobType)) {
            throw new IllegalStateException("Active sync job already exists. jobType=" + jobType);
        }

        return syncJobRepository.save(SyncJob.start(jobType));
    }

    @Transactional(readOnly = true)
    public boolean hasActiveJob(SyncJobType jobType) {
        return syncJobRepository.existsByJobTypeAndStatusIn(jobType, ACTIVE_STATUSES);
    }

    @Transactional
    public void updateExpectedCount(Long jobId, Integer totalExpectedCount) {
        SyncJob syncJob = getSyncJob(jobId);
        syncJob.updateExpectedCount(totalExpectedCount);
    }

    @Transactional
    public void recordBatchSuccess(Long jobId, int batchNo, Integer requestPageNo, int requestedCount, int stagedCount) {
        SyncJob syncJob = getSyncJob(jobId);
        SyncJobBatch batch = SyncJobBatch.of(syncJob, batchNo, requestPageNo, requestedCount);
        batch.markSuccess(stagedCount);

        syncJob.addStagedCount(stagedCount);
        syncJobBatchRepository.save(batch);
    }

    @Transactional
    public void recordBatchFailure(Long jobId, int batchNo, Integer requestPageNo, int requestedCount, String errorMessage) {
        SyncJob syncJob = getSyncJob(jobId);
        SyncJobBatch batch = SyncJobBatch.of(syncJob, batchNo, requestPageNo, requestedCount);
        batch.markFailed(errorMessage);

        syncJob.addFailedBatchCount();
        syncJobBatchRepository.save(batch);
    }

    @Transactional
    public void markStagingCompleted(Long jobId) {
        SyncJob syncJob = getSyncJob(jobId);
        syncJob.markStagingCompleted();
    }

    @Transactional
    public void markMerging(Long jobId) {
        SyncJob syncJob = getSyncJob(jobId);
        syncJob.markMerging();
    }

    @Transactional
    public void markSuccess(Long jobId, int totalMergedCount) {
        SyncJob syncJob = getSyncJob(jobId);
        syncJob.markSuccess(totalMergedCount);
    }

    @Transactional
    public void markValidationFailed(Long jobId, String errorMessage) {
        SyncJob syncJob = getSyncJob(jobId);
        syncJob.markValidationFailed(errorMessage);
    }

    @Transactional
    public void markFailed(Long jobId, Throwable throwable) {
        markFailed(jobId, throwable == null ? null : throwable.getMessage());
    }

    @Transactional
    public void markFailed(Long jobId, String errorMessage) {
        SyncJob syncJob = getSyncJob(jobId);
        syncJob.markFailed(errorMessage);
    }

    private SyncJob getSyncJob(Long jobId) {
        return syncJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("SyncJob not found. jobId=" + jobId));
    }
}
