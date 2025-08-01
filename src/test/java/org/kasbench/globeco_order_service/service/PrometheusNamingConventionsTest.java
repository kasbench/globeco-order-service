package org.kasbench.globeco_order_service.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test class to verify that all custom metrics follow Prometheus naming conventions.
 * This test validates the requirements from Requirement 4:
 * - Counter metrics use _total suffix
 * - Duration metrics use _seconds suffix and histogram type
 * - Gauge metrics use descriptive names without time-based suffixes
 * - All metrics use snake_case format with clear, descriptive names
 */
class PrometheusNamingConventionsTest {

    @Test
    void shouldFollowPrometheusNamingConventionsForDatabaseMetrics() {
        // Given
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        HikariDataSource hikariDataSource = mock(HikariDataSource.class);
        HikariPoolMXBean hikariPoolMXBean = mock(HikariPoolMXBean.class);
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        
        DatabaseMetricsService databaseMetricsService = new DatabaseMetricsService(meterRegistry, hikariDataSource);
        databaseMetricsService.initializeDatabaseMetrics();

        // When & Then - Verify counter metrics use _total suffix
        Counter poolExhaustionCounter = meterRegistry.find("db_pool_exhaustion_events_total").counter();
        assertThat(poolExhaustionCounter).isNotNull();
        assertThat(poolExhaustionCounter.getId().getName()).endsWith("_total");
        assertThat(poolExhaustionCounter.getId().getDescription()).isEqualTo("Number of times the database pool reached capacity");

        Counter acquisitionFailureCounter = meterRegistry.find("db_connection_acquisition_failures_total").counter();
        assertThat(acquisitionFailureCounter).isNotNull();
        assertThat(acquisitionFailureCounter.getId().getName()).endsWith("_total");
        assertThat(acquisitionFailureCounter.getId().getDescription()).isEqualTo("Failed attempts to acquire a database connection");

        // Verify duration metrics use _seconds suffix and Timer type (which creates histograms)
        Timer acquisitionTimer = meterRegistry.find("db_connection_acquisition_duration_seconds").timer();
        assertThat(acquisitionTimer).isNotNull();
        assertThat(acquisitionTimer.getId().getName()).endsWith("_seconds");
        assertThat(acquisitionTimer.getId().getDescription()).isEqualTo("Time taken to acquire a database connection");

        // Verify gauge metrics use descriptive names without time-based suffixes
        Gauge totalConnectionsGauge = meterRegistry.find("db_pool_connections_total").gauge();
        assertThat(totalConnectionsGauge).isNotNull();
        assertThat(totalConnectionsGauge.getId().getName()).doesNotEndWith("_seconds");
        assertThat(totalConnectionsGauge.getId().getName()).doesNotEndWith("_duration");
        assertThat(totalConnectionsGauge.getId().getDescription()).isEqualTo("Maximum connections the pool can handle");

        Gauge activeConnectionsGauge = meterRegistry.find("db_pool_connections_active").gauge();
        assertThat(activeConnectionsGauge).isNotNull();
        assertThat(activeConnectionsGauge.getId().getName()).doesNotEndWith("_seconds");
        assertThat(activeConnectionsGauge.getId().getName()).doesNotEndWith("_duration");
        assertThat(activeConnectionsGauge.getId().getDescription()).isEqualTo("Currently active database connections");

        Gauge idleConnectionsGauge = meterRegistry.find("db_pool_connections_idle").gauge();
        assertThat(idleConnectionsGauge).isNotNull();
        assertThat(idleConnectionsGauge.getId().getName()).doesNotEndWith("_seconds");
        assertThat(idleConnectionsGauge.getId().getName()).doesNotEndWith("_duration");
        assertThat(idleConnectionsGauge.getId().getDescription()).isEqualTo("Currently idle database connections");
    }

    @Test
    void shouldFollowPrometheusNamingConventionsForHttpMetrics() {
        // Given
        String serviceName = "test-service";
        String serviceUrl = "https://test-service:8443";
        MeterRegistry localMeterRegistry = new SimpleMeterRegistry();
        HttpMetricsService localHttpMetricsService = new HttpMetricsService(localMeterRegistry);
        localHttpMetricsService.registerHttpConnectionPoolMetrics(serviceName, serviceUrl);

        // When & Then - Verify gauge metrics use descriptive names without time-based suffixes
        Gauge totalConnectionsGauge = localMeterRegistry.find("http_pool_connections_total")
                .tag("service", serviceName)
                .tag("protocol", "https")
                .gauge();
        assertThat(totalConnectionsGauge).isNotNull();
        assertThat(totalConnectionsGauge.getId().getName()).doesNotEndWith("_seconds");
        assertThat(totalConnectionsGauge.getId().getName()).doesNotEndWith("_duration");
        assertThat(totalConnectionsGauge.getId().getDescription()).isEqualTo("Maximum HTTP connections allowed in the pool");

        Gauge activeConnectionsGauge = localMeterRegistry.find("http_pool_connections_active")
                .tag("service", serviceName)
                .tag("protocol", "https")
                .gauge();
        assertThat(activeConnectionsGauge).isNotNull();
        assertThat(activeConnectionsGauge.getId().getName()).doesNotEndWith("_seconds");
        assertThat(activeConnectionsGauge.getId().getName()).doesNotEndWith("_duration");
        assertThat(activeConnectionsGauge.getId().getDescription()).isEqualTo("Currently active HTTP connections in the pool");

        Gauge idleConnectionsGauge = localMeterRegistry.find("http_pool_connections_idle")
                .tag("service", serviceName)
                .tag("protocol", "https")
                .gauge();
        assertThat(idleConnectionsGauge).isNotNull();
        assertThat(idleConnectionsGauge.getId().getName()).doesNotEndWith("_seconds");
        assertThat(idleConnectionsGauge.getId().getName()).doesNotEndWith("_duration");
        assertThat(idleConnectionsGauge.getId().getDescription()).isEqualTo("Currently idle HTTP connections in the pool");
    }

    @Test
    void shouldUseSnakeCaseFormatForAllMetrics() {
        // Given
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        HikariDataSource hikariDataSource = mock(HikariDataSource.class);
        HikariPoolMXBean hikariPoolMXBean = mock(HikariPoolMXBean.class);
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        
        DatabaseMetricsService databaseMetricsService = new DatabaseMetricsService(meterRegistry, hikariDataSource);
        HttpMetricsService httpMetricsService = new HttpMetricsService(meterRegistry);
        databaseMetricsService.initializeDatabaseMetrics();
        httpMetricsService.registerHttpConnectionPoolMetrics("test-service", "http://test:8080");

        // When & Then - Verify all metric names use snake_case format
        meterRegistry.getMeters().forEach(meter -> {
            String metricName = meter.getId().getName();
            
            // Verify snake_case format (no camelCase, PascalCase, or kebab-case)
            assertThat(metricName).matches("^[a-z][a-z0-9_]*[a-z0-9]$");
            
            // Verify no consecutive underscores
            assertThat(metricName).doesNotContain("__");
            
            // Verify no leading or trailing underscores
            assertThat(metricName).doesNotStartWith("_");
            assertThat(metricName).doesNotEndWith("_");
            
            // Verify descriptive naming (at least 3 parts separated by underscores for custom metrics)
            if (metricName.startsWith("db_") || metricName.startsWith("http_")) {
                String[] parts = metricName.split("_");
                assertThat(parts.length).isGreaterThanOrEqualTo(3);
            }
        });
    }

    @Test
    void shouldHaveDescriptiveMetricDescriptions() {
        // Given
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        HikariDataSource hikariDataSource = mock(HikariDataSource.class);
        HikariPoolMXBean hikariPoolMXBean = mock(HikariPoolMXBean.class);
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        
        DatabaseMetricsService databaseMetricsService = new DatabaseMetricsService(meterRegistry, hikariDataSource);
        HttpMetricsService httpMetricsService = new HttpMetricsService(meterRegistry);
        databaseMetricsService.initializeDatabaseMetrics();
        httpMetricsService.registerHttpConnectionPoolMetrics("test-service", "http://test:8080");

        // When & Then - Verify all custom metrics have descriptive descriptions
        meterRegistry.getMeters().forEach(meter -> {
            String metricName = meter.getId().getName();
            String description = meter.getId().getDescription();
            
            if (metricName.startsWith("db_") || metricName.startsWith("http_")) {
                // All custom metrics should have descriptions
                assertThat(description).isNotNull();
                assertThat(description).isNotEmpty();
                
                // Descriptions should be meaningful (at least 10 characters)
                assertThat(description.length()).isGreaterThanOrEqualTo(10);
                
                // Descriptions should start with a capital letter
                assertThat(description).matches("^[A-Z].*");
            }
        });
    }

    @Test
    void shouldHaveProperLabelsForHttpMetrics() {
        // Given
        MeterRegistry localMeterRegistry = new SimpleMeterRegistry();
        HttpMetricsService localHttpMetricsService = new HttpMetricsService(localMeterRegistry);
        localHttpMetricsService.registerHttpConnectionPoolMetrics("security-service", "https://security:8443");
        localHttpMetricsService.registerHttpConnectionPoolMetrics("portfolio-service", "http://portfolio:8080");

        // When & Then - Verify proper labeling
        Gauge httpsGauge = localMeterRegistry.find("http_pool_connections_total")
                .tag("service", "security-service")
                .tag("protocol", "https")
                .gauge();
        assertThat(httpsGauge).isNotNull();
        assertThat(httpsGauge.getId().getTag("service")).isEqualTo("security-service");
        assertThat(httpsGauge.getId().getTag("protocol")).isEqualTo("https");

        Gauge httpGauge = localMeterRegistry.find("http_pool_connections_total")
                .tag("service", "portfolio-service")
                .tag("protocol", "http")
                .gauge();
        assertThat(httpGauge).isNotNull();
        assertThat(httpGauge.getId().getTag("service")).isEqualTo("portfolio-service");
        assertThat(httpGauge.getId().getTag("protocol")).isEqualTo("http");
    }

    @Test
    void shouldValidateAllRequiredMetricsArePresent() {
        // Given
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        HikariDataSource hikariDataSource = mock(HikariDataSource.class);
        HikariPoolMXBean hikariPoolMXBean = mock(HikariPoolMXBean.class);
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        
        DatabaseMetricsService databaseMetricsService = new DatabaseMetricsService(meterRegistry, hikariDataSource);
        HttpMetricsService httpMetricsService = new HttpMetricsService(meterRegistry);
        databaseMetricsService.initializeDatabaseMetrics();
        httpMetricsService.registerHttpConnectionPoolMetrics("test-service", "http://test:8080");

        // When & Then - Verify all required metrics from the requirements are present
        
        // Database metrics from Requirement 1
        assertThat(meterRegistry.find("db_connection_acquisition_duration_seconds").timer()).isNotNull();
        assertThat(meterRegistry.find("db_pool_exhaustion_events_total").counter()).isNotNull();
        assertThat(meterRegistry.find("db_connection_acquisition_failures_total").counter()).isNotNull();
        assertThat(meterRegistry.find("db_pool_connections_total").gauge()).isNotNull();
        assertThat(meterRegistry.find("db_pool_connections_active").gauge()).isNotNull();
        assertThat(meterRegistry.find("db_pool_connections_idle").gauge()).isNotNull();

        // HTTP metrics from Requirement 2
        assertThat(meterRegistry.find("http_pool_connections_total").gauge()).isNotNull();
        assertThat(meterRegistry.find("http_pool_connections_active").gauge()).isNotNull();
        assertThat(meterRegistry.find("http_pool_connections_idle").gauge()).isNotNull();
    }
}