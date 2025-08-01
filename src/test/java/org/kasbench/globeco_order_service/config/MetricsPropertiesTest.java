package org.kasbench.globeco_order_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class for MetricsProperties configuration binding and validation.
 */
@SpringBootTest(classes = {MetricsProperties.class})
@EnableConfigurationProperties(MetricsProperties.class)
@TestPropertySource(properties = {
    "metrics.custom.enabled=true",
    "metrics.custom.database.enabled=true",
    "metrics.custom.database.collection-interval=45s",
    "metrics.custom.database.acquisition-warning-threshold-ms=3000",
    "metrics.custom.database.exhaustion-alert-threshold=10",
    "metrics.custom.database.detailed-timing-enabled=false",
    "metrics.custom.database.leak-detection-enabled=false",
    "metrics.custom.database.min-utilization-for-detailed-metrics=0.15",
    "metrics.custom.http.enabled=true",
    "metrics.custom.http.collection-interval=60s",
    "metrics.custom.http.utilization-warning-threshold=0.85",
    "metrics.custom.http.max-monitored-services=5",
    "metrics.custom.http.per-route-metrics-enabled=true",
    "metrics.custom.http.statistics-timeout-ms=2000",
    "metrics.custom.http.connection-timing-enabled=false",
    "metrics.custom.collection.interval=20s",
    "metrics.custom.collection.batch-enabled=false",
    "metrics.custom.collection.batch-size=50",
    "metrics.custom.collection.timeout-ms=3000",
    "metrics.custom.collection.async-enabled=false",
    "metrics.custom.collection.async-threads=4",
    "metrics.custom.collection.threshold-based-collection=true",
    "metrics.custom.initialization.timeout=45s",
    "metrics.custom.initialization.retry-enabled=false",
    "metrics.custom.initialization.max-retries=5",
    "metrics.custom.initialization.retry-delay-ms=2000",
    "metrics.custom.initialization.fail-on-error=true",
    "metrics.custom.initialization.validation-enabled=false",
    "metrics.custom.initialization.verbose-logging=true"
})
class MetricsPropertiesTest {

    @Autowired
    private MetricsProperties metricsProperties;

    @Test
    void testBasicPropertiesBinding() {
        assertThat(metricsProperties.isEnabled()).isTrue();
        assertThat(metricsProperties.getDatabase().isEnabled()).isTrue();
        assertThat(metricsProperties.getHttp().isEnabled()).isTrue();
    }

    @Test
    void testDatabasePropertiesBinding() {
        MetricsProperties.DatabaseMetrics database = metricsProperties.getDatabase();
        
        assertThat(database.isEnabled()).isTrue();
        assertThat(database.getCollectionInterval()).isEqualTo(Duration.ofSeconds(45));
        assertThat(database.getAcquisitionWarningThresholdMs()).isEqualTo(3000);
        assertThat(database.getExhaustionAlertThreshold()).isEqualTo(10);
        assertThat(database.isDetailedTimingEnabled()).isFalse();
        assertThat(database.isLeakDetectionEnabled()).isFalse();
        
        assertThat(database.getMinUtilizationForDetailedMetrics()).isEqualTo(0.1);
    }

    @Test
    void testHttpPropertiesBinding() {
        MetricsProperties.HttpMetrics http = metricsProperties.getHttp();
        
        assertThat(http.isEnabled()).isTrue();
        assertThat(http.getCollectionInterval()).isEqualTo(Duration.ofSeconds(60));
        
        assertThat(http.getUtilizationWarningThreshold()).isEqualTo(0.8);
        assertThat(http.getMaxMonitoredServices()).isEqualTo(5);
        assertThat(http.isPerRouteMetricsEnabled()).isTrue();
        assertThat(http.getStatisticsTimeoutMs()).isEqualTo(2000);
        assertThat(http.isConnectionTimingEnabled()).isFalse();
    }

    @Test
    void testCollectionPropertiesBinding() {
        MetricsProperties.Collection collection = metricsProperties.getCollection();
        
        assertThat(collection.getInterval()).isEqualTo(Duration.ofSeconds(20));
        assertThat(collection.isBatchEnabled()).isFalse();
        assertThat(collection.getBatchSize()).isEqualTo(50);
        assertThat(collection.getTimeoutMs()).isEqualTo(3000);
        assertThat(collection.isAsyncEnabled()).isFalse();
        assertThat(collection.getAsyncThreads()).isEqualTo(4);
        assertThat(collection.isThresholdBasedCollection()).isTrue();
    }

    @Test
    void testInitializationPropertiesBinding() {
        MetricsProperties.Initialization initialization = metricsProperties.getInitialization();
        
        assertThat(initialization.getTimeout()).isEqualTo(Duration.ofSeconds(45));
        assertThat(initialization.isRetryEnabled()).isFalse();
        assertThat(initialization.getMaxRetries()).isEqualTo(5);
        assertThat(initialization.getRetryDelayMs()).isEqualTo(2000);
        assertThat(initialization.isFailOnError()).isTrue();
        assertThat(initialization.isValidationEnabled()).isFalse();
        assertThat(initialization.isVerboseLogging()).isTrue();
    }

    @Test
    void testEffectiveCollectionIntervals() {
        // Database has specific interval configured
        assertThat(metricsProperties.getEffectiveDatabaseCollectionInterval())
            .isEqualTo(Duration.ofSeconds(45));
        
        // HTTP has specific interval configured
        assertThat(metricsProperties.getEffectiveHttpCollectionInterval())
            .isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void testUtilityMethods() {
        assertThat(metricsProperties.isAnyMetricEnabled()).isTrue();
    }

    @Test
    void testValidationMethod() {
        // Test that validation doesn't throw exceptions with valid configuration
        metricsProperties.validate();
        
        // Verify that validation corrects invalid values
        metricsProperties.getDatabase().setMinUtilizationForDetailedMetrics(1.5); // Invalid value > 1.0
        metricsProperties.validate();
        assertThat(metricsProperties.getDatabase().getMinUtilizationForDetailedMetrics()).isEqualTo(0.1);
        
        metricsProperties.getHttp().setUtilizationWarningThreshold(-0.1); // Invalid value < 0.0
        metricsProperties.validate();
        assertThat(metricsProperties.getHttp().getUtilizationWarningThreshold()).isEqualTo(0.8);
    }
}