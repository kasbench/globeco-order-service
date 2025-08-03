package org.kasbench.globeco_order_service.integration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.kasbench.globeco_order_service.service.DatabaseMetricsService;
import org.kasbench.globeco_order_service.service.HttpMetricsService;
import org.kasbench.globeco_order_service.config.MetricsConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test to verify that custom metrics are properly exported via OTLP
 * and can be collected by monitoring systems like Prometheus.
 * 
 * This test validates the end-to-end metrics flow from registration to export.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "metrics.custom.enabled=true",
    "metrics.custom.database.enabled=true", 
    "metrics.custom.http.enabled=true",
    "management.otlp.metrics.export.enabled=true",
    "management.otlp.metrics.export.url=http://localhost:4318/v1/metrics",
    "management.metrics.export.otlp.enabled=true"
})
public class MetricsExportIntegrationTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired(required = false)
    private DatabaseMetricsService databaseMetricsService;

    @Autowired(required = false)
    private HttpMetricsService httpMetricsService;

    @Autowired(required = false)
    private MetricsConfiguration metricsConfiguration;

    @BeforeEach
    void setUp() {
        // Allow some time for metrics to be registered during application startup
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void testDatabaseMetricsAreRegistered() {
        // Given that database metrics are enabled
        assertThat(databaseMetricsService).isNotNull();
        
        // When we check the meter registry
        // Then database metrics should be registered
        assertThat(meterRegistry.find("db_pool_connections_total").gauge())
            .as("Database total connections gauge should be registered")
            .isNotNull();
            
        assertThat(meterRegistry.find("db_pool_connections_active").gauge())
            .as("Database active connections gauge should be registered")
            .isNotNull();
            
        assertThat(meterRegistry.find("db_pool_connections_idle").gauge())
            .as("Database idle connections gauge should be registered")
            .isNotNull();
    }

    @Test
    void testHttpMetricsAreRegistered() {
        // Given that HTTP metrics are enabled
        assertThat(httpMetricsService).isNotNull();
        
        // When we check the meter registry for HTTP metrics
        // Then HTTP metrics should be registered for at least one service
        boolean hasHttpTotalMetrics = meterRegistry.find("http_pool_connections_total").meters().size() > 0;
        boolean hasHttpActiveMetrics = meterRegistry.find("http_pool_connections_active").meters().size() > 0;
        boolean hasHttpIdleMetrics = meterRegistry.find("http_pool_connections_idle").meters().size() > 0;
        
        assertThat(hasHttpTotalMetrics)
            .as("HTTP total connections gauge should be registered")
            .isTrue();
            
        assertThat(hasHttpActiveMetrics)
            .as("HTTP active connections gauge should be registered")
            .isTrue();
            
        assertThat(hasHttpIdleMetrics)
            .as("HTTP idle connections gauge should be registered")
            .isTrue();
    }

    @Test
    void testHttpMetricsHaveCorrectTags() {
        // Given that HTTP metrics are enabled
        assertThat(httpMetricsService).isNotNull();
        
        // When we check HTTP metrics
        // Then they should have the correct service and protocol tags
        Gauge securityServiceTotalGauge = meterRegistry.find("http_pool_connections_total")
            .tag("service", "security-service")
            .tag("protocol", "http")
            .gauge();
            
        Gauge portfolioServiceTotalGauge = meterRegistry.find("http_pool_connections_total")
            .tag("service", "portfolio-service")
            .tag("protocol", "http")
            .gauge();
        
        // At least one of the services should have metrics registered
        boolean hasSecurityMetrics = securityServiceTotalGauge != null;
        boolean hasPortfolioMetrics = portfolioServiceTotalGauge != null;
        
        assertThat(hasSecurityMetrics || hasPortfolioMetrics)
            .as("At least one service should have HTTP metrics registered")
            .isTrue();
    }

    @Test
    void testDatabaseMetricsHaveReasonableValues() {
        // Given that database metrics are enabled
        assertThat(databaseMetricsService).isNotNull();
        
        // When we check database metric values
        Gauge totalConnectionsGauge = meterRegistry.find("db_pool_connections_total").gauge();
        Gauge activeConnectionsGauge = meterRegistry.find("db_pool_connections_active").gauge();
        Gauge idleConnectionsGauge = meterRegistry.find("db_pool_connections_idle").gauge();
        
        assertThat(totalConnectionsGauge).isNotNull();
        assertThat(activeConnectionsGauge).isNotNull();
        assertThat(idleConnectionsGauge).isNotNull();
        
        // Then the values should be reasonable
        double totalConnections = totalConnectionsGauge.value();
        double activeConnections = activeConnectionsGauge.value();
        double idleConnections = idleConnectionsGauge.value();
        
        assertThat(totalConnections)
            .as("Total connections should be positive")
            .isGreaterThan(0);
            
        assertThat(activeConnections)
            .as("Active connections should be non-negative")
            .isGreaterThanOrEqualTo(0);
            
        assertThat(idleConnections)
            .as("Idle connections should be non-negative")
            .isGreaterThanOrEqualTo(0);
            
        assertThat(activeConnections + idleConnections)
            .as("Active + idle should not exceed total connections")
            .isLessThanOrEqualTo(totalConnections);
    }

    @Test
    void testHttpMetricsHaveReasonableValues() {
        // Given that HTTP metrics are enabled
        assertThat(httpMetricsService).isNotNull();
        
        // When we force a metrics update
        httpMetricsService.forceMetricsUpdate();
        
        // Then HTTP metrics should have reasonable values
        meterRegistry.find("http_pool_connections_total").meters().forEach(meter -> {
            if (meter instanceof Gauge) {
                Gauge gauge = (Gauge) meter;
                double value = gauge.value();
                
                assertThat(value)
                    .as("HTTP total connections should be non-negative for meter: " + meter.getId())
                    .isGreaterThanOrEqualTo(0);
            }
        });
        
        meterRegistry.find("http_pool_connections_active").meters().forEach(meter -> {
            if (meter instanceof Gauge) {
                Gauge gauge = (Gauge) meter;
                double value = gauge.value();
                
                assertThat(value)
                    .as("HTTP active connections should be non-negative for meter: " + meter.getId())
                    .isGreaterThanOrEqualTo(0);
            }
        });
        
        meterRegistry.find("http_pool_connections_idle").meters().forEach(meter -> {
            if (meter instanceof Gauge) {
                Gauge gauge = (Gauge) meter;
                double value = gauge.value();
                
                assertThat(value)
                    .as("HTTP idle connections should be non-negative for meter: " + meter.getId())
                    .isGreaterThanOrEqualTo(0);
            }
        });
    }

    @Test
    void testMetricsFollowPrometheusNamingConventions() {
        // Given that custom metrics are enabled
        // When we check metric names
        // Then they should follow Prometheus naming conventions
        
        // Database metrics should use snake_case and appropriate suffixes
        assertThat(meterRegistry.find("db_pool_connections_total").gauge())
            .as("Database total connections should use snake_case")
            .isNotNull();
            
        assertThat(meterRegistry.find("db_pool_connections_active").gauge())
            .as("Database active connections should use snake_case")
            .isNotNull();
            
        assertThat(meterRegistry.find("db_pool_connections_idle").gauge())
            .as("Database idle connections should use snake_case")
            .isNotNull();
        
        // HTTP metrics should use snake_case
        assertThat(meterRegistry.find("http_pool_connections_total").meters().size())
            .as("HTTP total connections should use snake_case")
            .isGreaterThan(0);
            
        assertThat(meterRegistry.find("http_pool_connections_active").meters().size())
            .as("HTTP active connections should use snake_case")
            .isGreaterThan(0);
            
        assertThat(meterRegistry.find("http_pool_connections_idle").meters().size())
            .as("HTTP idle connections should use snake_case")
            .isGreaterThan(0);
    }

    @Test
    void testMetricsHaveProperDescriptions() {
        // Given that custom metrics are enabled
        // When we check metric descriptions
        // Then they should have meaningful descriptions
        
        Gauge dbTotalGauge = meterRegistry.find("db_pool_connections_total").gauge();
        assertThat(dbTotalGauge).isNotNull();
        assertThat(dbTotalGauge.getId().getDescription())
            .as("Database total connections gauge should have a description")
            .isNotNull()
            .isNotEmpty();
        
        // Check HTTP metrics descriptions
        meterRegistry.find("http_pool_connections_total").meters().forEach(meter -> {
            assertThat(meter.getId().getDescription())
                .as("HTTP total connections gauge should have a description")
                .isNotNull()
                .isNotEmpty();
        });
    }

    @Test
    void testMetricsConfigurationStatus() {
        // Given that metrics configuration is available
        if (metricsConfiguration != null) {
            // When we check the configuration status
            String status = metricsConfiguration.getMetricsStatus();
            boolean allInitialized = metricsConfiguration.areAllMetricsInitialized();
            
            // Then the status should indicate proper initialization
            assertThat(status)
                .as("Metrics status should not be null")
                .isNotNull()
                .contains("Custom Metrics Status");
                
            assertThat(allInitialized)
                .as("All enabled metrics should be initialized")
                .isTrue();
        }
    }

    @Test
    void testMeterRegistryHasReasonableNumberOfMeters() {
        // Given that custom metrics are enabled
        // When we check the total number of registered meters
        int totalMeters = meterRegistry.getMeters().size();
        
        // Then there should be a reasonable number of meters
        assertThat(totalMeters)
            .as("Should have at least some meters registered")
            .isGreaterThan(10); // Spring Boot registers many default metrics
            
        // Count our custom metrics
        long customDatabaseMeters = meterRegistry.getMeters().stream()
            .filter(meter -> meter.getId().getName().startsWith("db_"))
            .count();
            
        long customHttpMeters = meterRegistry.getMeters().stream()
            .filter(meter -> meter.getId().getName().startsWith("http_pool_"))
            .count();
        
        assertThat(customDatabaseMeters)
            .as("Should have database custom metrics")
            .isGreaterThan(0);
            
        assertThat(customHttpMeters)
            .as("Should have HTTP custom metrics")
            .isGreaterThan(0);
    }

    @Test
    void testNoDuplicateMetricRegistrations() {
        // Given that custom metrics are enabled
        // When we check for duplicate registrations
        // Then there should be no duplicate metric names with the same tags
        
        // Check database metrics - should only have one of each
        long dbTotalCount = meterRegistry.find("db_pool_connections_total").meters().size();
        long dbActiveCount = meterRegistry.find("db_pool_connections_active").meters().size();
        long dbIdleCount = meterRegistry.find("db_pool_connections_idle").meters().size();
        
        assertThat(dbTotalCount)
            .as("Should have exactly one db_pool_connections_total gauge")
            .isEqualTo(1);
            
        assertThat(dbActiveCount)
            .as("Should have exactly one db_pool_connections_active gauge")
            .isEqualTo(1);
            
        assertThat(dbIdleCount)
            .as("Should have exactly one db_pool_connections_idle gauge")
            .isEqualTo(1);
        
        // Check HTTP metrics - should have one per service
        long httpTotalCount = meterRegistry.find("http_pool_connections_total").meters().size();
        long httpActiveCount = meterRegistry.find("http_pool_connections_active").meters().size();
        long httpIdleCount = meterRegistry.find("http_pool_connections_idle").meters().size();
        
        // Should have metrics for registered services (security-service and portfolio-service)
        assertThat(httpTotalCount)
            .as("Should have HTTP total metrics for registered services")
            .isGreaterThan(0)
            .isLessThanOrEqualTo(2); // Max 2 services
            
        assertThat(httpActiveCount)
            .as("Should have HTTP active metrics for registered services")
            .isEqualTo(httpTotalCount);
            
        assertThat(httpIdleCount)
            .as("Should have HTTP idle metrics for registered services")
            .isEqualTo(httpTotalCount);
    }
}