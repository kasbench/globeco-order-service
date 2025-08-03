package org.kasbench.globeco_order_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestTimingContextTest {

    @Mock
    private HttpRequestMetricsService metricsService;

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(metricsService);
    }

    @Test
    void constructor_ShouldInitializeContextAndIncrementInFlight() {
        // When
        RequestTimingContext context = new RequestTimingContext("GET", "/api/v1/orders", metricsService);

        // Then
        assertThat(context.getMethod()).isEqualTo("GET");
        assertThat(context.getPath()).isEqualTo("/api/v1/orders");
        assertThat(context.getStartTime()).isGreaterThan(0);
        assertThat(context.isCompleted()).isFalse();
        assertThat(context.isInFlightIncremented()).isTrue();
        
        verify(metricsService).incrementInFlightRequests();
    }

    @Test
    void constructor_WithNullMethod_ShouldNormalizeToUnknown() {
        // When
        RequestTimingContext context = new RequestTimingContext(null, "/api/v1/orders", metricsService);

        // Then
        assertThat(context.getMethod()).isEqualTo("UNKNOWN");
        verify(metricsService).incrementInFlightRequests();
    }

    @Test
    void constructor_WithNullPath_ShouldSanitizeToUnknown() {
        // When
        RequestTimingContext context = new RequestTimingContext("GET", null, metricsService);

        // Then
        assertThat(context.getPath()).isEqualTo("/unknown");
        verify(metricsService).incrementInFlightRequests();
    }

    @Test
    void constructor_WithNullMetricsService_ShouldNotCrash() {
        // When
        RequestTimingContext context = new RequestTimingContext("GET", "/api/v1/orders", null);

        // Then
        assertThat(context.getMethod()).isEqualTo("GET");
        assertThat(context.getPath()).isEqualTo("/api/v1/orders");
        assertThat(context.isInFlightIncremented()).isFalse();
    }

    @Test
    void constructor_WithIncrementException_ShouldHandleGracefully() {
        // Given
        doThrow(new RuntimeException("Increment failed")).when(metricsService).incrementInFlightRequests();

        // When
        RequestTimingContext context = new RequestTimingContext("GET", "/api/v1/orders", metricsService);

        // Then
        assertThat(context.getMethod()).isEqualTo("GET");
        assertThat(context.getPath()).isEqualTo("/api/v1/orders");
        assertThat(context.isInFlightIncremented()).isFalse();
        verify(metricsService).incrementInFlightRequests();
    }

    @Test
    void complete_ShouldRecordMetricsAndDecrementInFlight() throws InterruptedException {
        // Given
        RequestTimingContext context = new RequestTimingContext("POST", "/api/v1/orders", metricsService);
        Thread.sleep(1); // Ensure some duration
        
        // When
        context.complete(201);

        // Then
        assertThat(context.isCompleted()).isTrue();
        verify(metricsService).recordRequest(eq("POST"), eq("/api/v1/orders"), eq(201), anyLong());
        verify(metricsService).decrementInFlightRequests();
    }

    @Test
    void complete_CalledMultipleTimes_ShouldBeIdempotent() {
        // Given
        RequestTimingContext context = new RequestTimingContext("GET", "/api/v1/orders", metricsService);
        
        // When
        context.complete(200);
        context.complete(200); // Second call should be ignored

        // Then
        assertThat(context.isCompleted()).isTrue();
        verify(metricsService, times(1)).recordRequest(anyString(), anyString(), anyInt(), anyLong());
        verify(metricsService, times(1)).decrementInFlightRequests();
    }

    @Test
    void complete_WithRecordException_ShouldStillDecrementInFlight() {
        // Given
        RequestTimingContext context = new RequestTimingContext("GET", "/api/v1/orders", metricsService);
        doThrow(new RuntimeException("Record failed")).when(metricsService)
            .recordRequest(anyString(), anyString(), anyInt(), anyLong());
        
        // When
        context.complete(200);

        // Then
        assertThat(context.isCompleted()).isTrue();
        verify(metricsService).recordRequest(anyString(), anyString(), anyInt(), anyLong());
        verify(metricsService).decrementInFlightRequests();
    }

    @Test
    void complete_WithDecrementException_ShouldStillMarkCompleted() {
        // Given
        RequestTimingContext context = new RequestTimingContext("GET", "/api/v1/orders", metricsService);
        doThrow(new RuntimeException("Decrement failed")).when(metricsService).decrementInFlightRequests();
        
        // When
        context.complete(200);

        // Then
        assertThat(context.isCompleted()).isTrue();
        verify(metricsService).recordRequest(anyString(), anyString(), anyInt(), anyLong());
        verify(metricsService).decrementInFlightRequests();
    }

    @Test
    void complete_WithNullMetricsService_ShouldNotCrash() {
        // Given
        RequestTimingContext context = new RequestTimingContext("GET", "/api/v1/orders", null);
        
        // When
        context.complete(200);

        // Then
        assertThat(context.isCompleted()).isTrue();
    }

    @Test
    void cleanup_WithIncompleteContext_ShouldCompleteWithUnknownStatus() {
        // Given
        RequestTimingContext context = new RequestTimingContext("GET", "/api/v1/orders", metricsService);
        
        // When
        context.cleanup();

        // Then
        assertThat(context.isCompleted()).isTrue();
        verify(metricsService).recordRequest(eq("GET"), eq("/api/v1/orders"), eq(0), anyLong());
        verify(metricsService).decrementInFlightRequests();
    }

    @Test
    void cleanup_WithAlreadyCompletedContext_ShouldNotDoAnything() {
        // Given
        RequestTimingContext context = new RequestTimingContext("GET", "/api/v1/orders", metricsService);
        context.complete(200);
        reset(metricsService); // Reset to verify no additional calls
        
        // When
        context.cleanup();

        // Then
        assertThat(context.isCompleted()).isTrue();
        verifyNoInteractions(metricsService);
    }

    @Test
    void getCurrentDuration_ShouldReturnPositiveValue() throws InterruptedException {
        // Given
        RequestTimingContext context = new RequestTimingContext("GET", "/api/v1/orders", metricsService);
        Thread.sleep(1); // Ensure some duration
        
        // When
        long duration = context.getCurrentDuration();

        // Then
        assertThat(duration).isGreaterThan(0);
    }

    @Test
    void toString_ShouldContainContextInformation() {
        // Given
        RequestTimingContext context = new RequestTimingContext("GET", "/api/v1/orders", metricsService);
        
        // When
        String result = context.toString();

        // Then
        assertThat(result).contains("GET");
        assertThat(result).contains("/api/v1/orders");
        assertThat(result).contains("completed=false");
    }

    @Test
    void constructor_WithLowercaseMethod_ShouldNormalizeToUppercase() {
        // When
        RequestTimingContext context = new RequestTimingContext("get", "/api/v1/orders", metricsService);

        // Then
        assertThat(context.getMethod()).isEqualTo("GET");
    }

    @Test
    void constructor_WithPathWithQueryParams_ShouldSanitizePath() {
        // When
        RequestTimingContext context = new RequestTimingContext("GET", "/api/v1/orders?page=1&size=10", metricsService);

        // Then
        assertThat(context.getPath()).isEqualTo("/api/v1/orders");
    }
}