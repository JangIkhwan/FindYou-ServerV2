package com.kuit.findyou.domain.report.service.sync.db_con_usage;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
@Import({
        TestDatabaseConfig.class,
        ConnectionTrackingConfig.class
})
class ProtectingReportSyncV2ConnectionPerfTest {

    private static final int NEW_REPORT_COUNT = 3;
    private static final int[] LATENCIES_MS = {0, 500, 1_000, 3_000};
    private static final int DEFAULT_RUNS = 3;

    private final ProtectingReportSyncServiceV2Impl syncService;
    private final JdbcTemplate jdbcTemplate;
    private final ConnectionHoldRecorder connectionHoldRecorder;

    @MockitoBean
    ProtectingAnimalApiClient protectingAnimalApiClient;

    @MockitoBean
    KakaoCoordinateClient kakaoCoordinateClient;

    @Autowired
    ProtectingReportSyncV2ConnectionPerfTest(
            ProtectingReportSyncServiceV2Impl syncService,
            JdbcTemplate jdbcTemplate
    ) {
        this.syncService = syncService;
        this.jdbcTemplate = jdbcTemplate;
        this.connectionHoldRecorder = ConnectionTrackingConfig.connectionHoldRecorder();
    }

    @Test
    void measureConnectionHeldTimeByExternalApiLatency() throws Exception {
        int runs = intProperty("syncConnPerfRuns", DEFAULT_RUNS);
        String version = System.getProperty("syncConnPerfVersion", "after");
        Path outputPath = Path.of(System.getProperty(
                "syncConnPerfOutput",
                "build/reports/sync-connection-perf/v2-results.csv"
        ));

        for (LatencyTarget target : LatencyTarget.values()) {
            for (int latencyMs : LATENCIES_MS) {
                for (int run = 1; run <= runs; run++) {
                    runMeasurement(version, target, latencyMs, run, outputPath);
                }
            }
        }
    }

    private void runMeasurement(String version, LatencyTarget target, int latencyMs,
                                int run, Path outputPath) throws Exception {
        prepareDatabase();
        prepareExternalClientStubs(target, latencyMs);

        connectionHoldRecorder.start();
        long startedAt = System.nanoTime();
        ConnectionHoldRecorder.Result connectionResult;
        long totalTimeMs;

        try {
            syncService.syncProtectingReports();
        } finally {
            totalTimeMs = (System.nanoTime() - startedAt) / 1_000_000;
            connectionResult = connectionHoldRecorder.stop();
        }

        assertThat(countActiveProtectingReports()).isEqualTo(NEW_REPORT_COUNT);

        System.out.printf(
                "version=%s, target=%s, latency=%dms, run=%d, connectionHeld=%dms, acquireCount=%d, totalTime=%dms%n",
                version,
                target,
                latencyMs,
                run,
                connectionResult.heldTimeMillis(),
                connectionResult.acquireCount(),
                totalTimeMs
        );
        appendCsv(outputPath, version, target, latencyMs, run, connectionResult, totalTimeMs);
    }

    private void prepareExternalClientStubs(LatencyTarget target, int latencyMs) {
        Mockito.reset(protectingAnimalApiClient, kakaoCoordinateClient);

        Mockito.when(protectingAnimalApiClient.fetchPage(anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    sleepIfNeeded(target == LatencyTarget.PUBLIC_API ? latencyMs : 0);

                    int pageNo = invocation.getArgument(0);
                    int pageSize = invocation.getArgument(1);
                    return pageResult(pageNo, pageSize);
                });

        Mockito.when(kakaoCoordinateClient.requestCoordinateOrDefault(anyString()))
                .thenAnswer(invocation -> {
                    sleepIfNeeded(target == LatencyTarget.KAKAO ? latencyMs : 0);
                    return new KakaoCoordinateClient.Coordinate(
                            new BigDecimal("37.500000"),
                            new BigDecimal("127.100000")
                    );
                });
    }

    private ProtectingAnimalPageResult pageResult(int pageNo, int pageSize) {
        if (pageNo != 1) {
            return new ProtectingAnimalPageResult(pageNo, pageSize, NEW_REPORT_COUNT, List.of());
        }

        return new ProtectingAnimalPageResult(pageNo, pageSize, NEW_REPORT_COUNT, apiItems());
    }

    private List<ProtectingAnimalItemDTO> apiItems() {
        return List.of(
                apiItem("API-1"),
                apiItem("API-2"),
                apiItem("API-3")
        );
    }

    private ProtectingAnimalItemDTO apiItem(String noticeNo) {
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
                null,
                null,
                "M",
                "Y",
                "connection hold synthetic workload",
                "강남구 보호소",
                "02-0000-0000",
                "서울 강남구 테헤란로 1",
                "담당자",
                "강남구청"
        );
    }

    private void prepareDatabase() {
        truncateSyncTables();
        seedExistingProtectingReport();
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

    private void seedExistingProtectingReport() {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO reports
                (id, breed, species, tag, date, address, latitude, longitude, user_id, dtype, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?)
                """,
                1L,
                "진돗개",
                "강아지",
                "PROTECTING",
                Date.valueOf(LocalDate.of(2024, 7, 18)),
                "서울 강남구 테헤란로 1",
                new BigDecimal("37.500000"),
                new BigDecimal("127.100000"),
                "PROTECTING",
                "Y",
                now,
                now
        );

        jdbcTemplate.update("""
                INSERT INTO protecting_reports
                (id, sex, age, weight, fur_color, neutering, significant, found_location,
                 notice_number, notice_start_date, notice_end_date, care_name, care_tel, authority)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                1L,
                "M",
                "5",
                "5",
                "갈색",
                "Y",
                "seed existing report",
                "서울 강남구 테스트장소",
                "DB-1",
                Date.valueOf(LocalDate.of(2024, 7, 18)),
                Date.valueOf(LocalDate.of(2024, 7, 25)),
                "강남구 보호소",
                "02-0000-0000",
                "강남구청"
        );
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

    private void appendCsv(Path outputPath, String version, LatencyTarget target, int latencyMs,
                           int run, ConnectionHoldRecorder.Result connectionResult,
                           long totalTimeMs) throws Exception {
        Files.createDirectories(outputPath.getParent());
        if (Files.notExists(outputPath) || Files.size(outputPath) == 0) {
            Files.writeString(outputPath, "version,target,latency_ms,run,connection_held_ms,total_time_ms,connection_acquire_count%n".formatted());
        }
        Files.writeString(
                outputPath,
                "%s,%s,%d,%d,%d,%d,%d%n".formatted(
                        version,
                        target,
                        latencyMs,
                        run,
                        connectionResult.heldTimeMillis(),
                        totalTimeMs,
                        connectionResult.acquireCount()
                ),
                java.nio.file.StandardOpenOption.APPEND
        );
    }

    private void sleepIfNeeded(int latencyMs) {
        if (latencyMs <= 0) {
            return;
        }
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while applying synthetic API latency", e);
        }
    }

    private static int intProperty(String name, int defaultValue) {
        return Integer.parseInt(System.getProperty(name, String.valueOf(defaultValue)));
    }

    enum LatencyTarget {
        PUBLIC_API,
        KAKAO
    }
}
