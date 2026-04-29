package com.kuit.findyou.domain.report.model.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "sync_job_batch",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sync_job_batch_job_batch_no", columnNames = {"sync_job_id", "batch_no"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SyncJobBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sync_job_id", nullable = false)
    private SyncJob syncJob;

    @Column(name = "batch_no", nullable = false)
    private int batchNo;

    @Column(name = "request_page_no")
    private Integer requestPageNo;

    @Column(name = "requested_count", nullable = false)
    private int requestedCount;

    @Column(name = "staged_count", nullable = false)
    private int stagedCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private SyncJobBatchStatus status;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private SyncJobBatch(SyncJob syncJob, int batchNo, Integer requestPageNo, int requestedCount) {
        this.syncJob = syncJob;
        this.batchNo = batchNo;
        this.requestPageNo = requestPageNo;
        this.requestedCount = requestedCount;
        this.startedAt = LocalDateTime.now();
    }

    public static SyncJobBatch start(SyncJob syncJob, int batchNo, Integer requestPageNo, int requestedCount) {
        return new SyncJobBatch(syncJob, batchNo, requestPageNo, requestedCount);
    }

    public void markSuccess(int stagedCount) {
        this.status = SyncJobBatchStatus.SUCCESS;
        this.stagedCount = stagedCount;
        this.finishedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = SyncJobBatchStatus.FAILED;
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
