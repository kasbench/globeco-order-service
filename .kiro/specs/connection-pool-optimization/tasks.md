# Implementation Plan

- [x] 1. Update connection pool configuration and reduce semaphore concurrency
  - Update `application.yml` to increase HikariCP maximum-pool-size to 60 and minimum-idle to 20
  - Set connection-timeout to 5000ms and validation-timeout to 2000ms
  - Update `BatchProcessingService.java` to reduce MAX_CONCURRENT_DB_OPERATIONS from 25 to 15
  - Add leak-detection-threshold configuration for debugging
  - _Requirements: 1.1, 1.2, 2.1, 2.2_

- [x] 2. Implement connection pool monitoring service
  - Create `ConnectionPoolMonitoringService.java` with scheduled metrics collection every 5 seconds
  - Implement metrics for active connections, idle connections, threads waiting, and utilization percentage
  - Add warning log when utilization exceeds 75%
  - Create `ConnectionPoolHealth.java` model class with health status methods
  - Register Micrometer gauges for real-time monitoring
  - _Requirements: 1.4, 1.5, 8.1, 8.2, 8.3, 8.4, 8.5_

- [x] 3. Add eager fetching to OrderRepository to eliminate N+1 queries
  - Add `findAllByIdWithRelations()` method to `OrderRepository.java` with JOIN FETCH for status, orderType, and blotter
  - Update `OrderService.loadAndValidateOrdersForBulkSubmission()` to use the new eager fetching method
  - Verify single query execution with query logging
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

- [x] 4. Restructure transaction boundaries in OrderService
  - [x] 4.1 Split `submitOrder()` method into separate load and update transactions
    - Create `loadOrderInReadTransaction()` with @Transactional(readOnly=true, timeout=3)
    - Create `updateOrderInWriteTransaction()` with @Transactional(timeout=5)
    - Refactor `submitOrder()` to call load, then external service, then update
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_
  
  - [x] 4.2 Update bulk submission flow to use split transactions
    - Modify `submitOrdersBatch()` to use read-only transaction for order loading
    - Ensure external trade service call happens outside transaction scope
    - Use write transaction only for status updates
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [ ] 5. Implement JDBC batch updates for order status changes
  - Create `BatchUpdateService.java` with JDBC batch update method
  - Implement `batchUpdateOrderStatuses()` using JdbcTemplate with PreparedStatement batching
  - Update `OrderService.updateOrderStatusesFromBulkResponse()` to use batch updates
  - Add batch size configuration (50 orders per batch)
  - Handle optimistic locking with version field
  - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

- [ ] 6. Configure HTTP client connection pooling for external services
  - Update `HttpClientConfiguration.java` to create PoolingHttpClientConnectionManager
  - Configure max total connections to 50 and max per route to 25
  - Set keep-alive strategy to 30 seconds
  - Configure RestTemplate to use the pooled HTTP client
  - Set connect timeout to 5 seconds and read timeout to 45 seconds
  - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

- [ ] 7. Reduce logging overhead in high-throughput paths
  - Update `application.yml` to set service and controller logging to INFO level in production
  - Add conditional logging checks (if logger.isDebugEnabled()) in BatchProcessingService
  - Add conditional logging checks in OrderService bulk submission methods
  - Replace per-order debug logs with summary logs in batch processing
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

- [ ] 8. Add semaphore metrics and monitoring
  - Add Counter for semaphore wait events in BatchProcessingService
  - Add Timer for semaphore wait duration tracking
  - Add Gauge for available permits monitoring
  - Implement `getAverageWaitTime()` method for health checks
  - Log warning when semaphore wait exceeds threshold
  - _Requirements: 2.3, 2.4_

- [ ] 9. Update circuit breaker configuration
  - Update `ConnectionPoolCircuitBreaker.java` to set utilization threshold to 0.75
  - Set failure threshold to 3 consecutive failures
  - Set recovery time to 15 seconds
  - Add state change event logging with timestamp and reason
  - Update metrics to track state transitions
  - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_

- [ ] 10. Add performance validation metrics
  - Implement orders per second calculation in BulkSubmissionPerformanceMonitor
  - Add P50, P95, P99 latency tracking using Micrometer percentile histograms
  - Track database query execution time in OrderService
  - Track external service call duration separately from total time
  - Record transaction hold time for connections
  - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

- [ ] 11. Create health check endpoint for connection pool status
  - Add GET endpoint `/api/v1/health/connection-pool` in SystemHealthController
  - Return ConnectionPoolHealth object with current metrics
  - Include health status (HEALTHY/WARNING/CRITICAL)
  - Add recommendations based on current state
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

- [ ]* 12. Write integration tests for optimized connection pool behavior
  - Create test simulating concurrent batch requests
  - Verify connection pool utilization stays below 75%
  - Test that no threads wait for connections under normal load
  - Validate circuit breaker doesn't activate during test
  - Measure throughput improvement compared to baseline
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.2_

- [ ]* 13. Write performance tests for transaction optimization
  - Test transaction duration for load operations (should be < 3 seconds)
  - Test transaction duration for update operations (should be < 5 seconds)
  - Verify connections are released between transactions
  - Measure total time savings from split transactions
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [ ]* 14. Write tests for eager fetching query optimization
  - Verify single query execution with query counter
  - Test that all relations are loaded (status, orderType, blotter)
  - Measure query execution time improvement
  - Validate no lazy loading exceptions occur
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

- [ ]* 15. Write tests for JDBC batch updates
  - Test batch update with various batch sizes
  - Verify all orders are updated correctly
  - Test rollback behavior on failure
  - Measure update performance improvement
  - Test optimistic locking with version field
  - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

- [ ] 16. Update documentation and create operational runbook
  - Document new connection pool configuration settings
  - Create monitoring dashboard specification for Grafana
  - Write runbook for high connection pool utilization scenarios
  - Document circuit breaker behavior and recovery procedures
  - Create alert definitions for operations team
  - Write integration guide for Order Generation Service with retry strategies, error handling patterns, and circuit breaker awareness to prevent dropped orders
  - _Requirements: All requirements_
