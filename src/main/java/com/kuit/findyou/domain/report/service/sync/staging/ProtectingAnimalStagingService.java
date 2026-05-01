package com.kuit.findyou.domain.report.service.sync.staging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuit.findyou.domain.report.model.sync.PublicAnimalStaging;
import com.kuit.findyou.domain.report.model.sync.SyncJob;
import com.kuit.findyou.domain.report.repository.sync.PublicAnimalStagingRepository;
import com.kuit.findyou.domain.report.repository.sync.SyncJobRepository;
import com.kuit.findyou.global.external.dto.ProtectingAnimalItemDTO;
import com.kuit.findyou.global.external.dto.ProtectingAnimalPageResult;
import com.kuit.findyou.global.external.util.ProtectingAnimalParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProtectingAnimalStagingService {

    private static final String DEFAULT_SIGNIFICANT = "미등록";

    private final PublicAnimalStagingRepository publicAnimalStagingRepository;
    private final SyncJobRepository syncJobRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public int savePage(Long syncJobId, int batchNo, ProtectingAnimalPageResult pageResult) {
        SyncJob syncJob = syncJobRepository.getReferenceById(syncJobId);

        List<PublicAnimalStaging> stagingRows = pageResult.items().stream()
                .map(item -> toStaging(syncJob, batchNo, item))
                .toList();

        publicAnimalStagingRepository.saveAll(stagingRows);
        publicAnimalStagingRepository.flush();

        return stagingRows.size();
    }

    private PublicAnimalStaging toStaging(SyncJob syncJob, int batchNo, ProtectingAnimalItemDTO item) {
        String rawData = toRawData(item);

        return PublicAnimalStaging.builder()
                .syncJob(syncJob)
                .batchNo(batchNo)
                .noticeNumber(requiredNoticeNumber(item.noticeNo()))
                .species(ProtectingAnimalParser.parseSpecies(item.upKindNm()))
                .breed(ProtectingAnimalParser.trimOrNull(item.kindNm()))
                .happenDate(ProtectingAnimalParser.parseDate(item.happenDt()))
                .address(ProtectingAnimalParser.parseAddress(item.careAddr()))
                .latitude(null)
                .longitude(null)
                .sex(ProtectingAnimalParser.parseSex(item.sexCd()).name())
                .neutering(ProtectingAnimalParser.parseNeutering(item.neuterYn()).name())
                .age(ProtectingAnimalParser.parseAge(item.age()))
                .weight(ProtectingAnimalParser.parseWeight(item.weight()))
                .furColor(ProtectingAnimalParser.parseColor(item.colorCd()))
                .significant(parseSignificant(item.specialMark()))
                .foundLocation(ProtectingAnimalParser.trimOrNull(item.happenPlace()))
                .noticeStartDate(ProtectingAnimalParser.parseDate(item.noticeSdt()))
                .noticeEndDate(ProtectingAnimalParser.parseDate(item.noticeEdt()))
                .careName(ProtectingAnimalParser.trimOrNull(item.careNm()))
                .careTel(ProtectingAnimalParser.trimOrNull(item.careTel()))
                .authority(ProtectingAnimalParser.trimOrNull(item.orgNm()))
                .imageUrl1(ProtectingAnimalParser.trimOrNull(item.popfile1()))
                .imageUrl2(ProtectingAnimalParser.trimOrNull(item.popfile2()))
                .rawData(rawData)
                .rawHash(sha256(rawData))
                .build();
    }

    private String requiredNoticeNumber(String noticeNumber) {
        String normalized = ProtectingAnimalParser.trimOrNull(noticeNumber);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("noticeNumber is required for staging");
        }
        return normalized;
    }

    private String parseSignificant(String significant) {
        if (significant == null || significant.isBlank()) {
            return DEFAULT_SIGNIFICANT;
        }
        return significant.trim();
    }

    private String toRawData(ProtectingAnimalItemDTO item) {
        try {
            return objectMapper.writeValueAsString(item);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize protecting animal item", e);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}
