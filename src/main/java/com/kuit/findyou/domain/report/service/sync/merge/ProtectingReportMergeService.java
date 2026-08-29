package com.kuit.findyou.domain.report.service.sync.merge;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProtectingReportMergeService {

    private static final int INSERT_CHUNK_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public int merge(Long syncJobId) {
        int existingCount = countExistingReports(syncJobId);
        updateExistingReports(syncJobId);
        updateExistingProtectingReports(syncJobId);

        int insertedCount = insertNewReports(syncJobId);

        replaceReportImages(syncJobId);

        softDeleteReportsMissingFromStaging(syncJobId);

        return existingCount + insertedCount;
    }

    private int countExistingReports(Long syncJobId) {
        String sql = """
                SELECT COUNT(*)
                FROM public_animal_staging s
                JOIN protecting_reports pr ON pr.notice_number = s.notice_number
                WHERE s.sync_job_id = ?
                """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, syncJobId);
        return count == null ? 0 : count;
    }

    private void updateExistingReports(Long syncJobId) {
        String sql = """
                UPDATE reports r
                JOIN protecting_reports pr ON pr.id = r.id
                JOIN public_animal_staging s ON s.notice_number = pr.notice_number
                SET r.breed = s.breed,
                    r.species = s.species,
                    r.date = s.happen_date,
                    r.address = s.address,
                    r.latitude = s.latitude,
                    r.longitude = s.longitude,
                    r.updated_at = CURRENT_TIMESTAMP
                WHERE s.sync_job_id = ?
                """;
        jdbcTemplate.update(sql, syncJobId);
    }

    private void updateExistingProtectingReports(Long syncJobId) {
        String sql = """
                UPDATE protecting_reports pr
                JOIN public_animal_staging s ON s.notice_number = pr.notice_number
                SET pr.sex = s.sex,
                    pr.age = s.age,
                    pr.weight = s.weight,
                    pr.fur_color = s.fur_color,
                    pr.neutering = s.neutering,
                    pr.significant = s.significant,
                    pr.found_location = s.found_location,
                    pr.notice_start_date = s.notice_start_date,
                    pr.notice_end_date = s.notice_end_date,
                    pr.care_name = s.care_name,
                    pr.care_tel = s.care_tel,
                    pr.authority = s.authority
                WHERE s.sync_job_id = ?
                """;
        jdbcTemplate.update(sql, syncJobId);
    }

    private int insertNewReports(Long syncJobId) {
        int insertedCount = 0;
        String lastNoticeNumber = null;

        while (true) {
            List<String> noticeNumbers = findNewNoticeNumbers(syncJobId, lastNoticeNumber, INSERT_CHUNK_SIZE);
            if (noticeNumbers.isEmpty()) {
                break;
            }

            for (String noticeNumber : noticeNumbers) {
                long reportId = insertReportFromStaging(syncJobId, noticeNumber);
                insertProtectingReportFromStaging(reportId, syncJobId, noticeNumber);
            }

            insertedCount += noticeNumbers.size();
            lastNoticeNumber = noticeNumbers.get(noticeNumbers.size() - 1);
        }

        return insertedCount;
    }

    private List<String> findNewNoticeNumbers(Long syncJobId, String lastNoticeNumber, int limit) {
        String sql = """
                SELECT s.notice_number
                FROM public_animal_staging s
                LEFT JOIN protecting_reports pr ON pr.notice_number = s.notice_number
                WHERE s.sync_job_id = ?
                  AND pr.id IS NULL
                  AND (? IS NULL OR s.notice_number > ?)
                ORDER BY s.notice_number
                LIMIT ?
                """;
        return jdbcTemplate.queryForList(sql, String.class, syncJobId, lastNoticeNumber, lastNoticeNumber, limit);
    }

    private long insertReportFromStaging(Long syncJobId, String noticeNumber) {
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
                SELECT s.breed,
                       s.species,
                       'PROTECTING',
                       s.happen_date,
                       s.address,
                       s.latitude,
                       s.longitude,
                       NULL,
                       'PROTECTING',
                       'Y',
                       CURRENT_TIMESTAMP,
                       CURRENT_TIMESTAMP
                FROM public_animal_staging s
                WHERE s.sync_job_id = ?
                  AND s.notice_number = ?
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, syncJobId);
            ps.setString(2, noticeNumber);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to retrieve generated report id. noticeNumber=" + noticeNumber);
        }
        return key.longValue();
    }

    private void insertProtectingReportFromStaging(long reportId, Long syncJobId, String noticeNumber) {
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
                SELECT ?,
                       s.sex,
                       s.age,
                       s.weight,
                       s.fur_color,
                       s.neutering,
                       s.significant,
                       s.found_location,
                       s.notice_number,
                       s.notice_start_date,
                       s.notice_end_date,
                       s.care_name,
                       s.care_tel,
                       s.authority
                FROM public_animal_staging s
                WHERE s.sync_job_id = ?
                  AND s.notice_number = ?
                """;

        int insertedCount = jdbcTemplate.update(sql, reportId, syncJobId, noticeNumber);
        if (insertedCount != 1) {
            throw new IllegalStateException("Failed to insert protecting report. noticeNumber=" + noticeNumber);
        }
    }

    private void replaceReportImages(Long syncJobId) {
        deactivateExistingReportImages(syncJobId);
        insertReportImagesFromStaging(syncJobId);
    }

    private void deactivateExistingReportImages(Long syncJobId) {
        String sql = """
                UPDATE report_images ri
                JOIN protecting_reports pr ON pr.id = ri.report_id
                JOIN public_animal_staging s ON s.notice_number = pr.notice_number
                SET ri.status = 'N',
                    ri.updated_at = CURRENT_TIMESTAMP
                WHERE s.sync_job_id = ?
                  AND ri.status = 'Y'
                """;
        jdbcTemplate.update(sql, syncJobId);
    }

    private void insertReportImagesFromStaging(Long syncJobId) {
        insertReportImagesFromStagingColumn(syncJobId, "image_url1");
        insertReportImagesFromStagingColumn(syncJobId, "image_url2");
    }

    private void insertReportImagesFromStagingColumn(Long syncJobId, String imageUrlColumn) {
        String sql = """
                INSERT INTO report_images (
                    image_url,
                    report_id,
                    status,
                    created_at,
                    updated_at
                )
                SELECT s.%s,
                       pr.id,
                       'Y',
                       CURRENT_TIMESTAMP,
                       CURRENT_TIMESTAMP
                FROM public_animal_staging s
                JOIN protecting_reports pr ON pr.notice_number = s.notice_number
                WHERE s.sync_job_id = ?
                  AND s.%s IS NOT NULL
                  AND TRIM(s.%s) <> ''
                """.formatted(imageUrlColumn, imageUrlColumn, imageUrlColumn);
        jdbcTemplate.update(sql, syncJobId);
    }

    private int softDeleteReportsMissingFromStaging(Long syncJobId) {
        String sql = """
                  UPDATE reports r
                  JOIN protecting_reports pr ON pr.id = r.id
                  SET r.status = 'N',
                      r.updated_at = CURRENT_TIMESTAMP
                  WHERE r.status = 'Y'
                    AND NOT EXISTS (
                        SELECT 1
                        FROM public_animal_staging s
                        WHERE s.sync_job_id = ?
                          AND s.notice_number = pr.notice_number
                )
                """;
        return jdbcTemplate.update(sql, syncJobId);
    }

    public void deleteStaging(Long syncJobId) {
        String sql = """
                  DELETE FROM public_animal_staging WHERE sync_job_id = ?
                """;

        jdbcTemplate.update(sql, syncJobId);
    }
}
