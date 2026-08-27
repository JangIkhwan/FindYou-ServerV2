package com.kuit.findyou.domain.report.service.sync.db_con_usage;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;

final class ConnectionHoldRecorder {
    /*
    * DB 커넥션 사용시간을 측정하기 위한 커넥션 래퍼
    * */
    private final ThreadLocal<Measurement> currentMeasurement = new ThreadLocal<>();

    void start() {
        currentMeasurement.set(new Measurement());
    }

    Result stop() {
        Measurement measurement = currentMeasurement.get();
        currentMeasurement.remove();
        if (measurement == null) {
            return new Result(0, 0);
        }
        return new Result(measurement.heldNanos / 1_000_000, measurement.acquireCount);
    }

    Connection wrap(Connection connection) {
        Measurement measurement = currentMeasurement.get();
        if (measurement == null) {
            return connection;
        }
        measurement.acquireCount++;
        long acquiredAtNanos = System.nanoTime();
        InvocationHandler handler = new ConnectionInvocationHandler(connection, measurement, acquiredAtNanos);
        return (Connection) Proxy.newProxyInstance(
                connection.getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                handler
        );
    }

    record Result(long heldTimeMillis, int acquireCount) {
    }

    private static class Measurement {
        private long heldNanos;
        private int acquireCount;
    }

    private static class ConnectionInvocationHandler implements InvocationHandler {

        private final Connection delegate;
        private final Measurement measurement;
        private final long acquiredAtNanos;
        private boolean closed;

        ConnectionInvocationHandler(Connection delegate, Measurement measurement, long acquiredAtNanos) {
            this.delegate = delegate;
            this.measurement = measurement;
            this.acquiredAtNanos = acquiredAtNanos;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("close".equals(method.getName()) && method.getParameterCount() == 0) {
                if (!closed) {
                    closed = true;
                    measurement.heldNanos += System.nanoTime() - acquiredAtNanos;
                }
                return method.invoke(delegate, args);
            }

            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }
    }
}
