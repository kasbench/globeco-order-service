# **GlobeCo Order Service - Complete API Usage Guide**
## **Comprehensive Guide for LLM Integration**

### **Overview**
This document provides complete instructions for interacting with the GlobeCo Order Service API. The service provides endpoints for managing orders, statuses, order types, and blotters with comprehensive CRUD operations and batch processing capabilities.

**Base URL:** `http://localhost:8081/api/v1`

**Key Features:**
- **Batch Order Processing**: Create up to 1000 orders in a single request
- **Non-atomic Processing**: Individual failures don't affect other operations
- **Optimistic Locking**: Version-based concurrency control
- **Comprehensive Error Handling**: Detailed validation and error messages
- **RESTful Design**: Standard HTTP methods and status codes

---

## **🎯 Quick Reference - All Endpoints**

### **Orders**
| Method | Path | Description |
|--------|------|-------------|
| GET | `/orders` | List all orders |
| GET | `/order/{id}` | Get order by ID |
| POST | `/orders` | Create orders (batch) |
| PUT | `/order/{id}` | Update order |
| DELETE | `/order/{id}?version={version}` | Delete order |
| POST | `/orders/{id}/submit` | Submit order to trade service |

### **Statuses**
| Method | Path | Description |
|--------|------|-------------|
| GET | `/statuses` | List all statuses |
| GET | `/status/{id}` | Get status by ID |
| POST | `/statuses` | Create status |
| PUT | `/status/{id}` | Update status |
| DELETE | `/status/{id}?version={version}` | Delete status |

### **Order Types**
| Method | Path | Description |
|--------|------|-------------|
| GET | `/orderTypes` | List all order types |
| GET | `/orderTypes/{id}` | Get order type by ID |
| POST | `/orderTypes` | Create order type |
| PUT | `/orderType/{id}` | Update order type |
| DELETE | `/orderType/{id}?version={version}` | Delete order type |

### **Blotters**
| Method | Path | Description |
|--------|------|-------------|
| GET | `/blotters` | List all blotters |
| GET | `/blotter/{id}` | Get blotter by ID |
| POST | `/blotters` | Create blotter |
| PUT | `/blotter/{id}` | Update blotter |
| DELETE | `/blotter/{id}?version={version}` | Delete blotter |

---

## **🎯 Advanced Query Examples**

### **Pagination Examples**
```http
# Get first 25 orders
GET /api/v1/orders?limit=25

# Get orders 51-100 (skip first 50)
GET /api/v1/orders?limit=50&offset=50

# Get next page with custom page size
GET /api/v1/orders?limit=10&offset=20
```

### **Sorting Examples**
```http
# Sort by security ticker (ascending)
GET /api/v1/orders?sort=security.ticker

# Sort by order timestamp (descending)
GET /api/v1/orders?sort=-orderTimestamp

# Multi-field sorting: portfolio name ascending, then quantity descending
GET /api/v1/orders?sort=portfolio.name,-quantity

# Complex sorting with pagination
GET /api/v1/orders?sort=-orderTimestamp,security.ticker&limit=25&offset=0
```

### **Filtering Examples**
```http
# Filter by single security ticker
GET /api/v1/orders?security.ticker=IBM

# Filter by multiple security tickers (OR logic)
GET /api/v1/orders?security.ticker=IBM,AAPL,MSFT

# Filter by portfolio name
GET /api/v1/orders?portfolio.name=Growth Fund

# Filter by status (multiple values)
GET /api/v1/orders?status.abbreviation=NEW,SENT,FILLED

# Filter by order type
GET /api/v1/orders?orderType.abbreviation=BUY

# Multiple filters (AND logic between different fields)
GET /api/v1/orders?security.ticker=IBM&status.abbreviation=NEW&portfolio.name=Growth Fund
```

### **Combined Examples**
```http
# Complete example: pagination + sorting + filtering
GET /api/v1/orders?limit=50&offset=100&sort=security.ticker,-orderTimestamp&security.ticker=IBM&status.abbreviation=NEW,SENT

# Performance-optimized query
GET /api/v1/orders?limit=25&sort=id&status.abbreviation=NEW

# Complex business query
GET /api/v1/orders?portfolio.name=Growth Fund,Tech Fund&orderType.abbreviation=BUY&sort=portfolio.name,-quantity&limit=100
```

### **Error Handling Examples**
```http
# Invalid sort field
GET /api/v1/orders?sort=invalidField
# Returns 400: Invalid sort fields: [invalidField]. Valid fields are: [id, security.ticker, ...]

# Invalid filter field
GET /api/v1/orders?invalidField=value
# Returns 400: Invalid filter fields: [invalidField]. Valid fields are: [security.ticker, ...]

# Invalid limit
GET /api/v1/orders?limit=2000
# Returns 400: Limit must be between 1 and 1000
```

---

## **📊 Data Transfer Objects (DTOs)**

### **OrderPostDTO** (Create Orders)
```json
{
  "blotterId": 1,                                    // Required: Integer, nullable
  "statusId": 1,                                     // Required: Integer
  "portfolioId": "PORT123456789012345678",           // Required: String (max 24 chars)
  "orderTypeId": 2,                                  // Required: Integer
  "securityId": "SEC123456789012345678901",          // Required: String
  "quantity": 100.00000000,                          // Required: Decimal (positive)
  "limitPrice": 50.25000000,                         // Optional: Decimal (positive if provided)
  "tradeOrderId": 12345,                             // Optional: Integer
  "orderTimestamp": "2024-06-01T12:00:00Z",          // Required: ISO 8601 datetime
  "version": 1                                       // Required: Integer
}
```

### **OrderWithDetailsDTO** (Response)
```json
{
  "id": 101,
  "blotter": {
    "id": 1,
    "name": "Default Blotter",
    "version": 1
  },
  "status": {
    "id": 1,
    "abbreviation": "NEW",
    "description": "New Order",
    "version": 1
  },
  "security": {
    "securityId": "SEC123456789012345678901",
    "ticker": "IBM"
  },
  "portfolio": {
    "portfolioId": "PORT123456789012345678",
    "name": "Growth Fund"
  },
  "orderType": {
    "id": 2,
    "abbreviation": "BUY",
    "description": "Buy Order",
    "version": 1
  },
  "quantity": 100.00000000,
  "limitPrice": 50.25000000,
  "tradeOrderId": 12345,
  "orderTimestamp": "2024-06-01T12:00:00Z",
  "version": 1
}
```

### **OrderPageResponseDTO** (Paginated Response)
```json
{
  "content": [
    {
      "id": 101,
      "blotter": { "id": 1, "name": "Default", "version": 1 },
      "status": { "id": 1, "abbreviation": "NEW", "description": "New", "version": 1 },
      "security": { "securityId": "SEC123456789012345678901", "ticker": "IBM" },
      "portfolio": { "portfolioId": "PORT123456789012345678", "name": "Growth Fund" },
      "orderType": { "id": 2, "abbreviation": "BUY", "description": "Buy", "version": 1 },
      "quantity": 100.00000000,
      "limitPrice": 50.25000000,
      "tradeOrderId": 12345,
      "orderTimestamp": "2024-06-01T12:00:00Z",
      "version": 1
    }
  ],
  "pagination": {
    "pageSize": 50,
    "offset": 0,
    "totalElements": 150,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

### **SecurityDTO** (External Service Integration)
```json
{
  "securityId": "SEC123456789012345678901",
  "ticker": "IBM"
}
```

### **PortfolioDTO** (External Service Integration)
```json
{
  "portfolioId": "PORT123456789012345678",
  "name": "Growth Fund"
}
```

### **OrderListResponseDTO** (Batch Response)
```json
{
  "status": "SUCCESS|PARTIAL|FAILURE",
  "message": "Human-readable summary",
  "totalReceived": 2,
  "successful": 2,
  "failed": 0,
  "orders": [
    {
      "status": "SUCCESS|FAILURE",
      "message": "Individual result message",
      "orderDetails": "OrderWithDetailsDTO object or null",
      "orderId": 101,
      "requestIndex": 0
    }
  ]
}
```

### **Status/OrderType/Blotter DTOs**
```json
// For GET and PUT
{
  "id": 1,
  "abbreviation": "NEW",  // or "name" for Blotter
  "description": "New Order",
  "version": 1
}

// For POST (no id field)
{
  "abbreviation": "NEW",  // or "name" for Blotter
  "description": "New Order",
  "version": 1
}
```

---

## **⚡ Performance Recommendations**

### **Caching Benefits**
The order service implements **Caffeine caching** for external service data with significant performance improvements:
- **Security data**: 80-95% cache hit rate, 5-minute TTL
- **Portfolio data**: 80-95% cache hit rate, 5-minute TTL
- **Performance improvement**: 3-5x faster response times for cached data

### **Query Optimization Tips**
1. **Use pagination**: Always specify reasonable `limit` values (25-100) for large datasets
2. **Efficient sorting**: Sort by indexed fields (`id`, `orderTimestamp`) when possible
3. **Targeted filtering**: Use specific filters to reduce result sets before sorting
4. **Combine operations**: Use pagination + filtering + sorting in single requests

### **Best Practices**
```http
# ✅ Good: Efficient paginated query
GET /api/v1/orders?limit=50&sort=id&status.abbreviation=NEW

# ✅ Good: Targeted filtering with reasonable pagination
GET /api/v1/orders?limit=25&security.ticker=IBM,AAPL&sort=orderTimestamp

# ❌ Avoid: Large result sets without pagination
GET /api/v1/orders?limit=1000

# ❌ Avoid: Complex sorting on non-indexed fields with large datasets
GET /api/v1/orders?sort=portfolio.name,security.ticker&limit=500
```

### **Error Handling**
- **400 Bad Request**: Invalid parameters, field validation errors
- **500 Internal Server Error**: External service failures (with graceful degradation)
- **Timeout handling**: External services have 5-second timeouts with fallback behavior

---

## **🚀 Order Management API**

### **1. List All Orders (with Pagination, Sorting, and Filtering)**
```http
GET /api/v1/orders
GET /api/v1/orders?limit=50&offset=0
GET /api/v1/orders?sort=security.ticker,-orderTimestamp
GET /api/v1/orders?security.ticker=IBM,AAPL&status.abbreviation=NEW
GET /api/v1/orders?limit=25&offset=50&sort=portfolio.name&blotter.name=Default
```

**Query Parameters:**
- **Pagination:**
  - `limit` (optional): Number of orders to return (1-1000, default: 50)
  - `offset` (optional): Number of orders to skip (default: 0)
- **Sorting:**
  - `sort` (optional): Comma-separated list of fields to sort by
  - Prefix with `-` for descending order (e.g., `-orderTimestamp`)
  - Valid fields: `id`, `security.ticker`, `portfolio.name`, `blotter.name`, `status.abbreviation`, `orderType.abbreviation`, `quantity`, `orderTimestamp`
  - Default: `id` (ascending)
- **Filtering:**
  - `security.ticker` (optional): Filter by security ticker (comma-separated for multiple values)
  - `portfolio.name` (optional): Filter by portfolio name (comma-separated for multiple values)
  - `blotter.name` (optional): Filter by blotter name (comma-separated for multiple values)
  - `status.abbreviation` (optional): Filter by status abbreviation (comma-separated for multiple values)
  - `orderType.abbreviation` (optional): Filter by order type abbreviation (comma-separated for multiple values)
  - `orderTimestamp` (optional): Filter by order timestamp (comma-separated for multiple values)

**Response (200):**
```json
{
  "content": [
    {
      "id": 101,
      "blotter": { "id": 1, "name": "Default", "version": 1 },
      "status": { "id": 1, "abbreviation": "NEW", "description": "New", "version": 1 },
      "security": {
        "securityId": "SEC123456789012345678901",
        "ticker": "IBM"
      },
      "portfolio": {
        "portfolioId": "PORT123456789012345678",
        "name": "Growth Fund"
      },
      "orderType": { "id": 2, "abbreviation": "BUY", "description": "Buy", "version": 1 },
      "quantity": 100.00000000,
      "limitPrice": 50.25000000,
      "tradeOrderId": null,
      "orderTimestamp": "2024-06-01T12:00:00Z",
      "version": 1
    }
  ],
  "pagination": {
    "pageSize": 50,
    "offset": 0,
    "totalElements": 1,
    "hasNext": false,
    "hasPrevious": false
  }
}
```

**Response (400) - Invalid Parameters:**
```json
{
  "message": "Invalid sort fields: [invalidField]. Valid fields are: [id, security.ticker, portfolio.name, blotter.name, status.abbreviation, orderType.abbreviation, quantity, orderTimestamp]",
  "validSortFields": ["id", "security.ticker", "portfolio.name", "blotter.name", "status.abbreviation", "orderType.abbreviation", "quantity", "orderTimestamp"]
}
```

### **2. Get Order by ID**
```http
GET /api/v1/order/101
```

**Response (200):** Same as OrderWithDetailsDTO above
**Response (404):** Order not found

### **3. Create Orders (Batch Processing)**
```http
POST /api/v1/orders
Content-Type: application/json

[
  {
    "blotterId": 1,
    "statusId": 1,
    "portfolioId": "PORT123456789012345678",
    "orderTypeId": 2,
    "securityId": "SEC123456789012345678901",
    "quantity": 100.00000000,
    "limitPrice": 50.25000000,
    "orderTimestamp": "2024-06-01T12:00:00Z",
    "version": 1
  },
  {
    "blotterId": 1,
    "statusId": 1,
    "portfolioId": "PORT987654321098765432",
    "orderTypeId": 3,
    "securityId": "SEC987654321098765432109",
    "quantity": 200.00000000,
    "limitPrice": null,
    "orderTimestamp": "2024-06-01T12:01:00Z",
    "version": 1
  }
]
```

**Success Response (200):**
```json
{
  "status": "SUCCESS",
  "message": "All 2 orders processed successfully",
  "totalReceived": 2,
  "successful": 2,
  "failed": 0,
  "orders": [
    {
      "status": "SUCCESS",
      "message": "Order created successfully",
      "orderDetails": {
        "id": 101,
        "blotter": { "id": 1, "name": "Default", "version": 1 },
        "status": { "id": 1, "abbreviation": "NEW", "description": "New", "version": 1 },
        "portfolioId": "PORT123456789012345678",
        "orderType": { "id": 2, "abbreviation": "BUY", "description": "Buy", "version": 1 },
        "securityId": "SEC123456789012345678901",
        "quantity": 100.00000000,
        "limitPrice": 50.25000000,
        "tradeOrderId": null,
        "orderTimestamp": "2024-06-01T12:00:00Z",
        "version": 1
      },
      "orderId": 101,
      "requestIndex": 0
    },
    {
      "status": "SUCCESS",
      "message": "Order created successfully",
      "orderDetails": {
        "id": 102,
        "blotter": { "id": 1, "name": "Default", "version": 1 },
        "status": { "id": 1, "abbreviation": "NEW", "description": "New", "version": 1 },
        "portfolioId": "PORT987654321098765432",
        "orderType": { "id": 3, "abbreviation": "SELL", "description": "Sell", "version": 1 },
        "securityId": "SEC987654321098765432109",
        "quantity": 200.00000000,
        "limitPrice": null,
        "tradeOrderId": null,
        "orderTimestamp": "2024-06-01T12:01:00Z",
        "version": 1
      },
      "orderId": 102,
      "requestIndex": 1
    }
  ]
}
```

**Partial Success Response (207):**
```json
{
  "status": "PARTIAL",
  "message": "1 of 2 orders processed successfully, 1 failed",
  "totalReceived": 2,
  "successful": 1,
  "failed": 1,
  "orders": [
    {
      "status": "SUCCESS",
      "message": "Order created successfully",
      "orderDetails": { "..." },
      "orderId": 101,
      "requestIndex": 0
    },
    {
      "status": "FAILURE",
      "message": "Portfolio ID is required",
      "orderDetails": null,
      "orderId": null,
      "requestIndex": 1
    }
  ]
}
```

**Validation Error Response (400):**
```json
{
  "status": "FAILURE",
  "message": "Request validation failed: Invalid request data",
  "totalReceived": 0,
  "successful": 0,
  "failed": 0,
  "orders": []
}
```

**Batch Size Error Response (413):**
```json
{
  "status": "FAILURE",
  "message": "Batch size 1001 exceeds maximum allowed size of 1000",
  "totalReceived": 0,
  "successful": 0,
  "failed": 0,
  "orders": []
}
```

### **4. Update Order**
```http
PUT /api/v1/order/101
Content-Type: application/json

{
  "id": 101,
  "blotterId": 1,
  "statusId": 2,
  "portfolioId": "PORT123456789012345678",
  "orderTypeId": 2,
  "securityId": "SEC123456789012345678901",
  "quantity": 150.00000000,
  "limitPrice": 55.00000000,
  "tradeOrderId": null,
  "orderTimestamp": "2024-06-01T12:00:00Z",
  "version": 2
}
```

**Response (200):** Updated OrderWithDetailsDTO
**Response (404):** Order not found

### **5. Delete Order**
```http
DELETE /api/v1/order/101?version=1
```

**Response (204):** Order deleted successfully
**Response (409):** Version mismatch conflict
**Response (404):** Order not found

### **6. Submit Order to Trade Service**
```http
POST /api/v1/orders/101/submit
```

**Success Response (200):**
```json
{
  "id": 101,
  "blotterId": 1,
  "statusId": 2,
  "portfolioId": "PORT123456789012345678",
  "orderTypeId": 2,
  "securityId": "SEC123456789012345678901",
  "quantity": 100.00000000,
  "limitPrice": 50.25000000,
  "tradeOrderId": 12345,
  "orderTimestamp": "2024-06-01T12:00:00Z",
  "version": 1
}
```

**Failure Response (400):**
```json
{
  "status": "not submitted"
}
```

---

## **📋 Reference Data APIs**

### **Status Management**

#### **List All Statuses**
```http
GET /api/v1/statuses
```

**Response (200):**
```json
[
  {
    "id": 1,
    "abbreviation": "NEW",
    "description": "New Order",
    "version": 1
  },
  {
    "id": 2,
    "abbreviation": "SENT",
    "description": "Sent to Trade Service",
    "version": 1
  }
]
```

#### **Get Status by ID**
```http
GET /api/v1/status/1
```

**Response (200):**
```json
{
  "id": 1,
  "abbreviation": "NEW",
  "description": "New Order",
  "version": 1
}
```

#### **Create Status**
```http
POST /api/v1/statuses
Content-Type: application/json

{
  "abbreviation": "HOLD",
  "description": "Order on Hold",
  "version": 1
}
```

**Response (200):**
```json
{
  "id": 3,
  "abbreviation": "HOLD",
  "description": "Order on Hold",
  "version": 1
}
```

#### **Update Status**
```http
PUT /api/v1/status/3
Content-Type: application/json

{
  "id": 3,
  "abbreviation": "HOLD",
  "description": "Order on Hold (Updated)",
  "version": 2
}
```

#### **Delete Status**
```http
DELETE /api/v1/status/3?version=2
```

**Response (204):** Status deleted
**Response (409):** Version mismatch

### **Order Type Management**

#### **List All Order Types**
```http
GET /api/v1/orderTypes
```

**Response (200):**
```json
[
  {
    "id": 1,
    "abbreviation": "MKT",
    "description": "Market Order",
    "version": 1
  },
  {
    "id": 2,
    "abbreviation": "BUY",
    "description": "Buy Order",
    "version": 1
  },
  {
    "id": 3,
    "abbreviation": "SELL",
    "description": "Sell Order",
    "version": 1
  }
]
```

#### **Get Order Type by ID**
```http
GET /api/v1/orderTypes/2
```

#### **Create Order Type**
```http
POST /api/v1/orderTypes
Content-Type: application/json

{
  "abbreviation": "LIMIT",
  "description": "Limit Order",
  "version": 1
}
```

#### **Update Order Type**
```http
PUT /api/v1/orderType/4
Content-Type: application/json

{
  "id": 4,
  "abbreviation": "LIMIT",
  "description": "Limit Order (Updated)",
  "version": 2
}
```

#### **Delete Order Type**
```http
DELETE /api/v1/orderType/4?version=2
```

### **Blotter Management**

#### **List All Blotters**
```http
GET /api/v1/blotters
```

**Response (200):**
```json
[
  {
    "id": 1,
    "name": "Default Blotter",
    "version": 1
  },
  {
    "id": 2,
    "name": "High Frequency Trading",
    "version": 1
  }
]
```

#### **Get Blotter by ID**
```http
GET /api/v1/blotter/1
```

#### **Create Blotter**
```http
POST /api/v1/blotters
Content-Type: application/json

{
  "name": "Arbitrage Trading",
  "version": 1
}
```

#### **Update Blotter**
```http
PUT /api/v1/blotter/3
Content-Type: application/json

{
  "id": 3,
  "name": "Arbitrage Trading (Updated)",
  "version": 2
}
```

#### **Delete Blotter**
```http
DELETE /api/v1/blotter/3?version=2
```

---

## **⚠️ Error Handling**

### **HTTP Status Codes**
| Code | Description | When Used |
|------|-------------|-----------|
| 200 | OK | Successful operation |
| 204 | No Content | Successful deletion |
| 207 | Multi-Status | Partial success in batch operations |
| 400 | Bad Request | Validation errors, malformed requests |
| 404 | Not Found | Resource not found |
| 409 | Conflict | Version mismatch, constraint violations |
| 413 | Payload Too Large | Batch size exceeds 1000 |
| 500 | Internal Server Error | Unexpected server errors |

### **Common Validation Errors**
```json
// Missing required fields
{
  "status": "FAILURE",
  "message": "Portfolio ID is required",
  "requestIndex": 0
}

// Invalid foreign key references
{
  "status": "FAILURE",
  "message": "Blotter with ID 999 not found",
  "requestIndex": 1
}

// Invalid values
{
  "status": "FAILURE",
  "message": "Quantity must be positive",
  "requestIndex": 2
}

// String length violations
{
  "status": "FAILURE",
  "message": "Portfolio ID exceeds maximum length of 24 characters",
  "requestIndex": 3
}
```

---

## **💡 Code Examples**

### **JavaScript/Node.js**
```javascript
const axios = require('axios');

// Create orders batch
async function createOrders() {
  const orders = [
    {
      blotterId: 1,
      statusId: 1,
      portfolioId: "PORT123456789012345678",
      orderTypeId: 2,
      securityId: "SEC123456789012345678901",
      quantity: 100.0,
      limitPrice: 50.25,
      orderTimestamp: new Date().toISOString(),
      version: 1
    }
  ];

  try {
    const response = await axios.post('http://localhost:8081/api/v1/orders', orders, {
      headers: { 'Content-Type': 'application/json' }
    });
    
    console.log('Orders created:', response.data);
    return response.data;
  } catch (error) {
    console.error('Error creating orders:', error.response.data);
    throw error;
  }
}

// Get all orders
async function getAllOrders() {
  try {
    const response = await axios.get('http://localhost:8081/api/v1/orders');
    return response.data;
  } catch (error) {
    console.error('Error fetching orders:', error.response.data);
    throw error;
  }
}
```

### **Python**
```python
import requests
import json
from datetime import datetime

# Create orders batch
def create_orders():
    orders = [
        {
            "blotterId": 1,
            "statusId": 1,
            "portfolioId": "PORT123456789012345678",
            "orderTypeId": 2,
            "securityId": "SEC123456789012345678901",
            "quantity": 100.0,
            "limitPrice": 50.25,
            "orderTimestamp": datetime.utcnow().isoformat() + "Z",
            "version": 1
        }
    ]
    
    try:
        response = requests.post(
            'http://localhost:8081/api/v1/orders',
            json=orders,
            headers={'Content-Type': 'application/json'}
        )
        response.raise_for_status()
        return response.json()
    except requests.exceptions.RequestException as e:
        print(f"Error creating orders: {e}")
        if hasattr(e, 'response') and e.response is not None:
            print(f"Response: {e.response.text}")
        raise

# Get all statuses
def get_all_statuses():
    try:
        response = requests.get('http://localhost:8081/api/v1/statuses')
        response.raise_for_status()
        return response.json()
    except requests.exceptions.RequestException as e:
        print(f"Error fetching statuses: {e}")
        raise
```

### **cURL Examples**
```bash
# Create orders batch
curl -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '[
    {
      "blotterId": 1,
      "statusId": 1,
      "portfolioId": "PORT123456789012345678",
      "orderTypeId": 2,
      "securityId": "SEC123456789012345678901",
      "quantity": 100.0,
      "limitPrice": 50.25,
      "orderTimestamp": "2024-06-01T12:00:00Z",
      "version": 1
    }
  ]'

# Get all orders
curl -X GET http://localhost:8081/api/v1/orders

# Get order by ID
curl -X GET http://localhost:8081/api/v1/order/101

# Delete order
curl -X DELETE "http://localhost:8081/api/v1/order/101?version=1"

# Create status
curl -X POST http://localhost:8081/api/v1/statuses \
  -H "Content-Type: application/json" \
  -d '{
    "abbreviation": "HOLD",
    "description": "Order on Hold",
    "version": 1
  }'
```

---

## **📚 Best Practices**

### **1. Batch Processing**
- Use batch processing for high-volume order creation
- Maximum 1000 orders per batch
- Handle partial failures gracefully
- Check individual order results in response

### **2. Error Handling**
- Always check HTTP status codes
- Parse error messages for validation failures
- Implement retry logic for 5xx errors
- Log failed orders for manual review

### **3. Version Management**
- Always include version numbers for updates/deletes
- Handle 409 conflicts by refetching current version
- Use optimistic locking to prevent concurrent modification issues

### **4. Data Validation**
- Validate required fields before sending requests
- Ensure foreign key references exist
- Check string length constraints
- Validate decimal precision for monetary values

### **5. Performance**
- Use batch operations when possible
- Cache reference data (statuses, order types, blotters)
- Implement pagination for large result sets
- Use appropriate timeouts for HTTP requests

---

## **🔧 Common Integration Patterns**

### **1. Order Creation Workflow**
```javascript
async function createOrderWorkflow(orderData) {
  // 1. Validate reference data exists
  const [statuses, orderTypes, blotters] = await Promise.all([
    getStatuses(),
    getOrderTypes(),
    getBlotters()
  ]);
  
  // 2. Validate order data
  validateOrder(orderData, { statuses, orderTypes, blotters });
  
  // 3. Create order batch
  const response = await createOrders([orderData]);
  
  // 4. Handle results
  if (response.status === 'SUCCESS') {
    console.log('Order created successfully:', response.orders[0].orderId);
    return response.orders[0];
  } else {
    console.error('Order creation failed:', response.orders[0].message);
    throw new Error(response.orders[0].message);
  }
}
```

### **2. Bulk Order Processing**
```python
def process_bulk_orders(order_list, batch_size=100):
    results = []
    
    for i in range(0, len(order_list), batch_size):
        batch = order_list[i:i + batch_size]
        
        try:
            response = create_orders(batch)
            results.append(response)
            
            # Log batch results
            print(f"Batch {i//batch_size + 1}: {response['successful']} successful, {response['failed']} failed")
            
        except Exception as e:
            print(f"Batch {i//batch_size + 1} failed: {e}")
            results.append({"error": str(e), "batch_start": i})
    
    return results
```

### **3. Reference Data Caching**
```javascript
class ReferenceDataCache {
  constructor() {
    this.cache = {};
    this.ttl = 5 * 60 * 1000; // 5 minutes
  }
  
  async getStatuses() {
    return this.getCachedData('statuses', () => 
      axios.get('/api/v1/statuses').then(r => r.data)
    );
  }
  
  async getCachedData(key, fetchFn) {
    const cached = this.cache[key];
    if (cached && Date.now() - cached.timestamp < this.ttl) {
      return cached.data;
    }
    
    const data = await fetchFn();
    this.cache[key] = { data, timestamp: Date.now() };
    return data;
  }
}
```

---

This comprehensive guide provides all the information needed to successfully integrate with the GlobeCo Order Service API. Use the examples and patterns as starting points for your specific implementation needs. 