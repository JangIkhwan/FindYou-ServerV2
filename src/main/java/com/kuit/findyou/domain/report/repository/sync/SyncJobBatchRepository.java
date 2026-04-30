package com.kuit.findyou.domain.report.repository.sync;

import com.kuit.findyou.domain.report.model.sync.SyncJobBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SyncJobBatchRepository extends JpaRepository<SyncJobBatch, Long> {
}
