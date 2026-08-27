package com.kuit.findyou.domain.report.service.sync.memory_usage;

import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

final class HeapSampler implements AutoCloseable {
    /*
    * 경량 폴링 방식으로 힙 메모리 최대 사용량을 설정된 주기만큼 수집하는 데몬을 만들고
    * 최대 사용량을 반환하는 클래스
    * */

    private final long intervalMillis;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong peakBytes = new AtomicLong(0);
    private Thread worker;

    HeapSampler(long intervalMillis) {
        this.intervalMillis = intervalMillis;
    }

    void start() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("HeapSampler is already running");
        }
        sample();
        worker = new Thread(this::sampleUntilStopped, "sync-perf-heap-sampler");
        worker.setDaemon(true);
        worker.start();
    }

    long stopAndGetPeakBytes() {
        close();
        return peakBytes.get();
    }

    @Override
    public void close() {
        running.set(false);
        if (worker == null) {
            return;
        }
        try {
            worker.join(intervalMillis * 2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void sampleUntilStopped() {
        while (running.get()) {
            sample();
            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running.set(false);
            }
        }
        sample();
    }

    private void sample() {
        long used = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        peakBytes.accumulateAndGet(used, Math::max);
    }
}
