package com.kuit.findyou.domain.report.service.sync.memory_usage;

import com.kuit.findyou.domain.report.service.sync.ProtectingReportSyncService;
import com.kuit.findyou.global.config.TestDatabaseConfig;
import com.kuit.findyou.global.external.client.KakaoCoordinateClient;
import com.kuit.findyou.global.external.client.ProtectingAnimalApiClient;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;

@SpringBootTest(properties = {
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false",
        "spring.jpa.properties.hibernate.format_sql=false",
        "logging.level.org.hibernate.SQL=off"
})
@ActiveProfiles("test")
@Import(TestDatabaseConfig.class)
class ProtectingReportSyncMemoryPerfTest {
    /*
    * 공공데이터 동기화 로직의 메모리 사용량을 측정하기 위해서 사용하는 클래스
    * */

    private static final int DEFAULT_ROWS = 1_000;
    private static final int DEFAULT_WARMUPS = 1;
    private static final int DEFAULT_RUNS = 3;
    private static final int SEED_CHUNK_SIZE = 1_000;
    private static final long SAMPLE_INTERVAL_MILLIS = 50;

    @Autowired
    ProtectingReportSyncService syncService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    ProtectingAnimalApiClient protectingAnimalApiClient;

    @MockitoBean
    KakaoCoordinateClient kakaoCoordinateClient;

    @Test
    void measurePeakHeapForWorstCaseSync() throws Exception {
        int rows = intProperty("syncPerfRows", DEFAULT_ROWS);
        int warmups = intProperty("syncPerfWarmups", DEFAULT_WARMUPS);
        int runs = intProperty("syncPerfRuns", DEFAULT_RUNS);
        String version = System.getProperty("syncPerfVersion", "before");
        String minHeap = System.getProperty("syncPerfMinHeap", "512m");
        String maxHeap = System.getProperty("syncPerfMaxHeap", "512m");
        Path outputPath = Path.of(System.getProperty(
                "syncPerfOutput",
                "build/reports/sync-memory-perf/results.csv"
        ));

        /*
         * Synthetic worst-case workload:
         * - DB has rows existing protecting reports.
         * - API returns rows items with completely different notice numbers.
         * - The sync deletes all existing rows and creates rows new reports.
         *
         * This is intentionally not production traffic modeling. It makes the Before
         * implementation's simultaneous List/Set/entity retention easy to observe.
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

            Long finalCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM protecting_reports",
                    Long.class
            );
            assertThat(finalCount).isEqualTo(rows);

            if (warmup) {
                System.out.printf(
                        "warmup rows=%d, peakHeap=%.1fMB, totalTime=%dms%n",
                        rows,
                        toMegabytes(peakHeapBytes),
                        totalTimeMillis
                );
                continue;
            }

            System.out.printf(
                    "rows=%d, run=%d, peakHeap=%.1fMB, totalTime=%dms%n",
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
        Mockito.when(protectingAnimalApiClient.fetchAllProtectingAnimals())
                .thenReturn(new LazyProtectingAnimalItemList(rows));
        Mockito.when(kakaoCoordinateClient.requestCoordinateOrDefault(anyString()))
                .thenReturn(new KakaoCoordinateClient.Coordinate(
                        new BigDecimal("37.500000"),
                        new BigDecimal("127.100000")
                ));
    }

    private void prepareDatabase(int rows) {
        truncateReportTables();
        seedExistingProtectingReports(rows);
    }

    private void truncateReportTables() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
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
                    ps.setString(3, "DOG");
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
                    ps.setString(3, "3");
                    ps.setString(4, "5");
                    ps.setString(5, "갈색");
                    ps.setString(6, "Y");
                    ps.setString(7, "seed existing report");
                    ps.setString(8, "서울 강남구 테스트장소");
                    ps.setString(9, "DB-" + id);
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
