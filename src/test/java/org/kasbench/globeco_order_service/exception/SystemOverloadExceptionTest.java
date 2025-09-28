package org.kasbench.globeco_order_service.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SystemOverloadExceptionTest {
    
    @Test
    void testConstructorWithMessageAndRetryAfter() {
        String message = "System temporarily overloaded";
        int retryAfterSeconds = 300;
        
        SystemOverloadException exception = new SystemOverloadException(message, retryAfterSeconds);
        
        assertEquals(message, exception.getMessage());
        assertEquals(retryAfterSeconds, exception.getRetryAfterSeconds());
        assertEquals("system_overload", exception.getOverloadReason());
        assertNull(exception.getCause());
    }
    
    @Test
    void testConstructorWithMessageRetryAfterAndReason() {
        String message = "Database connection pool exhausted";
        int retryAfterSeconds = 180;
        String overloadReason = "database_connection_pool_exhausted";
        
        SystemOverloadException exception = new SystemOverloadException(message, retryAfterSeconds, overloadReason);
        
        assertEquals(message, exception.getMessage());
        assertEquals(retryAfterSeconds, exception.getRetryAfterSeconds());
        assertEquals(overloadReason, exception.getOverloadReason());
        assertNull(exception.getCause());
    }
    
    @Test
    void testConstructorWithAllParameters() {
        String message = "Thread pool overloaded";
        int retryAfterSeconds = 120;
        String overloadReason = "thread_pool_exhausted";
        RuntimeException cause = new RuntimeException("Underlying cause");
        
        SystemOverloadException exception = new SystemOverloadException(message, retryAfterSeconds, overloadReason, cause);
        
        assertEquals(message, exception.getMessage());
        assertEquals(retryAfterSeconds, exception.getRetryAfterSeconds());
        assertEquals(overloadReason, exception.getOverloadReason());
        assertEquals(cause, exception.getCause());
    }
    
    @Test
    void testGetRetryAfterSeconds() {
        int expectedRetryAfter = 240;
        SystemOverloadException exception = new SystemOverloadException("Test message", expectedRetryAfter);
        
        assertEquals(expectedRetryAfter, exception.getRetryAfterSeconds());
    }
    
    @Test
    void testGetOverloadReason() {
        String expectedReason = "memory_exhausted";
        SystemOverloadException exception = new SystemOverloadException("Test message", 60, expectedReason);
        
        assertEquals(expectedReason, exception.getOverloadReason());
    }
    
    @Test
    void testDefaultOverloadReason() {
        SystemOverloadException exception = new SystemOverloadException("Test message", 60);
        
        assertEquals("system_overload", exception.getOverloadReason());
    }
    
    @Test
    void testExceptionInheritance() {
        SystemOverloadException exception = new SystemOverloadException("Test message", 60);
        
        assertTrue(exception instanceof RuntimeException);
        assertTrue(exception instanceof Exception);
        assertTrue(exception instanceof Throwable);
    }
    
    @Test
    void testExceptionWithZeroRetryAfter() {
        SystemOverloadException exception = new SystemOverloadException("Immediate retry", 0);
        
        assertEquals(0, exception.getRetryAfterSeconds());
        assertEquals("Immediate retry", exception.getMessage());
    }
    
    @Test
    void testExceptionWithNegativeRetryAfter() {
        SystemOverloadException exception = new SystemOverloadException("Invalid retry", -1);
        
        assertEquals(-1, exception.getRetryAfterSeconds());
        assertEquals("Invalid retry", exception.getMessage());
    }
    
    @Test
    void testExceptionWithLargeRetryAfter() {
        int largeRetryAfter = 3600; // 1 hour
        SystemOverloadException exception = new SystemOverloadException("Long retry", largeRetryAfter);
        
        assertEquals(largeRetryAfter, exception.getRetryAfterSeconds());
    }
    
    @Test
    void testExceptionWithNullMessage() {
        SystemOverloadException exception = new SystemOverloadException(null, 60);
        
        assertNull(exception.getMessage());
        assertEquals(60, exception.getRetryAfterSeconds());
        assertEquals("system_overload", exception.getOverloadReason());
    }
    
    @Test
    void testExceptionWithEmptyMessage() {
        SystemOverloadException exception = new SystemOverloadException("", 60);
        
        assertEquals("", exception.getMessage());
        assertEquals(60, exception.getRetryAfterSeconds());
    }
    
    @Test
    void testExceptionWithNullOverloadReason() {
        SystemOverloadException exception = new SystemOverloadException("Test", 60, null);
        
        assertNull(exception.getOverloadReason());
        assertEquals(60, exception.getRetryAfterSeconds());
    }
}