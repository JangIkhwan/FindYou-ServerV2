package com.kuit.findyou.domain.report.service.sync;

import com.kuit.findyou.domain.report.model.sync.SyncJob;
import com.kuit.findyou.domain.report.model.sync.SyncJobType;
import com.kuit.findyou.domain.report.service.sync.job.SyncJobService;
import com.kuit.findyou.domain.report.service.sync.staging.ProtectingAnimalStagingService;
import com.kuit.findyou.domain.report.service.sync.staging.StagingValidationResult;
import com.kuit.findyou.global.external.client.ProtectingAnimalApiClient;
import com.kuit.findyou.global.external.dto.ProtectingAnimalPageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class ProtectingReportSyncServiceV2Impl implements ProtectingReportSyncService{

    private static final int PAGE_SIZE = 500;
    private final ProtectingAnimalApiClient protectingAnimalApiClient;
    private final SyncJobService syncJobService;
    private final ProtectingAnimalStagingService stagingService;

    @Override
    public void syncProtectingReports() {
        long startMs = System.currentTimeMillis();

        SyncJob job = syncJobService.startJob(SyncJobType.PROTECTING_REPORT_SYNC);

        log.info("공공데이터 동기화 잡 {} : 시작", job.getId());

        try {
            int pageNo = 1;
            Integer totalExpectedCount = null;

            while (true) {
                ProtectingAnimalPageResult pageResult = null;

                try{
                    pageResult = protectingAnimalApiClient.fetchPage(pageNo, PAGE_SIZE);

                    if (totalExpectedCount == null) {
                        totalExpectedCount = pageResult.totalCount();
                        syncJobService.updateExpectedCount(job.getId(), totalExpectedCount);

                        log.info("공공데이터 동기화 잡 {} : totalExpectedCount = {}", job.getId(), totalExpectedCount);
                    }

                    if (pageResult.items().isEmpty()) {
                        break;
                    }

                    int stagedCount = stagingService.savePage(job.getId(), pageNo, pageResult);
                    syncJobService.recordBatchSuccess(job.getId(), pageNo, pageNo, pageResult.items().size(), stagedCount);

                    log.info("공공데이터 동기화 잡 {} : API에서 전체 {} 페이지 중 {} 번째 페이지를 스테이징에 저장 완료 ", job.getId(), (int) Math.ceil((double) totalExpectedCount / PAGE_SIZE), pageNo);

                    pageNo++;
                }
                catch (Exception e){
                    log.error("공공데이터 동기화 잡 {} : pageNo = {} 에서 에러 발생, 에러 사유 = {}", job.getId(), pageNo, e.getMessage());

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

            StagingValidationResult validation = stagingService.validate(job.getId());
            if (!validation.valid()) {
                log.warn("공공데이터 동기화 잡 {} : 스테이징 데이터 검증 실패 {} ", job.getId(), validation.message());
                syncJobService.markValidationFailed(job.getId(), validation.message());
                return;
            }

            syncJobService.markMerging(job.getId());

            // TODO : merge의 실행 시간이 길어진다면 그 전에 leaseUntil을 더 길게 설정해야할 수도 있다
            stagingService.merge(job.getId());

            long endMs = System.currentTimeMillis();

            log.info("공공데이터 동기화 잡 {} : 성공. 소요 시간 = {} ms", job.getId(), endMs - startMs);

        } catch (Exception e) {
            log.error("공공데이터 동기화 잡 {} : 실패 사유 = {}", job.getId(), e.getMessage());

            syncJobService.markFailed(job.getId(), e);
        }
    }
}
