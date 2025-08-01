package org.kasbench.globeco_order_service.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatabaseMetricsServiceTest {

    @Mock
    private HikariDataSource hikariDataSource;

    @Mock
    private HikariPoolMXBean hikariPoolMXBean;

    @Mock
    private DataSource nonHikariDataSource;

    private MeterRegistry meterRegistry;
    private DatabaseMetricsService databaseMetricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    void shouldInitializeSuccessfullyWithHikariDataSource() {
        // Given
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        databaseMetricsService = new DatabaseMetricsService(meterRegistry, hikariDataSource);

        // When
        databaseMetricsService.initializeDatabaseMetrics();

        // Then
        assertThat(databaseMetricsService.isInitialized()).isTrue();
        
        // Verify metrics are registered
        assertThat(meterRegistry.find("db_connection_acquisition_duration_seconds").timer()).isNotNull();
        assertThat(meterRegistry.find("db_pool_exhaustion_events_total").counter()).isNotNull();
        assertThat(meterRegistry.find("db_connection_acquisition_failures_total").counter()).isNotNull();
        assertThat(meterRegistry.find("db_pool_connections_total").gauge()).isNotNull();
        assertThat(meterRegistry.find("db_pool_connections_active").gauge()).isNotNull();
        assertThat(meterRegistry.find("db_pool_connections_idle").gauge()).isNotNull();
    }

    @Test
    void shouldHandleNonHikariDataSourceGracefully() {
        // Given
        databaseMetricsService = new DatabaseMetricsService(meterRegistry, nonHikariDataSource);

        // When
        databaseMetricsService.initializeDatabaseMetrics();

        // Then
        assertThat(databaseMetricsService.isInitialized()).isFalse();
        
        // Verify no metrics are registered when initialization fails
        assertThat(meterRegistry.find("db_connection_acquisition_duration_seconds").timer()).isNull();
    }

    @Test
    void shouldRecordConnectionAcquisitionDuration() {
        // Given
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        databaseMetricsService = new DatabaseMetricsService(meterRegistry, hikariDataSource);
        databaseMetricsService.initializeDatabaseMetrics();

        // When
        databaseMetricsService.recordConnectionAcquisitionDuration(100, TimeUnit.MILLISECONDS);

        // Then
        Timer timer = meterRegistry.find("db_connection_acquisition_duration_seconds").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(100);
    }

    @Test
    void shouldRecordConnectionAcquisitionDurationWithSample() {
        // Given
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        databaseMetricsService = new DatabaseMetricsService(meterRegistry, hikariDataSource);
        databaseMetricsService.initializeDatabaseMetrics();

        // When
        Timer.Sample sample = databaseMetricsService.startConnectionAcquisitionTimer();
        // Simulate some processing time
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        databaseMetricsService.recordConnectionAcquisitionDuration(sample);

        // Then
        Timer timer = meterRegistry.find("db_connection_acquisition_duration_seconds").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isGreaterThan(0);
    }

    @Test
    void shouldRecordPoolExhaustionEvent() {
        // Given
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        databaseMetricsService = new DatabaseMetricsService(meterRegistry, hikariDataSource);
        databaseMetricsService.initializeDatabaseMetrics();

        // When
        databaseMetricsService.recordPoolExhaustionEvent();
        databaseMetricsService.recordPoolExhaustionEvent();

        // Then
        Counter counter = meterRegistry.find("db_pool_exhaustion_events_total").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2);
    }

    @Test
    void shouldRecordAcquisitionFailure() {
        // Given
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        databaseMetricsService = new DatabaseMetricsService(meterRegistry, hikariDataSource);
        databaseMetricsService.initializeDatabaseMetrics();

        // When
        databaseMetricsService.recordAcquisitionFailure();
        databaseMetricsService.recordAcquisitionFailure();
        databaseMetricsService.recordAcquisitionFailure();

        // Then
        Counter counter = meterRegistry.find("db_connection_acquisition_failures_total").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(3);
    }

    @Test
    void shouldProvideCorrectGaugeValues() {
        // Given
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        when(hikariDataSource.getMaximumPoolSize()).thenReturn(20);
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(5);
        when(hikariPoolMXBean.getIdleConnections()).thenReturn(10);

        databaseMetricsService = new DatabaseMetricsService(meterRegistry, hikariDataSource);
        databaseMetricsService.initializeDatabaseMetrics();

        // When & Then
        Gauge totalGauge = meterRegistry.find("db_pool_connections_total").gauge();
        assertThat(totalGauge).isNotNull();
        assertThat(totalGauge.value()).isEqualTo(20.0);

        Gauge activeGauge = meterRegistry.find("db_pool_connections_active").gauge();
        assertThat(activeGauge).isNotNull();
        assertThat(activeGauge.value()).isEqualTo(5.0);

        Gauge idleGauge = meterRegistry.find("db_pool_connections_idle").gauge();
        assertThat(idleGauge).isNotNull();
        assertThat(idleGauge.value()).isEqualTo(10.0);
    }

    @Test
    void shouldHandleHikariPoolMXBeanExceptions() {
        // Given
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        when(hikariDataSource.getMaximumPoolSize()).thenThrow(new RuntimeException("Pool error"));
        when(hikariPoolMXBean.getActiveConnections()).thenThrow(new RuntimeException("Pool error"));
        when(hikariPoolMXBean.getIdleConnections()).thenThrow(new RuntimeException("Pool error"));

        databaseMetricsService = new DatabaseMetricsService(meterRegistry, hikariDataSource);
        databaseMetricsService.initializeDatabaseMetrics();

        // When & Then - should not throw exceptions and return 0 values
        Gauge totalGauge = meterRegistry.find("db_pool_connections_total").gauge();
        assertThat(totalGauge.value()).isEqualTo(0.0);

        Gauge activeGauge = meterRegistry.find("db_pool_connections_active").gauge();
        assertThat(activeGauge.value()).isEqualTo(0.0);

        Gauge idleGauge = meterRegistry.find("db_pool_connections_idle").gauge();
        assertThat(idleGauge.value()).isEqualTo(0.0);
    }

    @Test
    void shouldProvidePoolStatistics() {
        // Given
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        when(hikariDataSource.getMaximumPoolSize()).thenReturn(20);
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(5);
        when(hikariPoolMXBean.getIdleConnections()).thenReturn(10);
        when(hikariPoolMXBean.getThreadsAwaitingConnection()).thenReturn(2);

        databaseMetricsService = new DatabaseMetricsService(meterRegistry, hikariDataSource);
        databaseMetricsService.initializeDatabaseMetrics();

        // When
        String statistics = databaseMetricsService.getPoolStatistics();

        // Then
        assertThat(statistics).contains("Total: 20");
        assertThat(statistics).contains("Active: 5");
        assertThat(statistics).contains("Idle: 10");
        assertThat(statistics).contains("Waiting: 2");
    }

    @Test
    void shouldHandleUninitializedState() {
        // Given
        databaseMetricsService = new DatabaseMetricsService(meterRegistry, nonHikariDataSource);
        databaseMetricsService.initializeDatabaseMetrics();

        // When & Then
        assertThat(databaseMetricsService.isInitialized()).isFalse();
        assertThat(databaseMetricsService.getPoolStatistics()).isEqualTo("Database metrics not initialized");

        // Should handle method calls gracefully without throwing exceptions
        databaseMetricsService.recordConnectionAcquisitionDuration(100, TimeUnit.MILLISECONDS);
        databaseMetricsService.recordPoolExhaustionEvent();
        databaseMetricsService.recordAcquisitionFailure();
    }

    @Test
    void shouldHandleNullTimerSample() {
        // Given
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        databaseMetricsService = new DatabaseMetricsService(meterRegistry, hikariDataSource);
        databaseMetricsService.initializeDatabaseMetrics();

        // When & Then - should not throw exception
        databaseMetricsService.recordConnectionAcquisitionDuration(null);

        Timer timer = meterRegistry.find("db_connection_acquisition_duration_seconds").timer();
        assertThat(timer.count()).isEqualTo(0);
    }
}