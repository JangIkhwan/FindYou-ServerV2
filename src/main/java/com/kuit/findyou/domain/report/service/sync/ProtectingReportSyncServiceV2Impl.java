package com.kuit.findyou.domain.report.service.sync;

import com.kuit.findyou.domain.report.model.sync.SyncJob;
import com.kuit.findyou.domain.report.model.sync.SyncJobType;
import com.kuit.findyou.domain.report.service.sync.job.SyncJobService;
import com.kuit.findyou.domain.report.service.sync.merge.ProtectingReportMergeService;
import com.kuit.findyou.domain.report.service.sync.staging.ProtectingAnimalStagingService;
import com.kuit.findyou.global.common.exception.CustomException;
import com.kuit.findyou.global.external.client.ProtectingAnimalApiClient;
import com.kuit.findyou.global.external.dto.ProtectingAnimalPageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static com.kuit.findyou.global.common.response.status.BaseExceptionResponseStatus.PROTECTING_REPORT_SYNC_FAILED;

@Slf4j
@RequiredArgsConstructor
//@Service
public class ProtectingReportSyncServiceV2Impl implements ProtectingReportSyncService{

    private final ProtectingAnimalApiClient protectingAnimalApiClient;
    private final SyncJobService syncJobService;
    private final ProtectingAnimalStagingService stagingService;
    private final ProtectingReportMergeService mergeService;

    @Override
    public void syncProtectingReports() {
        SyncJob job = syncJobService.startJob(SyncJobType.PROTECTING_REPORT_SYNC);

        try {
            int pageNo = 1;
            Integer totalExpectedCount = null;

            while (true) {
                ProtectingAnimalPageResult pageResult = null;

                try{
                    pageResult = protectingAnimalApiClient.fetchPage(pageNo);

                    if (totalExpectedCount == null) {
                        totalExpectedCount = pageResult.totalCount();
                        syncJobService.updateExpectedCount(job.getId(), totalExpectedCount);
                    }

                    if (pageResult.items().isEmpty()) {
                        break;
                    }

                    int stagedCount = stagingService.savePage(job.getId(), pageNo, pageResult);
                    syncJobService.recordBatchSuccess(job.getId(), pageNo, pageNo, pageResult.items().size(), stagedCount);

                    pageNo++;
                }
                catch (Exception e){
                    syncJobService.recordBatchFailure(
                            job.getId(),
                            pageNo,
                            pageNo,
                            pageResult == null ? 0 : pageResult.items().size(),
                            e.getMessage()
                    );
                    throw e;
                }
            }

            syncJobService.markStagingCompleted(job.getId());

            StagingValidationResult validation = stagingService.validate(job.getId(), totalExpectedCount);
            if (!validation.valid()) {
                syncJobService.markValidationFailed(job.getId(), validation.message());
                return;
            }

            syncJobService.markMerging(job.getId());

            int mergedCount = mergeService.merge(job.getId());
            syncJobService.markSuccess(job.getId(), mergedCount);

        } catch (Exception e) {
            syncJobService.markFailed(job.getId(), e);
            throw new CustomException(PROTECTING_REPORT_SYNC_FAILED);
        }
    }

}
