package org.kasbench.globeco_order_service.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatusCodeHandlerTest {

    @AfterEach
    void tearDown() {
        // Clear cache after each test to ensure test isolation
        StatusCodeHandler.clearCache();
    }

    @Test
    void normalize_WithValidStatusCode_ShouldReturnStringValue() {
        // When
        String result = StatusCodeHandler.normalize(200);

        // Then
        assertThat(result).isEqualTo("200");
    }

    @Test
    void normalize_WithZeroStatusCode_ShouldReturnUnknown() {
        // When
        String result = StatusCodeHandler.normalize(0);

        // Then
        assertThat(result).isEqualTo("unknown");
    }

    @Test
    void normalize_WithNegativeStatusCode_ShouldReturnUnknown() {
        // When
        String result = StatusCodeHandler.normalize(-1);

        // Then
        assertThat(result).isEqualTo("unknown");
    }

    @Test
    void normalize_WithStandardStatusCodes_ShouldReturnStringValues() {
        // Test common HTTP status codes
        assertThat(StatusCodeHandler.normalize(100)).isEqualTo("100");
        assertThat(StatusCodeHandler.normalize(200)).isEqualTo("200");
        assertThat(StatusCodeHandler.normalize(301)).isEqualTo("301");
        assertThat(StatusCodeHandler.normalize(404)).isEqualTo("404");
        assertThat(StatusCodeHandler.normalize(500)).isEqualTo("500");
    }

    @Test
    void normalize_WithBoundaryStatusCodes_ShouldReturnStringValues() {
        // Test boundary values
        assertThat(StatusCodeHandler.normalize(100)).isEqualTo("100"); // Minimum valid
        assertThat(StatusCodeHandler.normalize(599)).isEqualTo("599"); // Maximum valid
    }

    @Test
    void normalize_WithNonStandardButReasonableStatusCode_ShouldReturnStringValue() {
        // When
        String result = StatusCodeHandler.normalize(299);

        // Then
        assertThat(result).isEqualTo("299");
    }

    @Test
    void normalize_WithVeryHighStatusCode_ShouldReturnUnknown() {
        // When
        String result = StatusCodeHandler.normalize(1000);

        // Then
        assertThat(result).isEqualTo("unknown");
    }

    @Test
    void isValidHttpStatusCode_WithValidCodes_ShouldReturnTrue() {
        // Test valid ranges
        assertThat(StatusCodeHandler.isValidHttpStatusCode(100)).isTrue();
        assertThat(StatusCodeHandler.isValidHttpStatusCode(200)).isTrue();
        assertThat(StatusCodeHandler.isValidHttpStatusCode(300)).isTrue();
        assertThat(StatusCodeHandler.isValidHttpStatusCode(400)).isTrue();
        assertThat(StatusCodeHandler.isValidHttpStatusCode(500)).isTrue();
        assertThat(StatusCodeHandler.isValidHttpStatusCode(599)).isTrue();
    }

    @Test
    void isValidHttpStatusCode_WithInvalidCodes_ShouldReturnFalse() {
        // Test invalid ranges
        assertThat(StatusCodeHandler.isValidHttpStatusCode(99)).isFalse();
        assertThat(StatusCodeHandler.isValidHttpStatusCode(600)).isFalse();
        assertThat(StatusCodeHandler.isValidHttpStatusCode(0)).isFalse();
        assertThat(StatusCodeHandler.isValidHttpStatusCode(-1)).isFalse();
    }

    @Test
    void getStatusCategory_WithValidCodes_ShouldReturnCorrectCategory() {
        // Test all categories
        assertThat(StatusCodeHandler.getStatusCategory(100)).isEqualTo("1xx");
        assertThat(StatusCodeHandler.getStatusCategory(200)).isEqualTo("2xx");
        assertThat(StatusCodeHandler.getStatusCategory(300)).isEqualTo("3xx");
        assertThat(StatusCodeHandler.getStatusCategory(400)).isEqualTo("4xx");
        assertThat(StatusCodeHandler.getStatusCategory(500)).isEqualTo("5xx");
    }

    @Test
    void getStatusCategory_WithInvalidCodes_ShouldReturnUnknown() {
        // Test invalid codes
        assertThat(StatusCodeHandler.getStatusCategory(99)).isEqualTo("unknown");
        assertThat(StatusCodeHandler.getStatusCategory(600)).isEqualTo("unknown");
        assertThat(StatusCodeHandler.getStatusCategory(0)).isEqualTo("unknown");
    }

    @Test
    void isSuccess_WithSuccessCodes_ShouldReturnTrue() {
        // Test 2xx success codes
        assertThat(StatusCodeHandler.isSuccess(200)).isTrue();
        assertThat(StatusCodeHandler.isSuccess(201)).isTrue();
        assertThat(StatusCodeHandler.isSuccess(204)).isTrue();
        assertThat(StatusCodeHandler.isSuccess(299)).isTrue();
    }

    @Test
    void isSuccess_WithNonSuccessCodes_ShouldReturnFalse() {
        // Test non-2xx codes
        assertThat(StatusCodeHandler.isSuccess(100)).isFalse();
        assertThat(StatusCodeHandler.isSuccess(300)).isFalse();
        assertThat(StatusCodeHandler.isSuccess(400)).isFalse();
        assertThat(StatusCodeHandler.isSuccess(500)).isFalse();
    }

    @Test
    void isClientError_WithClientErrorCodes_ShouldReturnTrue() {
        // Test 4xx client error codes
        assertThat(StatusCodeHandler.isClientError(400)).isTrue();
        assertThat(StatusCodeHandler.isClientError(404)).isTrue();
        assertThat(StatusCodeHandler.isClientError(422)).isTrue();
        assertThat(StatusCodeHandler.isClientError(499)).isTrue();
    }

    @Test
    void isClientError_WithNonClientErrorCodes_ShouldReturnFalse() {
        // Test non-4xx codes
        assertThat(StatusCodeHandler.isClientError(200)).isFalse();
        assertThat(StatusCodeHandler.isClientError(300)).isFalse();
        assertThat(StatusCodeHandler.isClientError(500)).isFalse();
    }

    @Test
    void isServerError_WithServerErrorCodes_ShouldReturnTrue() {
        // Test 5xx server error codes
        assertThat(StatusCodeHandler.isServerError(500)).isTrue();
        assertThat(StatusCodeHandler.isServerError(502)).isTrue();
        assertThat(StatusCodeHandler.isServerError(503)).isTrue();
        assertThat(StatusCodeHandler.isServerError(599)).isTrue();
    }

    @Test
    void isServerError_WithNonServerErrorCodes_ShouldReturnFalse() {
        // Test non-5xx codes
        assertThat(StatusCodeHandler.isServerError(200)).isFalse();
        assertThat(StatusCodeHandler.isServerError(300)).isFalse();
        assertThat(StatusCodeHandler.isServerError(400)).isFalse();
    }

    @Test
    void isRedirection_WithRedirectionCodes_ShouldReturnTrue() {
        // Test 3xx redirection codes
        assertThat(StatusCodeHandler.isRedirection(300)).isTrue();
        assertThat(StatusCodeHandler.isRedirection(301)).isTrue();
        assertThat(StatusCodeHandler.isRedirection(302)).isTrue();
        assertThat(StatusCodeHandler.isRedirection(399)).isTrue();
    }

    @Test
    void isRedirection_WithNonRedirectionCodes_ShouldReturnFalse() {
        // Test non-3xx codes
        assertThat(StatusCodeHandler.isRedirection(200)).isFalse();
        assertThat(StatusCodeHandler.isRedirection(400)).isFalse();
        assertThat(StatusCodeHandler.isRedirection(500)).isFalse();
    }

    @Test
    void isInformational_WithInformationalCodes_ShouldReturnTrue() {
        // Test 1xx informational codes
        assertThat(StatusCodeHandler.isInformational(100)).isTrue();
        assertThat(StatusCodeHandler.isInformational(101)).isTrue();
        assertThat(StatusCodeHandler.isInformational(199)).isTrue();
    }

    @Test
    void isInformational_WithNonInformationalCodes_ShouldReturnFalse() {
        // Test non-1xx codes
        assertThat(StatusCodeHandler.isInformational(200)).isFalse();
        assertThat(StatusCodeHandler.isInformational(300)).isFalse();
        assertThat(StatusCodeHandler.isInformational(400)).isFalse();
    }

    @Test
    void getStatusDescription_WithCommonCodes_ShouldReturnDescriptions() {
        // Test common status code descriptions
        assertThat(StatusCodeHandler.getStatusDescription(200)).isEqualTo("OK");
        assertThat(StatusCodeHandler.getStatusDescription(201)).isEqualTo("Created");
        assertThat(StatusCodeHandler.getStatusDescription(204)).isEqualTo("No Content");
        assertThat(StatusCodeHandler.getStatusDescription(400)).isEqualTo("Bad Request");
        assertThat(StatusCodeHandler.getStatusDescription(401)).isEqualTo("Unauthorized");
        assertThat(StatusCodeHandler.getStatusDescription(403)).isEqualTo("Forbidden");
        assertThat(StatusCodeHandler.getStatusDescription(404)).isEqualTo("Not Found");
        assertThat(StatusCodeHandler.getStatusDescription(405)).isEqualTo("Method Not Allowed");
        assertThat(StatusCodeHandler.getStatusDescription(409)).isEqualTo("Conflict");
        assertThat(StatusCodeHandler.getStatusDescription(422)).isEqualTo("Unprocessable Entity");
        assertThat(StatusCodeHandler.getStatusDescription(500)).isEqualTo("Internal Server Error");
        assertThat(StatusCodeHandler.getStatusDescription(502)).isEqualTo("Bad Gateway");
        assertThat(StatusCodeHandler.getStatusDescription(503)).isEqualTo("Service Unavailable");
        assertThat(StatusCodeHandler.getStatusDescription(504)).isEqualTo("Gateway Timeout");
    }

    @Test
    void getStatusDescription_WithUncommonValidCode_ShouldReturnCategoryDescription() {
        // When
        String result = StatusCodeHandler.getStatusDescription(299);

        // Then
        assertThat(result).isEqualTo("2xx Response");
    }

    @Test
    void getStatusDescription_WithInvalidCode_ShouldReturnUnknownStatus() {
        // When
        String result = StatusCodeHandler.getStatusDescription(999);

        // Then
        assertThat(result).isEqualTo("Unknown Status");
    }

    @Test
    void isValidMetricStatusCode_WithValidCodes_ShouldReturnTrue() {
        // Test reasonable status codes for metrics
        assertThat(StatusCodeHandler.isValidMetricStatusCode(200)).isTrue();
        assertThat(StatusCodeHandler.isValidMetricStatusCode(404)).isTrue();
        assertThat(StatusCodeHandler.isValidMetricStatusCode(500)).isTrue();
        assertThat(StatusCodeHandler.isValidMetricStatusCode(999)).isTrue();
    }

    @Test
    void isValidMetricStatusCode_WithInvalidCodes_ShouldReturnFalse() {
        // Test invalid codes for metrics
        assertThat(StatusCodeHandler.isValidMetricStatusCode(0)).isFalse();
        assertThat(StatusCodeHandler.isValidMetricStatusCode(-1)).isFalse();
        assertThat(StatusCodeHandler.isValidMetricStatusCode(1000)).isFalse();
    }

    @Test
    void getCacheStatistics_ShouldReturnStatistics() {
        // Given
        StatusCodeHandler.normalize(200);
        StatusCodeHandler.normalize(404);
        
        // When
        String stats = StatusCodeHandler.getCacheStatistics();

        // Then
        assertThat(stats).contains("StatusCodeHandler Cache");
        assertThat(stats).contains("Size:");
    }

    @Test
    void clearCache_ShouldClearCache() {
        // Given
        StatusCodeHandler.normalize(200);
        String statsBefore = StatusCodeHandler.getCacheStatistics();
        
        // When
        StatusCodeHandler.clearCache();
        String statsAfter = StatusCodeHandler.getCacheStatistics();

        // Then
        assertThat(statsBefore).contains("Size: 1");
        assertThat(statsAfter).contains("Size: 0");
    }

    @Test
    void getUnknownStatus_ShouldReturnConstant() {
        // When
        String unknownStatus = StatusCodeHandler.getUnknownStatus();

        // Then
        assertThat(unknownStatus).isEqualTo("unknown");
    }

    @Test
    void normalize_WithCaching_ShouldUseCachedResult() {
        // Given
        int statusCode = 200;
        
        // When
        String result1 = StatusCodeHandler.normalize(statusCode);
        String result2 = StatusCodeHandler.normalize(statusCode);

        // Then
        assertThat(result1).isEqualTo(result2);
        assertThat(result1).isEqualTo("200");
    }

    @Test
    void normalize_WithEdgeCaseStatusCode_ShouldHandleGracefully() {
        // Test with edge case that might be in non-standard range but reasonable
        // When
        String result = StatusCodeHandler.normalize(700);

        // Then
        assertThat(result).isEqualTo("700"); // Should accept as reasonable custom code
    }
}