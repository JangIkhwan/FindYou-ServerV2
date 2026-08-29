package com.kuit.findyou.domain.report.model.sync;

public enum SyncJobStatus {
    RUNNING,
    STAGING_COMPLETED,
    MERGING,
    SUCCESS,
    FAILED,
    VALIDATION_FAILED,
    EXPIRED
}
