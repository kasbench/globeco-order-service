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
 * 
 * NOTE: This class now requires external service integration for security.ticker 
 * and portfolio.name filtering. The OrderService is responsible for resolving
 * human-readable identifiers to actual IDs before creating specifications.
 */
public class FilteringSpecification {
    
    // Valid filterable fields
    private static final Set<String> VALID_FILTER_FIELDS = Set.of(
        "security.ticker",      // Requires external service lookup to resolve ticker -> securityId
        "portfolio.name",       // Requires external service lookup to resolve name -> portfolioId
        "blotter.name",         // Direct join with blotter table
        "status.abbreviation",  // Direct join with status table
        "orderType.abbreviation", // Direct join with orderType table
        "orderTimestamp"        // Direct field filtering
    );
    
    /**
     * Create a JPA Specification for filtering orders based on provided filter parameters.
     * 
     * IMPORTANT: For security.ticker and portfolio.name filters, the filterParams should
     * contain the resolved IDs, not the human-readable names. The calling service is
     * responsible for this resolution.
     * 
     * @param filterParams Map of filter field names to comma-separated values
     * @param resolvedSecurityIds Map of ticker -> securityId for resolved securities (optional)
     * @param resolvedPortfolioIds Map of name -> portfolioId for resolved portfolios (optional)
     * @return Specification for use with JPA repository queries
     */
    public static Specification<Order> createFilterSpecification(
            Map<String, String> filterParams, 
            Map<String, String> resolvedSecurityIds,
            Map<String, String> resolvedPortfolioIds) {
        
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
            Specification<Order> fieldSpec = createFieldSpecification(
                fieldName, filterValue, resolvedSecurityIds, resolvedPortfolioIds);
            if (fieldSpec != null) {
                spec = spec.and(fieldSpec);
            }
        }
        
        return spec;
    }
    
    /**
     * Backward compatibility method - creates specification without external service resolution.
     * This will log warnings for security.ticker and portfolio.name filters and skip them.
     * 
     * @param filterParams Map of filter field names to comma-separated values
     * @return Specification for use with JPA repository queries
     */
    public static Specification<Order> createFilterSpecification(Map<String, String> filterParams) {
        return createFilterSpecification(filterParams, null, null);
    }
    
    /**
     * Create a specification for a specific field and its filter values.
     * 
     * @param fieldName The field to filter on
     * @param filterValue Comma-separated values for OR logic
     * @param resolvedSecurityIds Map of ticker -> securityId for resolved securities
     * @param resolvedPortfolioIds Map of name -> portfolioId for resolved portfolios
     * @return Specification for this field
     */
    private static Specification<Order> createFieldSpecification(
            String fieldName, 
            String filterValue,
            Map<String, String> resolvedSecurityIds,
            Map<String, String> resolvedPortfolioIds) {
        
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
                Predicate predicate = createFieldPredicate(
                    root, criteriaBuilder, fieldName, value, 
                    resolvedSecurityIds, resolvedPortfolioIds);
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
     * @param resolvedSecurityIds Map of ticker -> securityId for resolved securities
     * @param resolvedPortfolioIds Map of name -> portfolioId for resolved portfolios
     * @return Predicate for this field and value
     */
    private static Predicate createFieldPredicate(
            Root<Order> root, 
            CriteriaBuilder criteriaBuilder, 
            String fieldName, 
            String value,
            Map<String, String> resolvedSecurityIds,
            Map<String, String> resolvedPortfolioIds) {
        
        switch (fieldName) {
            case "security.ticker":
                // Use resolved security ID if available, otherwise skip filtering
                if (resolvedSecurityIds != null && resolvedSecurityIds.containsKey(value)) {
                    String securityId = resolvedSecurityIds.get(value);
                    return criteriaBuilder.equal(root.get("securityId"), securityId);
                } else {
                    // Log warning and skip this filter - the calling service should handle this
                    System.err.println("WARNING: security.ticker filter '" + value + 
                        "' cannot be resolved. Security service lookup required.");
                    return null;
                }
                
            case "portfolio.name":
                // Use resolved portfolio ID if available, otherwise skip filtering
                if (resolvedPortfolioIds != null && resolvedPortfolioIds.containsKey(value)) {
                    String portfolioId = resolvedPortfolioIds.get(value);
                    return criteriaBuilder.equal(root.get("portfolioId"), portfolioId);
                } else {
                    // Log warning and skip this filter - the calling service should handle this
                    System.err.println("WARNING: portfolio.name filter '" + value + 
                        "' cannot be resolved. Portfolio service lookup required.");
                    return null;
                }
                
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
    
    /**
     * Extract security tickers from filter parameters.
     * 
     * @param filterParams Map of filter parameters
     * @return Set of unique security tickers that need to be resolved
     */
    public static Set<String> extractSecurityTickers(Map<String, String> filterParams) {
        if (filterParams == null || !filterParams.containsKey("security.ticker")) {
            return Collections.emptySet();
        }
        
        String tickerValue = filterParams.get("security.ticker");
        if (tickerValue == null || tickerValue.trim().isEmpty()) {
            return Collections.emptySet();
        }
        
        return Arrays.stream(tickerValue.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(java.util.stream.Collectors.toSet());
    }
    
    /**
     * Extract portfolio names from filter parameters.
     * 
     * @param filterParams Map of filter parameters
     * @return Set of unique portfolio names that need to be resolved
     */
    public static Set<String> extractPortfolioNames(Map<String, String> filterParams) {
        if (filterParams == null || !filterParams.containsKey("portfolio.name")) {
            return Collections.emptySet();
        }
        
        String nameValue = filterParams.get("portfolio.name");
        if (nameValue == null || nameValue.trim().isEmpty()) {
            return Collections.emptySet();
        }
        
        return Arrays.stream(nameValue.split(","))
                .map(String::trim)
                .filter(n -> !n.isEmpty())
                .collect(java.util.stream.Collectors.toSet());
    }
} 