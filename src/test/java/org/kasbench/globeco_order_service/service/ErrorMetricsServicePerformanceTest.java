package org.kasbench.globeco_order_service.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Test for ErrorMetricsService performance metrics functionality.
 * Verifies that error handling performance metrics are properly recorded and tracked.
 */
@ExtendWith(MockitoExtension.class)
class ErrorMetricsServicePerformanceTest {

    @Mock
    private SystemOverloadDetector systemOverloadDetector;

    private MeterRegistry meterRegistry;
    private ErrorMetricsService errorMetricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        errorMetricsService = new ErrorMetricsService(meterRegistry, systemOverloadDetector);
        
        // Set up mock behavior
        when(systemOverloadDetector.isInitialized()).thenReturn(true);
        when(systemOverloadDetector.getThreadPoolUtilization()).thenReturn(0.5);
        when(systemOverloadDetector.getDatabaseConnectionUtilization()).thenReturn(0.6);
        when(systemOverloadDetector.getMemoryUtilization()).thenReturn(0.7);
        when(systemOverloadDetector.getAverageOverloadDetectionTimeMillis()).thenReturn(1.5);
        when(systemOverloadDetector.getTotalOverloadChecks()).thenReturn(100L);
        
        // Initialize the service
        errorMetricsService.initializeErrorMetrics();
    }

    @Test
    void testErrorHandlingPerformanceMetricsRecording() {
        // Given
        String errorType = "system_overload";
        double durationMillis = 15.5;

        // When
        errorMetricsService.recordErrorHandlingPerformance(errorType, durationMillis);

        // Then
        Timer timer = meterRegistry.find("error_handling_duration_milliseconds")
            .tag("error_type", errorType)
            .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
            .isCloseTo(durationMillis, org.assertj.core.data.Offset.offset(0.1));
    }

    @Test
    void testMultipleErrorHandlingPerformanceRecordings() {
        // Given
        String errorType1 = "validation_error";
        String errorType2 = "runtime_error";
        double duration1 = 5.2;
        double duration2 = 12.8;

        // When
        errorMetricsService.recordErrorHandlingPerformance(errorType1, duration1);
        errorMetricsService.recordErrorHandlingPerformance(errorType1, duration1 * 2);
        errorMetricsService.recordErrorHandlingPerformance(errorType2, duration2);

        // Then
        Timer timer1 = meterRegistry.find("error_handling_duration_milliseconds")
            .tag("error_type", errorType1)
            .timer();
        Timer timer2 = meterRegistry.find("error_handling_duration_milliseconds")
            .tag("error_type", errorType2)
            .timer();

        assertThat(timer1).isNotNull();
        assertThat(timer1.count()).isEqualTo(2);
        assertThat(timer1.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
            .isCloseTo(duration1 + (duration1 * 2), org.assertj.core.data.Offset.offset(0.1));

        assertThat(timer2).isNotNull();
        assertThat(timer2.count()).isEqualTo(1);
        assertThat(timer2.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
            .isCloseTo(duration2, org.assertj.core.data.Offset.offset(0.1));
    }

    @Test
    void testOverloadDetectionPerformanceMetrics() {
        // Given - metrics should be registered during initialization
        
        // When - checking if overload detection performance metrics are available
        var avgTimeGauge = meterRegistry.find("overload_detection_average_duration_milliseconds").gauge();
        var totalChecksGauge = meterRegistry.find("overload_detection_total_checks").gauge();

        // Then
        assertThat(avgTimeGauge).isNotNull();
        assertThat(totalChecksGauge).isNotNull();
        
        assertThat(avgTimeGauge.value()).isEqualTo(1.5); // From mock
        assertThat(totalChecksGauge.value()).isEqualTo(100.0); // From mock
    }

    @Test
    void testOverloadDetectionPerformanceMetricsWithoutDetector() {
        // Given - service without SystemOverloadDetector
        ErrorMetricsService serviceWithoutDetector = new ErrorMetricsService(meterRegistry, null);
        serviceWithoutDetector.initializeErrorMetrics();

        // When - checking if overload detection performance metrics are available
        var avgTimeGauge = meterRegistry.find("overload_detection_average_duration_milliseconds").gauge();
        var totalChecksGauge = meterRegistry.find("overload_detection_total_checks").gauge();

        // Then - metrics should not be registered
        assertThat(avgTimeGauge).isNull();
        assertThat(totalChecksGauge).isNull();
    }

    @Test
    void testErrorHandlingPerformanceWithExceptions() {
        // Given - mock that throws exception
        MeterRegistry faultyRegistry = mock(MeterRegistry.class);
        when(faultyRegistry.find(anyString())).thenThrow(new RuntimeException("Registry error"));
        
        ErrorMetricsService faultyService = new ErrorMetricsService(faultyRegistry, systemOverloadDetector);

        // When - recording performance metrics that encounter exceptions
        // Should not throw exception
        faultyService.recordErrorHandlingPerformance("test_error", 10.0);

        // Then - should handle gracefully (no exception thrown)
        // This test passes if no exception is thrown
    }

    @Test
    void testPerformanceMetricsForDifferentErrorTypes() {
        // Given - different error types with different performance characteristics
        String[] errorTypes = {"system_overload", "validation_error", "runtime_error", "generic_error"};
        double[] durations = {25.5, 2.1, 18.7, 12.3};

        // When - recording performance for each error type
        for (int i = 0; i < errorTypes.length; i++) {
            errorMetricsService.recordErrorHandlingPerformance(errorTypes[i], durations[i]);
        }

        // Then - verify each error type has its own timer
        for (int i = 0; i < errorTypes.length; i++) {
            Timer timer = meterRegistry.find("error_handling_duration_milliseconds")
                .tag("error_type", errorTypes[i])
                .timer();

            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isCloseTo(durations[i], org.assertj.core.data.Offset.offset(0.1));
        }
    }

    @Test
    void testPerformanceMetricsStatistics() {
        // Given - multiple recordings for statistical analysis
        String errorType = "test_error";
        double[] durations = {5.0, 10.0, 15.0, 20.0, 25.0};

        // When - recording multiple performance measurements
        for (double duration : durations) {
            errorMetricsService.recordErrorHandlingPerformance(errorType, duration);
        }

        // Then - verify statistical properties
        Timer timer = meterRegistry.find("error_handling_duration_milliseconds")
            .tag("error_type", errorType)
            .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(durations.length);
        
        double totalExpected = 0;
        for (double duration : durations) {
            totalExpected += duration;
        }
        
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
            .isCloseTo(totalExpected, org.assertj.core.data.Offset.offset(0.1));
        
        double meanExpected = totalExpected / durations.length;
        assertThat(timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS))
            .isCloseTo(meanExpected, org.assertj.core.data.Offset.offset(0.1));
    }

    @Test
    void testPerformanceMetricsWithZeroDuration() {
        // Given
        String errorType = "instant_error";
        double zeroDuration = 0.0;

        // When
        errorMetricsService.recordErrorHandlingPerformance(errorType, zeroDuration);

        // Then
        Timer timer = meterRegistry.find("error_handling_duration_milliseconds")
            .tag("error_type", errorType)
            .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(0.0);
    }

    @Test
    void testPerformanceMetricsWithVeryLargeDuration() {
        // Given
        String errorType = "slow_error";
        double largeDuration = 5000.0; // 5 seconds

        // When
        errorMetricsService.recordErrorHandlingPerformance(errorType, largeDuration);

        // Then
        Timer timer = meterRegistry.find("error_handling_duration_milliseconds")
            .tag("error_type", errorType)
            .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
            .isCloseTo(largeDuration, org.assertj.core.data.Offset.offset(1.0));
    }

    @Test
    void testOverloadDetectionMetricsUpdate() {
        // Given - updated values from SystemOverloadDetector
        when(systemOverloadDetector.getAverageOverloadDetectionTimeMillis()).thenReturn(2.5);
        when(systemOverloadDetector.getTotalOverloadChecks()).thenReturn(250L);

        // When - forcing metrics update
        errorMetricsService.forceMetricsUpdate();

        // Then - gauges should reflect updated values
        var avgTimeGauge = meterRegistry.find("overload_detection_average_duration_milliseconds").gauge();
        var totalChecksGauge = meterRegistry.find("overload_detection_total_checks").gauge();

        assertThat(avgTimeGauge).isNotNull();
        assertThat(totalChecksGauge).isNotNull();
        
        assertThat(avgTimeGauge.value()).isEqualTo(2.5);
        assertThat(totalChecksGauge.value()).isEqualTo(250.0);
    }
}