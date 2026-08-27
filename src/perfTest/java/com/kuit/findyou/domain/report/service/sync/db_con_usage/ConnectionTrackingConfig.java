package com.kuit.findyou.domain.report.service.sync.db_con_usage;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@TestConfiguration
class ConnectionTrackingConfig {
    /*
    * 커넥션 사용시간을 추적하기 위한 데이터소스를 등록하는 설정 클래스
    * */
    private static final ConnectionHoldRecorder CONNECTION_HOLD_RECORDER = new ConnectionHoldRecorder();

    static ConnectionHoldRecorder connectionHoldRecorder() {
        return CONNECTION_HOLD_RECORDER;
    }

    @Bean
    static BeanPostProcessor connectionTrackingDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof DataSource dataSource && !(bean instanceof TrackingDataSource)) {
                    return new TrackingDataSource(dataSource, CONNECTION_HOLD_RECORDER);
                }
                return bean;
            }
        };
    }

    private static class TrackingDataSource extends DelegatingDataSource {

        private final ConnectionHoldRecorder recorder;

        TrackingDataSource(DataSource targetDataSource, ConnectionHoldRecorder recorder) {
            super(targetDataSource);
            this.recorder = recorder;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return recorder.wrap(super.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return recorder.wrap(super.getConnection(username, password));
        }
    }
}
