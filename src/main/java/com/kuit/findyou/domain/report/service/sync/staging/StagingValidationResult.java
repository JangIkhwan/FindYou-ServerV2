package com.kuit.findyou.domain.report.service.sync.staging;

public record StagingValidationResult(
        boolean valid,
        String message
) {

    public static StagingValidationResult success() {
        return new StagingValidationResult(true, null);
    }

    public static StagingValidationResult failure(String message) {
        return new StagingValidationResult(false, message);
    }
}
