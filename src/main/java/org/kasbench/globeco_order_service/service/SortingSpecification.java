package org.kasbench.globeco_order_service.service;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Utility class for handling dynamic sorting specifications.
 * Supports nested field sorting and multi-field sorting with direction control.
 */
public class SortingSpecification {
    
    // Valid sortable fields - these map to actual entity properties and joined fields
    private static final Set<String> VALID_SORT_FIELDS = Set.of(
        "id",
        "security.ticker",      // Will be handled specially since it requires external service data
        "portfolio.name",       // Will be handled specially since it requires external service data  
        "blotter.name",
        "status.abbreviation",
        "orderType.abbreviation",
        "quantity",
        "orderTimestamp"
    );
    
    // Mapping from API field names to actual entity property paths
    private static final java.util.Map<String, String> FIELD_MAPPINGS = java.util.Map.of(
        "id", "id",
        "security.ticker", "securityId",           // Sort by securityId since ticker requires external service
        "portfolio.name", "portfolioId",           // Sort by portfolioId since name requires external service
        "blotter.name", "blotter.name",
        "status.abbreviation", "status.abbreviation",
        "orderType.abbreviation", "orderType.abbreviation", 
        "quantity", "quantity",
        "orderTimestamp", "orderTimestamp"
    );
    
    /**
     * Parse sort parameter and create Spring Data Sort object.
     * 
     * @param sortParam Comma-separated list of sort fields with optional direction prefix
     * @return Sort object for use with repository queries
     * @throws IllegalArgumentException if any sort field is invalid
     */
    public static Sort parseSort(String sortParam) {
        if (sortParam == null || sortParam.trim().isEmpty()) {
            // Default sort by id ascending
            return Sort.by(Direction.ASC, "id");
        }
        
        List<Order> orders = new ArrayList<>();
        String[] sortFields = sortParam.split(",");
        
        for (String field : sortFields) {
            field = field.trim();
            if (field.isEmpty()) {
                continue;
            }
            
            Direction direction = Direction.ASC;
            String fieldName = field;
            
            // Check for descending direction prefix
            if (field.startsWith("-")) {
                direction = Direction.DESC;
                fieldName = field.substring(1);
            }
            
            // Validate field name
            if (!VALID_SORT_FIELDS.contains(fieldName)) {
                throw new IllegalArgumentException(
                    String.format("Invalid sort field: '%s'. Valid fields are: %s", 
                        fieldName, VALID_SORT_FIELDS));
            }
            
            // Map to actual entity property path
            String entityProperty = FIELD_MAPPINGS.get(fieldName);
            orders.add(new Order(direction, entityProperty));
        }
        
        if (orders.isEmpty()) {
            // Fallback to default sort
            return Sort.by(Direction.ASC, "id");
        }
        
        return Sort.by(orders);
    }
    
    /**
     * Validate that all sort fields are valid.
     * 
     * @param sortParam Comma-separated list of sort fields
     * @throws IllegalArgumentException if any field is invalid
     */
    public static void validateSortFields(String sortParam) {
        if (sortParam == null || sortParam.trim().isEmpty()) {
            return; // Empty sort is valid (uses default)
        }
        
        String[] sortFields = sortParam.split(",");
        List<String> invalidFields = new ArrayList<>();
        
        for (String field : sortFields) {
            field = field.trim();
            if (field.isEmpty()) {
                continue;
            }
            
            // Remove direction prefix for validation
            String fieldName = field.startsWith("-") ? field.substring(1) : field;
            
            if (!VALID_SORT_FIELDS.contains(fieldName)) {
                invalidFields.add(fieldName);
            }
        }
        
        if (!invalidFields.isEmpty()) {
            throw new IllegalArgumentException(
                String.format("Invalid sort fields: %s. Valid fields are: %s", 
                    invalidFields, VALID_SORT_FIELDS));
        }
    }
    
    /**
     * Get the set of valid sort field names.
     * 
     * @return Set of valid field names for sorting
     */
    public static Set<String> getValidSortFields() {
        return VALID_SORT_FIELDS;
    }
    
    /**
     * Check if sorting by external service fields (security.ticker, portfolio.name).
     * These fields require special handling since they need external service data.
     * 
     * @param sortParam Sort parameter string
     * @return true if sorting includes external service fields
     */
    public static boolean requiresExternalServiceData(String sortParam) {
        if (sortParam == null || sortParam.trim().isEmpty()) {
            return false;
        }
        
        return Arrays.stream(sortParam.split(","))
                .map(String::trim)
                .map(field -> field.startsWith("-") ? field.substring(1) : field)
                .anyMatch(field -> field.equals("security.ticker") || field.equals("portfolio.name"));
    }
} 