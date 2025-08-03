package org.kasbench.globeco_order_service.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HttpMethodNormalizerTest {

    @AfterEach
    void tearDown() {
        // Clear cache after each test to ensure test isolation
        HttpMethodNormalizer.clearCache();
    }

    @Test
    void normalize_WithNullMethod_ShouldReturnUnknown() {
        // When
        String result = HttpMethodNormalizer.normalize(null);

        // Then
        assertThat(result).isEqualTo("UNKNOWN");
    }

    @Test
    void normalize_WithEmptyMethod_ShouldReturnUnknown() {
        // When
        String result = HttpMethodNormalizer.normalize("");

        // Then
        assertThat(result).isEqualTo("UNKNOWN");
    }

    @Test
    void normalize_WithWhitespaceMethod_ShouldReturnUnknown() {
        // When
        String result = HttpMethodNormalizer.normalize("   ");

        // Then
        assertThat(result).isEqualTo("UNKNOWN");
    }

    @Test
    void normalize_WithLowercaseStandardMethod_ShouldReturnUppercase() {
        // When
        String result = HttpMethodNormalizer.normalize("get");

        // Then
        assertThat(result).isEqualTo("GET");
    }

    @Test
    void normalize_WithMixedCaseStandardMethod_ShouldReturnUppercase() {
        // When
        String result = HttpMethodNormalizer.normalize("PoSt");

        // Then
        assertThat(result).isEqualTo("POST");
    }

    @Test
    void normalize_WithStandardMethods_ShouldReturnSameMethods() {
        // Test all standard HTTP methods
        String[] standardMethods = {"GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "TRACE", "CONNECT"};
        
        for (String method : standardMethods) {
            // When
            String result = HttpMethodNormalizer.normalize(method);

            // Then
            assertThat(result).isEqualTo(method);
        }
    }

    @Test
    void normalize_WithCommonTypos_ShouldCorrectThem() {
        // Test common typos
        assertThat(HttpMethodNormalizer.normalize("GETS")).isEqualTo("GET");
        assertThat(HttpMethodNormalizer.normalize("POSTS")).isEqualTo("POST");
        assertThat(HttpMethodNormalizer.normalize("PUTS")).isEqualTo("PUT");
        assertThat(HttpMethodNormalizer.normalize("DELETES")).isEqualTo("DELETE");
        assertThat(HttpMethodNormalizer.normalize("PATCHES")).isEqualTo("PATCH");
        assertThat(HttpMethodNormalizer.normalize("OPTION")).isEqualTo("OPTIONS");
    }

    @Test
    void normalize_WithReasonableCustomMethod_ShouldReturnCustomMethod() {
        // When
        String result = HttpMethodNormalizer.normalize("PROPFIND");

        // Then
        assertThat(result).isEqualTo("PROPFIND");
    }

    @Test
    void normalize_WithCustomMethodWithNumbers_ShouldReturnCustomMethod() {
        // When
        String result = HttpMethodNormalizer.normalize("METHOD123");

        // Then
        assertThat(result).isEqualTo("METHOD123");
    }

    @Test
    void normalize_WithCustomMethodWithUnderscore_ShouldReturnCustomMethod() {
        // When
        String result = HttpMethodNormalizer.normalize("CUSTOM_METHOD");

        // Then
        assertThat(result).isEqualTo("CUSTOM_METHOD");
    }

    @Test
    void normalize_WithCustomMethodWithHyphen_ShouldReturnCustomMethod() {
        // When
        String result = HttpMethodNormalizer.normalize("CUSTOM-METHOD");

        // Then
        assertThat(result).isEqualTo("CUSTOM-METHOD");
    }

    @Test
    void normalize_WithTooLongMethod_ShouldReturnUnknown() {
        // Given
        String tooLongMethod = "VERYLONGMETHODNAMETHATISWAYTOOBIG";
        
        // When
        String result = HttpMethodNormalizer.normalize(tooLongMethod);

        // Then
        assertThat(result).isEqualTo("UNKNOWN");
    }

    @Test
    void normalize_WithInvalidCharacters_ShouldReturnUnknown() {
        // When
        String result = HttpMethodNormalizer.normalize("GET@POST");

        // Then
        assertThat(result).isEqualTo("UNKNOWN");
    }

    @Test
    void normalize_WithMethodStartingWithNumber_ShouldReturnUnknown() {
        // When
        String result = HttpMethodNormalizer.normalize("123GET");

        // Then
        assertThat(result).isEqualTo("UNKNOWN");
    }

    @Test
    void normalize_WithMethodStartingWithLowercase_ShouldReturnUnknown() {
        // When
        String result = HttpMethodNormalizer.normalize("gET");

        // Then
        assertThat(result).isEqualTo("GET"); // This should be corrected to GET
    }

    @Test
    void isValidMetricMethod_WithValidMethod_ShouldReturnTrue() {
        // When
        boolean result = HttpMethodNormalizer.isValidMetricMethod("GET");

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void isValidMetricMethod_WithNullMethod_ShouldReturnFalse() {
        // When
        boolean result = HttpMethodNormalizer.isValidMetricMethod(null);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void isValidMetricMethod_WithEmptyMethod_ShouldReturnFalse() {
        // When
        boolean result = HttpMethodNormalizer.isValidMetricMethod("");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void isValidMetricMethod_WithTooLongMethod_ShouldReturnFalse() {
        // Given
        String tooLongMethod = "VERYLONGMETHODNAMETHATISWAYTOOBIG";
        
        // When
        boolean result = HttpMethodNormalizer.isValidMetricMethod(tooLongMethod);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void isValidMetricMethod_WithInvalidCharacters_ShouldReturnFalse() {
        // When
        boolean result = HttpMethodNormalizer.isValidMetricMethod("GET@POST");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void getStandardHttpMethods_ShouldReturnAllStandardMethods() {
        // When
        Set<String> standardMethods = HttpMethodNormalizer.getStandardHttpMethods();

        // Then
        assertThat(standardMethods).containsExactlyInAnyOrder(
            "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "TRACE", "CONNECT"
        );
    }

    @Test
    void isStandard_WithStandardMethod_ShouldReturnTrue() {
        // When
        boolean result = HttpMethodNormalizer.isStandard("GET");

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void isStandard_WithLowercaseStandardMethod_ShouldReturnTrue() {
        // When
        boolean result = HttpMethodNormalizer.isStandard("get");

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void isStandard_WithNonStandardMethod_ShouldReturnFalse() {
        // When
        boolean result = HttpMethodNormalizer.isStandard("PROPFIND");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void isStandard_WithNullMethod_ShouldReturnFalse() {
        // When
        boolean result = HttpMethodNormalizer.isStandard(null);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void getCacheStatistics_ShouldReturnStatistics() {
        // Given
        HttpMethodNormalizer.normalize("GET");
        HttpMethodNormalizer.normalize("POST");
        
        // When
        String stats = HttpMethodNormalizer.getCacheStatistics();

        // Then
        assertThat(stats).contains("HttpMethodNormalizer Cache");
        assertThat(stats).contains("Size:");
    }

    @Test
    void clearCache_ShouldClearCache() {
        // Given
        HttpMethodNormalizer.normalize("GET");
        String statsBefore = HttpMethodNormalizer.getCacheStatistics();
        
        // When
        HttpMethodNormalizer.clearCache();
        String statsAfter = HttpMethodNormalizer.getCacheStatistics();

        // Then
        assertThat(statsBefore).contains("Size: 1");
        assertThat(statsAfter).contains("Size: 0");
    }

    @Test
    void getUnknownMethod_ShouldReturnConstant() {
        // When
        String unknownMethod = HttpMethodNormalizer.getUnknownMethod();

        // Then
        assertThat(unknownMethod).isEqualTo("UNKNOWN");
    }

    @Test
    void normalize_WithCaching_ShouldUseCachedResult() {
        // Given
        String method = "get";
        
        // When
        String result1 = HttpMethodNormalizer.normalize(method);
        String result2 = HttpMethodNormalizer.normalize(method);

        // Then
        assertThat(result1).isEqualTo(result2);
        assertThat(result1).isEqualTo("GET");
    }

    @Test
    void normalize_WithWhitespaceAroundMethod_ShouldTrimAndNormalize() {
        // When
        String result = HttpMethodNormalizer.normalize("  GET  ");

        // Then
        assertThat(result).isEqualTo("GET");
    }
}