# Implementation Plan

- [x] 1. Create HTTP request metrics service infrastructure
  - Implement HttpRequestMetricsService class with MeterRegistry integration
  - Create methods to register HTTP request counter, histogram, and gauge metrics
  - Add thread-safe metric recording methods with proper error handling
  - Implement route pattern sanitization utilities to prevent high cardinality
  - _Requirements: 1.1, 1.2, 2.1, 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3, 6.1, 6.2, 6.3, 6.4_

- [x] 2. Implement HTTP request metrics interceptor
  - Create HttpRequestMetricsInterceptor class implementing HandlerInterceptor
  - Add preHandle method to start request timing and increment in-flight gauge
  - Implement afterCompletion method to record metrics and decrement in-flight gauge
  - Add ThreadLocal context management for request timing information
  - Implement route pattern extraction from Spring HandlerMapping
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 3.1, 3.2, 3.3, 4.1, 4.2, 4.3, 4.4_

- [x] 3. Create configuration class for HTTP request metrics
  - Implement HttpRequestMetricsConfiguration class with conditional enablement
  - Register HttpRequestMetricsInterceptor with Spring's InterceptorRegistry
  - Add configuration properties for enabling/disabling HTTP request metrics
  - Integrate with existing MetricsConfiguration and MetricsProperties
  - Update [the Kubernetes deployment](../../../k8s/deployment.yaml) with new configuration, if required.
  - _Requirements: 4.1, 4.2, 5.1, 5.2, 5.3, 5.4_

- [x] 4. Implement request timing context and utilities
  - Create RequestTimingContext class to hold timing and request information
  - Implement RoutePatternSanitizer utility for path sanitization
  - Add proper ThreadLocal management with cleanup in finally blocks
  - Create utility methods for HTTP method normalization and status code handling
  - _Requirements: 1.4, 2.1, 2.2, 4.3, 6.1, 6.2, 6.3, 6.4_

- [ ] 5. Add comprehensive error handling and resilience
  - Implement error handling in interceptor methods to prevent request processing interference
  - Add fallback mechanisms for route pattern extraction failures
  - Ensure metric recording failures don't impact application performance
  - Add proper logging for debugging metric collection issues
  - _Requirements: 4.2, 4.3_

- [ ] 6. Create unit tests for HTTP request metrics service
  - Write unit tests for HttpRequestMetricsService metric registration and recording
  - Test route pattern sanitization with various URL patterns
  - Create tests for concurrent metric recording scenarios
  - Verify metric naming conventions and labeling requirements
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3, 6.1, 6.2, 6.3, 6.4_

- [ ] 7. Create unit tests for HTTP request metrics interceptor
  - Write unit tests for interceptor preHandle and afterCompletion methods
  - Test request timing accuracy and ThreadLocal context management
  - Create tests for route pattern extraction from different handler types
  - Test error handling scenarios and ensure request processing continues
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 3.1, 3.2, 3.3, 4.1, 4.2, 4.3, 4.4_

- [ ] 8. Create integration tests for end-to-end HTTP metrics collection
  - Write integration tests that make actual HTTP requests to various endpoints
  - Verify all three metrics (counter, histogram, gauge) are properly recorded
  - Test metrics collection for different HTTP methods, status codes, and endpoints
  - Validate metrics export via existing OTLP configuration
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3, 4.1, 4.2, 5.1, 5.2, 5.3, 5.4_

- [ ] 9. Add configuration properties and conditional enablement
  - Add HTTP request metrics properties to application.yml
  - Implement conditional bean creation based on configuration properties
  - Update existing MetricsProperties class to include HTTP request metrics settings
  - Add validation for configuration properties and sensible defaults
  - _Requirements: 5.1, 5.2, 5.3, 5.4_

- [ ] 10. Integrate with existing metrics infrastructure
  - Update MetricsConfiguration to initialize HTTP request metrics
  - Ensure HTTP request metrics use the same MeterRegistry as existing metrics
  - Verify metrics include proper resource attributes (service.name, service.version, etc.)
  - Test integration with existing OTLP export configuration
  - _Requirements: 5.1, 5.2, 5.3, 5.4_

- [ ] 11. Add performance optimization and caching
  - Implement metric instance caching to avoid repeated MeterRegistry lookups
  - Add route pattern caching for frequently accessed endpoints
  - Optimize ThreadLocal usage and ensure proper cleanup
  - Add performance tests to measure metrics collection overhead
  - _Requirements: 4.2, 4.3, 6.4_

- [ ] 12. Create comprehensive integration tests for all endpoint types
  - Test metrics collection for API endpoints (/api/v1/orders, /api/v1/blotters, etc.)
  - Verify metrics for health check endpoints and static resources
  - Test error response scenarios (4xx, 5xx status codes)
  - Validate metrics for different HTTP methods (GET, POST, PUT, DELETE, PATCH)
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 4.1, 4.2, 4.3_