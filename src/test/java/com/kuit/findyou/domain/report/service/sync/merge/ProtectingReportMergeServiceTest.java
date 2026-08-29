package com.kuit.findyou.domain.report.service.sync.merge;

import com.kuit.findyou.domain.report.model.ProtectingReport;
import com.kuit.findyou.domain.report.repository.ProtectingReportRepository;
import com.kuit.findyou.global.config.TestDatabaseConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({ProtectingReportMergeService.class, TestDatabaseConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProtectingReportMergeServiceTest {

    @Autowired
    ProtectingReportMergeService protectingReportMergeService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ProtectingReportRepository protectingReportRepository;

    @Autowired
    EntityManager entityManager;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.execute("TRUNCATE TABLE public_animal_staging");
        jdbcTemplate.execute("TRUNCATE TABLE sync_job_batch");
        jdbcTemplate.execute("TRUNCATE TABLE sync_job");
        jdbcTemplate.execute("TRUNCATE TABLE report_images");
        jdbcTemplate.execute("TRUNCATE TABLE protecting_reports");
        jdbcTemplate.execute("TRUNCATE TABLE reports");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    @Test
    @DisplayName("staging에만 있는 신규 공고를 운영 테이블에 추가한다")
    void should_InsertNewProtectingReport_When_StagingNoticeDoesNotExist() {
        // given
        long syncJobId = insertSyncJob();
        insertStaging(syncJobId, "NEW-1", "진돗개", "강아지", "서울시 강남구", "신규 특징");

        // when
        int mergedCount = protectingReportMergeService.merge(syncJobId);

        // then
        assertThat(mergedCount).isEqualTo(1);

        Map<String, Object> row = findProtectingReport("NEW-1");
        assertThat(row.get("breed")).isEqualTo("진돗개");
        assertThat(row.get("species")).isEqualTo("강아지");
        assertThat(row.get("address")).isEqualTo("서울시 강남구");
        assertThat(row.get("status")).isEqualTo("Y");
        assertThat(row.get("significant")).isEqualTo("신규 특징");
        assertThat(row.get("care_name")).isEqualTo("테스트보호소");
    }

    @Test
    @DisplayName("기존 공고가 staging 값으로 최신화된다")
    void should_UpdateExistingProtectingReport_When_StagingNoticeAlreadyExists() {
        // given
        long syncJobId = insertSyncJob();
        insertExistingProtectingReport("EXIST-1", "이전품종", "강아지", "이전주소", "이전 특징", "Y");
        insertStaging(syncJobId, "EXIST-1", "최신품종", "고양이", "최신주소", "최신 특징");

        // when
        int mergedCount = protectingReportMergeService.merge(syncJobId);

        // then
        assertThat(mergedCount).isEqualTo(1);

        Map<String, Object> row = findProtectingReport("EXIST-1");
        assertThat(row.get("breed")).isEqualTo("최신품종");
        assertThat(row.get("species")).isEqualTo("고양이");
        assertThat(row.get("address")).isEqualTo("최신주소");
        assertThat(row.get("status")).isEqualTo("Y");
        assertThat(row.get("significant")).isEqualTo("최신 특징");
        assertThat(row.get("found_location")).isEqualTo("테스트 발견장소");
    }

    @Test
    @DisplayName("운영 테이블에만 있는 공고는 soft delete 처리한다")
    void should_SoftDeleteProtectingReport_When_NoticeMissingFromStaging() {
        // given
        long syncJobId = insertSyncJob();
        insertExistingProtectingReport("OLD-1", "이전품종", "강아지", "이전주소", "이전 특징", "Y");

        // when
        int mergedCount = protectingReportMergeService.merge(syncJobId);

        // then
        assertThat(mergedCount).isZero();

        Map<String, Object> row = findProtectingReport("OLD-1");
        assertThat(row.get("status")).isEqualTo("N");
        assertThat(row.get("notice_number")).isEqualTo("OLD-1");
    }

    @Test
    @DisplayName("staging에서 사라진 보호 공고를 soft delete한 후 active 이미지도 soft delete한다")
    void should_SoftDeleteActiveImages_When_ProtectingReportMissingFromStaging() {
        // given
        long syncJobId = insertSyncJob();
        long reportId = insertExistingProtectingReport("OLD-IMG-1", "이전품종", "강아지", "이전주소", "이전 특징", "Y");
        insertReportImage(reportId, "https://old.example.com/1.jpg", "Y");
        insertReportImage(reportId, "https://old.example.com/2.jpg", "Y");
        insertReportImage(reportId, "https://old.example.com/already-inactive.jpg", "N");

        // when
        int mergedCount = protectingReportMergeService.merge(syncJobId);

        // then
        assertThat(mergedCount).isZero();

        Map<String, Object> row = findProtectingReport("OLD-IMG-1");
        assertThat(row.get("status")).isEqualTo("N");
        assertThat(findActiveReportImageUrls(reportId)).isEmpty();
        assertThat(findInactiveReportImageUrls(reportId))
                .containsExactlyInAnyOrder(
                        "https://old.example.com/1.jpg",
                        "https://old.example.com/2.jpg",
                        "https://old.example.com/already-inactive.jpg"
                );

        entityManager.flush();
        entityManager.clear();

        ProtectingReport foundReport = protectingReportRepository.findWithImagesById(reportId).orElseThrow();
        assertThat(foundReport.getReportImages()).isEmpty();
    }

    @Test
    @DisplayName("기존 보호 공고 이미지를 staging 이미지로 교체하고 active 이미지만 연관관계로 조회한다")
    void should_ReplaceExistingProtectingReportImages_And_LoadOnlyActiveImages() {
        // given
        long syncJobId = insertSyncJob();
        long reportId = insertExistingProtectingReport("EXIST-IMG-1", "이전품종", "강아지", "이전주소", "이전 특징", "Y");
        insertReportImage(reportId, "https://old.example.com/1.jpg", "Y");
        insertReportImage(reportId, "https://old.example.com/2.jpg", "Y");
        insertStaging(
                syncJobId,
                "EXIST-IMG-1",
                "최신품종",
                "강아지",
                "최신주소",
                "최신 특징",
                "https://new.example.com/1.jpg",
                "https://new.example.com/2.jpg"
        );

        // when
        int mergedCount = protectingReportMergeService.merge(syncJobId);

        // then
        assertThat(mergedCount).isEqualTo(1);
        assertThat(countReportImages(reportId)).isEqualTo(4);
        assertThat(findActiveReportImageUrls(reportId))
                .containsExactlyInAnyOrder("https://new.example.com/1.jpg", "https://new.example.com/2.jpg");
        assertThat(findInactiveReportImageUrls(reportId))
                .containsExactlyInAnyOrder("https://old.example.com/1.jpg", "https://old.example.com/2.jpg");

        entityManager.flush();
        entityManager.clear();

        ProtectingReport foundReport = protectingReportRepository.findWithImagesById(reportId).orElseThrow();
        assertThat(foundReport.getReportImagesUrlList())
                .containsExactlyInAnyOrder("https://new.example.com/1.jpg", "https://new.example.com/2.jpg");
    }

    @Test
    @DisplayName("공공 API 이미지가 null 또는 빈 문자열이면 기존 보호 공고의 active 이미지가 없어진다")
    void should_RemoveActiveImages_When_StagingImagesAreNullOrBlank() {
        // given
        long syncJobId = insertSyncJob();
        long reportId = insertExistingProtectingReport("EXIST-IMG-EMPTY", "이전품종", "강아지", "이전주소", "이전 특징", "Y");
        insertReportImage(reportId, "https://old.example.com/1.jpg", "Y");
        insertReportImage(reportId, "https://old.example.com/2.jpg", "Y");
        insertStaging(
                syncJobId,
                "EXIST-IMG-EMPTY",
                "최신품종",
                "강아지",
                "최신주소",
                "최신 특징",
                null,
                "   "
        );

        // when
        int mergedCount = protectingReportMergeService.merge(syncJobId);

        // then
        assertThat(mergedCount).isEqualTo(1);
        assertThat(countReportImages(reportId)).isEqualTo(2);
        assertThat(findActiveReportImageUrls(reportId)).isEmpty();
        assertThat(findInactiveReportImageUrls(reportId))
                .containsExactlyInAnyOrder("https://old.example.com/1.jpg", "https://old.example.com/2.jpg");

        entityManager.flush();
        entityManager.clear();

        ProtectingReport foundReport = protectingReportRepository.findWithImagesById(reportId).orElseThrow();
        assertThat(foundReport.getReportImages()).isEmpty();
    }
    
    @Test
    @DisplayName("jobId에 해당하는 스테이징 데이터를 삭제한다")
    void should_DeleteStagingSuccess(){
        // given
        long prevSyncJobId = insertSyncJob();
        long prevStagingId = insertStaging(prevSyncJobId, "EXIST-1", "품종", "고양이", "주소", "특징");

        long syncJobId = insertSyncJob();
        long stagingId = insertStaging(syncJobId, "EXIST-1", "품종", "고양이", "주소", "특징");

        // when
        protectingReportMergeService.deleteStaging(syncJobId);
        
        // then
        assertThat(existsProtectingReportStaging(prevStagingId)).isTrue();
        assertThat(existsProtectingReportStaging(stagingId)).isFalse();
    }

    private long insertSyncJob() {
        String sql = """
                INSERT INTO sync_job (
                    job_type,
                    status,
                    total_staged_count,
                    total_merged_count,
                    failed_batch_no,
                    started_at,
                    created_at,
                    updated_at
                )
                VALUES ('PROTECTING_REPORT_SYNC', 'MERGING', 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS), keyHolder);
        return keyHolder.getKey().longValue();
    }

    private long insertStaging(long syncJobId, String noticeNumber, String breed, String species, String address, String significant) {
        return insertStaging(syncJobId, noticeNumber, breed, species, address, significant, null, null);
    }

    private long insertStaging(long syncJobId, String noticeNumber, String breed, String species, String address,
                               String significant, String imageUrl1, String imageUrl2) {
        String sql = """
                INSERT INTO public_animal_staging (
                    sync_job_id,
                    batch_no,
                    notice_number,
                    species,
                    breed,
                    happen_date,
                    address,
                    latitude,
                    longitude,
                    sex,
                    neutering,
                    age,
                    weight,
                    fur_color,
                    significant,
                    found_location,
                    notice_start_date,
                    notice_end_date,
                    care_name,
                    care_tel,
                    authority,
                    image_url1,
                    image_url2,
                    raw_data,
                    raw_hash
                )
                VALUES (?, 1, ?, ?, ?, '2026-05-01', ?, 37.123456, 127.123456,
                        'M', 'Y', '3', '5', '갈색', ?, '테스트 발견장소',
                        '2026-05-01', '2026-05-10', '테스트보호소', '02-123-4567',
                        '테스트구청', ?, ?, '{}', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa')
                """;
        
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, syncJobId);
            ps.setString(2, noticeNumber);
            ps.setString(3, species);
            ps.setString(4, breed);
            ps.setString(5, address);
            ps.setString(6, significant);
            ps.setString(7, imageUrl1);
            ps.setString(8, imageUrl2);
            return ps;
        }, keyHolder);

        return keyHolder.getKey().longValue();
    }

    private long insertExistingProtectingReport(String noticeNumber, String breed, String species,
                                                String address, String significant, String status) {
        long reportId = insertReport(breed, species, address, status);

        String sql = """
                INSERT INTO protecting_reports (
                    id,
                    sex,
                    age,
                    weight,
                    fur_color,
                    neutering,
                    significant,
                    found_location,
                    notice_number,
                    notice_start_date,
                    notice_end_date,
                    care_name,
                    care_tel,
                    authority
                )
                VALUES (?, 'F', '2', '4', '흰색', 'N', ?, '이전 발견장소', ?,
                        '2026-04-01', '2026-04-10', '이전보호소', '02-000-0000', '이전구청')
                """;

        jdbcTemplate.update(sql, reportId, significant, noticeNumber);
        return reportId;
    }

    private long insertReport(String breed, String species, String address, String status) {
        String sql = """
                INSERT INTO reports (
                    breed,
                    species,
                    tag,
                    date,
                    address,
                    latitude,
                    longitude,
                    user_id,
                    dtype,
                    status,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, 'PROTECTING', '2026-04-01', ?, 37.000000, 127.000000,
                        NULL, 'PROTECTING', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, breed);
            ps.setString(2, species);
            ps.setString(3, address);
            ps.setString(4, status);
            return ps;
        }, keyHolder);

        return keyHolder.getKey().longValue();
    }

    private void insertReportImage(long reportId, String imageUrl, String status) {
        String sql = """
                INSERT INTO report_images (
                    image_url,
                    report_id,
                    status,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """;
        jdbcTemplate.update(sql, imageUrl, reportId, status);
    }

    private Map<String, Object> findProtectingReport(String noticeNumber) {
        String sql = """
                SELECT r.breed,
                       r.species,
                       r.date,
                       r.address,
                       r.latitude,
                       r.longitude,
                       r.status,
                       pr.sex,
                       pr.age,
                       pr.weight,
                       pr.fur_color,
                       pr.neutering,
                       pr.significant,
                       pr.found_location,
                       pr.notice_number,
                       pr.notice_start_date,
                       pr.notice_end_date,
                       pr.care_name,
                       pr.care_tel,
                       pr.authority
                FROM protecting_reports pr
                JOIN reports r ON r.id = pr.id
                WHERE pr.notice_number = ?
                """;

        return jdbcTemplate.queryForMap(sql, noticeNumber);
    }

    public boolean existsProtectingReportStaging(Long id) {
        String sql = """
            SELECT EXISTS (
                SELECT 1
                FROM public_animal_staging
                WHERE id = ?
            )
        """;

        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, id));
    }

    private int countReportImages(long reportId) {
        String sql = """
                SELECT COUNT(*)
                FROM report_images
                WHERE report_id = ?
                """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, reportId);
        return count == null ? 0 : count;
    }

    private List<String> findActiveReportImageUrls(long reportId) {
        return findReportImageUrlsByStatus(reportId, "Y");
    }

    private List<String> findInactiveReportImageUrls(long reportId) {
        return findReportImageUrlsByStatus(reportId, "N");
    }

    private List<String> findReportImageUrlsByStatus(long reportId, String status) {
        String sql = """
                SELECT image_url
                FROM report_images
                WHERE report_id = ?
                  AND status = ?
                ORDER BY image_url
                """;
        return jdbcTemplate.queryForList(sql, String.class, reportId, status);
    }
}
