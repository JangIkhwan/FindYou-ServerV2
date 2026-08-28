package com.kuit.findyou.domain.report.service.sync.memory_usage;

import com.kuit.findyou.domain.report.service.sync.ProtectingReportSyncServiceV2Impl;
import com.kuit.findyou.global.config.TestDatabaseConfig;
import com.kuit.findyou.global.external.client.KakaoCoordinateClient;
import com.kuit.findyou.global.external.client.ProtectingAnimalApiClient;
import com.kuit.findyou.global.external.dto.ProtectingAnimalItemDTO;
import com.kuit.findyou.global.external.dto.ProtectingAnimalPageResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false",
        "spring.jpa.properties.hibernate.format_sql=false",
        "logging.level.org.hibernate.SQL=off"
})
@ActiveProfiles("test")
@Import(TestDatabaseConfig.class)
class ProtectingReportSyncV2MemoryPerfTest {

    private static final int DEFAULT_ROWS = 1_000;
    private static final int DEFAULT_WARMUPS = 1;
    private static final int DEFAULT_RUNS = 3;
    private static final int SEED_CHUNK_SIZE = 1_000;
    private static final long SAMPLE_INTERVAL_MILLIS = 50;

    @Autowired
    ProtectingReportSyncServiceV2Impl syncService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    ProtectingAnimalApiClient protectingAnimalApiClient;

    @MockitoBean
    KakaoCoordinateClient kakaoCoordinateClient;

    @Test
    void measurePeakHeapForV2WorstCaseSync() throws Exception {
        int rows = intProperty("syncPerfRows", DEFAULT_ROWS);
        int warmups = intProperty("syncPerfWarmups", DEFAULT_WARMUPS);
        int runs = intProperty("syncPerfRuns", DEFAULT_RUNS);
        String version = System.getProperty("syncPerfVersion", "after");
        String minHeap = System.getProperty("syncPerfMinHeap", "512m");
        String maxHeap = System.getProperty("syncPerfMaxHeap", "512m");
        Path outputPath = Path.of(System.getProperty(
                "syncPerfOutput",
                "build/reports/sync-memory-perf/v2-results.csv"
        ));

        /*
         * Synthetic worst-case workload:
         * - DB has rows existing protecting reports.
         * - Public API returns rows items with completely different notice numbers.
         * - V2 stages API data page-by-page, inserts rows new active reports, and
         *   soft-deletes the existing rows that are missing from staging.
         *
         * This is intentionally not production traffic modeling. It keeps the same
         * Before/After comparison variable, rows, while following V2's page API flow.
         */
        for (int run = 0; run < warmups + runs; run++) {
            boolean warmup = run < warmups;
            int measurementRun = run - warmups + 1;

            prepareDatabase(rows);
            prepareExternalClientStubs(rows);
            stabilizeHeap();

            HeapSampler sampler = new HeapSampler(SAMPLE_INTERVAL_MILLIS);
            sampler.start();
            long startedAt = System.nanoTime();

            syncService.syncProtectingReports();

            long totalTimeMillis = (System.nanoTime() - startedAt) / 1_000_000;
            long peakHeapBytes = sampler.stopAndGetPeakBytes();

            assertThat(countActiveProtectingReports()).isEqualTo(rows);

            if (warmup) {
                System.out.printf(
                        "warmup version=%s, rows=%d, peakHeap=%.1fMB, totalTime=%dms%n",
                        version,
                        rows,
                        toMegabytes(peakHeapBytes),
                        totalTimeMillis
                );
                continue;
            }

            System.out.printf(
                    "version=%s, rows=%d, run=%d, peakHeap=%.1fMB, totalTime=%dms%n",
                    version,
                    rows,
                    measurementRun,
                    toMegabytes(peakHeapBytes),
                    totalTimeMillis
            );
            appendCsv(outputPath, version, rows, minHeap, maxHeap, measurementRun, peakHeapBytes, totalTimeMillis);
        }
    }

    private void prepareExternalClientStubs(int rows) {
        Mockito.reset(protectingAnimalApiClient, kakaoCoordinateClient);
        Mockito.when(protectingAnimalApiClient.fetchPage(anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    int pageNo = invocation.getArgument(0);
                    int pageSize = invocation.getArgument(1);
                    return pageResult(rows, pageNo, pageSize);
                });
        Mockito.when(kakaoCoordinateClient.requestCoordinateOrDefault(anyString()))
                .thenReturn(new KakaoCoordinateClient.Coordinate(
                        new BigDecimal("37.500000"),
                        new BigDecimal("127.100000")
                ));
    }

    private ProtectingAnimalPageResult pageResult(int rows, int pageNo, int pageSize) {
        int from = (pageNo - 1) * pageSize;
        if (from >= rows) {
            return new ProtectingAnimalPageResult(pageNo, pageSize, rows, List.of());
        }

        int to = Math.min(from + pageSize, rows);
        List<ProtectingAnimalItemDTO> items = new ArrayList<>(to - from);
        for (int index = from; index < to; index++) {
            items.add(apiItem(index + 1));
        }
        return new ProtectingAnimalPageResult(pageNo, pageSize, rows, items);
    }

    private ProtectingAnimalItemDTO apiItem(int sequence) {
        String noticeNo = "API-%08d".formatted(sequence);
        return new ProtectingAnimalItemDTO(
                "20240718",
                "서울 강남구 테스트장소",
                "개",
                "진돗개",
                "갈색",
                "2021(년생)",
                "5(Kg)",
                noticeNo,
                "20240718",
                "20240725",
                "https://example.com/protecting/" + noticeNo + ".jpg",
                null,
                "M",
                "Y",
                "synthetic v2 worst-case workload",
                "강남구 보호소",
                "02-0000-0000",
                "서울 강남구 테헤란로 1",
                "담당자",
                "강남구청"
        );
    }

    private void prepareDatabase(int rows) {
        truncateSyncTables();
        seedExistingProtectingReports(rows);
    }

    private void truncateSyncTables() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            jdbcTemplate.execute("TRUNCATE TABLE public_animal_staging");
            jdbcTemplate.execute("TRUNCATE TABLE sync_job_batch");
            jdbcTemplate.execute("TRUNCATE TABLE sync_job");
            jdbcTemplate.execute("TRUNCATE TABLE report_images");
            jdbcTemplate.execute("TRUNCATE TABLE protecting_reports");
            jdbcTemplate.execute("TRUNCATE TABLE reports");
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    private void seedExistingProtectingReports(int rows) {
        for (int offset = 0; offset < rows; offset += SEED_CHUNK_SIZE) {
            int batchSize = Math.min(SEED_CHUNK_SIZE, rows - offset);
            int batchOffset = offset;
            jdbcTemplate.batchUpdate("""
                    INSERT INTO reports
                    (id, breed, species, tag, date, address, latitude, longitude, user_id, dtype, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?)
                    """, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    long id = batchOffset + i + 1L;
                    LocalDateTime now = LocalDateTime.now();
                    ps.setLong(1, id);
                    ps.setString(2, "진돗개");
                    ps.setString(3, "강아지");
                    ps.setString(4, "PROTECTING");
                    ps.setDate(5, Date.valueOf(LocalDate.of(2024, 7, 18)));
                    ps.setString(6, "서울 강남구 테헤란로 1");
                    ps.setBigDecimal(7, new BigDecimal("37.500000"));
                    ps.setBigDecimal(8, new BigDecimal("127.100000"));
                    ps.setString(9, "PROTECTING");
                    ps.setString(10, "Y");
                    ps.setObject(11, now);
                    ps.setObject(12, now);
                }

                @Override
                public int getBatchSize() {
                    return batchSize;
                }
            });

            jdbcTemplate.batchUpdate("""
                    INSERT INTO protecting_reports
                    (id, sex, age, weight, fur_color, neutering, significant, found_location,
                     notice_number, notice_start_date, notice_end_date, care_name, care_tel, authority)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    long id = batchOffset + i + 1L;
                    ps.setLong(1, id);
                    ps.setString(2, "M");
                    ps.setString(3, "5");
                    ps.setString(4, "5");
                    ps.setString(5, "갈색");
                    ps.setString(6, "Y");
                    ps.setString(7, "seed existing report");
                    ps.setString(8, "서울 강남구 테스트장소");
                    ps.setString(9, "DB-%08d".formatted(id));
                    ps.setDate(10, Date.valueOf(LocalDate.of(2024, 7, 18)));
                    ps.setDate(11, Date.valueOf(LocalDate.of(2024, 7, 25)));
                    ps.setString(12, "강남구 보호소");
                    ps.setString(13, "02-0000-0000");
                    ps.setString(14, "강남구청");
                }

                @Override
                public int getBatchSize() {
                    return batchSize;
                }
            });
        }
    }

    private long countActiveProtectingReports() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM protecting_reports pr
                JOIN reports r ON r.id = pr.id
                WHERE r.status = 'Y'
                """, Long.class);
        return count == null ? 0 : count;
    }

    private void appendCsv(Path outputPath, String version, int rows, String minHeap, String maxHeap,
                           int run, long peakHeapBytes, long totalTimeMillis) throws Exception {
        Files.createDirectories(outputPath.getParent());
        if (Files.notExists(outputPath) || Files.size(outputPath) == 0) {
            Files.writeString(outputPath, "version,rows,min_heap,max_heap,run,peak_heap_mb,total_time_ms%n".formatted());
        }
        Files.writeString(
                outputPath,
                "%s,%d,%s,%s,%d,%.1f,%d%n".formatted(
                        version,
                        rows,
                        minHeap,
                        maxHeap,
                        run,
                        toMegabytes(peakHeapBytes),
                        totalTimeMillis
                ),
                java.nio.file.StandardOpenOption.APPEND
        );
    }

    private void stabilizeHeap() throws InterruptedException {
        System.gc();
        Thread.sleep(200);
    }

    private static int intProperty(String name, int defaultValue) {
        return Integer.parseInt(System.getProperty(name, String.valueOf(defaultValue)));
    }

    private static double toMegabytes(long bytes) {
        return bytes / 1024.0 / 1024.0;
    }
}
