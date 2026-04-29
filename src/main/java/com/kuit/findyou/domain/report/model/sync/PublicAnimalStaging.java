package com.kuit.findyou.domain.report.model.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "public_animal_staging",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_public_animal_staging_job_public", columnNames = {"sync_job_id", "notice_number"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PublicAnimalStaging {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sync_job_id", nullable = false)
    private SyncJob syncJob;

    @Column(name = "batch_no", nullable = false)
    private int batchNo;

    @Column(name = "notice_number", length = 30, nullable = false)
    private String noticeNumber;

    @Column(name = "species", length = 100)
    private String species;

    @Column(name = "breed", length = 20)
    private String breed;

    @Column(name = "happen_date")
    private LocalDate happenDate;

    @Column(name = "address", length = 200)
    private String address;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "sex", columnDefinition = "CHAR(1)")
    private String sex;

    @Column(name = "neutering", columnDefinition = "CHAR(1)")
    private String neutering;

    @Column(name = "age", length = 10)
    private String age;

    @Column(name = "weight", length = 10)
    private String weight;

    @Column(name = "fur_color", length = 100)
    private String furColor;

    @Column(name = "significant", length = 255)
    private String significant;

    @Column(name = "found_location", length = 255)
    private String foundLocation;

    @Column(name = "notice_start_date")
    private LocalDate noticeStartDate;

    @Column(name = "notice_end_date")
    private LocalDate noticeEndDate;

    @Column(name = "care_name", length = 50)
    private String careName;

    @Column(name = "care_tel", length = 14)
    private String careTel;

    @Column(name = "authority", length = 50)
    private String authority;

    @Column(name = "image_url1", length = 2083)
    private String imageUrl1;

    @Column(name = "image_url2", length = 2083)
    private String imageUrl2;

    @Column(name = "raw_data", columnDefinition = "JSON", nullable = false)
    private String rawData;

    @Column(name = "raw_hash", length = 64, nullable = false)
    private String rawHash;

    @CreationTimestamp
    @Column(name = "staged_at", nullable = false, updatable = false)
    private LocalDateTime stagedAt;

    @Builder
    private PublicAnimalStaging(SyncJob syncJob, int batchNo, String noticeNumber, String species, String breed,
                                LocalDate happenDate, String address, BigDecimal latitude, BigDecimal longitude,
                                String sex, String neutering, String age, String weight, String furColor,
                                String significant, String foundLocation, LocalDate noticeStartDate,
                                LocalDate noticeEndDate, String careName, String careTel, String authority,
                                String imageUrl1, String imageUrl2, String rawData, String rawHash) {
        this.syncJob = syncJob;
        this.batchNo = batchNo;
        this.noticeNumber = noticeNumber;
        this.species = species;
        this.breed = breed;
        this.happenDate = happenDate;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.sex = sex;
        this.neutering = neutering;
        this.age = age;
        this.weight = weight;
        this.furColor = furColor;
        this.significant = significant;
        this.foundLocation = foundLocation;
        this.noticeStartDate = noticeStartDate;
        this.noticeEndDate = noticeEndDate;
        this.careName = careName;
        this.careTel = careTel;
        this.authority = authority;
        this.imageUrl1 = imageUrl1;
        this.imageUrl2 = imageUrl2;
        this.rawData = rawData;
        this.rawHash = rawHash;
    }
}
