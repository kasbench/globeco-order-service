# GlobeCo Order Service Web UI API Guide

## Overview

This comprehensive guide provides all the information needed to build a modern web UI for the GlobeCo Order Service. The Order Service is a RESTful API that manages trading orders with advanced features including batch processing, filtering, sorting, and pagination.

**Service Information:**
- **Base URL**: `http://localhost:8081/api/v1` (development) or `http://globeco-order-service:8081/api/v1` (production)
- **API Version**: 2.0.0
- **Content Type**: `application/json`
- **Authentication**: None required (internal service)

## Table of Contents

1. [Core Concepts](#core-concepts)
2. [Order Management](#order-management)
3. [Reference Data Management](#reference-data-management)
4. [Batch Operations](#batch-operations)
5. [Advanced Features](#advanced-features)
6. [UI Implementation Patterns](#ui-implementation-patterns)
7. [Error Handling](#error-handling)
8. [Performance Considerations](#performance-considerations)

## Core Concepts

### Data Model Overview

The Order Service manages several interconnected entities:

- **Orders**: The primary entity representing trading orders
- **Statuses**: Order lifecycle states (NEW, SENT, FILLED, CANCELLED, etc.)
- **Order Types**: Trading instructions (BUY, SELL, MARKET, LIMIT, etc.)
- **Blotters**: Trading desks or organizational units
- **Securities**: External references to financial instruments (resolved by ticker)
- **Portfolios**: External references to investment portfolios (resolved by name)

### Key Features for UI Implementation

1. **Advanced Pagination**: Efficient handling of large datasets
2. **Multi-field Sorting**: Complex sorting with multiple criteria
3. **Powerful Filtering**: Filter by multiple fields with OR/AND logic
4. **Batch Processing**: Create up to 1000 orders in a single request
5. **Batch Submission**: Submit up to 100 orders to trading system
6. **Real-time Status Updates**: Track order lifecycle changes
7. **External Service Integration**: Automatic resolution of tickers and portfolio names

## Order Management

### 1. List Orders with Advanced Features

**Endpoint**: `GET /api/v1/orders`

This is the primary endpoint for displaying orders in your UI with comprehensive pagination, sorting, and filtering capabilities.

#### Request Parameters

```typescript
interface OrderListParams {
  // Pagination
  limit?: number;        // 1-1000, default: 50
  offset?: number;       // ≥0, default: 0
  
  // Sorting (comma-separated, prefix with '-' for descending)
  sort?: string;         // e.g., "-orderTimestamp,security.ticker"
  
  // Filtering (comma-separated values for OR logic)
  'security.ticker'?: string;        // e.g., "AAPL,MSFT,GOOGL"
  'portfolio.name'?: string;         // e.g., "Growth Fund,Tech Fund"
  'blotter.name'?: string;           // e.g., "Default,Trading Desk A"
  'status.abbreviation'?: string;    // e.g., "NEW,SENT"
  'orderType.abbreviation'?: string; // e.g., "BUY,SELL"
  'orderTimestamp'?: string;         // ISO 8601 format
}
```

#### Valid Sort Fields
- `id`, `security.ticker`, `portfolio.name`, `blotter.name`
- `status.abbreviation`, `orderType.abbreviation`, `quantity`, `orderTimestamp`

#### Valid Filter Fields
- `security.ticker`, `portfolio.name`, `blotter.name`
- `status.abbreviation`, `orderType.abbreviation`, `orderTimestamp`

#### Response Format

```typescript
interface OrderPageResponse {
  content: OrderWithDetails[];
  pagination: {
    pageSize: number;
    offset: number;
    totalElements: number;
    hasNext: boolean;
    hasPrevious: boolean;
  };
}

interface OrderWithDetails {
  id: number;
  blotter: { id: number; name: string; version: number };
  status: { id: number; abbreviation: string; description: string; version: number };
  security: { securityId: string; ticker: string };
  portfolio: { portfolioId: string; name: string };
  orderType: { id: number; abbreviation: string; description: string; version: number };
  quantity: number;
  limitPrice?: number;
  tradeOrderId?: number;
  orderTimestamp: string; // ISO 8601
  version: number;
}
```

#### Example Requests

```javascript
// Basic pagination
const response = await fetch('/api/v1/orders?limit=20&offset=40');

// Sorting by multiple fields
const response = await fetch('/api/v1/orders?sort=-orderTimestamp,security.ticker');

// Complex filtering
const response = await fetch('/api/v1/orders?security.ticker=AAPL,MSFT&status.abbreviation=NEW,SENT');

// Combined parameters
const response = await fetch('/api/v1/orders?limit=25&sort=-orderTimestamp&security.ticker=AAPL&status.abbreviation=NEW');
```

### 2. Get Single Order

**Endpoint**: `GET /api/v1/order/{id}`

Retrieve detailed information for a specific order.

```javascript
const response = await fetch('/api/v1/order/123');
const order = await response.json(); // OrderWithDetails
```

### 3. Update Order

**Endpoint**: `PUT /api/v1/order/{id}`

Update an existing order with optimistic locking.

```typescript
interface OrderUpdateRequest {
  blotterId: number;
  statusId: number;
  portfolioId: string;
  orderTypeId: number;
  securityId: string;
  quantity: number;
  limitPrice?: number;
  tradeOrderId?: number;
  orderTimestamp: string;
  version: number; // Required for optimistic locking
}
```

```javascript
const updateData = {
  blotterId: 1,
  statusId: 2,
  portfolioId: "PORT123456789012345678",
  orderTypeId: 3,
  securityId: "SEC123456789012345678901",
  quantity: 150.0,
  limitPrice: 55.25,
  orderTimestamp: "2024-06-01T12:00:00Z",
  version: 1
};

const response = await fetch('/api/v1/order/123', {
  method: 'PUT',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(updateData)
});
```

### 4. Delete Order

**Endpoint**: `DELETE /api/v1/order/{id}?version={version}`

Delete an order with optimistic locking.

```javascript
const response = await fetch('/api/v1/order/123?version=1', {
  method: 'DELETE'
});
// Returns 204 No Content on success
```

## Reference Data Management

### 1. Statuses

**List All**: `GET /api/v1/statuses`
**Get by ID**: `GET /api/v1/status/{id}`
**Create**: `POST /api/v1/statuses`
**Update**: `PUT /api/v1/status/{id}`
**Delete**: `DELETE /api/v1/status/{id}?version={version}`

```typescript
interface Status {
  id: number;
  abbreviation: string;
  description: string;
  version: number;
}
```

### 2. Order Types

**List All**: `GET /api/v1/orderTypes`
**Get by ID**: `GET /api/v1/orderType/{id}`
**Create**: `POST /api/v1/orderTypes`
**Update**: `PUT /api/v1/orderType/{id}`
**Delete**: `DELETE /api/v1/orderType/{id}?version={version}`

```typescript
interface OrderType {
  id: number;
  abbreviation: string;
  description: string;
  version: number;
}
```

### 3. Blotters

**List All**: `GET /api/v1/blotters`
**Get by ID**: `GET /api/v1/blotter/{id}`
**Create**: `POST /api/v1/blotters`
**Update**: `PUT /api/v1/blotter/{id}`
**Delete**: `DELETE /api/v1/blotter/{id}?version={version}`

```typescript
interface Blotter {
  id: number;
  name: string;
  version: number;
}
```

## Batch Operations

### 1. Batch Order Creation

**Endpoint**: `POST /api/v1/orders`

Create multiple orders in a single request (up to 1000 orders).

```typescript
interface OrderCreateRequest {
  blotterId: number;
  statusId: number;
  portfolioId: string;
  orderTypeId: number;
  securityId: string;
  quantity: number;
  limitPrice?: number;
  tradeOrderId?: number;
  orderTimestamp: string;
  version: number;
}

interface BatchCreateResponse {
  status: 'SUCCESS' | 'PARTIAL' | 'FAILURE';
  message: string;
  totalReceived: number;
  successful: number;
  failed: number;
  orders: Array<{
    status: 'SUCCESS' | 'FAILURE';
    message: string;
    orderDetails?: OrderWithDetails;
    orderId?: number;
    requestIndex: number;
  }>;
}
```

#### Example Implementation

```javascript
const orders = [
  {
    blotterId: 1,
    statusId: 1,
    portfolioId: "PORT123456789012345678",
    orderTypeId: 2,
    securityId: "SEC123456789012345678901",
    quantity: 100.0,
    limitPrice: 50.25,
    orderTimestamp: "2024-06-01T12:00:00Z",
    version: 1
  },
  {
    blotterId: 1,
    statusId: 1,
    portfolioId: "PORT987654321098765432",
    orderTypeId: 3,
    securityId: "SEC987654321098765432109",
    quantity: 200.0,
    limitPrice: 75.50,
    orderTimestamp: "2024-06-01T12:01:00Z",
    version: 1
  }
];

const response = await fetch('/api/v1/orders', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(orders)
});

const result = await response.json();
// Handle different HTTP status codes: 200, 207, 400, 413, 500
```

### 2. Batch Order Submission

**Endpoint**: `POST /api/v1/orders/batch/submit`

Submit multiple orders to the trading system (up to 100 orders).

```typescript
interface BatchSubmitRequest {
  orderIds: number[]; // 1-100 order IDs
}

interface BatchSubmitResponse {
  status: 'SUCCESS' | 'PARTIAL' | 'FAILURE';
  message: string;
  totalRequested: number;
  successful: number;
  failed: number;
  results: Array<{
    orderId: number;
    status: 'SUCCESS' | 'FAILURE';
    message: string;
    tradeOrderId?: number;
    requestIndex: number;
  }>;
}
```

#### Example Implementation

```javascript
const submitRequest = {
  orderIds: [1, 2, 3, 4, 5]
};

const response = await fetch('/api/v1/orders/batch/submit', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(submitRequest)
});

const result = await response.json();
// Handle different HTTP status codes: 200, 207, 400, 413, 500
```

### 3. Single Order Submission

**Endpoint**: `POST /api/v1/orders/{id}/submit`

Submit a single order to the trading system.

```javascript
const response = await fetch('/api/v1/orders/123/submit', {
  method: 'POST'
});

if (response.ok) {
  const updatedOrder = await response.json(); // OrderDTO
} else {
  // Handle submission failure
}
```

## Advanced Features

### 1. Smart Filtering with External Service Integration

The Order Service automatically resolves human-readable identifiers:

- **Security Tickers**: `security.ticker=AAPL` automatically resolves to the correct `securityId`
- **Portfolio Names**: `portfolio.name=Growth Fund` automatically resolves to the correct `portfolioId`

This means your UI can use user-friendly values without worrying about internal IDs.

#### Filter Logic

- **Within a field**: Multiple values use OR logic (`security.ticker=AAPL,MSFT` = AAPL OR MSFT)
- **Between fields**: Different fields use AND logic (`security.ticker=AAPL&status.abbreviation=NEW` = AAPL AND NEW)

### 2. Advanced Sorting

Support for multi-field sorting with mixed directions:

```javascript
// Sort by timestamp descending, then by ticker ascending
const url = '/api/v1/orders?sort=-orderTimestamp,security.ticker';

// Sort by portfolio name ascending, then by quantity descending
const url = '/api/v1/orders?sort=portfolio.name,-quantity';
```

### 3. Pagination Strategies

#### Offset-based Pagination (Current)
```javascript
// Page 1 (first 20 items)
const page1 = await fetch('/api/v1/orders?limit=20&offset=0');

// Page 2 (next 20 items)
const page2 = await fetch('/api/v1/orders?limit=20&offset=20');

// Page 3 (next 20 items)
const page3 = await fetch('/api/v1/orders?limit=20&offset=40');
```

#### Infinite Scroll Implementation
```javascript
let offset = 0;
const limit = 50;
let hasMore = true;

async function loadMoreOrders() {
  if (!hasMore) return;
  
  const response = await fetch(`/api/v1/orders?limit=${limit}&offset=${offset}`);
  const data = await response.json();
  
  // Append new orders to existing list
  orders.push(...data.content);
  
  offset += limit;
  hasMore = data.pagination.hasNext;
}
```

## UI Implementation Patterns

### 1. Order List Component

```typescript
interface OrderListProps {
  filters?: OrderFilters;
  sorting?: SortConfig;
  pagination?: PaginationConfig;
  onOrderSelect?: (order: OrderWithDetails) => void;
  onOrderUpdate?: (orderId: number) => void;
}

interface OrderFilters {
  securityTicker?: string[];
  portfolioName?: string[];
  blotterName?: string[];
  statusAbbreviation?: string[];
  orderTypeAbbreviation?: string[];
  orderTimestamp?: string[];
}

interface SortConfig {
  field: string;
  direction: 'asc' | 'desc';
  secondary?: SortConfig;
}

interface PaginationConfig {
  pageSize: number;
  currentPage: number;
}
```

### 2. Filter Component

```typescript
interface FilterComponentProps {
  availableFilters: {
    securities: Array<{ ticker: string; name: string }>;
    portfolios: Array<{ name: string; id: string }>;
    blotters: Array<{ name: string; id: number }>;
    statuses: Array<{ abbreviation: string; description: string }>;
    orderTypes: Array<{ abbreviation: string; description: string }>;
  };
  currentFilters: OrderFilters;
  onFiltersChange: (filters: OrderFilters) => void;
}
```

### 3. Batch Operations Component

```typescript
interface BatchOperationsProps {
  selectedOrders: number[];
  onBatchCreate: (orders: OrderCreateRequest[]) => Promise<BatchCreateResponse>;
  onBatchSubmit: (orderIds: number[]) => Promise<BatchSubmitResponse>;
  maxBatchSize: {
    create: 1000;
    submit: 100;
  };
}
```

### 4. Order Form Component

```typescript
interface OrderFormProps {
  mode: 'create' | 'edit';
  initialData?: Partial<OrderCreateRequest>;
  referenceData: {
    blotters: Blotter[];
    statuses: Status[];
    orderTypes: OrderType[];
  };
  onSubmit: (order: OrderCreateRequest) => Promise<void>;
  onCancel: () => void;
}
```

## Error Handling

### HTTP Status Codes

- **200**: Success
- **204**: Success (no content, for DELETE operations)
- **207**: Multi-Status (partial success in batch operations)
- **400**: Bad Request (validation errors)
- **404**: Not Found
- **409**: Conflict (version mismatch)
- **413**: Payload Too Large (batch size exceeded)
- **500**: Internal Server Error

### Error Response Format

```typescript
interface ErrorResponse {
  message: string;
  validSortFields?: string[];      // For sort validation errors
  validFilterFields?: string[];    // For filter validation errors
}
```

### Error Handling Patterns

```javascript
async function handleApiCall(apiCall) {
  try {
    const response = await apiCall();
    
    if (response.ok) {
      return await response.json();
    }
    
    const errorData = await response.json();
    
    switch (response.status) {
      case 400:
        throw new ValidationError(errorData.message);
      case 404:
        throw new NotFoundError(errorData.message);
      case 409:
        throw new ConflictError('Resource has been modified by another user');
      case 413:
        throw new PayloadTooLargeError(errorData.message);
      case 500:
        throw new ServerError('An unexpected error occurred');
      default:
        throw new Error(`HTTP ${response.status}: ${errorData.message}`);
    }
  } catch (error) {
    if (error instanceof TypeError) {
      throw new NetworkError('Network connection failed');
    }
    throw error;
  }
}
```

### Batch Operation Error Handling

```javascript
function handleBatchResponse(response) {
  const { status, successful, failed, orders } = response;
  
  switch (status) {
    case 'SUCCESS':
      showSuccessMessage(`All ${successful} orders processed successfully`);
      break;
      
    case 'PARTIAL':
      showWarningMessage(`${successful} orders succeeded, ${failed} failed`);
      // Show detailed results for failed orders
      const failures = orders.filter(o => o.status === 'FAILURE');
      showFailureDetails(failures);
      break;
      
    case 'FAILURE':
      showErrorMessage(`All orders failed: ${response.message}`);
      break;
  }
}
```

## Performance Considerations

### 1. Pagination Best Practices

- **Default Page Size**: Use 50 items per page for optimal performance
- **Maximum Page Size**: Don't exceed 100 items per page for UI responsiveness
- **Large Datasets**: Use server-side pagination, not client-side
- **Infinite Scroll**: Implement virtual scrolling for very large lists

### 2. Filtering and Sorting

- **Debounce Filter Input**: Wait 300-500ms after user stops typing
- **Cache Filter Options**: Cache reference data (statuses, order types, etc.)
- **Combine Operations**: Send filters, sorting, and pagination in single request
- **URL State Management**: Store filter/sort state in URL for bookmarking

### 3. Batch Operations

- **Progress Indicators**: Show progress for large batch operations
- **Chunking**: For very large batches, consider client-side chunking
- **Error Recovery**: Allow retry of failed items in batch operations
- **Background Processing**: Use web workers for large data processing

### 4. Caching Strategies

```javascript
// Cache reference data
const referenceDataCache = {
  statuses: { data: null, expiry: null },
  orderTypes: { data: null, expiry: null },
  blotters: { data: null, expiry: null }
};

async function getCachedReferenceData(type) {
  const cache = referenceDataCache[type];
  const now = Date.now();
  
  if (cache.data && cache.expiry > now) {
    return cache.data;
  }
  
  const data = await fetch(`/api/v1/${type}`).then(r => r.json());
  cache.data = data;
  cache.expiry = now + (5 * 60 * 1000); // 5 minutes
  
  return data;
}
```

### 5. Real-time Updates

```javascript
// Polling for order status updates
class OrderStatusPoller {
  constructor(orderIds, onUpdate) {
    this.orderIds = orderIds;
    this.onUpdate = onUpdate;
    this.interval = null;
  }
  
  start() {
    this.interval = setInterval(async () => {
      for (const orderId of this.orderIds) {
        try {
          const order = await fetch(`/api/v1/order/${orderId}`).then(r => r.json());
          this.onUpdate(order);
        } catch (error) {
          console.error(`Failed to update order ${orderId}:`, error);
        }
      }
    }, 5000); // Poll every 5 seconds
  }
  
  stop() {
    if (this.interval) {
      clearInterval(this.interval);
      this.interval = null;
    }
  }
}
```

## Health Monitoring

The service provides health endpoints for monitoring:

- **Liveness**: `GET /actuator/health/liveness`
- **Readiness**: `GET /actuator/health/readiness`
- **Startup**: `GET /actuator/health/startup`

Use these endpoints to implement health checks in your UI:

```javascript
async function checkServiceHealth() {
  try {
    const response = await fetch('/actuator/health/readiness');
    return response.ok;
  } catch (error) {
    return false;
  }
}
```

## Example Complete Implementation

Here's a complete example of a React component that demonstrates the key patterns:

```typescript
import React, { useState, useEffect, useCallback } from 'react';

interface OrderManagementProps {
  // Component props
}

export const OrderManagement: React.FC<OrderManagementProps> = () => {
  const [orders, setOrders] = useState<OrderWithDetails[]>([]);
  const [pagination, setPagination] = useState({
    pageSize: 50,
    offset: 0,
    totalElements: 0,
    hasNext: false,
    hasPrevious: false
  });
  const [filters, setFilters] = useState<OrderFilters>({});
  const [sorting, setSorting] = useState<SortConfig>({
    field: 'orderTimestamp',
    direction: 'desc'
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadOrders = useCallback(async () => {
    setLoading(true);
    setError(null);
    
    try {
      const params = new URLSearchParams();
      
      // Pagination
      params.set('limit', pagination.pageSize.toString());
      params.set('offset', pagination.offset.toString());
      
      // Sorting
      const sortString = sorting.direction === 'desc' 
        ? `-${sorting.field}` 
        : sorting.field;
      params.set('sort', sortString);
      
      // Filters
      Object.entries(filters).forEach(([key, values]) => {
        if (values && values.length > 0) {
          params.set(key, values.join(','));
        }
      });
      
      const response = await fetch(`/api/v1/orders?${params}`);
      
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }
      
      const data = await response.json();
      setOrders(data.content);
      setPagination(data.pagination);
      
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An error occurred');
    } finally {
      setLoading(false);
    }
  }, [pagination.pageSize, pagination.offset, filters, sorting]);

  useEffect(() => {
    loadOrders();
  }, [loadOrders]);

  const handleFilterChange = (newFilters: OrderFilters) => {
    setFilters(newFilters);
    setPagination(prev => ({ ...prev, offset: 0 })); // Reset to first page
  };

  const handleSortChange = (field: string) => {
    setSorting(prev => ({
      field,
      direction: prev.field === field && prev.direction === 'asc' ? 'desc' : 'asc'
    }));
    setPagination(prev => ({ ...prev, offset: 0 })); // Reset to first page
  };

  const handlePageChange = (newOffset: number) => {
    setPagination(prev => ({ ...prev, offset: newOffset }));
  };

  const handleBatchSubmit = async (orderIds: number[]) => {
    try {
      const response = await fetch('/api/v1/orders/batch/submit', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ orderIds })
      });
      
      const result = await response.json();
      
      // Handle batch response
      if (result.status === 'SUCCESS') {
        alert(`All ${result.successful} orders submitted successfully`);
      } else if (result.status === 'PARTIAL') {
        alert(`${result.successful} orders succeeded, ${result.failed} failed`);
      } else {
        alert(`Batch submission failed: ${result.message}`);
      }
      
      // Reload orders to show updated statuses
      loadOrders();
      
    } catch (err) {
      alert(`Batch submission error: ${err instanceof Error ? err.message : 'Unknown error'}`);
    }
  };

  return (
    <div className="order-management">
      {/* Filter Component */}
      <OrderFilters 
        filters={filters} 
        onFiltersChange={handleFilterChange} 
      />
      
      {/* Order Table */}
      <OrderTable
        orders={orders}
        sorting={sorting}
        onSortChange={handleSortChange}
        loading={loading}
        error={error}
        onBatchSubmit={handleBatchSubmit}
      />
      
      {/* Pagination Component */}
      <Pagination
        pagination={pagination}
        onPageChange={handlePageChange}
      />
    </div>
  );
};
```

This guide provides everything needed to build a comprehensive, performant web UI for the GlobeCo Order Service. The API is designed to support modern UI patterns with efficient data loading, powerful filtering and sorting, and robust batch operations. 