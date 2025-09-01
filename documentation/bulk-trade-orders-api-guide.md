# Bulk Trade Orders API Guide

## Overview

The Bulk Trade Orders API allows client microservices to create multiple trade orders in a single atomic transaction. This enhancement provides improved performance and consistency for high-volume trading operations.

## API Endpoint

### Create Bulk Trade Orders

**Endpoint:** `POST /api/v1/tradeOrders/bulk`

**Description:** Creates multiple trade orders in a single atomic transaction. All orders succeed or all fail together.

**Content-Type:** `application/json`

## Request Structure

### BulkTradeOrderRequestDTO

```json
{
  "tradeOrders": [
    {
      "orderId": 12345,
      "portfolioId": "PORTFOLIO_001",
      "orderType": "BUY",
      "securityId": "AAPL",
      "quantity": 100.00,
      "limitPrice": 150.25,
      "tradeTimestamp": "2024-01-15T10:30:00Z",
      "blotterId": 1
    },
    {
      "orderId": 67890,
      "portfolioId": "PORTFOLIO_002", 
      "orderType": "SELL",
      "securityId": "GOOGL",
      "quantity": 50.00,
      "limitPrice": 2500.75,
      "tradeTimestamp": "2024-01-15T10:30:00Z",
      "blotterId": 1
    }
  ]
}
```

### Request Field Specifications

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `tradeOrders` | Array | Yes | 1-1000 items | Array of trade orders to create |

#### Individual Trade Order Fields (TradeOrderPostDTO)

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `orderId` | Integer | Yes | Unique | Unique identifier for the trade order |
| `portfolioId` | String | Yes | Max 50 chars | Portfolio identifier |
| `orderType` | String | Yes | BUY/SELL | Type of trade order |
| `securityId` | String | Yes | Max 50 chars | Security identifier |
| `quantity` | BigDecimal | Yes | > 0 | Quantity of securities |
| `limitPrice` | BigDecimal | Yes | > 0 | Limit price for the order |
| `tradeTimestamp` | OffsetDateTime | Yes | ISO 8601 | Timestamp of the trade |
| `blotterId` | Integer | Yes | Valid ID | Reference to blotter entity |

## Response Structure

### BulkTradeOrderResponseDTO

#### Success Response (HTTP 201)

```json
{
  "status": "SUCCESS",
  "message": "All trade orders created successfully",
  "totalRequested": 2,
  "successful": 2,
  "failed": 0,
  "results": [
    {
      "requestIndex": 0,
      "status": "SUCCESS",
      "message": "Trade order created successfully",
      "tradeOrder": {
        "id": 101,
        "orderId": 12345,
        "portfolioId": "PORTFOLIO_001",
        "orderType": "BUY",
        "securityId": "AAPL",
        "quantity": 100.00,
        "limitPrice": 150.25,
        "tradeTimestamp": "2024-01-15T10:30:00Z",
        "quantitySent": 0.00,
        "submitted": false,
        "version": 1,
        "blotter": {
          "id": 1,
          "abbreviation": "EQ",
          "name": "Equity",
          "version": 1
        }
      }
    },
    {
      "requestIndex": 1,
      "status": "SUCCESS", 
      "message": "Trade order created successfully",
      "tradeOrder": {
        "id": 102,
        "orderId": 67890,
        "portfolioId": "PORTFOLIO_002",
        "orderType": "SELL",
        "securityId": "GOOGL",
        "quantity": 50.00,
        "limitPrice": 2500.75,
        "tradeTimestamp": "2024-01-15T10:30:00Z",
        "quantitySent": 0.00,
        "submitted": false,
        "version": 1,
        "blotter": {
          "id": 1,
          "abbreviation": "EQ",
          "name": "Equity",
          "version": 1
        }
      }
    }
  ]
}
```

#### Validation Error Response (HTTP 400)

```json
{
  "status": "FAILURE",
  "message": "Bulk operation failed due to validation errors: Invalid portfolio ID: INVALID_PORTFOLIO",
  "totalRequested": 2,
  "successful": 0,
  "failed": 2,
  "results": [
    {
      "requestIndex": 0,
      "status": "FAILURE",
      "message": "Invalid portfolio ID: INVALID_PORTFOLIO",
      "tradeOrder": null
    },
    {
      "requestIndex": 1,
      "status": "FAILURE",
      "message": "Invalid portfolio ID: INVALID_PORTFOLIO", 
      "tradeOrder": null
    }
  ]
}
```

#### Database Constraint Error Response (HTTP 400)

```json
{
  "status": "FAILURE",
  "message": "Bulk operation failed due to database constraint violations",
  "totalRequested": 2,
  "successful": 0,
  "failed": 2,
  "results": [
    {
      "requestIndex": 0,
      "status": "FAILURE",
      "message": "Database constraint violation",
      "tradeOrder": null
    },
    {
      "requestIndex": 1,
      "status": "FAILURE",
      "message": "Database constraint violation",
      "tradeOrder": null
    }
  ]
}
```

#### Server Error Response (HTTP 500)

```json
{
  "status": "FAILURE",
  "message": "Bulk operation failed due to unexpected error",
  "totalRequested": 2,
  "successful": 0,
  "failed": 2,
  "results": [
    {
      "requestIndex": 0,
      "status": "FAILURE",
      "message": "Internal server error",
      "tradeOrder": null
    },
    {
      "requestIndex": 1,
      "status": "FAILURE",
      "message": "Internal server error",
      "tradeOrder": null
    }
  ]
}
```

### Response Field Specifications

#### BulkTradeOrderResponseDTO

| Field | Type | Description |
|-------|------|-------------|
| `status` | Enum | Overall operation status: SUCCESS or FAILURE |
| `message` | String | Human-readable message describing the operation result |
| `totalRequested` | Integer | Total number of trade orders in the request |
| `successful` | Integer | Number of successfully created trade orders |
| `failed` | Integer | Number of failed trade orders |
| `results` | Array | Individual results for each trade order |

#### TradeOrderResultDTO

| Field | Type | Description |
|-------|------|-------------|
| `requestIndex` | Integer | Index of the trade order in the original request array |
| `status` | Enum | Individual order status: SUCCESS or FAILURE |
| `message` | String | Status message for this specific trade order |
| `tradeOrder` | Object | Created trade order details (null on failure) |

## HTTP Status Codes

| Status Code | Description | When Used |
|-------------|-------------|-----------|
| 201 Created | Success | All trade orders created successfully |
| 400 Bad Request | Client Error | Validation errors, constraint violations, malformed JSON |
| 500 Internal Server Error | Server Error | Transaction failures, unexpected server errors |

## Error Handling

### Validation Errors

The API validates:
- Request structure and required fields
- Array size limits (1-1000 trade orders)
- Individual trade order field constraints
- Business logic validation (portfolio existence, etc.)

### Atomic Transactions

- All trade orders are processed in a single database transaction
- If any order fails, the entire batch is rolled back
- No partial success scenarios - it's all or nothing

### Error Response Format

All error responses follow the same structure as success responses but with:
- `status`: "FAILURE"
- `successful`: 0
- `failed`: Equal to `totalRequested`
- Individual `results` entries with `status`: "FAILURE" and `tradeOrder`: null

## Client Implementation Examples

### Java (Spring Boot)

```java
@RestController
public class TradingController {
    
    @Autowired
    private RestTemplate restTemplate;
    
    public BulkTradeOrderResponseDTO createBulkOrders(List<TradeOrderPostDTO> orders) {
        BulkTradeOrderRequestDTO request = new BulkTradeOrderRequestDTO(orders);
        
        try {
            ResponseEntity<BulkTradeOrderResponseDTO> response = restTemplate.postForEntity(
                "http://trade-service/api/v1/tradeOrders/bulk",
                request,
                BulkTradeOrderResponseDTO.class
            );
            
            return response.getBody();
        } catch (HttpClientErrorException e) {
            // Handle 4xx errors (validation, constraints)
            throw new ValidationException("Bulk order validation failed: " + e.getMessage());
        } catch (HttpServerErrorException e) {
            // Handle 5xx errors (server issues)
            throw new ServiceException("Bulk order processing failed: " + e.getMessage());
        }
    }
}
```

### JavaScript/Node.js

```javascript
const axios = require('axios');

async function createBulkOrders(tradeOrders) {
    const request = {
        tradeOrders: tradeOrders
    };
    
    try {
        const response = await axios.post(
            'http://trade-service/api/v1/tradeOrders/bulk',
            request,
            {
                headers: {
                    'Content-Type': 'application/json'
                }
            }
        );
        
        return response.data;
    } catch (error) {
        if (error.response) {
            // Server responded with error status
            const { status, data } = error.response;
            
            if (status >= 400 && status < 500) {
                throw new Error(`Validation error: ${data.message}`);
            } else if (status >= 500) {
                throw new Error(`Server error: ${data.message}`);
            }
        } else {
            // Network or other error
            throw new Error(`Request failed: ${error.message}`);
        }
    }
}
```

### Python

```python
import requests
from typing import List, Dict, Any

def create_bulk_orders(trade_orders: List[Dict[str, Any]]) -> Dict[str, Any]:
    request_data = {
        "tradeOrders": trade_orders
    }
    
    try:
        response = requests.post(
            "http://trade-service/api/v1/tradeOrders/bulk",
            json=request_data,
            headers={"Content-Type": "application/json"}
        )
        
        response.raise_for_status()
        return response.json()
        
    except requests.exceptions.HTTPError as e:
        if 400 <= e.response.status_code < 500:
            raise ValueError(f"Validation error: {e.response.json().get('message', 'Unknown error')}")
        elif e.response.status_code >= 500:
            raise RuntimeError(f"Server error: {e.response.json().get('message', 'Unknown error')}")
    except requests.exceptions.RequestException as e:
        raise ConnectionError(f"Request failed: {str(e)}")
```

## Best Practices

### Batch Size Optimization

- **Recommended batch size**: 100-500 orders for optimal performance
- **Maximum batch size**: 1000 orders (enforced by API)
- **Minimum batch size**: 1 order (use single order API for better performance with single orders)

### Error Handling Strategy

1. **Retry Logic**: Implement exponential backoff for 5xx errors
2. **Validation**: Pre-validate requests client-side to reduce 4xx errors
3. **Monitoring**: Log batch sizes, success rates, and error patterns
4. **Fallback**: Have a fallback to single order creation for critical orders

### Performance Considerations

- **Connection Pooling**: Use connection pooling for high-volume scenarios
- **Timeout Configuration**: Set appropriate timeouts (recommended: 30-60 seconds)
- **Concurrent Requests**: Limit concurrent bulk requests to avoid overwhelming the service
- **Monitoring**: Track response times and adjust batch sizes accordingly

### Data Validation

Before sending requests:
- Validate all required fields are present
- Check data types and formats (especially dates and decimals)
- Ensure orderId uniqueness within the batch
- Verify portfolio and blotter IDs exist in your system

### Security Considerations

- **Authentication**: Include proper authentication headers
- **Authorization**: Ensure the client has permission to create orders for specified portfolios
- **Rate Limiting**: Respect any rate limiting policies
- **Data Sanitization**: Sanitize input data to prevent injection attacks

## Monitoring and Observability

### Metrics to Track

- **Success Rate**: Percentage of successful bulk operations
- **Batch Size Distribution**: Average and distribution of batch sizes
- **Response Times**: P50, P95, P99 response time percentiles
- **Error Rates**: Breakdown by error type (validation, constraint, server)
- **Throughput**: Orders processed per second/minute

### Logging Best Practices

Include in your logs:
- Request ID for correlation
- Batch size and operation duration
- Success/failure status and error details
- Client identification for troubleshooting

## Troubleshooting

### Common Issues

1. **Validation Errors (400)**
   - Check required fields are present and correctly formatted
   - Verify orderId uniqueness within the batch
   - Ensure portfolioId and blotterId references are valid

2. **Constraint Violations (400)**
   - Check for duplicate orderIds across the system
   - Verify foreign key references (blotterId, portfolioId)
   - Ensure data meets business rule constraints

3. **Server Errors (500)**
   - Check service health and database connectivity
   - Monitor for transaction timeout issues with large batches
   - Verify sufficient database connection pool capacity

4. **Timeout Issues**
   - Reduce batch size for better performance
   - Check network connectivity and latency
   - Verify service resource allocation

### Support Information

For additional support or questions about the Bulk Trade Orders API:
- Check service health endpoints for system status
- Review application logs for detailed error information
- Monitor service metrics for performance insights
- Contact the Trade Service team for escalation

## API Versioning

- **Current Version**: v1
- **Backward Compatibility**: Changes will maintain backward compatibility within major versions
- **Deprecation Policy**: 6-month notice for breaking changes
- **Version Header**: Include `Accept: application/json` header for consistent behavior

## Rate Limiting

- **Default Limits**: 100 requests per minute per client
- **Burst Capacity**: Up to 10 concurrent requests
- **Headers**: Rate limit information included in response headers
- **Exceeded Limits**: Returns HTTP 429 with retry-after header

This comprehensive guide should enable client microservices to successfully integrate with the Bulk Trade Orders API while following best practices for reliability, performance, and maintainability.