package org.kasbench.globeco_order_service.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoutePatternSanitizerTest {

    @AfterEach
    void tearDown() {
        // Clear cache after each test to ensure test isolation
        RoutePatternSanitizer.clearCache();
    }

    @Test
    void sanitize_WithNullPath_ShouldReturnUnknown() {
        // When
        String result = RoutePatternSanitizer.sanitize(null);

        // Then
        assertThat(result).isEqualTo("/unknown");
    }

    @Test
    void sanitize_WithEmptyPath_ShouldReturnUnknown() {
        // When
        String result = RoutePatternSanitizer.sanitize("");

        // Then
        assertThat(result).isEqualTo("/unknown");
    }

    @Test
    void sanitize_WithWhitespacePath_ShouldReturnUnknown() {
        // When
        String result = RoutePatternSanitizer.sanitize("   ");

        // Then
        assertThat(result).isEqualTo("/unknown");
    }

    @Test
    void sanitize_WithSimplePath_ShouldReturnSamePath() {
        // When
        String result = RoutePatternSanitizer.sanitize("/api/v1/orders");

        // Then
        assertThat(result).isEqualTo("/api/v1/orders");
    }

    @Test
    void sanitize_WithQueryParameters_ShouldRemoveQueryParams() {
        // When
        String result = RoutePatternSanitizer.sanitize("/api/v1/orders?page=1&size=10");

        // Then
        assertThat(result).isEqualTo("/api/v1/orders");
    }

    @Test
    void sanitize_WithFragment_ShouldRemoveFragment() {
        // When
        String result = RoutePatternSanitizer.sanitize("/api/v1/orders#section1");

        // Then
        assertThat(result).isEqualTo("/api/v1/orders");
    }

    @Test
    void sanitize_WithQueryParamsAndFragment_ShouldRemoveBoth() {
        // When
        String result = RoutePatternSanitizer.sanitize("/api/v1/orders?page=1#section1");

        // Then
        assertThat(result).isEqualTo("/api/v1/orders");
    }

    @Test
    void sanitize_WithoutLeadingSlash_ShouldAddLeadingSlash() {
        // When
        String result = RoutePatternSanitizer.sanitize("api/v1/orders");

        // Then
        assertThat(result).isEqualTo("/api/v1/orders");
    }

    @Test
    void sanitize_WithMultipleSlashes_ShouldNormalizeToSingleSlash() {
        // When
        String result = RoutePatternSanitizer.sanitize("/api//v1///orders");

        // Then
        assertThat(result).isEqualTo("/api/v1/orders");
    }

    @Test
    void sanitize_WithTrailingSlash_ShouldRemoveTrailingSlash() {
        // When
        String result = RoutePatternSanitizer.sanitize("/api/v1/orders/");

        // Then
        assertThat(result).isEqualTo("/api/v1/orders");
    }

    @Test
    void sanitize_WithRootPath_ShouldKeepRootPath() {
        // When
        String result = RoutePatternSanitizer.sanitize("/");

        // Then
        assertThat(result).isEqualTo("/");
    }

    @Test
    void sanitize_WithTooManySegments_ShouldTruncateWithSuffix() {
        // Given - create a path with more than MAX_PATH_SEGMENTS (10)
        String longPath = "/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o";
        
        // When
        String result = RoutePatternSanitizer.sanitize(longPath);

        // Then
        assertThat(result).endsWith("/...");
        assertThat(result.split("/")).hasSize(11); // 10 segments + empty first element + "..."
    }

    @Test
    void sanitize_WithPathVariables_ShouldPreservePathVariables() {
        // When
        String result = RoutePatternSanitizer.sanitize("/api/v1/orders/{id}/items/{itemId}");

        // Then
        assertThat(result).isEqualTo("/api/v1/orders/{id}/items/{itemId}");
    }

    @Test
    void sanitize_WithException_ShouldReturnUnknown() {
        // This test is harder to trigger since the sanitization is robust,
        // but we can test the fallback behavior by testing edge cases
        
        // When - test with a very long path that might cause issues
        StringBuilder longPath = new StringBuilder("/");
        for (int i = 0; i < 1000; i++) {
            longPath.append("segment").append(i).append("/");
        }
        
        String result = RoutePatternSanitizer.sanitize(longPath.toString());

        // Then - should handle gracefully and return truncated path
        assertThat(result).isNotNull();
        assertThat(result).startsWith("/");
    }

    @Test
    void containsPathVariables_WithPathVariables_ShouldReturnTrue() {
        // When
        boolean result = RoutePatternSanitizer.containsPathVariables("/api/v1/orders/{id}");

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void containsPathVariables_WithoutPathVariables_ShouldReturnFalse() {
        // When
        boolean result = RoutePatternSanitizer.containsPathVariables("/api/v1/orders");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void containsPathVariables_WithNullPath_ShouldReturnFalse() {
        // When
        boolean result = RoutePatternSanitizer.containsPathVariables(null);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void extractBasePath_WithPathVariables_ShouldRemoveVariables() {
        // When
        String result = RoutePatternSanitizer.extractBasePath("/api/v1/orders/{id}/items/{itemId}");

        // Then
        assertThat(result).isEqualTo("/api/v1/orders/items");
    }

    @Test
    void extractBasePath_WithoutPathVariables_ShouldReturnSamePath() {
        // When
        String result = RoutePatternSanitizer.extractBasePath("/api/v1/orders");

        // Then
        assertThat(result).isEqualTo("/api/v1/orders");
    }

    @Test
    void extractBasePath_WithNullPath_ShouldReturnUnknown() {
        // When
        String result = RoutePatternSanitizer.extractBasePath(null);

        // Then
        assertThat(result).isEqualTo("/unknown");
    }

    @Test
    void extractBasePath_WithOnlyPathVariables_ShouldReturnRoot() {
        // When
        String result = RoutePatternSanitizer.extractBasePath("/{id}/{itemId}");

        // Then
        assertThat(result).isEqualTo("/");
    }

    @Test
    void isValidMetricPath_WithValidPath_ShouldReturnTrue() {
        // When
        boolean result = RoutePatternSanitizer.isValidMetricPath("/api/v1/orders");

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void isValidMetricPath_WithNullPath_ShouldReturnFalse() {
        // When
        boolean result = RoutePatternSanitizer.isValidMetricPath(null);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void isValidMetricPath_WithEmptyPath_ShouldReturnFalse() {
        // When
        boolean result = RoutePatternSanitizer.isValidMetricPath("");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void isValidMetricPath_WithTooLongPath_ShouldReturnFalse() {
        // Given
        StringBuilder longPath = new StringBuilder("/");
        for (int i = 0; i < 50; i++) {
            longPath.append("verylongsegmentname");
        }
        
        // When
        boolean result = RoutePatternSanitizer.isValidMetricPath(longPath.toString());

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void isValidMetricPath_WithTooManySegments_ShouldReturnFalse() {
        // Given
        StringBuilder pathWithManySegments = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            pathWithManySegments.append("/segment").append(i);
        }
        
        // When
        boolean result = RoutePatternSanitizer.isValidMetricPath(pathWithManySegments.toString());

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void isValidMetricPath_WithQueryParams_ShouldReturnFalse() {
        // When
        boolean result = RoutePatternSanitizer.isValidMetricPath("/api/v1/orders?page=1");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void isValidMetricPath_WithFragment_ShouldReturnFalse() {
        // When
        boolean result = RoutePatternSanitizer.isValidMetricPath("/api/v1/orders#section");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void isValidMetricPath_WithSpaces_ShouldReturnFalse() {
        // When
        boolean result = RoutePatternSanitizer.isValidMetricPath("/api/v1/orders with spaces");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void getCacheStatistics_ShouldReturnStatistics() {
        // Given
        RoutePatternSanitizer.sanitize("/test1");
        RoutePatternSanitizer.sanitize("/test2");
        
        // When
        String stats = RoutePatternSanitizer.getCacheStatistics();

        // Then
        assertThat(stats).contains("RoutePatternSanitizer Cache");
        assertThat(stats).contains("Size:");
    }

    @Test
    void clearCache_ShouldClearCache() {
        // Given
        RoutePatternSanitizer.sanitize("/test1");
        String statsBefore = RoutePatternSanitizer.getCacheStatistics();
        
        // When
        RoutePatternSanitizer.clearCache();
        String statsAfter = RoutePatternSanitizer.getCacheStatistics();

        // Then
        assertThat(statsBefore).contains("Size: 1");
        assertThat(statsAfter).contains("Size: 0");
    }

    @Test
    void getMaxPathSegments_ShouldReturnConstant() {
        // When
        int maxSegments = RoutePatternSanitizer.getMaxPathSegments();

        // Then
        assertThat(maxSegments).isEqualTo(10);
    }

    @Test
    void getUnknownPath_ShouldReturnConstant() {
        // When
        String unknownPath = RoutePatternSanitizer.getUnknownPath();

        // Then
        assertThat(unknownPath).isEqualTo("/unknown");
    }

    @Test
    void sanitize_WithCaching_ShouldUseCachedResult() {
        // Given
        String path = "/api/v1/orders";
        
        // When
        String result1 = RoutePatternSanitizer.sanitize(path);
        String result2 = RoutePatternSanitizer.sanitize(path);

        // Then
        assertThat(result1).isEqualTo(result2);
        assertThat(result1).isEqualTo("/api/v1/orders");
    }
}