package org.kasbench.globeco_order_service.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Service for detecting system overload conditions by monitoring various system resources.
 * This service monitors thread pool utilization, database connection pool usage, and memory consumption
 * to determine if the system is experiencing overload conditions that warrant returning 503 Service Unavailable
 * responses instead of processing new requests.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "error.handling.enabled", havingValue = "true", matchIfMissing = true)
public class SystemOverloadDetector {

    private final DataSource dataSource;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final MemoryMXBean memoryMXBean;
    
    // HikariCP specific components for database monitoring
    private HikariDataSource hikariDataSource;
    private HikariPoolMXBean hikariPoolMXBean;
    
    // Performance metrics tracking
    private long totalOverloadChecks = 0;
    private long totalOverloadCheckTimeNanos = 0;
    
    // Configuration thresholds (can be made configurable via properties)
    private static final double THREAD_POOL_THRESHOLD = 0.9; // 90%
    private static final double DATABASE_POOL_THRESHOLD = 0.95; // 95%
    private static final double MEMORY_THRESHOLD = 0.85; // 85%
    private static final double REQUEST_RATIO_THRESHOLD = 0.9; // 90%
    
    // Base retry delays in seconds
    private static final int BASE_RETRY_DELAY = 60;
    private static final int MAX_RETRY_DELAY = 300;

    @Autowired
    public SystemOverloadDetector(DataSource dataSource, 
                                 @Autowired(required = false) ThreadPoolTaskExecutor taskExecutor) {
        this.dataSource = dataSource;
        this.taskExecutor = taskExecutor;
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        
        log.debug("SystemOverloadDetector initialized with DataSource: {}, TaskExecutor: {}", 
                 dataSource != null ? dataSource.getClass().getSimpleName() : "null",
                 taskExecutor != null ? "available" : "null");
    }

    /**
     * Initialize the overload detector by setting up HikariCP integration.
     */
    @PostConstruct
    public void initialize() {
        try {
            setupHikariIntegration();
            log.info("SystemOverloadDetector initialized successfully");
        } catch (Exception e) {
            log.warn("Failed to initialize SystemOverloadDetector: {}", e.getMessage(), e);
            // Continue application startup even if initialization fails
        }
    }

    /**
     * Sets up integration with HikariCP for accessing database pool statistics.
     */
    private void setupHikariIntegration() {
        if (dataSource instanceof HikariDataSource) {
            this.hikariDataSource = (HikariDataSource) dataSource;
            this.hikariPoolMXBean = hikariDataSource.getHikariPoolMXBean();
            log.debug("HikariCP integration established for overload detection");
        } else {
            log.warn("DataSource is not a HikariDataSource. Database overload detection may not work properly. Current type: {}", 
                    dataSource.getClass().getSimpleName());
        }
    }

    /**
     * Checks if the system is currently experiencing overload conditions.
     * This is the main method that determines whether to return 503 Service Unavailable.
     * 
     * @return true if the system is overloaded and should reject new requests
     */
    public boolean isSystemOverloaded() {
        long startTime = System.nanoTime();
        
        try {
            // Check thread pool utilization
            if (isThreadPoolOverloaded()) {
                log.debug("System overload detected: thread pool utilization exceeded threshold");
                recordOverloadDetectionPerformance(startTime, true);
                return true;
            }
            
            // Check database connection pool utilization
            if (isDatabaseConnectionPoolOverloaded()) {
                log.debug("System overload detected: database connection pool utilization exceeded threshold");
                recordOverloadDetectionPerformance(startTime, true);
                return true;
            }
            
            // Check memory usage
            if (isMemoryOverloaded()) {
                log.debug("System overload detected: memory utilization exceeded threshold");
                recordOverloadDetectionPerformance(startTime, true);
                return true;
            }
            
            // Check active request ratio (if thread pool is available)
            if (isActiveRequestRatioHigh()) {
                log.debug("System overload detected: active request ratio exceeded threshold");
                recordOverloadDetectionPerformance(startTime, true);
                return true;
            }
            
            recordOverloadDetectionPerformance(startTime, false);
            return false;
            
        } catch (Exception e) {
            log.warn("Error checking system overload status: {}", e.getMessage(), e);
            recordOverloadDetectionPerformance(startTime, false);
            // In case of error, assume system is not overloaded to avoid false positives
            return false;
        }
    }

    /**
     * Checks if the thread pool utilization exceeds the threshold.
     * 
     * @return true if thread pool is overloaded
     */
    public boolean isThreadPoolOverloaded() {
        if (taskExecutor == null) {
            log.debug("ThreadPoolTaskExecutor not available, skipping thread pool overload check");
            return false;
        }
        
        try {
            ThreadPoolExecutor threadPool = taskExecutor.getThreadPoolExecutor();
            if (threadPool == null) {
                log.debug("ThreadPoolExecutor not available, skipping thread pool overload check");
                return false;
            }
            
            int activeThreads = threadPool.getActiveCount();
            int maxThreads = threadPool.getMaximumPoolSize();
            
            if (maxThreads == 0) {
                return false;
            }
            
            double utilization = (double) activeThreads / maxThreads;
            boolean overloaded = utilization > THREAD_POOL_THRESHOLD;
            
            log.debug("Thread pool utilization: {}/{} ({:.2f}%), threshold: {:.2f}%, overloaded: {}", 
                     activeThreads, maxThreads, utilization * 100, THREAD_POOL_THRESHOLD * 100, overloaded);
            
            return overloaded;
            
        } catch (Exception e) {
            log.debug("Failed to check thread pool utilization: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the database connection pool utilization exceeds the threshold.
     * 
     * @return true if database connection pool is overloaded
     */
    public boolean isDatabaseConnectionPoolOverloaded() {
        if (hikariPoolMXBean == null || hikariDataSource == null) {
            log.debug("HikariCP MXBean not available, skipping database pool overload check");
            return false;
        }
        
        try {
            int activeConnections = hikariPoolMXBean.getActiveConnections();
            int maxConnections = hikariDataSource.getMaximumPoolSize();
            
            if (maxConnections == 0) {
                return false;
            }
            
            double utilization = (double) activeConnections / maxConnections;
            boolean overloaded = utilization > DATABASE_POOL_THRESHOLD;
            
            log.debug("Database pool utilization: {}/{} ({:.2f}%), threshold: {:.2f}%, overloaded: {}", 
                     activeConnections, maxConnections, utilization * 100, DATABASE_POOL_THRESHOLD * 100, overloaded);
            
            return overloaded;
            
        } catch (Exception e) {
            log.debug("Failed to check database connection pool utilization: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the memory usage exceeds the threshold.
     * 
     * @return true if memory is overloaded
     */
    public boolean isMemoryOverloaded() {
        try {
            MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
            long usedMemory = heapMemoryUsage.getUsed();
            long maxMemory = heapMemoryUsage.getMax();
            
            if (maxMemory <= 0) {
                // If max memory is not available, use committed memory as fallback
                maxMemory = heapMemoryUsage.getCommitted();
            }
            
            if (maxMemory <= 0) {
                log.debug("Memory information not available, skipping memory overload check");
                return false;
            }
            
            double utilization = (double) usedMemory / maxMemory;
            boolean overloaded = utilization > MEMORY_THRESHOLD;
            
            log.debug("Memory utilization: {}/{} MB ({:.2f}%), threshold: {:.2f}%, overloaded: {}", 
                     usedMemory / (1024 * 1024), maxMemory / (1024 * 1024), 
                     utilization * 100, MEMORY_THRESHOLD * 100, overloaded);
            
            return overloaded;
            
        } catch (Exception e) {
            log.debug("Failed to check memory utilization: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the active request ratio is high, indicating potential overload.
     * This uses thread pool active count as a proxy for active requests.
     * 
     * @return true if active request ratio is high
     */
    private boolean isActiveRequestRatioHigh() {
        if (taskExecutor == null) {
            return false;
        }
        
        try {
            ThreadPoolExecutor threadPool = taskExecutor.getThreadPoolExecutor();
            if (threadPool == null) {
                return false;
            }
            
            int activeCount = threadPool.getActiveCount();
            int corePoolSize = threadPool.getCorePoolSize();
            
            if (corePoolSize == 0) {
                return false;
            }
            
            double ratio = (double) activeCount / corePoolSize;
            boolean high = ratio > REQUEST_RATIO_THRESHOLD;
            
            log.debug("Active request ratio: {}/{} ({:.2f}%), threshold: {:.2f}%, high: {}", 
                     activeCount, corePoolSize, ratio * 100, REQUEST_RATIO_THRESHOLD * 100, high);
            
            return high;
            
        } catch (Exception e) {
            log.debug("Failed to check active request ratio: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Calculates the recommended retry delay based on current system conditions.
     * The delay is calculated based on the severity of the overload condition.
     * 
     * @return retry delay in seconds
     */
    public int calculateRetryDelay() {
        try {
            double maxUtilization = Math.max(
                Math.max(getThreadPoolUtilization(), getDatabaseConnectionUtilization()),
                getMemoryUtilization()
            );
            
            // Scale delay based on utilization severity
            // Base delay: 60 seconds, max delay: 300 seconds
            int calculatedDelay = (int) (BASE_RETRY_DELAY + (MAX_RETRY_DELAY - BASE_RETRY_DELAY) * maxUtilization);
            
            // Ensure delay is within bounds
            int finalDelay = Math.max(BASE_RETRY_DELAY, Math.min(MAX_RETRY_DELAY, calculatedDelay));
            
            log.debug("Calculated retry delay: {} seconds (max utilization: {:.2f}%)", 
                     finalDelay, maxUtilization * 100);
            
            return finalDelay;
            
        } catch (Exception e) {
            log.debug("Failed to calculate retry delay, using base delay: {}", e.getMessage());
            return BASE_RETRY_DELAY;
        }
    }

    /**
     * Gets the current thread pool utilization as a ratio (0.0 to 1.0).
     * 
     * @return thread pool utilization ratio
     */
    public double getThreadPoolUtilization() {
        if (taskExecutor == null) {
            return 0.0;
        }
        
        try {
            ThreadPoolExecutor threadPool = taskExecutor.getThreadPoolExecutor();
            if (threadPool == null) {
                return 0.0;
            }
            
            int activeThreads = threadPool.getActiveCount();
            int maxThreads = threadPool.getMaximumPoolSize();
            
            return maxThreads > 0 ? (double) activeThreads / maxThreads : 0.0;
            
        } catch (Exception e) {
            log.debug("Failed to get thread pool utilization: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Gets the current database connection pool utilization as a ratio (0.0 to 1.0).
     * 
     * @return database connection pool utilization ratio
     */
    public double getDatabaseConnectionUtilization() {
        if (hikariPoolMXBean == null || hikariDataSource == null) {
            return 0.0;
        }
        
        try {
            int activeConnections = hikariPoolMXBean.getActiveConnections();
            int maxConnections = hikariDataSource.getMaximumPoolSize();
            
            return maxConnections > 0 ? (double) activeConnections / maxConnections : 0.0;
            
        } catch (Exception e) {
            log.debug("Failed to get database connection utilization: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Gets the current memory utilization as a ratio (0.0 to 1.0).
     * 
     * @return memory utilization ratio
     */
    public double getMemoryUtilization() {
        try {
            MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
            long usedMemory = heapMemoryUsage.getUsed();
            long maxMemory = heapMemoryUsage.getMax();
            
            if (maxMemory <= 0) {
                maxMemory = heapMemoryUsage.getCommitted();
            }
            
            return maxMemory > 0 ? (double) usedMemory / maxMemory : 0.0;
            
        } catch (Exception e) {
            log.debug("Failed to get memory utilization: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Gets a detailed status report of all monitored resources.
     * 
     * @return formatted status string
     */
    public String getSystemStatus() {
        StringBuilder status = new StringBuilder();
        status.append("System Overload Status:\n");
        
        try {
            // Thread pool status
            if (taskExecutor != null) {
                ThreadPoolExecutor threadPool = taskExecutor.getThreadPoolExecutor();
                if (threadPool != null) {
                    status.append(String.format("  Thread Pool: %d/%d active (%.1f%%), threshold: %.1f%%\n",
                            threadPool.getActiveCount(), threadPool.getMaximumPoolSize(),
                            getThreadPoolUtilization() * 100, THREAD_POOL_THRESHOLD * 100));
                } else {
                    status.append("  Thread Pool: ThreadPoolExecutor not available\n");
                }
            } else {
                status.append("  Thread Pool: TaskExecutor not available\n");
            }
            
            // Database pool status
            if (hikariPoolMXBean != null && hikariDataSource != null) {
                status.append(String.format("  Database Pool: %d/%d active (%.1f%%), threshold: %.1f%%\n",
                        hikariPoolMXBean.getActiveConnections(), hikariDataSource.getMaximumPoolSize(),
                        getDatabaseConnectionUtilization() * 100, DATABASE_POOL_THRESHOLD * 100));
            } else {
                status.append("  Database Pool: HikariCP MXBean not available\n");
            }
            
            // Memory status
            MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
            long usedMB = heapMemoryUsage.getUsed() / (1024 * 1024);
            long maxMB = heapMemoryUsage.getMax() / (1024 * 1024);
            if (maxMB <= 0) {
                maxMB = heapMemoryUsage.getCommitted() / (1024 * 1024);
            }
            status.append(String.format("  Memory: %d/%d MB (%.1f%%), threshold: %.1f%%\n",
                    usedMB, maxMB, getMemoryUtilization() * 100, MEMORY_THRESHOLD * 100));
            
            // Overall status
            boolean overloaded = isSystemOverloaded();
            status.append(String.format("  Overall Status: %s\n", overloaded ? "OVERLOADED" : "NORMAL"));
            
            if (overloaded) {
                status.append(String.format("  Recommended Retry Delay: %d seconds", calculateRetryDelay()));
            }
            
        } catch (Exception e) {
            status.append("  Error retrieving system status: ").append(e.getMessage());
        }
        
        return status.toString();
    }

    /**
     * Records performance metrics for overload detection operations.
     * 
     * @param startTimeNanos the start time in nanoseconds
     * @param overloadDetected whether overload was detected
     */
    private void recordOverloadDetectionPerformance(long startTimeNanos, boolean overloadDetected) {
        try {
            long durationNanos = System.nanoTime() - startTimeNanos;
            
            synchronized (this) {
                totalOverloadChecks++;
                totalOverloadCheckTimeNanos += durationNanos;
            }
            
            double durationMillis = durationNanos / 1_000_000.0;
            
            log.debug("Overload detection performance - Duration: {:.3f}ms, Overload: {}", 
                     durationMillis, overloadDetected);
            
        } catch (Exception e) {
            log.debug("Failed to record overload detection performance: {}", e.getMessage());
        }
    }
    
    /**
     * Gets the average overload detection time in milliseconds.
     * 
     * @return average detection time in milliseconds
     */
    public double getAverageOverloadDetectionTimeMillis() {
        synchronized (this) {
            if (totalOverloadChecks == 0) {
                return 0.0;
            }
            return (totalOverloadCheckTimeNanos / (double) totalOverloadChecks) / 1_000_000.0;
        }
    }
    
    /**
     * Gets the total number of overload detection checks performed.
     * 
     * @return total number of checks
     */
    public long getTotalOverloadChecks() {
        synchronized (this) {
            return totalOverloadChecks;
        }
    }
    
    /**
     * Gets performance statistics for overload detection.
     * 
     * @return formatted performance statistics string
     */
    public String getPerformanceStatistics() {
        synchronized (this) {
            if (totalOverloadChecks == 0) {
                return "Overload Detection Performance: No checks performed yet";
            }
            
            double avgTimeMillis = getAverageOverloadDetectionTimeMillis();
            double totalTimeMillis = totalOverloadCheckTimeNanos / 1_000_000.0;
            
            return String.format("Overload Detection Performance:\n" +
                               "  - Total checks: %d\n" +
                               "  - Total time: %.2f ms\n" +
                               "  - Average time per check: %.3f ms\n" +
                               "  - Checks per second (estimated): %.1f",
                               totalOverloadChecks, totalTimeMillis, avgTimeMillis,
                               avgTimeMillis > 0 ? 1000.0 / avgTimeMillis : 0.0);
        }
    }
    
    /**
     * Resets performance statistics. Useful for testing and monitoring.
     */
    public void resetPerformanceStatistics() {
        synchronized (this) {
            totalOverloadChecks = 0;
            totalOverloadCheckTimeNanos = 0;
            log.debug("Overload detection performance statistics reset");
        }
    }
    
    /**
     * Checks if the overload detector is properly initialized.
     * 
     * @return true if the detector is ready to use
     */
    public boolean isInitialized() {
        return memoryMXBean != null && 
               (hikariDataSource == null || hikariPoolMXBean != null); // HikariCP is optional
    }
}