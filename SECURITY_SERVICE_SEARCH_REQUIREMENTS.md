# Security Service Search Enhancement Requirements

## Overview

The GlobeCo Order Service requires the ability to filter orders by security ticker (e.g., `security.ticker=AAPL`). Currently, the Security Service only supports lookup by `securityId`, but the Order Service needs to resolve human-readable tickers to security IDs for database filtering.

## Required Enhancement

### New Endpoint: GET /api/v1/securities

Add a new endpoint to search securities by ticker with support for exact and partial matching.

## API Specification

### Endpoint Details
```
GET /api/v1/securities
```

### Query Parameters

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| `ticker` | string | No | Search by exact ticker symbol (case-insensitive) | `AAPL` |
| `ticker_like` | string | No | Search by partial ticker match (case-insensitive) | `APP` |
| `limit` | integer | No | Maximum number of results (default: 50, max: 1000) | `10` |
| `offset` | integer | No | Number of results to skip for pagination (default: 0) | `20` |

### Parameter Validation Rules

1. **Mutual Exclusivity**: Only one of `ticker` or `ticker_like` can be provided
2. **Ticker Format**: Must be 1-50 characters, alphanumeric and dots only
3. **Limit Bounds**: Must be between 1 and 1000
4. **Offset Bounds**: Must be >= 0
5. **Required Search**: At least one search parameter (`ticker` or `ticker_like`) must be provided

### Success Response (HTTP 200)

#### Content-Type: `application/json`

#### Response Schema
```json
{
  "securities": [
    {
      "securityId": "string",
      "ticker": "string", 
      "description": "string",
      "securityTypeId": "string",
      "version": integer,
      "securityType": {
        "securityTypeId": "string",
        "abbreviation": "string",
        "description": "string",
        "version": integer
      }
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

**Exact ticker search:**
```bash
GET /api/v1/securities?ticker=AAPL
```
```json
{
  "securities": [
    {
      "securityId": "550e8400-e29b-41d4-a716-446655440000",
      "ticker": "AAPL",
      "description": "Apple Inc. Common Stock",
      "securityTypeId": "sec-type-001",
      "version": 1,
      "securityType": {
        "securityTypeId": "sec-type-001",
        "abbreviation": "CS",
        "description": "Common Stock",
        "version": 1
      }
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

**Partial ticker search:**
```bash
GET /api/v1/securities?ticker_like=APP&limit=2
```
```json
{
  "securities": [
    {
      "securityId": "550e8400-e29b-41d4-a716-446655440000",
      "ticker": "AAPL",
      "description": "Apple Inc. Common Stock",
      "securityTypeId": "sec-type-001",
      "version": 1,
      "securityType": {
        "securityTypeId": "sec-type-001",
        "abbreviation": "CS", 
        "description": "Common Stock",
        "version": 1
      }
    },
    {
      "securityId": "550e8400-e29b-41d4-a716-446655440001",
      "ticker": "APPN",
      "description": "Appian Corporation Common Stock",
      "securityTypeId": "sec-type-001", 
      "version": 1,
      "securityType": {
        "securityTypeId": "sec-type-001",
        "abbreviation": "CS",
        "description": "Common Stock", 
        "version": 1
      }
    }
  ],
  "pagination": {
    "totalElements": 15,
    "totalPages": 8,
    "currentPage": 0,
    "pageSize": 2,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

**No results found:**
```bash
GET /api/v1/securities?ticker=NONEXISTENT
```
```json
{
  "securities": [],
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

#### Missing Search Parameter
```json
{
  "error": "Bad Request",
  "message": "At least one search parameter is required: ticker or ticker_like",
  "details": {
    "validParameters": ["ticker", "ticker_like"],
    "providedParameters": []
  }
}
```

#### Conflicting Search Parameters
```json
{
  "error": "Bad Request", 
  "message": "Only one search parameter allowed: ticker or ticker_like",
  "details": {
    "conflictingParameters": ["ticker", "ticker_like"],
    "providedValues": {
      "ticker": "AAPL",
      "ticker_like": "APP"
    }
  }
}
```

#### Invalid Ticker Format
```json
{
  "error": "Bad Request",
  "message": "Invalid ticker format",
  "details": {
    "ticker": "AAPL@#$",
    "requirements": "Ticker must be 1-50 characters, alphanumeric and dots only",
    "pattern": "^[A-Za-z0-9.]{1,50}$"
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
      "provided": -5,
      "requirement": "Must be >= 0"
    }
  }
}
```

### HTTP 500 - Internal Server Error
```json
{
  "error": "Internal Server Error",
  "message": "An unexpected error occurred while searching securities",
  "requestId": "req-12345-67890"
}
```

## Implementation Requirements

### Performance Requirements
1. **Response Time**: < 200ms for exact ticker lookup
2. **Response Time**: < 500ms for partial ticker search with pagination
3. **Database Indexing**: Ensure ticker field is indexed for fast searching
4. **Case Insensitivity**: All ticker comparisons must be case-insensitive

### Search Behavior
1. **Exact Match (`ticker`)**: 
   - Case-insensitive exact match
   - Should typically return 0 or 1 result (tickers should be unique)
   - Fastest search operation
   
2. **Partial Match (`ticker_like`)**:
   - Case-insensitive substring search
   - Should support prefix, suffix, and infix matching
   - Results ordered by relevance (exact matches first, then alphabetical)

### Database Considerations
1. **Indexing**: Create database index on UPPER(ticker) for case-insensitive searching
2. **Pagination**: Use efficient pagination (LIMIT/OFFSET or cursor-based)
3. **Connection Pooling**: Ensure proper database connection management

### Integration Points
1. **Order Service Integration**: This endpoint will be called by the Order Service's `FilteringSpecification` to resolve tickers to security IDs
2. **Caching**: Consider implementing response caching for frequently searched tickers
3. **Rate Limiting**: Implement appropriate rate limiting to prevent abuse

### Testing Requirements
1. **Unit Tests**: Test all parameter validation scenarios
2. **Integration Tests**: Test database search functionality
3. **Performance Tests**: Verify response time requirements
4. **Edge Case Tests**: Test with special characters, very long tickers, empty results

### Logging Requirements
1. **Request Logging**: Log all search requests with parameters
2. **Performance Logging**: Log response times for monitoring
3. **Error Logging**: Detailed logging for all error scenarios
4. **Usage Analytics**: Track most frequently searched tickers

## Backward Compatibility
- This is a new endpoint, so no breaking changes to existing functionality
- Existing `/api/v1/security/{securityId}` endpoint remains unchanged
- Response format follows existing security response schema for consistency

## Future Enhancements (Out of Scope)
- Full-text search on security descriptions
- Advanced filtering by security type
- Bulk ticker resolution endpoint
- WebSocket-based real-time ticker updates 