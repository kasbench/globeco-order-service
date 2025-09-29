package org.kasbench.globeco_order_service.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kasbench.globeco_order_service.controller.GlobalExceptionHandler;
import org.kasbench.globeco_order_service.dto.ErrorResponseDTO;
import org.kasbench.globeco_order_service.exception.SystemOverloadException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Test for enhanced error logging and monitoring capabilities.
 * Verifies that structured logging, performance metrics, and error classification work correctly.
 */
@ExtendWith(MockitoExtension.class)
class ErrorLoggingEnhancementsTest {

    @Mock
    private SystemOverloadDetector systemOverloadDetector;

    @Mock
    private WebRequest webRequest;

    private MeterRegistry meterRegistry;
    private ErrorMetricsService errorMetricsService;
    private GlobalExceptionHandler globalExceptionHandler;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        // Set up metrics registry
        meterRegistry = new SimpleMeterRegistry();
        errorMetricsService = new ErrorMetricsService(meterRegistry, systemOverloadDetector);
        
        // Set up global exception handler
        globalExceptionHandler = new GlobalExceptionHandler(systemOverloadDetector, errorMetricsService);

        // Set up log capture
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        logger.setLevel(Level.DEBUG);

        // Set up mocks
        when(webRequest.getDescription(false)).thenReturn("uri=/test/endpoint");
        when(systemOverloadDetector.isInitialized()).thenReturn(true);
        when(systemOverloadDetector.getThreadPoolUtilization()).thenReturn(0.85);
        when(systemOverloadDetector.getDatabaseConnectionUtilization()).thenReturn(0.90);
        when(systemOverloadDetector.getMemoryUtilization()).thenReturn(0.75);
        when(systemOverloadDetector.calculateRetryDelay()).thenReturn(120);
        
        // Initialize error metrics
        errorMetricsService.initializeErrorMetrics();
    }

    @Test
    void testSystemOverloadStructuredLogging() {
        // Given
        SystemOverloadException exception = new SystemOverloadException(
            "Database connection pool exhausted", 60, "database_connection_pool_exhausted");

        // When
        ResponseEntity<ErrorResponseDTO> response = globalExceptionHandler.handleSystemOverload(exception, webRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("120");

        // Verify structured logging
        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents).isNotEmpty();

        ILoggingEvent overloadLogEvent = logEvents.stream()
            .filter(event -> event.getLevel() == Level.WARN)
            .filter(event -> event.getMessage().contains("System overload detected"))
            .findFirst()
            .orElse(null);

        assertThat(overloadLogEvent).isNotNull();
        assertThat(overloadLogEvent.getFormattedMessage())
            .contains("Category: SYSTEM_OVERLOAD")
            .contains("Reason: database_connection_pool_exhausted")
            .contains("RetryDelay: 120s")
            .contains("ThreadPool: 85.0%")
            .contains("DatabasePool: 90.0%")
            .contains("Memory: 75.0%")
            .contains("Request: uri=/test/endpoint");
    }

    @Test
    void testValidationErrorStructuredLogging() {
        // Given
        IllegalArgumentException exception = new IllegalArgumentException("Invalid order quantity: must be positive");

        // When
        ResponseEntity<ErrorResponseDTO> response = globalExceptionHandler.handleValidationError(exception, webRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Verify structured logging
        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents).isNotEmpty();

        ILoggingEvent validationLogEvent = logEvents.stream()
            .filter(event -> event.getLevel() == Level.DEBUG)
            .filter(event -> event.getMessage().contains("Validation error"))
            .findFirst()
            .orElse(null);

        assertThat(validationLogEvent).isNotNull();
        assertThat(validationLogEvent.getFormattedMessage())
            .contains("Category: CLIENT_ERROR")
            .contains("Message: Invalid order quantity: must be positive")
            .contains("Request: uri=/test/endpoint");
    }

    @Test
    void testRuntimeErrorStructuredLogging() {
        // Given
        RuntimeException exception = new RuntimeException("Database connection failed");

        // When
        ResponseEntity<ErrorResponseDTO> response = globalExceptionHandler.handleRuntimeError(exception, webRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        // Verify structured logging
        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents).isNotEmpty();

        ILoggingEvent runtimeLogEvent = logEvents.stream()
            .filter(event -> event.getLevel() == Level.ERROR)
            .filter(event -> event.getMessage().contains("Runtime error"))
            .findFirst()
            .orElse(null);

        assertThat(runtimeLogEvent).isNotNull();
        assertThat(runtimeLogEvent.getFormattedMessage())
            .contains("Category: RUNTIME_ERROR")
            .contains("Message: Database connection failed")
            .contains("ExceptionType: RuntimeException")
            .contains("Request: uri=/test/endpoint");
    }

    @Test
    void testPerformanceMetricsLogging() {
        // Given
        SystemOverloadException exception = new SystemOverloadException("System overloaded", 90, "test");

        // When
        globalExceptionHandler.handleSystemOverload(exception, webRequest);

        // Then
        List<ILoggingEvent> logEvents = logAppender.list;
        
        // Verify performance logging
        ILoggingEvent performanceLogEvent = logEvents.stream()
            .filter(event -> event.getLevel() == Level.DEBUG)
            .filter(event -> event.getMessage().contains("Error handling performance"))
            .findFirst()
            .orElse(null);

        assertThat(performanceLogEvent).isNotNull();
        assertThat(performanceLogEvent.getFormattedMessage())
            .contains("Type: system_overload")
            .contains("Duration:");
    }

    @Test
    void testSystemOverloadDetectorPerformanceTracking() {
        // Given - detector with some performance data
        when(systemOverloadDetector.getTotalOverloadChecks()).thenReturn(0L);
        when(systemOverloadDetector.getAverageOverloadDetectionTimeMillis()).thenReturn(0.0);

        // When - simulating overload checks
        when(systemOverloadDetector.getTotalOverloadChecks()).thenReturn(5L);
        when(systemOverloadDetector.getAverageOverloadDetectionTimeMillis()).thenReturn(1.5);

        // Then - verify performance statistics are available
        assertThat(systemOverloadDetector.getTotalOverloadChecks()).isEqualTo(5L);
        assertThat(systemOverloadDetector.getAverageOverloadDetectionTimeMillis()).isEqualTo(1.5);
    }

    @Test
    void testErrorMetricsServicePerformanceRecording() {
        // Given
        String errorType = "test_error";
        double durationMillis = 15.5;

        // When
        errorMetricsService.recordErrorHandlingPerformance(errorType, durationMillis);

        // Then - verify timer is created and recorded
        var timer = meterRegistry.find("error_handling_duration_milliseconds")
            .tag("error_type", errorType)
            .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
            .isCloseTo(durationMillis, org.assertj.core.data.Offset.offset(0.1));
    }

    @Test
    void testLogLevelsAreAppropriate() {
        // Test that different error types use appropriate log levels
        
        // System overload should use WARN level
        SystemOverloadException overloadException = new SystemOverloadException("Overloaded", 60, "test");
        globalExceptionHandler.handleSystemOverload(overloadException, webRequest);
        
        // Validation errors should use DEBUG level
        IllegalArgumentException validationException = new IllegalArgumentException("Invalid input");
        globalExceptionHandler.handleValidationError(validationException, webRequest);
        
        // Runtime errors should use ERROR level
        RuntimeException runtimeException = new RuntimeException("Runtime error");
        globalExceptionHandler.handleRuntimeError(runtimeException, webRequest);

        // Verify log levels
        List<ILoggingEvent> logEvents = logAppender.list;
        
        long warnCount = logEvents.stream().filter(event -> event.getLevel() == Level.WARN).count();
        long debugCount = logEvents.stream().filter(event -> event.getLevel() == Level.DEBUG).count();
        long errorCount = logEvents.stream().filter(event -> event.getLevel() == Level.ERROR).count();
        
        assertThat(warnCount).isGreaterThan(0); // System overload events
        assertThat(debugCount).isGreaterThan(0); // Validation errors and performance metrics
        assertThat(errorCount).isGreaterThan(0); // Runtime errors
    }
}