# Supplemental Requirement 5: Batch Order Submission

## Overview

Enhance the Order Service to support batch order submission functionality. Currently, orders must be submitted one by one using the `POST /api/v1/orders/{id}/submit` endpoint. This enhancement introduces a new endpoint that accepts a list of order IDs for batch submission while maintaining backward compatibility with the existing single-order submission endpoint.

## Current State

The existing order submission process requires individual API calls:
- **Endpoint**: `POST /api/v1/orders/{id}/submit`
- **Functionality**: Submits a single order to the GlobeCo Trade Service
- **Process**: 
  1. Validates order exists and is in "NEW" status
  2. Calls Trade Service API (`POST /api/v1/tradeOrders`)
  3. Updates order status from "NEW" to "SENT"
  4. Sets `tradeOrderId` in the order record
- **Limitation**: Requires separate API calls for each order, leading to increased latency and network overhead when submitting multiple orders

## Proposed Enhancement

### New Batch Submission Endpoint

**Endpoint**: `POST /api/v1/orders/batch/submit`

**Request Body**:
```json
{
  "orderIds": [1, 2, 3, 4, 5]
}
```

**Response Structure**:
```json
{
  "status": "SUCCESS|PARTIAL|FAILURE",
  "message": "Descriptive summary message",
  "totalRequested": 5,
  "successful": 4,
  "failed": 1,
  "results": [
    {
      "orderId": 1,
      "status": "SUCCESS",
      "message": "Order submitted successfully",
      "tradeOrderId": 12345,
      "requestIndex": 0
    },
    {
      "orderId": 2,
      "status": "SUCCESS", 
      "message": "Order submitted successfully",
      "tradeOrderId": 12346,
      "requestIndex": 1
    },
    {
      "orderId": 5,
      "status": "FAILURE",
      "message": "Order not found or not in NEW status",
      "requestIndex": 4
    }
  ]
}
```

### Business Logic Requirements

1. **Batch Size Limitations**: 
   - Maximum 100 order IDs per batch request
   - Return HTTP 413 (Payload Too Large) if exceeded

2. **Order Validation**:
   - Each order must exist in the database
   - Each order must be in "NEW" status
   - Invalid orders are reported in the response but don't stop processing of other orders

3. **Processing Strategy**:
   - Process orders individually (non-atomic batch)
   - Continue processing remaining orders even if some fail
   - Each order's success/failure is independent

4. **Trade Service Integration**:
   - For each valid order, call the Trade Service as per existing logic
   - Update order status to "SENT" and set `tradeOrderId` on successful submission
   - Handle Trade Service failures gracefully per order

5. **Response Status Logic**:
   - **SUCCESS**: All orders processed successfully
   - **PARTIAL**: Some orders succeeded, others failed
   - **FAILURE**: All orders failed or request validation failed

### HTTP Status Code Usage

- **200 OK**: All orders in the batch were submitted successfully
- **207 Multi-Status**: Partial success - some orders succeeded, others failed  
- **400 Bad Request**: Request validation failed (invalid JSON, missing orderIds, etc.)
- **413 Payload Too Large**: Batch size exceeds 100 orders
- **500 Internal Server Error**: Unexpected server error preventing batch processing

### DTOs Required

#### BatchSubmitRequestDTO
```java
public class BatchSubmitRequestDTO {
    @NotNull
    @Size(min = 1, max = 100)
    private List<Integer> orderIds;
}
```

#### BatchSubmitResponseDTO
```java
public class BatchSubmitResponseDTO {
    @NotNull
    private String status;
    @NotNull
    private String message;
    @NotNull
    private Integer totalRequested;
    @NotNull
    private Integer successful;
    @NotNull
    private Integer failed;
    @Valid
    private List<OrderSubmitResultDTO> results = new ArrayList<>();
}
```

#### OrderSubmitResultDTO
```java
public class OrderSubmitResultDTO {
    @NotNull
    private Integer orderId;
    @NotNull
    private String status;
    @NotNull
    private String message;
    private Integer tradeOrderId;
    @NotNull
    private Integer requestIndex;
}
```

## Backward Compatibility

The existing `POST /api/v1/orders/{id}/submit` endpoint will remain unchanged and fully functional. Clients can choose to:
1. Continue using single-order submission for individual orders
2. Use batch submission for multiple orders
3. Mix both approaches as needed

## Performance Considerations

1. **Parallel Processing**: Consider implementing parallel Trade Service calls within the batch to improve performance
2. **Circuit Breaker**: Implement circuit breaker pattern for Trade Service calls to handle service degradation
3. **Logging**: Add comprehensive logging for batch processing to aid in troubleshooting
4. **Metrics**: Add metrics to track batch sizes, success rates, and processing times

---

## Execution Plan

### Phase 1: DTO and Request/Response Structure
- [ ] **1.1** Create DTOs
  - [ ] Create `BatchSubmitRequestDTO` with validation annotations
  - [ ] Create `BatchSubmitResponseDTO` for batch response structure
  - [ ] Create `OrderSubmitResultDTO` for individual order results
  - [ ] Add factory methods for different response scenarios

### Phase 2: Service Layer Implementation
- [x] **2.1** Enhance `OrderService`
  - [x] Create `submitOrdersBatch(List<Integer> orderIds)` method
  - [x] Implement individual order validation logic
  - [x] Implement batch processing with error handling
  - [x] Add comprehensive logging for batch operations
- [x] **2.2** Trade Service Integration
  - [x] Reuse existing Trade Service integration logic
  - [x] Consider implementing parallel processing for Trade Service calls
  - [x] Add proper error handling for individual Trade Service failures
  - [x] Implement timeout and retry logic

### Phase 3: Controller Implementation  
- [x] **3.1** Update `OrderController`
  - [x] Add `POST /api/v1/orders/batch/submit` endpoint
  - [x] Implement request validation (batch size, null checks)
  - [x] Add proper HTTP status code handling
  - [x] Implement response mapping from service layer
- [x] **3.2** Error Handling
  - [x] Add validation for batch size limits (max 100)
  - [x] Handle malformed requests gracefully
  - [x] Return appropriate HTTP status codes per specification

### Phase 4: Testing
- [x] **4.1** Unit Tests
  - [x] Test `OrderService.submitOrdersBatch()` with various scenarios
  - [x] Test success, partial success, and complete failure cases
  - [x] Test validation logic for order states and existence
  - [x] Test error handling and edge cases
  - [x] Test `OrderController.submitOrdersBatch()` endpoint with various scenarios
  - [x] Test HTTP status code handling (200, 207, 400, 413, 500)
  - [x] Test request validation and error responses
- [ ] **4.2** Integration Tests
  - [ ] Test full batch submission flow end-to-end
  - [ ] Test Trade Service integration with batch processing
  - [ ] Test HTTP status codes and response structures
  - [ ] Test concurrent batch submissions
- [ ] **4.3** Performance Tests
  - [ ] Test batch processing performance with various batch sizes
  - [ ] Test parallel vs sequential Trade Service calls
  - [ ] Validate memory usage and resource consumption

### Phase 5: Documentation Updates
- [ ] **5.1** Update OpenAPI Specification
  - [ ] Add new `/api/v1/orders/batch/submit` endpoint
  - [ ] Document request/response schemas
  - [ ] Add comprehensive examples for all response scenarios
  - [ ] Document HTTP status codes and error conditions
- [ ] **5.2** Update API Usage Guide
  - [ ] Add batch submission examples
  - [ ] Document best practices for batch sizes
  - [ ] Add error handling examples
  - [ ] Update performance recommendations

### Phase 6: Configuration and Monitoring
- [ ] **6.1** Configuration
  - [ ] Add configurable batch size limits
  - [ ] Add configurable timeouts for batch processing
  - [ ] Configure logging levels for batch operations
- [ ] **6.2** Monitoring and Metrics
  - [ ] Add metrics for batch submission success rates
  - [ ] Add metrics for batch processing times
  - [ ] Add metrics for Trade Service call performance in batch operations
  - [ ] Add alerting for batch processing failures

### Phase 7: Validation and Rollout
- [ ] **7.1** End-to-end Validation
  - [ ] Validate all batch scenarios work correctly
  - [ ] Validate error messages are clear and actionable
  - [ ] Validate performance meets requirements
  - [ ] Validate backward compatibility is maintained
- [ ] **7.2** Production Rollout
  - [ ] Deploy to staging environment
  - [ ] Run comprehensive smoke tests
  - [ ] Monitor batch processing performance
  - [ ] Deploy to production with monitoring
  - [ ] Validate production metrics and performance

--- 