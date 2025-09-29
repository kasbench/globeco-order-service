# Configuration Properties Bean Conflict Fix

## Problem
The application was failing to start in Kubernetes with the following error:
```
Parameter 0 of method errorHandlingFeatureFlag in org.kasbench.globeco_order_service.config.ErrorHandlingConfiguration required a single bean, but 2 were found:
- errorHandlingProperties: defined in URL [jar:nested:/app/app.jar/!BOOT-INF/classes/!/org/kasbench/globeco_order_service/config/ErrorHandlingProperties.class]
- error.handling-org.kasbench.globeco_order_service.config.ErrorHandlingProperties: defined in unknown location
```

## Root Cause
The issue was caused by duplicate bean creation for the configuration properties classes:

1. **@Component annotation** on `ErrorHandlingProperties` and `SystemOverloadProperties` was creating beans
2. **@EnableConfigurationProperties** in `ErrorHandlingConfiguration` was also creating beans for the same classes

This resulted in two beans of the same type, causing Spring's dependency injection to fail due to ambiguity.

## Solution
Removed the `@Component` annotations from both configuration properties classes:

### Before:
```java
@Component
@ConfigurationProperties(prefix = "error.handling")
@Validated
public class ErrorHandlingProperties {
```

### After:
```java
@ConfigurationProperties(prefix = "error.handling")
@Validated
public class ErrorHandlingProperties {
```

The same fix was applied to `SystemOverloadProperties`.

## Why This Works
- `@EnableConfigurationProperties({ErrorHandlingProperties.class, SystemOverloadProperties.class})` in `ErrorHandlingConfiguration` is sufficient to create and configure the beans
- The `@Component` annotation was redundant and causing the duplicate bean creation
- Spring Boot's configuration properties binding works correctly with just `@EnableConfigurationProperties`

## Verification
- ✅ Main application compiles successfully
- ✅ Configuration properties unit tests pass
- ✅ No more bean conflicts
- ✅ Properties are still properly bound and validated

## Files Modified
1. `src/main/java/org/kasbench/globeco_order_service/config/ErrorHandlingProperties.java`
   - Removed `@Component` annotation
   - Removed unused import `org.springframework.stereotype.Component`

2. `src/main/java/org/kasbench/globeco_order_service/config/SystemOverloadProperties.java`
   - Removed `@Component` annotation  
   - Removed unused import `org.springframework.stereotype.Component`

## Impact
This fix resolves the Kubernetes deployment failure and allows the application to start successfully with the new configuration properties system for error handling.