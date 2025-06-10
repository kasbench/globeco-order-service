# **GlobeCo Order Service - Batch API Usage Guide**
## **Complete Guide for LLM Integration**

### **Overview**
This document provides comprehensive instructions for interacting with the GlobeCo Order Service batch order processing API. The API allows creating up to 1000 orders in a single request with detailed success/failure reporting for each order.

---

## **🎯 API Endpoint Details**

### **Base Information**
- **URL:** `POST /api/v1/orders`
- **Content-Type:** `application/json`
- **Max Batch Size:** 1000 orders per request
- **Processing Model:** Non-atomic (individual failures don't affect other orders)

### **HTTP Status Codes**
| Code | Meaning | When Used |
|------|---------|-----------|
| `200` | All orders processed successfully | 100% success rate |
| `207` | Partial success | Some orders succeeded, others failed |
| `400` | Request validation failed | Invalid request format or data |
| `413` | Payload too large | More than 1000 orders in request |
| `500` | Server error | Unexpected internal error |

---

## **📝 Request Format**

### **Request Structure**
```json
[
  {
    "blotterId": integer (required),
    "statusId": integer (required), 
    "portfolioId": "string (required, max 24 chars)",
    "orderTypeId": integer (required),
    "securityId": "string (required)",
    "quantity": number (required, decimal format),
    "limitPrice": number (optional, decimal format),
    "orderTimestamp": "string (required, ISO 8601 datetime)",
    "version": integer (required)
  }
]
```

### **Field Constraints**
- **blotterId:** Must exist in database, nullable
- **statusId:** Must exist in database  
- **portfolioId:** String, maximum 24 characters
- **orderTypeId:** Must exist in database
- **securityId:** String identifier
- **quantity:** Positive decimal number
- **limitPrice:** Optional positive decimal number
- **orderTimestamp:** ISO 8601 format (e.g., "2024-06-01T12:00:00Z")
- **version:** Integer version number

### **Example Request (2 Orders)**
```json
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
    "limitPrice": 75.50000000,
    "orderTimestamp": "2024-06-01T12:01:00Z",
    "version": 1
  }
]
```

---

## **📊 Response Format**

### **Response Structure**
```json
{
  "status": "SUCCESS|PARTIAL|FAILURE",
  "message": "Human-readable description",
  "totalReceived": integer,
  "successful": integer,
  "failed": integer,
  "orders": [
    {
      "status": "SUCCESS|FAILURE",
      "message": "Individual order result message",
      "orderDetails": OrderWithDetailsDTO | null,
      "orderId": integer | null,
      "requestIndex": integer
    }
  ]
}
```

### **OrderWithDetailsDTO Structure** (when successful)
```json
{
  "id": integer,
  "blotter": {
    "id": integer,
    "name": "string",
    "version": integer
  },
  "status": {
    "id": integer,
    "abbreviation": "string",
    "description": "string", 
    "version": integer
  },
  "portfolioId": "string",
  "orderType": {
    "id": integer,
    "abbreviation": "string",
    "description": "string",
    "version": integer
  },
  "securityId": "string",
  "quantity": number,
  "limitPrice": number | null,
  "tradeOrderId": integer | null,
  "orderTimestamp": "string",
  "version": integer
}
```

---

## **🎯 Response Scenarios**

### **Scenario 1: Complete Success (HTTP 200)**
**When:** All orders processed successfully
```json
{
  "status": "SUCCESS",
  "message": "All orders processed successfully",
  "totalReceived": 2,
  "successful": 2,
  "failed": 0,
  "orders": [
    {
      "status": "SUCCESS",
      "message": "Order created successfully",
      "orderDetails": {
        "id": 101,
        "blotter": {
          "id": 1,
          "name": "Default",
          "version": 1
        },
        "status": {
          "id": 1,
          "abbreviation": "NEW",
          "description": "New",
          "version": 1
        },
        "portfolioId": "PORT123456789012345678",
        "orderType": {
          "id": 2,
          "abbreviation": "BUY",
          "description": "Buy",
          "version": 1
        },
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
        "blotter": {
          "id": 1,
          "name": "Default",
          "version": 1
        },
        "status": {
          "id": 1,
          "abbreviation": "NEW",
          "description": "New",
          "version": 1
        },
        "portfolioId": "PORT987654321098765432",
        "orderType": {
          "id": 3,
          "abbreviation": "SELL",
          "description": "Sell",
          "version": 1
        },
        "securityId": "SEC987654321098765432109",
        "quantity": 200.00000000,
        "limitPrice": 75.50000000,
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

### **Scenario 2: Partial Success (HTTP 207)**
**When:** Some orders succeeded, others failed
```json
{
  "status": "PARTIAL",
  "message": "Some orders processed successfully, others failed",
  "totalReceived": 2,
  "successful": 1,
  "failed": 1,
  "orders": [
    {
      "status": "SUCCESS",
      "message": "Order created successfully",
      "orderDetails": {
        "id": 101,
        "blotter": {
          "id": 1,
          "name": "Default",
          "version": 1
        },
        "status": {
          "id": 1,
          "abbreviation": "NEW",
          "description": "New",
          "version": 1
        },
        "portfolioId": "PORT123456789012345678",
        "orderType": {
          "id": 2,
          "abbreviation": "BUY",
          "description": "Buy",
          "version": 1
        },
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
      "status": "FAILURE",
      "message": "Blotter with ID 999 not found",
      "orderDetails": null,
      "orderId": null,
      "requestIndex": 1
    }
  ]
}
```

### **Scenario 3: Complete Failure (HTTP 207)**
**When:** All orders failed during processing
```json
{
  "status": "FAILURE", 
  "message": "All orders failed to process",
  "totalReceived": 2,
  "successful": 0,
  "failed": 2,
  "orders": [
    {
      "status": "FAILURE",
      "message": "Blotter with ID 999 not found",
      "orderDetails": null,
      "orderId": null,
      "requestIndex": 0
    },
    {
      "status": "FAILURE",
      "message": "Status with ID 888 not found",
      "orderDetails": null,
      "orderId": null,
      "requestIndex": 1
    }
  ]
}
```

### **Scenario 4: Validation Error (HTTP 400)**
**When:** Request format or validation failed
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

### **Scenario 5: Batch Size Exceeded (HTTP 413)**
**When:** More than 1000 orders in request
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

### **Scenario 6: Server Error (HTTP 500)**
**When:** Unexpected internal error
```json
{
  "status": "FAILURE",
  "message": "Internal server error processing batch request",
  "totalReceived": 0,
  "successful": 0,
  "failed": 0,
  "orders": []
}
```

---

## **🔧 Implementation Instructions for LLMs**

### **1. Making the API Call**
```javascript
// Example using fetch API
const response = await fetch('http://localhost:8080/api/v1/orders', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify([
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
    }
  ])
});

const result = await response.json();
const httpStatus = response.status;
```

### **2. Response Processing Logic**
```javascript
function processOrderResponse(httpStatus, responseBody) {
  switch (httpStatus) {
    case 200:
      console.log("✅ All orders successful");
      console.log(`Created ${responseBody.successful} orders`);
      responseBody.orders.forEach((order, index) => {
        console.log(`Order ${index}: ID ${order.orderId} created successfully`);
      });
      break;
      
    case 207:
      if (responseBody.status === "PARTIAL") {
        console.log("⚠️ Partial success");
        console.log(`${responseBody.successful} succeeded, ${responseBody.failed} failed`);
      } else if (responseBody.status === "FAILURE") {
        console.log("❌ All orders failed during processing");
      }
      
      responseBody.orders.forEach((order, index) => {
        if (order.status === "SUCCESS") {
          console.log(`Order ${index}: ✅ Created with ID ${order.orderId}`);
        } else {
          console.log(`Order ${index}: ❌ Failed - ${order.message}`);
        }
      });
      break;
      
    case 400:
      console.log("❌ Request validation failed");
      console.log(responseBody.message);
      break;
      
    case 413:
      console.log("❌ Too many orders in batch (max 1000)");
      console.log(responseBody.message);
      break;
      
    case 500:
      console.log("❌ Server error");
      console.log(responseBody.message);
      break;
  }
}
```

### **3. Request Validation Checklist**
Before making a request, ensure:
- ✅ Request is an array (even for single orders)
- ✅ Array has 1-1000 items
- ✅ Each order has all required fields
- ✅ portfolioId is ≤ 24 characters
- ✅ quantity and limitPrice are positive numbers
- ✅ orderTimestamp is valid ISO 8601 format
- ✅ All IDs (blotterId, statusId, orderTypeId) exist in the system

### **4. Error Handling Strategy**
```javascript
async function createOrdersBatch(orders) {
  try {
    // Validate batch size
    if (orders.length === 0 || orders.length > 1000) {
      throw new Error(`Invalid batch size: ${orders.length}. Must be 1-1000 orders.`);
    }
    
    const response = await fetch('/api/v1/orders', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(orders)
    });
    
    const result = await response.json();
    
    // Handle different outcomes
    if (response.status === 200) {
      return { success: true, allSucceeded: true, result };
    } else if (response.status === 207) {
      return { success: true, allSucceeded: false, result };
    } else {
      return { success: false, error: result.message, result };
    }
    
  } catch (error) {
    return { success: false, error: error.message };
  }
}
```

---

## **📋 Common Use Cases**

### **Single Order Creation**
```json
[
  {
    "blotterId": 1,
    "statusId": 1,
    "portfolioId": "PORTFOLIO_001",
    "orderTypeId": 2,
    "securityId": "AAPL",
    "quantity": 100.0,
    "limitPrice": 150.0,
    "orderTimestamp": "2024-06-01T12:00:00Z",
    "version": 1
  }
]
```

### **Multiple Orders (Bulk Creation)**
```json
[
  {
    "blotterId": 1,
    "statusId": 1,
    "portfolioId": "PORTFOLIO_001",
    "orderTypeId": 2,
    "securityId": "AAPL",
    "quantity": 100.0,
    "limitPrice": 150.0,
    "orderTimestamp": "2024-06-01T12:00:00Z",
    "version": 1
  },
  {
    "blotterId": 1,
    "statusId": 1,
    "portfolioId": "PORTFOLIO_002",
    "orderTypeId": 3,
    "securityId": "GOOGL",
    "quantity": 50.0,
    "limitPrice": 2500.0,
    "orderTimestamp": "2024-06-01T12:01:00Z",
    "version": 1
  }
]
```

### **Market Order (No Limit Price)**
```json
[
  {
    "blotterId": 1,
    "statusId": 1,
    "portfolioId": "PORTFOLIO_001",
    "orderTypeId": 1,
    "securityId": "MSFT",
    "quantity": 75.0,
    "limitPrice": null,
    "orderTimestamp": "2024-06-01T12:00:00Z",
    "version": 1
  }
]
```

---

## **⚠️ Important Notes for LLMs**

1. **Always Use Arrays:** Even for single orders, wrap in array format
2. **Check Response Status:** Use both HTTP status code AND response.status field
3. **Handle Partial Success:** Process each order result individually 
4. **Respect Limits:** Maximum 1000 orders per request
5. **Validate Before Sending:** Check all required fields and constraints
6. **Parse requestIndex:** Use this to match responses to original requests
7. **Handle Nulls:** orderDetails and orderId will be null for failed orders
8. **Use Proper Timestamps:** ISO 8601 format required
9. **Monitor Response Size:** Large batches return large responses
10. **Error Messages:** Always check the message field for human-readable errors

---

## **🚀 Quick Start Example**

```json
// Simple working example
POST /api/v1/orders
Content-Type: application/json

[
  {
    "blotterId": 1,
    "statusId": 1,
    "portfolioId": "TEST_PORTFOLIO_001",
    "orderTypeId": 2,
    "securityId": "TEST_SECURITY_001",
    "quantity": 100.0,
    "limitPrice": 50.0,
    "orderTimestamp": "2024-06-01T12:00:00Z",
    "version": 1
  }
]
```

This example will create a single order and return a success response if all validation passes and the referenced IDs exist in the database.

---

**This guide provides everything needed to successfully interact with the GlobeCo Order Service batch processing API. Use the examples as templates and follow the validation guidelines for reliable integration.** 