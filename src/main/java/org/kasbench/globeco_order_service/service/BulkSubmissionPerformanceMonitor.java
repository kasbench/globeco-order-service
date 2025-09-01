package org.kasbench.globeco_order_service.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Performance monitoring service for bulk order submission operations.
 * Tracks key performance metrics and provides performance analysis capabilities.
 */
@Service
public class BulkSubmissionPerformanceMonitor {
    private static final Logger logger = LoggerFactory.getLogger(BulkSubmissionPerformanceMonitor.class);
    
    private final MeterRegistry meterRegistry;
    private final boolean performanceMonitoringEnabled;
    
    // Performance tracking variables
    private final AtomicLong totalSubmissions = new AtomicLong(0);
    private final AtomicLong totalOrdersProcessed = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final AtomicReference<Double> averageOrdersPerSecond = new AtomicReference<>(0.0);
    private final AtomicReference<Double> averageProcessingTimePerOrder = new AtomicReference<>(0.0);
    private final AtomicReference<String> lastPerformanceReport = new AtomicReference<>("");
    
    // Performance baseline for comparison (individual submission approach)
    private static final double INDIVIDUAL_SUBMISSION_AVG_TIME_PER_ORDER_MS = 150.0; // Estimated baseline
    private static final double INDIVIDUAL_SUBMISSION_OVERHEAD_PER_REQUEST_MS = 50.0; // HTTP overhead per request
    
    public BulkSubmissionPerformanceMonitor(
            MeterRegistry meterRegistry,
            @Value("${bulk.submission.monitoring.performance-monitoring-enabled:true}") boolean performanceMonitoringEnabled) {
        this.meterRegistry = meterRegistry;
        this.performanceMonitoringEnabled = performanceMonitoringEnabled;
        
        if (performanceMonitoringEnabled) {
            initializeMetrics();
            logger.info("Bulk submission performance monitoring initialized");
        }
    }
    
    private void initializeMetrics() {
        // Register gauges for real-time performance monitoring
        Gauge.builder("bulk_submission.performance.total_submissions", totalSubmissions, AtomicLong::get)
                .description("Total number of bulk submissions processed")
                .register(meterRegistry);
                
        Gauge.builder("bulk_submission.performance.total_orders_processed", totalOrdersProcessed, AtomicLong::get)
                .description("Total number of orders processed through bulk submission")
                .register(meterRegistry);
                
        Gauge.builder("bulk_submission.performance.average_orders_per_second", averageOrdersPerSecond, AtomicReference::get)
                .description("Average orders processed per second")
                .register(meterRegistry);
                
        Gauge.builder("bulk_submission.performance.average_processing_time_per_order_ms", averageProcessingTimePerOrder, AtomicReference::get)
                .description("Average processing time per order in milliseconds")
                .register(meterRegistry);
    }
    
    /**
     * Record performance metrics for a bulk submission operation.
     * 
     * @param orderCount Number of orders processed
     * @param processingTimeMs Total processing time in milliseconds
     * @param successfulOrders Number of successfully processed orders
     */
    public void recordBulkSubmission(int orderCount, long processingTimeMs, int successfulOrders) {
        if (!performanceMonitoringEnabled) {
            return;
        }
        
        totalSubmissions.incrementAndGet();
        totalOrdersProcessed.addAndGet(successfulOrders);
        totalProcessingTime.addAndGet(processingTimeMs);
        
        // Calculate performance metrics
        updatePerformanceMetrics();
        
        // Log performance comparison with individual submission approach
        logPerformanceComparison(orderCount, processingTimeMs, successfulOrders);
        
        // Generate performance report
        generatePerformanceReport(orderCount, processingTimeMs, successfulOrders);
    }
    
    private void updatePerformanceMetrics() {
        long totalOrders = totalOrdersProcessed.get();
        long totalTime = totalProcessingTime.get();
        
        if (totalTime > 0) {
            // Calculate orders per second
            double ordersPerSecond = (double) totalOrders / (totalTime / 1000.0);
            averageOrdersPerSecond.set(ordersPerSecond);
            
            // Calculate average processing time per order
            double avgTimePerOrder = (double) totalTime / totalOrders;
            averageProcessingTimePerOrder.set(avgTimePerOrder);
        }
    }
    
    private void logPerformanceComparison(int orderCount, long processingTimeMs, int successfulOrders) {
        if (successfulOrders == 0) {
            return;
        }
        
        // Calculate bulk submission metrics
        double bulkTimePerOrder = (double) processingTimeMs / successfulOrders;
        double bulkOrdersPerSecond = successfulOrders / (processingTimeMs / 1000.0);
        
        // Calculate estimated individual submission metrics
        double estimatedIndividualTime = orderCount * INDIVIDUAL_SUBMISSION_AVG_TIME_PER_ORDER_MS;
        double estimatedIndividualTimePerOrder = INDIVIDUAL_SUBMISSION_AVG_TIME_PER_ORDER_MS;
        double estimatedIndividualOrdersPerSecond = 1000.0 / INDIVIDUAL_SUBMISSION_AVG_TIME_PER_ORDER_MS;
        
        // Calculate performance improvements
        double timeImprovement = ((estimatedIndividualTime - processingTimeMs) / estimatedIndividualTime) * 100;
        double throughputImprovement = ((bulkOrdersPerSecond - estimatedIndividualOrdersPerSecond) / estimatedIndividualOrdersPerSecond) * 100;
        double efficiencyImprovement = ((estimatedIndividualTimePerOrder - bulkTimePerOrder) / estimatedIndividualTimePerOrder) * 100;
        
        logger.info("BULK_SUBMISSION_PERFORMANCE_COMPARISON: " +
                "orders={}, bulk_time={}ms, estimated_individual_time={:.0f}ms, " +
                "time_saved={:.0f}ms ({:.1f}% improvement), " +
                "bulk_throughput={:.1f} orders/sec, estimated_individual_throughput={:.1f} orders/sec, " +
                "throughput_improvement={:.1f}%, efficiency_improvement={:.1f}%",
                orderCount, processingTimeMs, estimatedIndividualTime,
                estimatedIndividualTime - processingTimeMs, timeImprovement,
                bulkOrdersPerSecond, estimatedIndividualOrdersPerSecond,
                throughputImprovement, efficiencyImprovement);
    }
    
    private void generatePerformanceReport(int orderCount, long processingTimeMs, int successfulOrders) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        StringBuilder report = new StringBuilder();
        report.append(String.format("=== BULK SUBMISSION PERFORMANCE REPORT ===\n"));
        report.append(String.format("Timestamp: %s\n", timestamp));
        report.append(String.format("Current Batch: %d orders processed in %d ms (%d successful)\n", 
                orderCount, processingTimeMs, successfulOrders));
        
        if (successfulOrders > 0) {
            double currentTimePerOrder = (double) processingTimeMs / successfulOrders;
            double currentOrdersPerSecond = successfulOrders / (processingTimeMs / 1000.0);
            
            report.append(String.format("Current Performance: %.2f ms/order, %.1f orders/sec\n",
                    currentTimePerOrder, currentOrdersPerSecond));
        }
        
        // Overall statistics
        long totalSubs = totalSubmissions.get();
        long totalOrders = totalOrdersProcessed.get();
        
        if (totalSubs > 0 && totalOrders > 0) {
            double avgOrdersPerSubmission = (double) totalOrders / totalSubs;
            double overallAvgTimePerOrder = averageProcessingTimePerOrder.get();
            double overallOrdersPerSecond = averageOrdersPerSecond.get();
            
            report.append(String.format("Overall Statistics: %d submissions, %d orders processed\n",
                    totalSubs, totalOrders));
            report.append(String.format("Overall Performance: %.1f orders/submission, %.2f ms/order, %.1f orders/sec\n",
                    avgOrdersPerSubmission, overallAvgTimePerOrder, overallOrdersPerSecond));
            
            // Performance vs baseline
            double baselineTimeForCurrentBatch = orderCount * INDIVIDUAL_SUBMISSION_AVG_TIME_PER_ORDER_MS;
            double timeSavings = baselineTimeForCurrentBatch - processingTimeMs;
            double improvementPercent = (timeSavings / baselineTimeForCurrentBatch) * 100;
            
            report.append(String.format("Performance vs Individual Submission: %.0f ms saved (%.1f%% improvement)\n",
                    timeSavings, improvementPercent));
        }
        
        report.append("==========================================");
        
        String reportString = report.toString();
        lastPerformanceReport.set(reportString);
        
        logger.info("BULK_SUBMISSION_PERFORMANCE_REPORT:\n{}", reportString);
    }
    
    /**
     * Get the latest performance report.
     * 
     * @return String containing the latest performance report
     */
    public String getLatestPerformanceReport() {
        return lastPerformanceReport.get();
    }
    
    /**
     * Get current performance statistics.
     * 
     * @return PerformanceStats object containing current metrics
     */
    public PerformanceStats getCurrentStats() {
        return new PerformanceStats(
                totalSubmissions.get(),
                totalOrdersProcessed.get(),
                averageOrdersPerSecond.get(),
                averageProcessingTimePerOrder.get()
        );
    }
    
    /**
     * Reset performance statistics (useful for testing or periodic resets).
     */
    public void resetStats() {
        totalSubmissions.set(0);
        totalOrdersProcessed.set(0);
        totalProcessingTime.set(0);
        averageOrdersPerSecond.set(0.0);
        averageProcessingTimePerOrder.set(0.0);
        lastPerformanceReport.set("");
        
        logger.info("Bulk submission performance statistics reset");
    }
    
    /**
     * Performance statistics data class.
     */
    public static class PerformanceStats {
        private final long totalSubmissions;
        private final long totalOrdersProcessed;
        private final double averageOrdersPerSecond;
        private final double averageProcessingTimePerOrder;
        
        public PerformanceStats(long totalSubmissions, long totalOrdersProcessed, 
                              double averageOrdersPerSecond, double averageProcessingTimePerOrder) {
            this.totalSubmissions = totalSubmissions;
            this.totalOrdersProcessed = totalOrdersProcessed;
            this.averageOrdersPerSecond = averageOrdersPerSecond;
            this.averageProcessingTimePerOrder = averageProcessingTimePerOrder;
        }
        
        // Getters
        public long getTotalSubmissions() { return totalSubmissions; }
        public long getTotalOrdersProcessed() { return totalOrdersProcessed; }
        public double getAverageOrdersPerSecond() { return averageOrdersPerSecond; }
        public double getAverageProcessingTimePerOrder() { return averageProcessingTimePerOrder; }
    }
}