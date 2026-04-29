package com.kuit.findyou.domain.report.model.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "sync_job")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SyncJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", length = 100, nullable = false)
    private SyncJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private SyncJobStatus status;

    @Column(name = "total_expected_count")
    private Integer totalExpectedCount;

    @Column(name = "total_staged_count", nullable = false)
    private int totalStagedCount;

    @Column(name = "total_merged_count", nullable = false)
    private int totalMergedCount;

    @Column(name = "failed_batch_count", nullable = false)
    private int failedBatchCount;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private SyncJob(SyncJobType jobType) {
        this.jobType = jobType;
        this.status = SyncJobStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    public static SyncJob start(SyncJobType jobType) {
        return new SyncJob(jobType);
    }

    public void updateExpectedCount(Integer totalExpectedCount) {
        this.totalExpectedCount = totalExpectedCount;
    }

    public void addStagedCount(int stagedCount) {
        this.totalStagedCount += stagedCount;
    }

    public void addFailedBatchCount() {
        this.failedBatchCount++;
    }

    public void markStagingCompleted() {
        this.status = SyncJobStatus.STAGING_COMPLETED;
    }

    public void markMerging() {
        this.status = SyncJobStatus.MERGING;
    }

    public void markSuccess(int totalMergedCount) {
        this.status = SyncJobStatus.SUCCESS;
        this.totalMergedCount = totalMergedCount;
        this.finishedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    public void markValidationFailed(String errorMessage) {
        this.status = SyncJobStatus.VALIDATION_FAILED;
        this.finishedAt = LocalDateTime.now();
        this.errorMessage = truncateErrorMessage(errorMessage);
    }

    public void markFailed(String errorMessage) {
        this.status = SyncJobStatus.FAILED;
        this.finishedAt = LocalDateTime.now();
        this.errorMessage = truncateErrorMessage(errorMessage);
    }

    private String truncateErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.length() <= 1000) {
            return errorMessage;
        }
        return errorMessage.substring(0, 1000);
    }
}
