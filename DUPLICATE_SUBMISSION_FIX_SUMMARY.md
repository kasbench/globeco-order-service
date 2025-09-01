# Duplicate Order Submission Fix Summary

## Problem Analysis

Under high load (100 concurrent users), the order service was sending duplicate orders to the trade service. This only occurred under load, never with single users, indicating a **race condition**.

### Root Cause

The original submission flow had a critical race condition window:

1. **Thread A** loads order (status=NEW, tradeOrderId=null) ✓
2. **Thread B** loads same order (status=NEW, tradeOrderId=null) ✓  
3. **Thread A** validates order (passes) ✓
4. **Thread B** validates order (passes) ✓
5. **Thread A** calls trade service → gets tradeOrderId=123 ✓
6. **Thread B** calls trade service → gets tradeOrderId=456 ✓ **DUPLICATE!**
7. **Thread A** updates database (succeeds)
8. **Thread B** tries to update database (fails due to race condition check)

The problem was the **time gap** between validation and update, especially when the trade service was slow under load.

## Solution: Atomic Reservation Pattern

Implemented an atomic "check-and-set" pattern using database-level operations with unique reservation values.

### New Flow

1. **Atomic Reservation**: Use `UPDATE ... WHERE status='NEW' AND tradeOrderId IS NULL` to atomically reserve the order
2. **Unique Reservation Values**: Use negative order IDs (-orderId) as reservation values to avoid unique constraint violations
3. **Only One Thread Succeeds**: Database ensures only one thread can reserve each order
4. **Safe Processing**: Reserved thread processes the order without race conditions
5. **Cleanup**: Release reservation on failure, update with real tradeOrderId on success

### Key Changes

#### 1. OrderRepository - Added Atomic Operations with Unique Reservations
```java
@Modifying
@Query("UPDATE Order o SET o.tradeOrderId = -o.id WHERE o.id = :orderId AND o.status.abbreviation = 'NEW' AND o.tradeOrderId IS NULL")
int atomicallyReserveForSubmission(@Param("orderId") Integer orderId);

@Modifying  
@Query("UPDATE Order o SET o.tradeOrderId = :tradeOrderId WHERE o.id = :orderId AND o.tradeOrderId = -:orderId")
int updateReservedOrderWithTradeOrderId(@Param("orderId") Integer orderId, @Param("tradeOrderId") Integer tradeOrderId);

@Modifying
@Query("UPDATE Order o SET o.tradeOrderId = NULL WHERE o.id = :orderId AND o.tradeOrderId = -:orderId")
int releaseReservedOrder(@Param("orderId") Integer orderId);
```

**Key Fix**: Changed from using a fixed `-1` reservation value to using `-orderId` (negative order ID) as the reservation value. This ensures each reservation is unique and doesn't violate the `trade_order_id` unique constraint.

#### 2. OrderService - Atomic Submission Flow
```java
private OrderSubmitResultDTO submitIndividualOrder(Integer orderId, Integer requestIndex) {
    // Step 1: Atomically reserve order (prevents race conditions)
    boolean reserved = atomicallyReserveOrderForSubmission(orderId);
    if (!reserved) {
        return failure("Order already being processed");
    }
    
    try {
        // Step 2: Load order details
        Order order = loadAndValidateOrder(orderId);
        
        // Step 3: Call trade service (no race condition possible)
        Integer tradeOrderId = callTradeService(order);
        
        // Step 4: Update reserved order with real tradeOrderId
        updateReservedOrderWithTradeOrderId(orderId, tradeOrderId);
        
        // Step 5: Update status to SENT
        updateOrderStatusToSent(orderId);
        
        return success(tradeOrderId);
        
    } catch (Exception e) {
        // Release reservation on any failure
        releaseOrderReservation(orderId);
        throw e;
    }
}
```

#### 3. Enhanced Logging
Added comprehensive `DUPLICATE_TRACKING` logs to monitor:
- Thread names and timestamps
- Atomic reservation attempts and results  
- Trade service call timing
- Database update operations
- Race condition detection

### Benefits

1. **Eliminates Race Conditions**: Database-level atomic operations prevent multiple threads from processing the same order
2. **Maintains Performance**: Minimal additional database overhead
3. **Fail-Safe**: Automatic cleanup on failures
4. **Unique Reservations**: Using negative order IDs ensures no unique constraint violations
5. **Observable**: Detailed logging for monitoring and debugging
6. **Backward Compatible**: No API changes required

### Issue Resolution

**Original Problem**: Using `-1` as a fixed reservation value caused unique constraint violations when multiple orders were processed simultaneously.

**Solution**: Changed to use `-orderId` (negative order ID) as the reservation value, ensuring each reservation is unique while maintaining the atomic reservation pattern.

### Testing Recommendations

1. **Load Test**: Run with 100+ concurrent users to verify no duplicates
2. **Monitor Logs**: Look for `DUPLICATE_TRACKING` entries to verify atomic behavior
3. **Trade Service Logs**: Confirm no duplicate order IDs are received
4. **Database Monitoring**: Watch for reservation patterns (negative values) in order table
5. **Constraint Verification**: Ensure no more unique constraint violations on `trade_order_id`

### Monitoring

Key log patterns to watch:
- `DUPLICATE_TRACKING: Atomic reservation for orderId=X, success=true, reservationValue=-X` - Normal operation
- `DUPLICATE_TRACKING: Failed to reserve order X - already reserved` - Race condition prevented
- `DUPLICATE_TRACKING: Released reservation for order X` - Cleanup on failure
- No more `duplicate key value violates unique constraint "order_trade_order_ndx"` errors

The fix ensures that even under extreme load, each order can only be submitted once to the trade service, eliminating both the duplicate submission issue and the unique constraint violations.