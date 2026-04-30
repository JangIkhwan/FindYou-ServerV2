package com.kuit.findyou.domain.report.repository.sync;

import com.kuit.findyou.domain.report.model.sync.SyncJob;
import com.kuit.findyou.domain.report.model.sync.SyncJobStatus;
import com.kuit.findyou.domain.report.model.sync.SyncJobType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface SyncJobRepository extends JpaRepository<SyncJob, Long> {

    boolean existsByJobTypeAndStatusIn(SyncJobType jobType, Collection<SyncJobStatus> statuses);
}
