# Portfolio Service Search Enhancement Requirements

## Overview

The GlobeCo Order Service requires the ability to filter orders by portfolio name (e.g., `portfolio.name=TechGrowthPortfolio`). Currently, the Portfolio Service only supports lookup by `portfolioId`, but the Order Service needs to resolve human-readable portfolio names to portfolio IDs for database filtering.

## Required Enhancement

### New Endpoint: GET /api/v1/portfolios

**IMPORTANT**: This endpoint URL already exists but currently returns ALL portfolios without search capability. This requirement is to **enhance the existing endpoint** with search functionality while maintaining backward compatibility.

## API Specification

### Endpoint Details
```
GET /api/v1/portfolios
```

### Query Parameters

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| `name` | string | No | Search by exact portfolio name (case-insensitive) | `TechGrowthPortfolio` |
| `name_like` | string | No | Search by partial name match (case-insensitive) | `Tech` |
| `limit` | integer | No | Maximum number of results (default: 50, max: 1000) | `10` |
| `offset` | integer | No | Number of results to skip for pagination (default: 0) | `20` |

### Parameter Validation Rules

1. **Mutual Exclusivity**: Only one of `name` or `name_like` can be provided
2. **Name Format**: Must be 1-200 characters, alphanumeric, spaces, hyphens, and underscores only
3. **Limit Bounds**: Must be between 1 and 1000
4. **Offset Bounds**: Must be >= 0
5. **Backward Compatibility**: If no search parameters provided, return all portfolios (existing behavior)

### Success Response (HTTP 200)

#### Content-Type: `application/json`

#### Response Schema
```json
{
  "portfolios": [
    {
      "portfolioId": "string",
      "name": "string",
      "dateCreated": "string (ISO 8601 datetime)",
      "version": integer
    }
  ],
  "pagination": {
    "totalElements": integer,
    "totalPages": integer,
    "currentPage": integer,
    "pageSize": integer,
    "hasNext": boolean,
    "hasPrevious": boolean
  }
}
```

#### Example Responses

**All portfolios (backward compatibility):**
```bash
GET /api/v1/portfolios
```
```json
{
  "portfolios": [
    {
      "portfolioId": "550e8400-e29b-41d4-a716-446655440000",
      "name": "TechGrowthPortfolio",
      "dateCreated": "2024-01-15T10:30:00Z",
      "version": 1
    },
    {
      "portfolioId": "550e8400-e29b-41d4-a716-446655440001", 
      "name": "ConservativeIncomePortfolio",
      "dateCreated": "2024-01-20T14:45:00Z",
      "version": 2
    }
  ],
  "pagination": {
    "totalElements": 2,
    "totalPages": 1,
    "currentPage": 0,
    "pageSize": 50,
    "hasNext": false,
    "hasPrevious": false
  }
}
```

**Exact name search:**
```bash
GET /api/v1/portfolios?name=TechGrowthPortfolio
```
```json
{
  "portfolios": [
    {
      "portfolioId": "550e8400-e29b-41d4-a716-446655440000",
      "name": "TechGrowthPortfolio", 
      "dateCreated": "2024-01-15T10:30:00Z",
      "version": 1
    }
  ],
  "pagination": {
    "totalElements": 1,
    "totalPages": 1,
    "currentPage": 0,
    "pageSize": 50,
    "hasNext": false,
    "hasPrevious": false
  }
}
```

**Partial name search:**
```bash
GET /api/v1/portfolios?name_like=Tech&limit=5
```
```json
{
  "portfolios": [
    {
      "portfolioId": "550e8400-e29b-41d4-a716-446655440000",
      "name": "TechGrowthPortfolio",
      "dateCreated": "2024-01-15T10:30:00Z", 
      "version": 1
    },
    {
      "portfolioId": "550e8400-e29b-41d4-a716-446655440002",
      "name": "FinTechInnovationFund",
      "dateCreated": "2024-02-01T09:15:00Z",
      "version": 1
    },
    {
      "portfolioId": "550e8400-e29b-41d4-a716-446655440003",
      "name": "TechDividendPortfolio", 
      "dateCreated": "2024-02-10T16:20:00Z",
      "version": 1
    }
  ],
  "pagination": {
    "totalElements": 3,
    "totalPages": 1,
    "currentPage": 0,
    "pageSize": 5,
    "hasNext": false,
    "hasPrevious": false
  }
}
```

**No results found:**
```bash
GET /api/v1/portfolios?name=NonExistentPortfolio
```
```json
{
  "portfolios": [],
  "pagination": {
    "totalElements": 0,
    "totalPages": 0,
    "currentPage": 0,
    "pageSize": 50,
    "hasNext": false,
    "hasPrevious": false
  }
}
```

## Error Handling

### HTTP 400 - Bad Request

#### Conflicting Search Parameters
```json
{
  "error": "Bad Request",
  "message": "Only one search parameter allowed: name or name_like", 
  "details": {
    "conflictingParameters": ["name", "name_like"],
    "providedValues": {
      "name": "TechGrowthPortfolio",
      "name_like": "Tech"
    }
  }
}
```

#### Invalid Name Format
```json
{
  "error": "Bad Request",
  "message": "Invalid portfolio name format",
  "details": {
    "name": "Portfolio@#$%^&*",
    "requirements": "Name must be 1-200 characters, alphanumeric, spaces, hyphens, and underscores only",
    "pattern": "^[A-Za-z0-9 _-]{1,200}$"
  }
}
```

#### Invalid Pagination Parameters
```json
{
  "error": "Bad Request",
  "message": "Invalid pagination parameters",
  "details": {
    "limit": {
      "provided": 1001,
      "requirement": "Must be between 1 and 1000"
    },
    "offset": {
      "provided": -10,
      "requirement": "Must be >= 0"
    }
  }
}
```

#### Empty Search Parameter
```json
{
  "error": "Bad Request",
  "message": "Search parameter cannot be empty",
  "details": {
    "parameter": "name",
    "providedValue": "",
    "requirement": "Must be at least 1 character"
  }
}
```

### HTTP 500 - Internal Server Error
```json
{
  "error": "Internal Server Error",
  "message": "An unexpected error occurred while searching portfolios",
  "requestId": "req-67890-12345"
}
```

## Implementation Requirements

### Performance Requirements
1. **Response Time**: < 200ms for exact name lookup
2. **Response Time**: < 500ms for partial name search with pagination
3. **Response Time**: < 300ms for retrieving all portfolios (backward compatibility)
4. **Database Indexing**: Ensure name field is indexed for fast searching
5. **Case Insensitivity**: All name comparisons must be case-insensitive

### Search Behavior
1. **No Parameters (Existing Behavior)**:
   - Return all portfolios with pagination
   - Maintain existing response format for backward compatibility
   - Default sorting by dateCreated descending (newest first)

2. **Exact Match (`name`)**:
   - Case-insensitive exact match
   - Should typically return 0 or 1 result (names should be unique)
   - Fastest search operation

3. **Partial Match (`name_like`)**:
   - Case-insensitive substring search
   - Should support prefix, suffix, and infix matching
   - Results ordered by relevance (exact matches first, then alphabetical by name)

### Database Considerations
1. **Indexing**: Create database index on UPPER(name) for case-insensitive searching
2. **Pagination**: Use efficient pagination (LIMIT/OFFSET or cursor-based)
3. **Connection Pooling**: Ensure proper database connection management
4. **Unique Constraints**: Ensure portfolio names are unique (case-insensitive)

### Backward Compatibility Requirements
1. **Existing Behavior**: `GET /api/v1/portfolios` without parameters must continue to work exactly as before
2. **Response Format**: Must maintain the existing response schema structure
3. **Performance**: Adding search capability must not degrade performance of the existing endpoint
4. **Client Impact**: No changes required to existing clients that use the endpoint without parameters

### Integration Points
1. **Order Service Integration**: This endpoint will be called by the Order Service's `FilteringSpecification` to resolve names to portfolio IDs
2. **Caching**: Consider implementing response caching for frequently searched portfolio names
3. **Rate Limiting**: Implement appropriate rate limiting to prevent abuse
4. **Authentication**: Maintain existing authentication requirements

### Testing Requirements
1. **Backward Compatibility Tests**: Ensure existing behavior is unchanged when no search parameters provided
2. **Unit Tests**: Test all parameter validation scenarios
3. **Integration Tests**: Test database search functionality
4. **Performance Tests**: Verify response time requirements for all scenarios
5. **Edge Case Tests**: Test with special characters, very long names, empty results
6. **Case Sensitivity Tests**: Verify case-insensitive searching works correctly

### Logging Requirements
1. **Request Logging**: Log all search requests with parameters (distinguish search vs. list-all)
2. **Performance Logging**: Log response times for monitoring different query types
3. **Error Logging**: Detailed logging for all error scenarios
4. **Usage Analytics**: Track most frequently searched portfolio names
5. **Backward Compatibility Monitoring**: Track usage of old vs. new functionality

## Migration and Deployment Strategy

### Phase 1: Backward Compatible Enhancement
1. Deploy enhanced endpoint with search capability
2. Ensure existing clients continue working without changes
3. Monitor performance impact on existing functionality

### Phase 2: Order Service Integration
1. Update Order Service to use the new search capability
2. Test end-to-end filtering functionality
3. Monitor search usage patterns

### Phase 3: Optimization
1. Optimize database queries based on usage patterns
2. Implement caching if needed
3. Add monitoring and alerting

## Security Considerations
1. **Input Validation**: Strict validation of all search parameters to prevent injection attacks
2. **Rate Limiting**: Prevent abuse of search functionality
3. **Access Control**: Maintain existing access control mechanisms
4. **Audit Logging**: Log search activities for security monitoring

## Response Format Changes

### Current Response (Array)
The existing endpoint currently returns an array directly:
```json
[
  {
    "portfolioId": "string",
    "name": "string", 
    "dateCreated": "string",
    "version": integer
  }
]
```

### New Response (Object with Pagination)
The enhanced endpoint will return an object with pagination metadata:
```json
{
  "portfolios": [...],
  "pagination": {...}
}
```

**BREAKING CHANGE MITIGATION**: Consider implementing content negotiation or versioning to maintain backward compatibility for existing clients that expect the array format.

## Future Enhancements (Out of Scope)
- Full-text search on portfolio descriptions
- Advanced filtering by date ranges
- Bulk portfolio name resolution endpoint
- Portfolio search by manager or category
- Export/import functionality for portfolio searches 