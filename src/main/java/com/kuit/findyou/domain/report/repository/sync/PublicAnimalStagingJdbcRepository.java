package com.kuit.findyou.domain.report.repository.sync;

import com.kuit.findyou.domain.report.model.sync.PublicAnimalStaging;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PublicAnimalStagingJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public int[][] upsertAll(List<PublicAnimalStaging> rows) {
        String sql = """
            INSERT INTO public_animal_staging (
                sync_job_id,
                batch_no,
                notice_number,
                species,
                breed,
                happen_date,
                address,
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
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                batch_no = VALUES(batch_no),
                species = VALUES(species),
                breed = VALUES(breed),
                happen_date = VALUES(happen_date),
                address = VALUES(address),
                sex = VALUES(sex),
                neutering = VALUES(neutering),
                age = VALUES(age),
                weight = VALUES(weight),
                fur_color = VALUES(fur_color),
                significant = VALUES(significant),
                found_location = VALUES(found_location),
                notice_start_date = VALUES(notice_start_date),
                notice_end_date = VALUES(notice_end_date),
                care_name = VALUES(care_name),
                care_tel = VALUES(care_tel),
                authority = VALUES(authority),
                image_url1 = VALUES(image_url1),
                image_url2 = VALUES(image_url2),
                raw_data = VALUES(raw_data),
                raw_hash = VALUES(raw_hash)
            """;

        return jdbcTemplate.batchUpdate(sql, rows, 500, (ps, row) -> {
            ps.setLong(1, row.getSyncJob().getId());
            ps.setInt(2, row.getBatchNo());
            ps.setString(3, row.getNoticeNumber());
            ps.setString(4, row.getSpecies());
            ps.setString(5, row.getBreed());
            ps.setObject(6, row.getHappenDate());
            ps.setString(7, row.getAddress());
            ps.setString(8, row.getSex());
            ps.setString(9, row.getNeutering());
            ps.setObject(10, row.getAge());
            ps.setObject(11, row.getWeight());
            ps.setString(12, row.getFurColor());
            ps.setString(13, row.getSignificant());
            ps.setString(14, row.getFoundLocation());
            ps.setObject(15, row.getNoticeStartDate());
            ps.setObject(16, row.getNoticeEndDate());
            ps.setString(17, row.getCareName());
            ps.setString(18, row.getCareTel());
            ps.setString(19, row.getAuthority());
            ps.setString(20, row.getImageUrl1());
            ps.setString(21, row.getImageUrl2());
            ps.setString(22, row.getRawData());
            ps.setString(23, row.getRawHash());
        });
    }

    public long countBySyncJobId(Long syncJobId) {
        String sql = """
                SELECT COUNT(*)
                FROM public_animal_staging
                WHERE sync_job_id = ?
                """;

        Long result = jdbcTemplate.queryForObject(sql, Long.class, syncJobId);
        return result != null ? result : 0L;
    }
}