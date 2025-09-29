package org.kasbench.globeco_order_service.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kasbench.globeco_order_service.controller.GlobalExceptionHandler;
import org.kasbench.globeco_order_service.dto.ErrorResponseDTO;
import org.kasbench.globeco_order_service.exception.SystemOverloadException;
import org.kasbench.globeco_order_service.service.ErrorClassification;
import org.kasbench.globeco_order_service.service.ErrorMetricsService;
import org.kasbench.globeco_order_service.service.SystemOverloadDetector;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.context.request.WebRequest;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration test for error logging and metrics emission functionality.
 * Verifies that structured logging, performance metrics, and error classification
 * work correctly across the error handling system.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "error.handling.enabled=true",
    "metrics.custom.enabled=true"
})
class ErrorLoggingAndMetricsIntegrationTest {

    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockBean
    private SystemOverloadDetector systemOverloadDetector;

    @MockBean
    private ErrorMetricsService errorMetricsService;

    @MockBean
    private WebRequest webRequest;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        // Set up log capture
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        logger.setLevel(Level.DEBUG);

        // Reset mocks
        reset(systemOverloadDetector, errorMetricsService, webRequest);

        // Default mock behavior
        when(webRequest.getDescription(false)).thenReturn("uri=/test/endpoint");
        when(systemOverloadDetector.isInitialized()).thenReturn(true);
        when(systemOverloadDetector.getThreadPoolUtilization()).thenReturn(0.85);
        when(systemOverloadDetector.getDatabaseConnectionUtilization()).thenReturn(0.90);
        when(systemOverloadDetector.getMemoryUtilization()).thenReturn(0.75);
        when(systemOverloadDetector.calculateRetryDelay()).thenReturn(120);
    }

    @Test
    void testSystemOverloadLoggingAndMetrics() {
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

        // Verify metrics recording
        verify(errorMetricsService).recordError(
            ErrorClassification.SERVICE_OVERLOADED,
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            ErrorClassification.getSeverityLevel(ErrorClassification.SERVICE_OVERLOADED)
        );
        verify(errorMetricsService).recordSystemOverload("database_connection_pool_exhausted", 120);
        verify(errorMetricsService).recordErrorHandlingPerformance(eq("system_overload"), anyDouble());

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
    void testValidationErrorLoggingAndMetrics() {
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
            .contains("Severity: " + ErrorClassification.getSeverityLevel(ErrorClassification.VALIDATION_ERROR))
            .contains("Message: Invalid order quantity: must be positive")
            .contains("Request: uri=/test/endpoint");

        // Verify metrics recording
        verify(errorMetricsService).recordError(
            ErrorClassification.VALIDATION_ERROR,
            HttpStatus.BAD_REQUEST.value(),
            ErrorClassification.getSeverityLevel(ErrorClassification.VALIDATION_ERROR)
        );
        verify(errorMetricsService).recordErrorHandlingPerformance(eq("validation_error"), anyDouble());
    }

    @Test
    void testRuntimeErrorLoggingAndMetrics() {
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
            .contains("Severity: " + ErrorClassification.getSeverityLevel(ErrorClassification.RUNTIME_ERROR))
            .contains("Message: Database connection failed")
            .contains("ExceptionType: RuntimeException")
            .contains("Request: uri=/test/endpoint");

        // Verify metrics recording
        verify(errorMetricsService).recordError(
            ErrorClassification.RUNTIME_ERROR,
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            ErrorClassification.getSeverityLevel(ErrorClassification.RUNTIME_ERROR)
        );
        verify(errorMetricsService).recordErrorHandlingPerformance(eq("runtime_error"), anyDouble());
    }

    @Test
    void testGenericErrorLoggingAndMetrics() {
        // Given
        Exception exception = new Exception("Unexpected system error");

        // When
        ResponseEntity<ErrorResponseDTO> response = globalExceptionHandler.handleGenericError(exception, webRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        // Verify structured logging
        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents).isNotEmpty();

        ILoggingEvent genericLogEvent = logEvents.stream()
            .filter(event -> event.getLevel() == Level.ERROR)
            .filter(event -> event.getMessage().contains("Unexpected error"))
            .findFirst()
            .orElse(null);

        assertThat(genericLogEvent).isNotNull();
        assertThat(genericLogEvent.getFormattedMessage())
            .contains("Category: INTERNAL_ERROR")
            .contains("Severity: " + ErrorClassification.getSeverityLevel(ErrorClassification.INTERNAL_ERROR))
            .contains("Message: Unexpected system error")
            .contains("ExceptionType: Exception")
            .contains("Request: uri=/test/endpoint");

        // Verify metrics recording
        verify(errorMetricsService).recordError(
            ErrorClassification.INTERNAL_ERROR,
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            ErrorClassification.getSeverityLevel(ErrorClassification.INTERNAL_ERROR)
        );
        verify(errorMetricsService).recordErrorHandlingPerformance(eq("generic_error"), anyDouble());
    }

    @Test
    void testErrorHandlingPerformanceMetrics() {
        // Given
        SystemOverloadException exception = new SystemOverloadException(
            "System overloaded", 90, "thread_pool_exhausted");

        // When
        long startTime = System.nanoTime();
        globalExceptionHandler.handleSystemOverload(exception, webRequest);
        long endTime = System.nanoTime();

        // Then
        double expectedMinDuration = (endTime - startTime) / 1_000_000.0; // Convert to milliseconds

        // Verify performance metrics were recorded
        verify(errorMetricsService).recordErrorHandlingPerformance(eq("system_overload"), 
            argThat(duration -> duration >= 0.0 && duration <= expectedMinDuration + 100)); // Allow some tolerance
    }

    @Test
    void testLoggingWithoutSystemOverloadDetector() {
        // Given
        when(systemOverloadDetector.isInitialized()).thenReturn(false);
        SystemOverloadException exception = new SystemOverloadException(
            "System overloaded", 60, "unknown");

        // When
        ResponseEntity<ErrorResponseDTO> response = globalExceptionHandler.handleSystemOverload(exception, webRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        // Verify logging still works without system metrics
        List<ILoggingEvent> logEvents = logAppender.list;
        ILoggingEvent overloadLogEvent = logEvents.stream()
            .filter(event -> event.getLevel() == Level.WARN)
            .filter(event -> event.getMessage().contains("System overload detected"))
            .findFirst()
            .orElse(null);

        assertThat(overloadLogEvent).isNotNull();
        assertThat(overloadLogEvent.getFormattedMessage())
            .contains("Category: SYSTEM_OVERLOAD")
            .contains("Reason: unknown")
            .contains("ThreadPool: N/A")
            .contains("DatabasePool: N/A")
            .contains("Memory: N/A");
    }

    @Test
    void testLoggingWithoutErrorMetricsService() {
        // Given - GlobalExceptionHandler with null ErrorMetricsService
        GlobalExceptionHandler handlerWithoutMetrics = new GlobalExceptionHandler(systemOverloadDetector, null);
        IllegalArgumentException exception = new IllegalArgumentException("Test validation error");

        // When
        ResponseEntity<ErrorResponseDTO> response = handlerWithoutMetrics.handleValidationError(exception, webRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Verify logging still works without metrics service
        List<ILoggingEvent> logEvents = logAppender.list;
        ILoggingEvent debugLogEvent = logEvents.stream()
            .filter(event -> event.getLevel() == Level.DEBUG)
            .filter(event -> event.getMessage().contains("ErrorMetricsService not available"))
            .findFirst()
            .orElse(null);

        assertThat(debugLogEvent).isNotNull();
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
        
        // Generic errors should use ERROR level
        Exception genericException = new Exception("Generic error");
        globalExceptionHandler.handleGenericError(genericException, webRequest);

        // Verify log levels
        List<ILoggingEvent> logEvents = logAppender.list;
        
        long warnCount = logEvents.stream().filter(event -> event.getLevel() == Level.WARN).count();
        long debugCount = logEvents.stream().filter(event -> event.getLevel() == Level.DEBUG).count();
        long errorCount = logEvents.stream().filter(event -> event.getLevel() == Level.ERROR).count();
        
        assertThat(warnCount).isGreaterThan(0); // System overload events
        assertThat(debugCount).isGreaterThan(0); // Validation errors and performance metrics
        assertThat(errorCount).isGreaterThan(0); // Runtime and generic errors
    }


}