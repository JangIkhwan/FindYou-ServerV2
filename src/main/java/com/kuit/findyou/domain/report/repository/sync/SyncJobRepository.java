package com.kuit.findyou.domain.report.repository.sync;

import com.kuit.findyou.domain.report.model.sync.SyncJob;
import com.kuit.findyou.domain.report.model.sync.SyncJobStatus;
import com.kuit.findyou.domain.report.model.sync.SyncJobType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;

@Repository
public interface SyncJobRepository extends JpaRepository<SyncJob, Long> {

    boolean existsByJobTypeAndStatusInAndLeaseUntilAfter(
            SyncJobType jobType,
            Collection<SyncJobStatus> statuses,
            LocalDateTime now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE SyncJob sj
            SET sj.status = :expiredStatus,
                sj.finishedAt = :now,
                sj.errorMessage = :errorMessage
            WHERE sj.jobType = :jobType
              AND sj.status IN :activeStatuses
              AND (sj.leaseUntil IS NULL OR sj.leaseUntil <= :now)
            """)
    int expireActiveJobs(
            @Param("jobType") SyncJobType jobType,
            @Param("activeStatuses") Collection<SyncJobStatus> activeStatuses,
            @Param("expiredStatus") SyncJobStatus expiredStatus,
            @Param("now") LocalDateTime now,
            @Param("errorMessage") String errorMessage
    );
}
