package org.kasbench.globeco_order_service.exception;

/**
 * Custom exception for system overload conditions.
 * Used to indicate that the system is temporarily overloaded and clients should retry.
 */
public class SystemOverloadException extends RuntimeException {
    
    private final int retryAfterSeconds;
    private final String overloadReason;
    
    public SystemOverloadException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
        this.overloadReason = "system_overload";
    }
    
    public SystemOverloadException(String message, int retryAfterSeconds, String overloadReason) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
        this.overloadReason = overloadReason;
    }
    
    public SystemOverloadException(String message, int retryAfterSeconds, String overloadReason, Throwable cause) {
        super(message, cause);
        this.retryAfterSeconds = retryAfterSeconds;
        this.overloadReason = overloadReason;
    }
    
    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
    
    public String getOverloadReason() {
        return overloadReason;
    }
}