package org.kasbench.globeco_order_service.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.*;

class SortingSpecificationTest {

    @Test
    void parseSort_SingleField_Ascending() {
        // When
        Sort result = SortingSpecification.parseSort("quantity");

        // Then
        assertNotNull(result);
        assertEquals(1, result.stream().count());
        Sort.Order order = result.iterator().next();
        assertEquals("quantity", order.getProperty());
        assertEquals(Sort.Direction.ASC, order.getDirection());
    }

    @Test
    void parseSort_SingleField_Descending() {
        // When
        Sort result = SortingSpecification.parseSort("-quantity");

        // Then
        assertNotNull(result);
        assertEquals(1, result.stream().count());
        Sort.Order order = result.iterator().next();
        assertEquals("quantity", order.getProperty());
        assertEquals(Sort.Direction.DESC, order.getDirection());
    }

    @Test
    void parseSort_MultipleFields_MixedDirections() {
        // When
        Sort result = SortingSpecification.parseSort("security.ticker,-orderTimestamp,quantity");

        // Then
        assertNotNull(result);
        java.util.List<Sort.Order> orders = result.toList();
        assertEquals(3, orders.size());

        // First field: security.ticker ASC (mapped to securityId)
        assertEquals("securityId", orders.get(0).getProperty());
        assertEquals(Sort.Direction.ASC, orders.get(0).getDirection());

        // Second field: orderTimestamp DESC
        assertEquals("orderTimestamp", orders.get(1).getProperty());
        assertEquals(Sort.Direction.DESC, orders.get(1).getDirection());

        // Third field: quantity ASC
        assertEquals("quantity", orders.get(2).getProperty());
        assertEquals(Sort.Direction.ASC, orders.get(2).getDirection());
    }

    @Test
    void parseSort_NestedFields() {
        // When
        Sort result = SortingSpecification.parseSort("blotter.name,status.abbreviation");

        // Then
        assertNotNull(result);
        java.util.List<Sort.Order> orders = result.toList();
        assertEquals(2, orders.size());

        assertEquals("blotter.name", orders.get(0).getProperty());
        assertEquals(Sort.Direction.ASC, orders.get(0).getDirection());

        assertEquals("status.abbreviation", orders.get(1).getProperty());
        assertEquals(Sort.Direction.ASC, orders.get(1).getDirection());
    }

    @Test
    void parseSort_EmptyString() {
        // When
        Sort result = SortingSpecification.parseSort("");

        // Then
        assertNotNull(result);
        assertEquals(1, result.stream().count());
        Sort.Order order = result.iterator().next();
        assertEquals("id", order.getProperty()); // Default sort by id
        assertEquals(Sort.Direction.ASC, order.getDirection());
    }

    @Test
    void parseSort_NullString() {
        // When
        Sort result = SortingSpecification.parseSort(null);

        // Then
        assertNotNull(result);
        assertEquals(1, result.stream().count());
        Sort.Order order = result.iterator().next();
        assertEquals("id", order.getProperty()); // Default sort by id
        assertEquals(Sort.Direction.ASC, order.getDirection());
    }

    @Test
    void parseSort_WithSpaces() {
        // When
        Sort result = SortingSpecification.parseSort(" quantity , -orderTimestamp ");

        // Then
        assertNotNull(result);
        java.util.List<Sort.Order> orders = result.toList();
        assertEquals(2, orders.size());

        assertEquals("quantity", orders.get(0).getProperty());
        assertEquals(Sort.Direction.ASC, orders.get(0).getDirection());

        assertEquals("orderTimestamp", orders.get(1).getProperty());
        assertEquals(Sort.Direction.DESC, orders.get(1).getDirection());
    }

    @Test
    void parseSort_InvalidField() {
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            SortingSpecification.parseSort("invalidField"));

        assertTrue(exception.getMessage().contains("Invalid sort field: 'invalidField'"));
    }

    @Test
    void validateSortFields_ValidFields() {
        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> 
            SortingSpecification.validateSortFields("quantity,-orderTimestamp,security.ticker"));
    }

    @Test
    void validateSortFields_InvalidField() {
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            SortingSpecification.validateSortFields("quantity,invalidField"));

        assertTrue(exception.getMessage().contains("Invalid sort fields: [invalidField]"));
    }

    @Test
    void validateSortFields_NullSortParam() {
        // When & Then - Should not throw exception for null
        assertDoesNotThrow(() -> 
            SortingSpecification.validateSortFields(null));
    }

    @Test
    void validateSortFields_EmptySortParam() {
        // When & Then - Should not throw exception for empty
        assertDoesNotThrow(() -> 
            SortingSpecification.validateSortFields(""));
    }

    @Test
    void getValidSortFields_ReturnsExpectedFields() {
        // When
        java.util.Set<String> validFields = SortingSpecification.getValidSortFields();

        // Then
        assertNotNull(validFields);
        assertTrue(validFields.contains("quantity"));
        assertTrue(validFields.contains("orderTimestamp"));
        assertTrue(validFields.contains("security.ticker"));
        assertTrue(validFields.contains("portfolio.name"));
        assertTrue(validFields.contains("blotter.name"));
        assertTrue(validFields.contains("status.abbreviation"));
        assertTrue(validFields.contains("orderType.abbreviation"));
        assertTrue(validFields.contains("id"));
    }

    @Test
    void requiresExternalServiceData_WithExternalFields() {
        // When & Then
        assertTrue(SortingSpecification.requiresExternalServiceData("security.ticker"));
        assertTrue(SortingSpecification.requiresExternalServiceData("portfolio.name"));
        assertTrue(SortingSpecification.requiresExternalServiceData("quantity,security.ticker"));
        assertTrue(SortingSpecification.requiresExternalServiceData("-portfolio.name,orderTimestamp"));
    }

    @Test
    void requiresExternalServiceData_WithoutExternalFields() {
        // When & Then
        assertFalse(SortingSpecification.requiresExternalServiceData("quantity"));
        assertFalse(SortingSpecification.requiresExternalServiceData("orderTimestamp,blotter.name"));
        assertFalse(SortingSpecification.requiresExternalServiceData(null));
        assertFalse(SortingSpecification.requiresExternalServiceData(""));
    }

    @Test
    void parseSort_FieldMapping() {
        // When
        Sort result = SortingSpecification.parseSort("security.ticker,portfolio.name");

        // Then
        java.util.List<Sort.Order> orders = result.toList();
        assertEquals(2, orders.size());

        // security.ticker should be mapped to securityId
        assertEquals("securityId", orders.get(0).getProperty());
        
        // portfolio.name should be mapped to portfolioId
        assertEquals("portfolioId", orders.get(1).getProperty());
    }
} 