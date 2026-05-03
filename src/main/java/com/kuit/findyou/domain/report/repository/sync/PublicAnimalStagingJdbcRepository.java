package com.kuit.findyou.domain.report.repository.sync;

import com.kuit.findyou.domain.report.model.sync.PublicAnimalStagingRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PublicAnimalStagingJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public int[][] upsertAll(List<PublicAnimalStagingRow> rows) {
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
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                batch_no = VALUES(batch_no),
                species = VALUES(species),
                breed = VALUES(breed),
                happen_date = VALUES(happen_date),
                address = VALUES(address),
                latitude = VALUES(latitude),
                longitude = VALUES(longitude),
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
            ps.setLong(1, row.syncJobId());
            ps.setInt(2, row.batchNo());
            ps.setString(3, row.noticeNumber());
            ps.setString(4, row.species());
            ps.setString(5, row.breed());
            ps.setObject(6, row.happenDate());
            ps.setString(7, row.address());
            ps.setObject(8, row.latitude());
            ps.setObject(9, row.longitude());
            ps.setString(10, row.sex());
            ps.setString(11, row.neutering());
            ps.setString(12, row.age());
            ps.setString(13, row.weight());
            ps.setString(14, row.furColor());
            ps.setString(15, row.significant());
            ps.setString(16, row.foundLocation());
            ps.setObject(17, row.noticeStartDate());
            ps.setObject(18, row.noticeEndDate());
            ps.setString(19, row.careName());
            ps.setString(20, row.careTel());
            ps.setString(21, row.authority());
            ps.setString(22, row.imageUrl1());
            ps.setString(23, row.imageUrl2());
            ps.setString(24, row.rawData());
            ps.setString(25, row.rawHash());
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