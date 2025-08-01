# Implementation Plan

- [x] 1. Create database metrics service infrastructure
  - Implement DatabaseMetricsService class with HikariCP integration
  - Create methods to register database connection pool gauges (total, active, idle)
  - Add connection acquisition duration histogram tracking
  - Implement counters for pool exhaustion and acquisition failures
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 2. Create HTTP metrics service infrastructure
  - Implement HttpMetricsService class for HTTP connection pool monitoring
  - Create methods to register HTTP connection pool gauges with protocol labels
  - Configure RestTemplate with PoolingHttpClientConnectionManager for metrics access
  - Add HTTP connection pool state tracking (total, active, idle connections)
  - _Requirements: 2.1, 2.2, 2.3_

- [x] 3. Implement metrics configuration and auto-registration
  - Create MetricsConfiguration class to centralize metric registration
  - Add @PostConstruct methods for automatic metric initialization on startup
  - Implement proper error handling for metric registration failures
  - Ensure metrics use existing MeterRegistry configuration
  - _Requirements: 3.1, 3.2, 3.3, 5.1_

- [x] 4. Integrate database metrics with existing data access
  - Modify application configuration to expose HikariCP MXBean for metrics access
  - Add database metrics collection to capture connection acquisition timing
  - Implement connection pool monitoring without modifying existing repository code
  - Add proper exception handling for database metrics collection
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 5.2_

- [x] 5. Integrate HTTP metrics with service clients
  - Configure RestTemplate bean with connection pooling and metrics access
  - Add HTTP metrics collection to PortfolioServiceClient and SecurityServiceClient
  - Implement protocol detection (http/https) for proper labeling
  - Ensure HTTP metrics don't interfere with existing service client functionality
  - _Requirements: 2.1, 2.2, 2.3, 5.3_

- [x] 6. Implement Prometheus naming conventions and labeling
  - Ensure all counter metrics use _total suffix
  - Implement duration metrics with _seconds suffix and histogram type
  - Use snake_case format for all metric names
  - Add descriptive metric descriptions and proper labeling
  - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [x] 7. Add configuration properties for metrics customization
  - Create application properties for enabling/disabling custom metrics
  - Add configuration for metrics collection intervals and thresholds
  - Implement conditional metric registration based on configuration
  - Ensure backward compatibility with existing configuration
  - _Requirements: 3.3, 5.1_

- [ ] 8. Create comprehensive unit tests for metrics services
  - Write unit tests for DatabaseMetricsService metric registration and collection
  - Create unit tests for HttpMetricsService with mocked connection managers
  - Test error handling scenarios for both metrics services
  - Verify metric naming conventions and labeling in tests
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 4.1, 4.2, 4.3, 4.4_

- [ ] 9. Create integration tests for end-to-end metrics flow
  - Write integration tests that verify metrics are collected during actual database operations
  - Create integration tests for HTTP metrics during service client calls
  - Test metrics export via existing OTLP configuration
  - Verify resource attributes are properly included in exported metrics
  - _Requirements: 3.1, 3.2, 3.4, 5.2, 5.3_

- [ ] 10. Add proper resource cleanup and lifecycle management
  - Implement proper shutdown hooks for metrics cleanup
  - Add resource management for metric collection threads if needed
  - Ensure metrics services properly handle application shutdown
  - Test metrics behavior during application restart scenarios
  - _Requirements: 5.4_