package com.kuit.findyou.domain.report.repository.sync;

import com.kuit.findyou.domain.report.model.sync.PublicAnimalStaging;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PublicAnimalStagingRepository extends JpaRepository<PublicAnimalStaging, Long> {

    long countBySyncJobId(Long syncJobId);
}
