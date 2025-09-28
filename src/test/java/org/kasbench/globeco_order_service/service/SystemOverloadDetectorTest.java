package org.kasbench.globeco_order_service.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.sql.DataSource;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.reflect.Field;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SystemOverloadDetector service.
 * Tests various resource utilization scenarios and overload detection logic.
 */
@ExtendWith(MockitoExtension.class)
class SystemOverloadDetectorTest {

    @Mock
    private HikariDataSource hikariDataSource;
    
    @Mock
    private HikariPoolMXBean hikariPoolMXBean;
    
    @Mock
    private ThreadPoolTaskExecutor taskExecutor;
    
    @Mock
    private ThreadPoolExecutor threadPoolExecutor;
    
    @Mock
    private DataSource regularDataSource;
    
    @Mock
    private MemoryMXBean memoryMXBean;
    
    @Mock
    private MemoryUsage memoryUsage;
    
    private SystemOverloadDetector detector;

    @BeforeEach
    void setUp() throws Exception {
        // Create detector with HikariDataSource
        detector = new SystemOverloadDetector(hikariDataSource, taskExecutor);
        
        // Set up HikariCP mocks
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        
        // Set up ThreadPoolTaskExecutor mocks
        when(taskExecutor.getThreadPoolExecutor()).thenReturn(threadPoolExecutor);
        
        // Initialize the detector
        detector.initialize();
        
        // Inject mock MemoryMXBean using reflection
        Field memoryMXBeanField = SystemOverloadDetector.class.getDeclaredField("memoryMXBean");
        memoryMXBeanField.setAccessible(true);
        memoryMXBeanField.set(detector, memoryMXBean);
        
        // Set up memory usage mocks
        when(memoryMXBean.getHeapMemoryUsage()).thenReturn(memoryUsage);
    }

    @Test
    void testInitializeWithHikariDataSource() {
        // Given: HikariDataSource is provided
        SystemOverloadDetector newDetector = new SystemOverloadDetector(hikariDataSource, taskExecutor);
        
        // When: initialize is called
        newDetector.initialize();
        
        // Then: detector should be initialized
        assertTrue(newDetector.isInitialized());
    }

    @Test
    void testInitializeWithRegularDataSource() {
        // Given: Regular DataSource (not HikariDataSource)
        SystemOverloadDetector newDetector = new SystemOverloadDetector(regularDataSource, taskExecutor);
        
        // When: initialize is called
        newDetector.initialize();
        
        // Then: detector should still be initialized (HikariCP is optional)
        assertTrue(newDetector.isInitialized());
    }

    @Test
    void testIsSystemOverloaded_ThreadPoolOverload() {
        // Given: Thread pool is overloaded (95% utilization)
        when(threadPoolExecutor.getActiveCount()).thenReturn(95);
        when(threadPoolExecutor.getMaximumPoolSize()).thenReturn(100);
        
        // Database and memory are normal
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(10);
        when(hikariDataSource.getMaximumPoolSize()).thenReturn(50);
        when(memoryUsage.getUsed()).thenReturn(500L * 1024 * 1024); // 500MB
        when(memoryUsage.getMax()).thenReturn(1000L * 1024 * 1024); // 1GB
        
        // When: checking system overload
        boolean result = detector.isSystemOverloaded();
        
        // Then: system should be overloaded
        assertTrue(result);
    }

    @Test
    void testIsSystemOverloaded_DatabasePoolOverload() {
        // Given: Database pool is overloaded (96% utilization)
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(48);
        when(hikariDataSource.getMaximumPoolSize()).thenReturn(50);
        
        // Thread pool and memory are normal
        when(threadPoolExecutor.getActiveCount()).thenReturn(10);
        when(threadPoolExecutor.getMaximumPoolSize()).thenReturn(100);
        when(memoryUsage.getUsed()).thenReturn(500L * 1024 * 1024); // 500MB
        when(memoryUsage.getMax()).thenReturn(1000L * 1024 * 1024); // 1GB
        
        // When: checking system overload
        boolean result = detector.isSystemOverloaded();
        
        // Then: system should be overloaded
        assertTrue(result);
    }

    @Test
    void testIsSystemOverloaded_MemoryOverload() {
        // Given: Memory is overloaded (90% utilization)
        when(memoryUsage.getUsed()).thenReturn(900L * 1024 * 1024); // 900MB
        when(memoryUsage.getMax()).thenReturn(1000L * 1024 * 1024); // 1GB
        
        // Thread pool and database are normal
        when(threadPoolExecutor.getActiveCount()).thenReturn(10);
        when(threadPoolExecutor.getMaximumPoolSize()).thenReturn(100);
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(10);
        when(hikariDataSource.getMaximumPoolSize()).thenReturn(50);
        
        // When: checking system overload
        boolean result = detector.isSystemOverloaded();
        
        // Then: system should be overloaded
        assertTrue(result);
    }

    @Test
    void testIsSystemOverloaded_NoOverload() {
        // Given: All resources are within normal limits
        when(threadPoolExecutor.getActiveCount()).thenReturn(50);
        when(threadPoolExecutor.getMaximumPoolSize()).thenReturn(100);
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(20);
        when(hikariDataSource.getMaximumPoolSize()).thenReturn(50);
        when(memoryUsage.getUsed()).thenReturn(400L * 1024 * 1024); // 400MB
        when(memoryUsage.getMax()).thenReturn(1000L * 1024 * 1024); // 1GB
        
        // When: checking system overload
        boolean result = detector.isSystemOverloaded();
        
        // Then: system should not be overloaded
        assertFalse(result);
    }

    @Test
    void testIsThreadPoolOverloaded_Overloaded() {
        // Given: Thread pool utilization is 95% (above 90% threshold)
        when(threadPoolExecutor.getActiveCount()).thenReturn(95);
        when(threadPoolExecutor.getMaximumPoolSize()).thenReturn(100);
        
        // When: checking thread pool overload
        boolean result = detector.isThreadPoolOverloaded();
        
        // Then: should be overloaded
        assertTrue(result);
    }

    @Test
    void testIsThreadPoolOverloaded_NotOverloaded() {
        // Given: Thread pool utilization is 80% (below 90% threshold)
        when(threadPoolExecutor.getActiveCount()).thenReturn(80);
        when(threadPoolExecutor.getMaximumPoolSize()).thenReturn(100);
        
        // When: checking thread pool overload
        boolean result = detector.isThreadPoolOverloaded();
        
        // Then: should not be overloaded
        assertFalse(result);
    }

    @Test
    void testIsThreadPoolOverloaded_NoTaskExecutor() {
        // Given: No task executor available
        SystemOverloadDetector detectorWithoutExecutor = new SystemOverloadDetector(hikariDataSource, null);
        detectorWithoutExecutor.initialize();
        
        // When: checking thread pool overload
        boolean result = detectorWithoutExecutor.isThreadPoolOverloaded();
        
        // Then: should not be overloaded (no executor to check)
        assertFalse(result);
    }

    @Test
    void testIsDatabaseConnectionPoolOverloaded_Overloaded() {
        // Given: Database pool utilization is 96% (above 95% threshold)
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(48);
        when(hikariDataSource.getMaximumPoolSize()).thenReturn(50);
        
        // When: checking database pool overload
        boolean result = detector.isDatabaseConnectionPoolOverloaded();
        
        // Then: should be overloaded
        assertTrue(result);
    }

    @Test
    void testIsDatabaseConnectionPoolOverloaded_NotOverloaded() {
        // Given: Database pool utilization is 80% (below 95% threshold)
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(40);
        when(hikariDataSource.getMaximumPoolSize()).thenReturn(50);
        
        // When: checking database pool overload
        boolean result = detector.isDatabaseConnectionPoolOverloaded();
        
        // Then: should not be overloaded
        assertFalse(result);
    }

    @Test
    void testIsDatabaseConnectionPoolOverloaded_NoHikariCP() {
        // Given: Regular DataSource (not HikariDataSource)
        SystemOverloadDetector detectorWithRegularDS = new SystemOverloadDetector(regularDataSource, taskExecutor);
        detectorWithRegularDS.initialize();
        
        // When: checking database pool overload
        boolean result = detectorWithRegularDS.isDatabaseConnectionPoolOverloaded();
        
        // Then: should not be overloaded (no HikariCP to check)
        assertFalse(result);
    }

    @Test
    void testIsMemoryOverloaded_Overloaded() {
        // Given: Memory utilization is 90% (above 85% threshold)
        when(memoryUsage.getUsed()).thenReturn(900L * 1024 * 1024); // 900MB
        when(memoryUsage.getMax()).thenReturn(1000L * 1024 * 1024); // 1GB
        
        // When: checking memory overload
        boolean result = detector.isMemoryOverloaded();
        
        // Then: should be overloaded
        assertTrue(result);
    }

    @Test
    void testIsMemoryOverloaded_NotOverloaded() {
        // Given: Memory utilization is 70% (below 85% threshold)
        when(memoryUsage.getUsed()).thenReturn(700L * 1024 * 1024); // 700MB
        when(memoryUsage.getMax()).thenReturn(1000L * 1024 * 1024); // 1GB
        
        // When: checking memory overload
        boolean result = detector.isMemoryOverloaded();
        
        // Then: should not be overloaded
        assertFalse(result);
    }

    @Test
    void testIsMemoryOverloaded_MaxMemoryNotAvailable() {
        // Given: Max memory is not available, use committed memory
        when(memoryUsage.getUsed()).thenReturn(700L * 1024 * 1024); // 700MB
        when(memoryUsage.getMax()).thenReturn(-1L); // Max not available
        when(memoryUsage.getCommitted()).thenReturn(1000L * 1024 * 1024); // 1GB committed
        
        // When: checking memory overload
        boolean result = detector.isMemoryOverloaded();
        
        // Then: should not be overloaded (70% of committed)
        assertFalse(result);
    }

    @Test
    void testCalculateRetryDelay_LowUtilization() {
        // Given: Low resource utilization (50%)
        when(threadPoolExecutor.getActiveCount()).thenReturn(50);
        when(threadPoolExecutor.getMaximumPoolSize()).thenReturn(100);
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(25);
        when(hikariDataSource.getMaximumPoolSize()).thenReturn(50);
        when(memoryUsage.getUsed()).thenReturn(500L * 1024 * 1024); // 500MB
        when(memoryUsage.getMax()).thenReturn(1000L * 1024 * 1024); // 1GB
        
        // When: calculating retry delay
        int delay = detector.calculateRetryDelay();
        
        // Then: delay should be closer to base delay (60 seconds)
        assertTrue(delay >= 60);
        assertTrue(delay <= 180); // Should be in lower range
    }

    @Test
    void testCalculateRetryDelay_HighUtilization() {
        // Given: High resource utilization (95%)
        when(threadPoolExecutor.getActiveCount()).thenReturn(95);
        when(threadPoolExecutor.getMaximumPoolSize()).thenReturn(100);
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(47);
        when(hikariDataSource.getMaximumPoolSize()).thenReturn(50);
        when(memoryUsage.getUsed()).thenReturn(950L * 1024 * 1024); // 950MB
        when(memoryUsage.getMax()).thenReturn(1000L * 1024 * 1024); // 1GB
        
        // When: calculating retry delay
        int delay = detector.calculateRetryDelay();
        
        // Then: delay should be closer to max delay (300 seconds)
        assertTrue(delay >= 250);
        assertTrue(delay <= 300);
    }

    @Test
    void testCalculateRetryDelay_ExceptionHandling() {
        // Given: Exception occurs during calculation
        when(threadPoolExecutor.getActiveCount()).thenThrow(new RuntimeException("Test exception"));
        
        // When: calculating retry delay
        int delay = detector.calculateRetryDelay();
        
        // Then: should return base delay
        assertEquals(60, delay);
    }

    @Test
    void testGetThreadPoolUtilization() {
        // Given: Thread pool with 75% utilization
        when(threadPoolExecutor.getActiveCount()).thenReturn(75);
        when(threadPoolExecutor.getMaximumPoolSize()).thenReturn(100);
        
        // When: getting utilization
        double utilization = detector.getThreadPoolUtilization();
        
        // Then: should return 0.75
        assertEquals(0.75, utilization, 0.01);
    }

    @Test
    void testGetDatabaseConnectionUtilization() {
        // Given: Database pool with 80% utilization
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(40);
        when(hikariDataSource.getMaximumPoolSize()).thenReturn(50);
        
        // When: getting utilization
        double utilization = detector.getDatabaseConnectionUtilization();
        
        // Then: should return 0.80
        assertEquals(0.80, utilization, 0.01);
    }

    @Test
    void testGetMemoryUtilization() {
        // Given: Memory with 60% utilization
        when(memoryUsage.getUsed()).thenReturn(600L * 1024 * 1024); // 600MB
        when(memoryUsage.getMax()).thenReturn(1000L * 1024 * 1024); // 1GB
        
        // When: getting utilization
        double utilization = detector.getMemoryUtilization();
        
        // Then: should return 0.60
        assertEquals(0.60, utilization, 0.01);
    }

    @Test
    void testGetSystemStatus() {
        // Given: System with various resource utilizations
        when(threadPoolExecutor.getActiveCount()).thenReturn(75);
        when(threadPoolExecutor.getMaximumPoolSize()).thenReturn(100);
        when(threadPoolExecutor.getCorePoolSize()).thenReturn(50);
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(30);
        when(hikariDataSource.getMaximumPoolSize()).thenReturn(50);
        when(memoryUsage.getUsed()).thenReturn(600L * 1024 * 1024); // 600MB
        when(memoryUsage.getMax()).thenReturn(1000L * 1024 * 1024); // 1GB
        
        // When: getting system status
        String status = detector.getSystemStatus();
        
        // Then: status should contain resource information
        assertNotNull(status);
        assertTrue(status.contains("Thread Pool"));
        assertTrue(status.contains("Database Pool"));
        assertTrue(status.contains("Memory"));
        assertTrue(status.contains("Overall Status"));
    }

    @Test
    void testIsInitialized() {
        // When: checking if detector is initialized
        boolean initialized = detector.isInitialized();
        
        // Then: should be initialized
        assertTrue(initialized);
    }

    @Test
    void testExceptionHandlingInOverloadCheck() {
        // Given: Exception occurs during overload check
        when(threadPoolExecutor.getActiveCount()).thenThrow(new RuntimeException("Test exception"));
        
        // When: checking system overload
        boolean result = detector.isSystemOverloaded();
        
        // Then: should return false (assume not overloaded on error)
        assertFalse(result);
    }

    @Test
    void testZeroMaxValues() {
        // Given: Zero max values (edge case)
        when(threadPoolExecutor.getActiveCount()).thenReturn(10);
        when(threadPoolExecutor.getMaximumPoolSize()).thenReturn(0);
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(5);
        when(hikariDataSource.getMaximumPoolSize()).thenReturn(0);
        
        // When: checking overload conditions
        boolean threadPoolOverloaded = detector.isThreadPoolOverloaded();
        boolean dbPoolOverloaded = detector.isDatabaseConnectionPoolOverloaded();
        
        // Then: should not be overloaded (division by zero protection)
        assertFalse(threadPoolOverloaded);
        assertFalse(dbPoolOverloaded);
    }

    @Test
    void testActiveRequestRatioHigh() {
        // Given: High active request ratio (95% of core pool size)
        when(threadPoolExecutor.getActiveCount()).thenReturn(95);
        when(threadPoolExecutor.getMaximumPoolSize()).thenReturn(100);
        when(threadPoolExecutor.getCorePoolSize()).thenReturn(100);
        
        // Other resources are normal
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(10);
        when(hikariDataSource.getMaximumPoolSize()).thenReturn(50);
        when(memoryUsage.getUsed()).thenReturn(400L * 1024 * 1024); // 400MB
        when(memoryUsage.getMax()).thenReturn(1000L * 1024 * 1024); // 1GB
        
        // When: checking system overload
        boolean result = detector.isSystemOverloaded();
        
        // Then: system should be overloaded due to high active request ratio
        assertTrue(result);
    }
}