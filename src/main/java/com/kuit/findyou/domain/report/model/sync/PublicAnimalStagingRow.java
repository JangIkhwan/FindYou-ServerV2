package com.kuit.findyou.domain.report.model.sync;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record PublicAnimalStagingRow(
            Long syncJobId,
            int batchNo,
            String noticeNumber,
            String species,
            String breed,
            LocalDate happenDate,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            String sex,
            String neutering,
            String age,
            String weight,
            String furColor,
            String significant,
            String foundLocation,
            LocalDate noticeStartDate,
            LocalDate noticeEndDate,
            String careName,
            String careTel,
            String authority,
            String imageUrl1,
            String imageUrl2,
            String rawData,
            String rawHash
    ) {}