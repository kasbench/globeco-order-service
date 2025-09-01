# Implementation Plan

- [ ] 1. Create DTOs for trade service bulk integration
  - Create `BulkTradeOrderRequestDTO` class to match trade service API specification
  - Create `BulkTradeOrderResponseDTO` class to handle trade service bulk responses
  - Create `TradeOrderResultDTO` class for individual order results within bulk response
  - Add proper validation annotations and builder patterns for all DTOs
  - _Requirements: 5.1, 5.2, 5.3_

- [ ] 2. Implement order validation and batch loading methods
  - Create `loadAndValidateOrdersForBulkSubmission()` method in OrderService
  - Implement batch loading of orders using `findAllById()` for efficiency
  - Add validation logic to filter orders in "NEW" status and exclude already processed orders
  - Create helper method to validate required fields for trade service submission
  - Write unit tests for order validation and batch loading logic
  - _Requirements: 6.2, 6.3, 4.1_

- [ ] 3. Implement bulk request building functionality
  - Create `buildBulkTradeOrderRequest()` method to convert Order entities to trade service format
  - Map order fields to TradeOrderPostDTO format (orderId, portfolioId, orderType, etc.)
  - Handle timestamp formatting and blotter ID mapping
  - Add validation to ensure all required fields are present before building request
  - Write unit tests for request building and field mapping
  - _Requirements: 5.1, 5.2_

- [ ] 4. Implement trade service bulk API client
  - Create `callTradeServiceBulk()` method to make HTTP POST to `/api/v1/tradeOrders/bulk`
  - Configure RestTemplate with appropriate timeout settings for bulk operations
  - Implement comprehensive error handling for HTTP status codes (201, 400, 500)
  - Add detailed logging for request/response including full error details as specified
  - Handle network exceptions and connectivity issues with proper error messages
  - Write unit tests for HTTP client functionality and error scenarios
  - _Requirements: 2.1, 2.2, 2.3, 1.1_

- [ ] 5. Implement bulk response processing and transformation
  - Create `transformBulkResponseToOrderServiceFormat()` method
  - Map `BulkTradeOrderResponseDTO` to existing `BatchSubmitResponseDTO` format
  - Transform `TradeOrderResultDTO` objects to `OrderSubmitResultDTO` format
  - Preserve request index mapping to maintain order correlation
  - Extract trade order IDs from successful responses for database updates
  - Write unit tests for response transformation and mapping logic
  - _Requirements: 3.1, 3.2, 3.3_

- [ ] 6. Implement batch database update operations
  - Create `updateOrderStatusesFromBulkResponse()` method for efficient batch updates
  - Implement batch status updates for successful orders (set status to "SENT")
  - Update tradeOrderId field for successfully submitted orders
  - Use efficient batch update operations to minimize database connections
  - Handle partial success scenarios where some orders succeed and others fail
  - Write unit tests for batch database operations and transaction handling
  - _Requirements: 4.1, 4.2, 4.3_

- [ ] 7. Replace individual submission logic with bulk implementation
  - Modify `submitOrdersBatch()` method to use new bulk submission approach
  - Remove existing individual processing loop and related methods
  - Remove complex atomic reservation logic (`atomicallyReserveOrderForSubmission`, etc.)
  - Remove semaphore-based concurrency control that's no longer needed
  - Simplify transaction management by eliminating individual order transactions
  - Maintain existing method signature and return type for API compatibility
  - _Requirements: 1.1, 1.3, 4.1, 4.2_

- [ ] 8. Add comprehensive error handling and logging
  - Implement detailed error logging for trade service failures as specified in requirements
  - Add performance logging for batch size and processing duration
  - Handle validation errors and provide meaningful client error messages
  - Implement proper exception handling for network and service failures
  - Add monitoring hooks for success rates and performance metrics
  - Write unit tests for error handling scenarios and logging behavior
  - _Requirements: 2.1, 2.2, 2.3, 2.4_

- [ ] 9. Update validation and maintain API contract
  - Ensure existing batch size limits (100 orders) are maintained
  - Preserve existing request validation in OrderController
  - Maintain existing HTTP status code behavior (200, 207, 400, 413, 500)
  - Ensure response format exactly matches existing `BatchSubmitResponseDTO` structure
  - Validate that error response formats remain unchanged
  - Write integration tests to verify API contract compliance
  - _Requirements: 3.1, 3.4, 6.1, 6.4_

- [ ] 10. Add configuration and feature management
  - Add application properties for bulk submission configuration
  - Implement feature flag for enabling/disabling bulk submission
  - Add timeout configuration for trade service bulk calls
  - Create configuration for fallback behavior if needed
  - Add monitoring and alerting configuration
  - Write tests for configuration handling and feature flag behavior
  - _Requirements: 1.4, 2.4_

- [ ] 11. Write comprehensive integration tests
  - Create integration tests for successful bulk submission scenarios
  - Test error handling with various trade service error responses
  - Test mixed scenarios with both valid and invalid orders
  - Verify performance improvements compared to individual submission approach
  - Test batch size limits and validation edge cases
  - Create tests for database transaction integrity and rollback scenarios
  - _Requirements: 1.4, 2.4, 3.4, 4.4_

- [ ] 12. Performance optimization and cleanup
  - Remove unused individual submission methods and related code
  - Clean up imports and remove unused dependencies
  - Optimize database queries and batch operations for performance
  - Add performance monitoring and metrics collection
  - Verify memory usage is acceptable for maximum batch sizes
  - Document performance improvements and system resource usage
  - _Requirements: 1.4, 4.1, 4.2, 4.3_