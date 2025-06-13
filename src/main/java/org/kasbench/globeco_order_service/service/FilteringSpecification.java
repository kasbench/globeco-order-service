package org.kasbench.globeco_order_service.service;

import org.kasbench.globeco_order_service.entity.Order;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.*;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Utility class for handling dynamic filtering specifications.
 * Supports nested field filtering, multiple values per filter (OR logic), 
 * and multiple filters (AND logic).
 */
public class FilteringSpecification {
    
    // Valid filterable fields
    private static final Set<String> VALID_FILTER_FIELDS = Set.of(
        "security.ticker",      // Will filter by securityId since ticker requires external service
        "portfolio.name",       // Will filter by portfolioId since name requires external service
        "blotter.name",
        "status.abbreviation", 
        "orderType.abbreviation",
        "orderTimestamp"
    );
    
    /**
     * Create a JPA Specification for filtering orders based on provided filter parameters.
     * 
     * @param filterParams Map of filter field names to comma-separated values
     * @return Specification for use with JPA repository queries
     */
    public static Specification<Order> createFilterSpecification(Map<String, String> filterParams) {
        if (filterParams == null || filterParams.isEmpty()) {
            return null; // No filtering
        }
        
        Specification<Order> spec = Specification.where(null);
        
        for (Map.Entry<String, String> entry : filterParams.entrySet()) {
            String fieldName = entry.getKey();
            String filterValue = entry.getValue();
            
            if (filterValue == null || filterValue.trim().isEmpty()) {
                continue; // Skip empty filter values
            }
            
            // Validate field name
            if (!VALID_FILTER_FIELDS.contains(fieldName)) {
                throw new IllegalArgumentException(
                    String.format("Invalid filter field: '%s'. Valid fields are: %s", 
                        fieldName, VALID_FILTER_FIELDS));
            }
            
            // Create specification for this field
            Specification<Order> fieldSpec = createFieldSpecification(fieldName, filterValue);
            if (fieldSpec != null) {
                spec = spec.and(fieldSpec);
            }
        }
        
        return spec;
    }
    
    /**
     * Create a specification for a specific field and its filter values.
     * 
     * @param fieldName The field to filter on
     * @param filterValue Comma-separated values for OR logic
     * @return Specification for this field
     */
    private static Specification<Order> createFieldSpecification(String fieldName, String filterValue) {
        String[] values = filterValue.split(",");
        List<String> trimmedValues = Arrays.stream(values)
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .toList();
        
        if (trimmedValues.isEmpty()) {
            return null;
        }
        
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            for (String value : trimmedValues) {
                Predicate predicate = createFieldPredicate(root, criteriaBuilder, fieldName, value);
                if (predicate != null) {
                    predicates.add(predicate);
                }
            }
            
            if (predicates.isEmpty()) {
                return null;
            }
            
            // OR logic for multiple values of the same field
            return criteriaBuilder.or(predicates.toArray(new Predicate[0]));
        };
    }
    
    /**
     * Create a predicate for a specific field and value.
     * 
     * @param root The root entity
     * @param criteriaBuilder The criteria builder
     * @param fieldName The field name
     * @param value The filter value
     * @return Predicate for this field and value
     */
    private static Predicate createFieldPredicate(Root<Order> root, CriteriaBuilder criteriaBuilder, 
                                                 String fieldName, String value) {
        switch (fieldName) {
            case "security.ticker":
                // Filter by securityId since ticker requires external service lookup
                return criteriaBuilder.equal(root.get("securityId"), value);
                
            case "portfolio.name":
                // Filter by portfolioId since name requires external service lookup
                return criteriaBuilder.equal(root.get("portfolioId"), value);
                
            case "blotter.name":
                Join<Order, ?> blotterJoin = root.join("blotter", JoinType.LEFT);
                return criteriaBuilder.equal(blotterJoin.get("name"), value);
                
            case "status.abbreviation":
                Join<Order, ?> statusJoin = root.join("status", JoinType.INNER);
                return criteriaBuilder.equal(statusJoin.get("abbreviation"), value);
                
            case "orderType.abbreviation":
                Join<Order, ?> orderTypeJoin = root.join("orderType", JoinType.INNER);
                return criteriaBuilder.equal(orderTypeJoin.get("abbreviation"), value);
                
            case "orderTimestamp":
                // Handle timestamp filtering - support ISO format
                try {
                    OffsetDateTime timestamp = OffsetDateTime.parse(value);
                    return criteriaBuilder.equal(root.get("orderTimestamp"), timestamp);
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException(
                        String.format("Invalid timestamp format for orderTimestamp: '%s'. Expected ISO format (e.g., 2023-12-01T10:30:00Z)", value));
                }
                
            default:
                throw new IllegalArgumentException("Unsupported filter field: " + fieldName);
        }
    }
    
    /**
     * Validate that all filter fields are valid.
     * 
     * @param filterParams Map of filter parameters
     * @throws IllegalArgumentException if any field is invalid
     */
    public static void validateFilterFields(Map<String, String> filterParams) {
        if (filterParams == null || filterParams.isEmpty()) {
            return; // Empty filters are valid
        }
        
        List<String> invalidFields = new ArrayList<>();
        
        for (String fieldName : filterParams.keySet()) {
            if (!VALID_FILTER_FIELDS.contains(fieldName)) {
                invalidFields.add(fieldName);
            }
        }
        
        if (!invalidFields.isEmpty()) {
            throw new IllegalArgumentException(
                String.format("Invalid filter fields: %s. Valid fields are: %s", 
                    invalidFields, VALID_FILTER_FIELDS));
        }
    }
    
    /**
     * Get the set of valid filter field names.
     * 
     * @return Set of valid field names for filtering
     */
    public static Set<String> getValidFilterFields() {
        return VALID_FILTER_FIELDS;
    }
    
    /**
     * Check if filtering by external service fields (security.ticker, portfolio.name).
     * These fields require special handling since they need external service data.
     * 
     * @param filterParams Map of filter parameters
     * @return true if filtering includes external service fields
     */
    public static boolean requiresExternalServiceData(Map<String, String> filterParams) {
        if (filterParams == null || filterParams.isEmpty()) {
            return false;
        }
        
        return filterParams.keySet().stream()
                .anyMatch(field -> field.equals("security.ticker") || field.equals("portfolio.name"));
    }
} 