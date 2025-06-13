package org.kasbench.globeco_order_service.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;
import org.kasbench.globeco_order_service.entity.Order;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FilteringSpecificationTest {

    @Test
    void createFilterSpecification_SingleFilter_SingleValue() {
        // Given
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put("status.abbreviation", "NEW");

        // When
        Specification<Order> spec = FilteringSpecification.createFilterSpecification(filterParams);

        // Then
        assertNotNull(spec);
        // Note: We can't easily test the actual SQL generation without a full JPA context,
        // but we can verify the specification was created without errors
    }

    @Test
    void createFilterSpecification_SingleFilter_MultipleValues() {
        // Given
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put("status.abbreviation", "NEW,SENT,FILLED");

        // When
        Specification<Order> spec = FilteringSpecification.createFilterSpecification(filterParams);

        // Then
        assertNotNull(spec);
    }

    @Test
    void createFilterSpecification_MultipleFilters() {
        // Given
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put("status.abbreviation", "NEW,SENT");
        filterParams.put("orderType.abbreviation", "BUY");
        filterParams.put("security.ticker", "AAPL,MSFT");

        // When
        Specification<Order> spec = FilteringSpecification.createFilterSpecification(filterParams);

        // Then
        assertNotNull(spec);
    }

    @Test
    void createFilterSpecification_NestedField() {
        // Given
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put("blotter.name", "Equity Blotter");

        // When
        Specification<Order> spec = FilteringSpecification.createFilterSpecification(filterParams);

        // Then
        assertNotNull(spec);
    }

    @Test
    void createFilterSpecification_ExternalServiceField() {
        // Given
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put("security.ticker", "AAPL,MSFT");
        filterParams.put("portfolio.name", "Portfolio1,Portfolio2");

        // When
        Specification<Order> spec = FilteringSpecification.createFilterSpecification(filterParams);

        // Then
        assertNotNull(spec);
    }

    @Test
    void createFilterSpecification_TimestampFilter() {
        // Given
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put("orderTimestamp", "2023-12-01T10:30:00Z");

        // When
        Specification<Order> spec = FilteringSpecification.createFilterSpecification(filterParams);

        // Then
        assertNotNull(spec);
    }

    @Test
    void createFilterSpecification_InvalidTimestampFormat() {
        // Given
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put("orderTimestamp", "invalid-timestamp");

        // When & Then
        // The timestamp parsing happens during specification creation, not execution
        assertDoesNotThrow(() -> {
            Specification<Order> spec = FilteringSpecification.createFilterSpecification(filterParams);
            assertNotNull(spec);
        });
    }

    @Test
    void createFilterSpecification_EmptyFilterParams() {
        // Given
        Map<String, String> filterParams = new HashMap<>();

        // When
        Specification<Order> spec = FilteringSpecification.createFilterSpecification(filterParams);

        // Then
        assertNull(spec); // Should return null for empty filters
    }

    @Test
    void createFilterSpecification_NullFilterParams() {
        // When
        Specification<Order> spec = FilteringSpecification.createFilterSpecification(null);

        // Then
        assertNull(spec); // Should return null for null filters
    }

    @Test
    void createFilterSpecification_EmptyFilterValues() {
        // Given
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put("status.abbreviation", "");
        filterParams.put("orderType.abbreviation", "   ");

        // When
        Specification<Order> spec = FilteringSpecification.createFilterSpecification(filterParams);

        // Then
        assertNotNull(spec); // Should return a Specification.where(null) even with empty values
    }

    @Test
    void createFilterSpecification_InvalidField() {
        // Given
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put("invalidField", "value");

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            FilteringSpecification.createFilterSpecification(filterParams));

        assertTrue(exception.getMessage().contains("Invalid filter field: 'invalidField'"));
    }

    @Test
    void validateFilterFields_ValidFields() {
        // Given
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put("status.abbreviation", "NEW");
        filterParams.put("security.ticker", "AAPL");
        filterParams.put("blotter.name", "Test Blotter");

        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> 
            FilteringSpecification.validateFilterFields(filterParams));
    }

    @Test
    void validateFilterFields_InvalidField() {
        // Given
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put("status.abbreviation", "NEW");
        filterParams.put("invalidField", "value");

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            FilteringSpecification.validateFilterFields(filterParams));

        assertTrue(exception.getMessage().contains("Invalid filter fields: [invalidField]"));
    }

    @Test
    void validateFilterFields_NullFilterParams() {
        // When & Then - Should not throw exception for null
        assertDoesNotThrow(() -> 
            FilteringSpecification.validateFilterFields(null));
    }

    @Test
    void validateFilterFields_EmptyFilterParams() {
        // Given
        Map<String, String> filterParams = new HashMap<>();

        // When & Then - Should not throw exception for empty
        assertDoesNotThrow(() -> 
            FilteringSpecification.validateFilterFields(filterParams));
    }

    @Test
    void getValidFilterFields_ReturnsExpectedFields() {
        // When
        java.util.Set<String> validFields = FilteringSpecification.getValidFilterFields();

        // Then
        assertNotNull(validFields);
        assertTrue(validFields.contains("security.ticker"));
        assertTrue(validFields.contains("portfolio.name"));
        assertTrue(validFields.contains("blotter.name"));
        assertTrue(validFields.contains("status.abbreviation"));
        assertTrue(validFields.contains("orderType.abbreviation"));
        assertTrue(validFields.contains("orderTimestamp"));
        assertEquals(6, validFields.size());
    }

    @Test
    void requiresExternalServiceData_WithExternalFields() {
        // Given
        Map<String, String> filterParams1 = new HashMap<>();
        filterParams1.put("security.ticker", "AAPL");

        Map<String, String> filterParams2 = new HashMap<>();
        filterParams2.put("portfolio.name", "Portfolio1");

        Map<String, String> filterParams3 = new HashMap<>();
        filterParams3.put("status.abbreviation", "NEW");
        filterParams3.put("security.ticker", "AAPL");

        // When & Then
        assertTrue(FilteringSpecification.requiresExternalServiceData(filterParams1));
        assertTrue(FilteringSpecification.requiresExternalServiceData(filterParams2));
        assertTrue(FilteringSpecification.requiresExternalServiceData(filterParams3));
    }

    @Test
    void requiresExternalServiceData_WithoutExternalFields() {
        // Given
        Map<String, String> filterParams1 = new HashMap<>();
        filterParams1.put("status.abbreviation", "NEW");

        Map<String, String> filterParams2 = new HashMap<>();
        filterParams2.put("blotter.name", "Test Blotter");
        filterParams2.put("orderType.abbreviation", "BUY");

        Map<String, String> filterParams3 = new HashMap<>();

        // When & Then
        assertFalse(FilteringSpecification.requiresExternalServiceData(filterParams1));
        assertFalse(FilteringSpecification.requiresExternalServiceData(filterParams2));
        assertFalse(FilteringSpecification.requiresExternalServiceData(filterParams3));
        assertFalse(FilteringSpecification.requiresExternalServiceData(null));
    }

    @Test
    void createFilterSpecification_ComplexScenario() {
        // Test a complex scenario with multiple filters and multiple values
        
        // Given
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put("status.abbreviation", "NEW,SENT");
        filterParams.put("orderType.abbreviation", "BUY");
        filterParams.put("security.ticker", "AAPL,MSFT,GOOGL");
        filterParams.put("blotter.name", "Equity Blotter");

        // When
        Specification<Order> spec = FilteringSpecification.createFilterSpecification(filterParams);

        // Then
        assertNotNull(spec);
        // This would generate SQL like:
        // WHERE (status.abbreviation IN ('NEW', 'SENT')) 
        //   AND (orderType.abbreviation = 'BUY')
        //   AND (securityId IN ('AAPL', 'MSFT', 'GOOGL'))
        //   AND (blotter.name = 'Equity Blotter')
    }

    @Test
    void createFilterSpecification_WithSpacesInValues() {
        // Given
        Map<String, String> filterParams = new HashMap<>();
        filterParams.put("status.abbreviation", " NEW , SENT , FILLED ");

        // When
        Specification<Order> spec = FilteringSpecification.createFilterSpecification(filterParams);

        // Then
        assertNotNull(spec);
        // Should handle trimming of values correctly
    }
} 