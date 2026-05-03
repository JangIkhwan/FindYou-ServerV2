package com.kuit.findyou.domain.report.service.sync.staging;

import com.kuit.findyou.domain.report.model.sync.PublicAnimalStagingRow;
import com.kuit.findyou.domain.report.repository.sync.PublicAnimalStagingJdbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProtectingAnimalStagingWriter {

  private final PublicAnimalStagingJdbcRepository publicAnimalStagingRepository;

  @Transactional
  public int saveRows(List<PublicAnimalStagingRow> stagingRows) {
      publicAnimalStagingRepository.upsertAll(stagingRows);
      return stagingRows.size();
  }
}