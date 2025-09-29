package org.kasbench.globeco_order_service.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ErrorMetricsService.
 * Tests error metrics recording, system overload tracking, and resource utilization monitoring.
 */
@ExtendWith(MockitoExtension.class)
class ErrorMetricsServiceTest {

    private MeterRegistry meterRegistry;
    
    @Mock
    private SystemOverloadDetector systemOverloadDetector;
    
    private ErrorMetricsService errorMetricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        errorMetricsService = new ErrorMetricsService(meterRegistry, systemOverloadDetector);
    }

    @Test
    @DisplayName("Should initialize error metrics successfully")
    void shouldInitializeErrorMetricsSuccessfully() {
        // Given
        when(systemOverloadDetector.getThreadPoolUtilization()).thenReturn(0.5);
        when(systemOverloadDetector.getDatabaseConnectionUtilization()).thenReturn(0.3);
        when(systemOverloadDetector.getMemoryUtilization()).thenReturn(0.7);
        
        // When
        errorMetricsService.initializeErrorMetrics();
        
        // Then
        assertTrue(errorMetricsService.isInitialized());
        
        // Verify that gauges are registered
        assertNotNull(meterRegistry.find("system_overload_current").gauge());
        assertNotNull(meterRegistry.find("system_overload_retry_after_seconds").gauge());
        assertNotNull(meterRegistry.find("system_resource_utilization").tag("resource_type", "thread_pool").gauge());
        assertNotNull(meterRegistry.find("system_resource_utilization").tag("resource_type", "database_pool").gauge());
        assertNotNull(meterRegistry.find("system_resource_utilization").tag("resource_type", "memory").gauge());
        assertNotNull(meterRegistry.find("http_requests_total").gauge());
        assertNotNull(meterRegistry.find("http_errors_total_count").gauge());
        assertNotNull(meterRegistry.find("http_error_rate_percent").gauge());
    }

    @Test
    @DisplayName("Should record error metrics correctly")
    void shouldRecordErrorMetricsCorrectly() {
        // Given
        errorMetricsService.initializeErrorMetrics();
        String errorCode = ErrorClassification.VALIDATION_ERROR;
        int httpStatus = 400;
        String severity = "LOW";
        
        // When
        errorMetricsService.recordError(errorCode, httpStatus, severity);
        
        // Then
        Counter errorCounter = meterRegistry.find("http_errors_total")
            .tag("error_code", errorCode)
            .tag("status_code", String.valueOf(httpStatus))
            .tag("severity", severity)
            .tag("retryable", "false")
            .counter();
        
        assertNotNull(errorCounter);
        assertEquals(1.0, errorCounter.count());
        assertEquals(1.0, errorMetricsService.getCurrentErrorRate()); // 1 error, 0 requests = 100% error rate initially
    }

    @Test
    @DisplayName("Should handle null error code gracefully")
    void shouldHandleNullErrorCodeGracefully() {
        // Given
        errorMetricsService.initializeErrorMetrics();
        
        // When & Then - should not throw exception
        assertDoesNotThrow(() -> {
            errorMetricsService.recordError(null, 500, "HIGH");
        });
        
        // Verify no counter was created for null error code
        assertEquals(0, meterRegistry.find("http_errors_total").counters().size());
    }

    @Test
    @DisplayName("Should handle empty error code gracefully")
    void shouldHandleEmptyErrorCodeGracefully() {
        // Given
        errorMetricsService.initializeErrorMetrics();
        
        // When & Then - should not throw exception
        assertDoesNotThrow(() -> {
            errorMetricsService.recordError("", 500, "HIGH");
        });
        
        // Verify no counter was created for empty error code
        assertEquals(0, meterRegistry.find("http_errors_total").counters().size());
    }

    @Test
    @DisplayName("Should record system overload events correctly")
    void shouldRecordSystemOverloadEventsCorrectly() {
        // Given
        errorMetricsService.initializeErrorMetrics();
        String overloadReason = "database_connection_pool_exhausted";
        int retryDelay = 300;
        
        // When
        errorMetricsService.recordSystemOverload(overloadReason, retryDelay);
        
        // Then
        Counter overloadCounter = meterRegistry.find("system_overload_events_total")
            .tag("reason", overloadReason)
            .counter();
        
        assertNotNull(overloadCounter);
        assertEquals(1.0, overloadCounter.count());
        
        // Check gauges
        Gauge currentOverloadGauge = meterRegistry.find("system_overload_current").gauge();
        Gauge retryDelayGauge = meterRegistry.find("system_overload_retry_after_seconds").gauge();
        
        assertNotNull(currentOverloadGauge);
        assertNotNull(retryDelayGauge);
        assertEquals(1.0, currentOverloadGauge.value());
        assertEquals(300.0, retryDelayGauge.value());
    }

    @Test
    @DisplayName("Should record overload recovery correctly")
    void shouldRecordOverloadRecoveryCorrectly() {
        // Given
        errorMetricsService.initializeErrorMetrics();
        errorMetricsService.recordSystemOverload("test_reason", 300);
        
        // When
        errorMetricsService.recordOverloadRecovery();
        
        // Then
        Gauge currentOverloadGauge = meterRegistry.find("system_overload_current").gauge();
        Gauge retryDelayGauge = meterRegistry.find("system_overload_retry_after_seconds").gauge();
        
        assertEquals(0.0, currentOverloadGauge.value());
        assertEquals(0.0, retryDelayGauge.value());
        
        // Verify that a duration timer was recorded
        Timer durationTimer = meterRegistry.find("system_overload_duration_seconds").timer();
        assertNotNull(durationTimer);
        assertEquals(1, durationTimer.count());
    }

    @Test
    @DisplayName("Should calculate error rate correctly")
    void shouldCalculateErrorRateCorrectly() {
        // Given
        errorMetricsService.initializeErrorMetrics();
        
        // When - record some requests and errors
        errorMetricsService.recordRequest();
        errorMetricsService.recordRequest();
        errorMetricsService.recordRequest();
        errorMetricsService.recordRequest(); // 4 total requests
        
        errorMetricsService.recordError(ErrorClassification.VALIDATION_ERROR, 400, "LOW"); // 1 error
        
        // Then
        assertEquals(25.0, errorMetricsService.getCurrentErrorRate()); // 1/4 = 25%
    }

    @Test
    @DisplayName("Should return zero error rate when no requests")
    void shouldReturnZeroErrorRateWhenNoRequests() {
        // Given
        errorMetricsService.initializeErrorMetrics();
        
        // When - no requests recorded
        double errorRate = errorMetricsService.getCurrentErrorRate();
        
        // Then
        assertEquals(0.0, errorRate);
    }

    @Test
    @DisplayName("Should track multiple error types separately")
    void shouldTrackMultipleErrorTypesSeparately() {
        // Given
        errorMetricsService.initializeErrorMetrics();
        
        // When
        errorMetricsService.recordError(ErrorClassification.VALIDATION_ERROR, 400, "LOW");
        errorMetricsService.recordError(ErrorClassification.VALIDATION_ERROR, 400, "LOW");
        errorMetricsService.recordError(ErrorClassification.SERVICE_OVERLOADED, 503, "CRITICAL");
        
        // Then
        Counter validationCounter = meterRegistry.find("http_errors_total")
            .tag("error_code", ErrorClassification.VALIDATION_ERROR)
            .counter();
        Counter overloadCounter = meterRegistry.find("http_errors_total")
            .tag("error_code", ErrorClassification.SERVICE_OVERLOADED)
            .counter();
        
        assertNotNull(validationCounter);
        assertNotNull(overloadCounter);
        assertEquals(2.0, validationCounter.count());
        assertEquals(1.0, overloadCounter.count());
    }

    @Test
    @DisplayName("Should provide metrics status information")
    void shouldProvideMetricsStatusInformation() {
        // Given
        errorMetricsService.initializeErrorMetrics();
        errorMetricsService.recordRequest();
        errorMetricsService.recordRequest();
        errorMetricsService.recordError(ErrorClassification.VALIDATION_ERROR, 400, "LOW");
        errorMetricsService.recordSystemOverload("test_reason", 300);
        
        // When
        String status = errorMetricsService.getMetricsStatus();
        
        // Then
        assertNotNull(status);
        assertTrue(status.contains("Total requests: 2"));
        assertTrue(status.contains("Total errors: 1"));
        assertTrue(status.contains("Error rate: 50.00%"));
        assertTrue(status.contains("Current overload events: 1"));
        assertTrue(status.contains("Current retry delay: 300s"));
    }

    @Test
    @DisplayName("Should handle SystemOverloadDetector unavailable gracefully")
    void shouldHandleSystemOverloadDetectorUnavailableGracefully() {
        // Given
        ErrorMetricsService serviceWithoutDetector = new ErrorMetricsService(meterRegistry, null);
        
        // When & Then - should not throw exception
        assertDoesNotThrow(() -> {
            serviceWithoutDetector.initializeErrorMetrics();
        });
        
        // Should still register basic metrics
        assertNotNull(meterRegistry.find("system_overload_current").gauge());
        assertNotNull(meterRegistry.find("http_requests_total").gauge());
    }

    @Test
    @DisplayName("Should force metrics update correctly")
    void shouldForceMetricsUpdateCorrectly() {
        // Given
        when(systemOverloadDetector.isInitialized()).thenReturn(true);
        when(systemOverloadDetector.getThreadPoolUtilization()).thenReturn(0.8);
        when(systemOverloadDetector.getDatabaseConnectionUtilization()).thenReturn(0.6);
        when(systemOverloadDetector.getMemoryUtilization()).thenReturn(0.9);
        
        errorMetricsService.initializeErrorMetrics();
        
        // When
        errorMetricsService.forceMetricsUpdate();
        
        // Then
        verify(systemOverloadDetector).getThreadPoolUtilization();
        verify(systemOverloadDetector).getDatabaseConnectionUtilization();
        verify(systemOverloadDetector).getMemoryUtilization();
    }

    @Test
    @DisplayName("Should handle metrics update errors gracefully")
    void shouldHandleMetricsUpdateErrorsGracefully() {
        // Given
        when(systemOverloadDetector.isInitialized()).thenReturn(true);
        when(systemOverloadDetector.getThreadPoolUtilization()).thenThrow(new RuntimeException("Test exception"));
        
        errorMetricsService.initializeErrorMetrics();
        
        // When & Then - should not throw exception
        assertDoesNotThrow(() -> {
            errorMetricsService.forceMetricsUpdate();
        });
    }

    @Test
    @DisplayName("Should tag retryable errors correctly")
    void shouldTagRetryableErrorsCorrectly() {
        // Given
        errorMetricsService.initializeErrorMetrics();
        
        // When
        errorMetricsService.recordError(ErrorClassification.SERVICE_OVERLOADED, 503, "CRITICAL");
        errorMetricsService.recordError(ErrorClassification.VALIDATION_ERROR, 400, "LOW");
        
        // Then
        Counter retryableCounter = meterRegistry.find("http_errors_total")
            .tag("error_code", ErrorClassification.SERVICE_OVERLOADED)
            .tag("retryable", "true")
            .counter();
        Counter nonRetryableCounter = meterRegistry.find("http_errors_total")
            .tag("error_code", ErrorClassification.VALIDATION_ERROR)
            .tag("retryable", "false")
            .counter();
        
        assertNotNull(retryableCounter);
        assertNotNull(nonRetryableCounter);
        assertEquals(1.0, retryableCounter.count());
        assertEquals(1.0, nonRetryableCounter.count());
    }

    @Test
    @DisplayName("Should track resource utilization through gauges")
    void shouldTrackResourceUtilizationThroughGauges() {
        // Given
        when(systemOverloadDetector.getThreadPoolUtilization()).thenReturn(0.75);
        when(systemOverloadDetector.getDatabaseConnectionUtilization()).thenReturn(0.85);
        when(systemOverloadDetector.getMemoryUtilization()).thenReturn(0.65);
        
        errorMetricsService.initializeErrorMetrics();
        
        // When
        Gauge threadPoolGauge = meterRegistry.find("system_resource_utilization")
            .tag("resource_type", "thread_pool").gauge();
        Gauge databasePoolGauge = meterRegistry.find("system_resource_utilization")
            .tag("resource_type", "database_pool").gauge();
        Gauge memoryGauge = meterRegistry.find("system_resource_utilization")
            .tag("resource_type", "memory").gauge();
        
        // Then
        assertNotNull(threadPoolGauge);
        assertNotNull(databasePoolGauge);
        assertNotNull(memoryGauge);
        
        assertEquals(0.75, threadPoolGauge.value(), 0.01);
        assertEquals(0.85, databasePoolGauge.value(), 0.01);
        assertEquals(0.65, memoryGauge.value(), 0.01);
    }
}