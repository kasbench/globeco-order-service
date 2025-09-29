package org.kasbench.globeco_order_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SystemOverloadProperties configuration binding and validation.
 */
@SpringBootTest(classes = {SystemOverloadProperties.class})
@EnableConfigurationProperties(SystemOverloadProperties.class)
class SystemOverloadPropertiesTest {

    @Autowired
    private SystemOverloadProperties properties;

    @Test
    void shouldBindDefaultProperties() {
        // Detection properties
        assertThat(properties.getDetection().getEnabled()).isTrue();
        assertThat(properties.getDetection().getCheckIntervalSeconds()).isEqualTo(30);
        assertThat(properties.getDetection().getConsecutiveThreshold()).isEqualTo(2);

        // Thread pool properties
        assertThat(properties.getThreadPool().getEnabled()).isTrue();
        assertThat(properties.getThreadPool().getThreshold()).isEqualTo(0.9);

        // Database properties
        assertThat(properties.getDatabase().getEnabled()).isTrue();
        assertThat(properties.getDatabase().getThreshold()).isEqualTo(0.95);

        // Memory properties
        assertThat(properties.getMemory().getEnabled()).isTrue();
        assertThat(properties.getMemory().getThreshold()).isEqualTo(0.85);

        // Request ratio properties
        assertThat(properties.getRequestRatio().getEnabled()).isTrue();
        assertThat(properties.getRequestRatio().getThreshold()).isEqualTo(0.9);

        // Retry properties
        assertThat(properties.getRetry().getBaseDelaySeconds()).isEqualTo(60);
        assertThat(properties.getRetry().getMaxDelaySeconds()).isEqualTo(300);
        assertThat(properties.getRetry().getAdaptiveCalculation()).isTrue();
        assertThat(properties.getRetry().getDelayMultiplier()).isEqualTo(2.0);
    }

    @SpringBootTest(classes = {SystemOverloadProperties.class})
    @EnableConfigurationProperties(SystemOverloadProperties.class)
    @TestPropertySource(properties = {
        "system.overload.detection.enabled=false",
        "system.overload.detection.check-interval-seconds=60",
        "system.overload.detection.consecutive-threshold=5",
        "system.overload.thread-pool.enabled=false",
        "system.overload.thread-pool.threshold=0.8",
        "system.overload.database.enabled=false",
        "system.overload.database.threshold=0.9",
        "system.overload.memory.enabled=false",
        "system.overload.memory.threshold=0.7",
        "system.overload.request-ratio.enabled=false",
        "system.overload.request-ratio.threshold=0.8",
        "system.overload.retry.base-delay-seconds=30",
        "system.overload.retry.max-delay-seconds=600",
        "system.overload.retry.adaptive-calculation=false",
        "system.overload.retry.delay-multiplier=3.0"
    })
    static class CustomPropertiesTest {

        @Autowired
        private SystemOverloadProperties properties;

        @Test
        void shouldBindCustomProperties() {
            // Detection properties
            assertThat(properties.getDetection().getEnabled()).isFalse();
            assertThat(properties.getDetection().getCheckIntervalSeconds()).isEqualTo(60);
            assertThat(properties.getDetection().getConsecutiveThreshold()).isEqualTo(5);

            // Thread pool properties
            assertThat(properties.getThreadPool().getEnabled()).isFalse();
            assertThat(properties.getThreadPool().getThreshold()).isEqualTo(0.8);

            // Database properties
            assertThat(properties.getDatabase().getEnabled()).isFalse();
            assertThat(properties.getDatabase().getThreshold()).isEqualTo(0.9);

            // Memory properties
            assertThat(properties.getMemory().getEnabled()).isFalse();
            assertThat(properties.getMemory().getThreshold()).isEqualTo(0.7);

            // Request ratio properties
            assertThat(properties.getRequestRatio().getEnabled()).isFalse();
            assertThat(properties.getRequestRatio().getThreshold()).isEqualTo(0.8);

            // Retry properties
            assertThat(properties.getRetry().getBaseDelaySeconds()).isEqualTo(30);
            assertThat(properties.getRetry().getMaxDelaySeconds()).isEqualTo(600);
            assertThat(properties.getRetry().getAdaptiveCalculation()).isFalse();
            assertThat(properties.getRetry().getDelayMultiplier()).isEqualTo(3.0);
        }
    }

    @Test
    void shouldAllowNestedPropertyModification() {
        // Modify detection properties
        properties.getDetection().setEnabled(false);
        properties.getDetection().setCheckIntervalSeconds(120);
        properties.getDetection().setConsecutiveThreshold(3);

        // Modify thread pool properties
        properties.getThreadPool().setEnabled(false);
        properties.getThreadPool().setThreshold(0.8);

        // Modify database properties
        properties.getDatabase().setEnabled(false);
        properties.getDatabase().setThreshold(0.9);

        // Modify memory properties
        properties.getMemory().setEnabled(false);
        properties.getMemory().setThreshold(0.7);

        // Modify request ratio properties
        properties.getRequestRatio().setEnabled(false);
        properties.getRequestRatio().setThreshold(0.8);

        // Modify retry properties
        properties.getRetry().setBaseDelaySeconds(30);
        properties.getRetry().setMaxDelaySeconds(600);
        properties.getRetry().setAdaptiveCalculation(false);
        properties.getRetry().setDelayMultiplier(3.0);

        // Verify changes
        assertThat(properties.getDetection().getEnabled()).isFalse();
        assertThat(properties.getDetection().getCheckIntervalSeconds()).isEqualTo(120);
        assertThat(properties.getDetection().getConsecutiveThreshold()).isEqualTo(3);
        assertThat(properties.getThreadPool().getEnabled()).isFalse();
        assertThat(properties.getThreadPool().getThreshold()).isEqualTo(0.8);
        assertThat(properties.getDatabase().getEnabled()).isFalse();
        assertThat(properties.getDatabase().getThreshold()).isEqualTo(0.9);
        assertThat(properties.getMemory().getEnabled()).isFalse();
        assertThat(properties.getMemory().getThreshold()).isEqualTo(0.7);
        assertThat(properties.getRequestRatio().getEnabled()).isFalse();
        assertThat(properties.getRequestRatio().getThreshold()).isEqualTo(0.8);
        assertThat(properties.getRetry().getBaseDelaySeconds()).isEqualTo(30);
        assertThat(properties.getRetry().getMaxDelaySeconds()).isEqualTo(600);
        assertThat(properties.getRetry().getAdaptiveCalculation()).isFalse();
        assertThat(properties.getRetry().getDelayMultiplier()).isEqualTo(3.0);
    }

    @Test
    void shouldCreateNestedObjectsAutomatically() {
        SystemOverloadProperties newProperties = new SystemOverloadProperties();
        
        // Verify nested objects are created automatically
        assertThat(newProperties.getDetection()).isNotNull();
        assertThat(newProperties.getThreadPool()).isNotNull();
        assertThat(newProperties.getDatabase()).isNotNull();
        assertThat(newProperties.getMemory()).isNotNull();
        assertThat(newProperties.getRequestRatio()).isNotNull();
        assertThat(newProperties.getRetry()).isNotNull();
    }

    @Test
    void shouldAllowNestedObjectReplacement() {
        SystemOverloadProperties.Detection newDetection = new SystemOverloadProperties.Detection();
        newDetection.setEnabled(false);
        newDetection.setCheckIntervalSeconds(90);
        newDetection.setConsecutiveThreshold(4);

        properties.setDetection(newDetection);

        assertThat(properties.getDetection().getEnabled()).isFalse();
        assertThat(properties.getDetection().getCheckIntervalSeconds()).isEqualTo(90);
        assertThat(properties.getDetection().getConsecutiveThreshold()).isEqualTo(4);
    }
}