package org.kasbench.globeco_order_service.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Test for SystemOverloadDetector performance metrics and monitoring capabilities.
 * Verifies that performance tracking works correctly and provides accurate metrics.
 */
@ExtendWith(MockitoExtension.class)
class SystemOverloadDetectorPerformanceTest {

    @Mock
    private HikariDataSource hikariDataSource;

    @Mock
    private HikariPoolMXBean hikariPoolMXBean;

    @Mock
    private ThreadPoolTaskExecutor taskExecutor;

    @Mock
    private ThreadPoolExecutor threadPoolExecutor;

    private SystemOverloadDetector systemOverloadDetector;

    @BeforeEach
    void setUp() {
        systemOverloadDetector = new SystemOverloadDetector(hikariDataSource, taskExecutor);
        
        // Set up HikariCP mocks
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        when(hikariDataSource.getMaximumPoolSize()).thenReturn(10);
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(5);
        
        // Set up thread pool mocks
        when(taskExecutor.getThreadPoolExecutor()).thenReturn(threadPoolExecutor);
        when(threadPoolExecutor.getActiveCount()).thenReturn(8);
        when(threadPoolExecutor.getMaximumPoolSize()).thenReturn(10);
        when(threadPoolExecutor.getCorePoolSize()).thenReturn(5);
        
        // Initialize the detector
        systemOverloadDetector.initialize();
    }

    @Test
    void testPerformanceMetricsInitialState() {
        // Given - fresh detector
        
        // When - checking initial performance metrics
        double avgTime = systemOverloadDetector.getAverageOverloadDetectionTimeMillis();
        long totalChecks = systemOverloadDetector.getTotalOverloadChecks();
        String stats = systemOverloadDetector.getPerformanceStatistics();
        
        // Then
        assertThat(avgTime).isEqualTo(0.0);
        assertThat(totalChecks).isEqualTo(0);
        assertThat(stats).contains("No checks performed yet");
    }

    @Test
    void testPerformanceMetricsAfterOverloadChecks() {
        // Given - system not overloaded
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(2); // Low utilization
        when(threadPoolExecutor.getActiveCount()).thenReturn(2); // Low utilization
        
        // When - performing multiple overload checks
        for (int i = 0; i < 10; i++) {
            systemOverloadDetector.isSystemOverloaded();
        }
        
        // Then
        double avgTime = systemOverloadDetector.getAverageOverloadDetectionTimeMillis();
        long totalChecks = systemOverloadDetector.getTotalOverloadChecks();
        String stats = systemOverloadDetector.getPerformanceStatistics();
        
        assertThat(avgTime).isGreaterThan(0.0);
        assertThat(avgTime).isLessThan(100.0); // Should be very fast (less than 100ms)
        assertThat(totalChecks).isEqualTo(10);
        assertThat(stats)
            .contains("Total checks: 10")
            .contains("Average time per check:")
            .contains("Checks per second");
    }

    @Test
    void testPerformanceMetricsWithOverloadDetected() {
        // Given - system overloaded (high database utilization)
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(10); // 100% utilization
        when(threadPoolExecutor.getActiveCount()).thenReturn(2); // Low utilization
        
        // When - performing overload checks that detect overload
        for (int i = 0; i < 5; i++) {
            boolean overloaded = systemOverloadDetector.isSystemOverloaded();
            assertThat(overloaded).isTrue(); // Should detect overload
        }
        
        // Then
        double avgTime = systemOverloadDetector.getAverageOverloadDetectionTimeMillis();
        long totalChecks = systemOverloadDetector.getTotalOverloadChecks();
        
        assertThat(avgTime).isGreaterThan(0.0);
        assertThat(totalChecks).isEqualTo(5);
    }

    @Test
    void testPerformanceMetricsReset() {
        // Given - detector with some performance data
        systemOverloadDetector.isSystemOverloaded();
        systemOverloadDetector.isSystemOverloaded();
        
        assertThat(systemOverloadDetector.getTotalOverloadChecks()).isEqualTo(2);
        assertThat(systemOverloadDetector.getAverageOverloadDetectionTimeMillis()).isGreaterThan(0.0);
        
        // When - resetting performance statistics
        systemOverloadDetector.resetPerformanceStatistics();
        
        // Then
        assertThat(systemOverloadDetector.getTotalOverloadChecks()).isEqualTo(0);
        assertThat(systemOverloadDetector.getAverageOverloadDetectionTimeMillis()).isEqualTo(0.0);
        assertThat(systemOverloadDetector.getPerformanceStatistics()).contains("No checks performed yet");
    }

    @Test
    void testPerformanceMetricsAccuracy() {
        // Given - system with predictable performance
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(1);
        when(threadPoolExecutor.getActiveCount()).thenReturn(1);
        
        // When - performing a known number of checks
        long startTime = System.nanoTime();
        int numChecks = 100;
        
        for (int i = 0; i < numChecks; i++) {
            systemOverloadDetector.isSystemOverloaded();
        }
        
        long endTime = System.nanoTime();
        double actualTotalTimeMillis = (endTime - startTime) / 1_000_000.0;
        
        // Then
        double reportedAvgTime = systemOverloadDetector.getAverageOverloadDetectionTimeMillis();
        double reportedTotalTime = reportedAvgTime * numChecks;
        long reportedTotalChecks = systemOverloadDetector.getTotalOverloadChecks();
        
        assertThat(reportedTotalChecks).isEqualTo(numChecks);
        assertThat(reportedAvgTime).isGreaterThan(0.0);
        
        // Reported total time should be reasonably close to actual time
        // Allow for some variance due to measurement overhead
        assertThat(reportedTotalTime).isLessThanOrEqualTo(actualTotalTimeMillis * 1.5);
        assertThat(reportedTotalTime).isGreaterThan(0.0);
    }

    @Test
    void testPerformanceStatisticsFormatting() {
        // Given - detector with some performance data
        for (int i = 0; i < 50; i++) {
            systemOverloadDetector.isSystemOverloaded();
        }
        
        // When - getting performance statistics
        String stats = systemOverloadDetector.getPerformanceStatistics();
        
        // Then - verify formatting and content
        assertThat(stats)
            .contains("Overload Detection Performance:")
            .contains("Total checks: 50")
            .contains("Total time:")
            .contains("Average time per check:")
            .contains("Checks per second (estimated):")
            .contains("ms");
        
        // Verify numeric values are reasonable
        assertThat(stats).matches(".*Average time per check: \\d+\\.\\d{3} ms.*");
        assertThat(stats).matches(".*Checks per second \\(estimated\\): \\d+\\.\\d.*");
    }

    @Test
    void testPerformanceMetricsThreadSafety() throws InterruptedException {
        // Given - multiple threads performing overload checks
        int numThreads = 5;
        int checksPerThread = 20;
        Thread[] threads = new Thread[numThreads];
        
        // When - running concurrent overload checks
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < checksPerThread; j++) {
                    systemOverloadDetector.isSystemOverloaded();
                }
            });
            threads[i].start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Then - verify metrics are consistent
        long totalChecks = systemOverloadDetector.getTotalOverloadChecks();
        double avgTime = systemOverloadDetector.getAverageOverloadDetectionTimeMillis();
        
        assertThat(totalChecks).isEqualTo(numThreads * checksPerThread);
        assertThat(avgTime).isGreaterThan(0.0);
        
        // Performance statistics should be consistent
        String stats = systemOverloadDetector.getPerformanceStatistics();
        assertThat(stats).contains("Total checks: " + (numThreads * checksPerThread));
    }

    @Test
    void testPerformanceMetricsWithExceptions() {
        // Given - system that throws exceptions during checks
        when(hikariPoolMXBean.getActiveConnections()).thenThrow(new RuntimeException("Database error"));
        
        // When - performing overload checks that encounter exceptions
        for (int i = 0; i < 3; i++) {
            boolean overloaded = systemOverloadDetector.isSystemOverloaded();
            assertThat(overloaded).isFalse(); // Should return false on exception
        }
        
        // Then - performance metrics should still be tracked
        long totalChecks = systemOverloadDetector.getTotalOverloadChecks();
        double avgTime = systemOverloadDetector.getAverageOverloadDetectionTimeMillis();
        
        assertThat(totalChecks).isEqualTo(3);
        assertThat(avgTime).isGreaterThan(0.0);
    }

    @Test
    void testPerformanceOverheadIsMinimal() {
        // Given - system with normal load
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(3);
        when(threadPoolExecutor.getActiveCount()).thenReturn(3);
        
        // When - performing many overload checks to measure overhead
        int numChecks = 1000;
        long startTime = System.nanoTime();
        
        for (int i = 0; i < numChecks; i++) {
            systemOverloadDetector.isSystemOverloaded();
        }
        
        long endTime = System.nanoTime();
        double totalTimeMillis = (endTime - startTime) / 1_000_000.0;
        double avgTimePerCheck = totalTimeMillis / numChecks;
        
        // Then - verify overhead is minimal (should be less than 1ms per check)
        assertThat(avgTimePerCheck).isLessThan(1.0);
        
        // Verify reported metrics match our measurements
        double reportedAvgTime = systemOverloadDetector.getAverageOverloadDetectionTimeMillis();
        assertThat(reportedAvgTime).isLessThan(1.0);
        assertThat(Math.abs(reportedAvgTime - avgTimePerCheck)).isLessThan(0.1); // Within 0.1ms
    }
}