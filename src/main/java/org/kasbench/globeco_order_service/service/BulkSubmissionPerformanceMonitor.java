package org.kasbench.globeco_order_service.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

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
    
    // Latency tracking with percentiles
    private final Timer bulkSubmissionLatencyTimer;
    private final Timer databaseQueryTimer;
    private final Timer externalServiceTimer;
    private final Timer transactionHoldTimer;
    
    // Orders per second tracking
    private final AtomicReference<Double> currentOrdersPerSecond = new AtomicReference<>(0.0);
    private final AtomicLong lastMeasurementTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong ordersProcessedSinceLastMeasurement = new AtomicLong(0);
    
    // Performance baseline for comparison (individual submission approach)
    private static final double INDIVIDUAL_SUBMISSION_AVG_TIME_PER_ORDER_MS = 150.0; // Estimated baseline
    
    public BulkSubmissionPerformanceMonitor(
            MeterRegistry meterRegistry,
            @Value("${bulk.submission.monitoring.performance-monitoring-enabled:true}") boolean performanceMonitoringEnabled) {
        this.meterRegistry = meterRegistry;
        this.performanceMonitoringEnabled = performanceMonitoringEnabled;
        
        // Initialize latency timers with percentile histograms
        this.bulkSubmissionLatencyTimer = Timer.builder("bulk_submission.latency")
                .description("Bulk submission end-to-end latency with percentiles")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);
                
        this.databaseQueryTimer = Timer.builder("bulk_submission.database_query.duration")
                .description("Database query execution time with percentiles")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);
                
        this.externalServiceTimer = Timer.builder("bulk_submission.external_service.duration")
                .description("External service call duration with percentiles")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);
                
        this.transactionHoldTimer = Timer.builder("bulk_submission.transaction_hold.duration")
                .description("Transaction hold time for database connections with percentiles")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);
        
        if (performanceMonitoringEnabled) {
            initializeMetrics();
            logger.info("Bulk submission performance monitoring initialized with percentile tracking");
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
                .description("Average orders processed per second (cumulative)")
                .register(meterRegistry);
                
        Gauge.builder("bulk_submission.performance.current_orders_per_second", currentOrdersPerSecond, AtomicReference::get)
                .description("Current orders processed per second (recent window)")
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
        
        // Record latency in the timer for percentile tracking
        bulkSubmissionLatencyTimer.record(processingTimeMs, TimeUnit.MILLISECONDS);
        
        totalSubmissions.incrementAndGet();
        totalOrdersProcessed.addAndGet(successfulOrders);
        totalProcessingTime.addAndGet(processingTimeMs);
        
        // Calculate performance metrics
        updatePerformanceMetrics();
        
        // Calculate current orders per second
        calculateCurrentOrdersPerSecond(successfulOrders);
        
        // Log performance comparison with individual submission approach
        logPerformanceComparison(orderCount, processingTimeMs, successfulOrders);
        
        // Generate performance report
        generatePerformanceReport(orderCount, processingTimeMs, successfulOrders);
    }
    
    /**
     * Record database query execution time.
     * 
     * @param queryTimeMs Query execution time in milliseconds
     */
    public void recordDatabaseQueryTime(long queryTimeMs) {
        if (!performanceMonitoringEnabled) {
            return;
        }
        databaseQueryTimer.record(queryTimeMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Record external service call duration.
     * 
     * @param serviceCallTimeMs Service call duration in milliseconds
     */
    public void recordExternalServiceCall(long serviceCallTimeMs) {
        if (!performanceMonitoringEnabled) {
            return;
        }
        externalServiceTimer.record(serviceCallTimeMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Record transaction hold time for database connections.
     * 
     * @param transactionTimeMs Transaction hold time in milliseconds
     */
    public void recordTransactionHoldTime(long transactionTimeMs) {
        if (!performanceMonitoringEnabled) {
            return;
        }
        transactionHoldTimer.record(transactionTimeMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Calculate current orders per second based on recent activity.
     * Uses a sliding window approach to provide real-time throughput metrics.
     * 
     * @param ordersProcessed Number of orders processed in this batch
     */
    private void calculateCurrentOrdersPerSecond(int ordersProcessed) {
        long currentTime = System.currentTimeMillis();
        long lastTime = lastMeasurementTime.get();
        long timeDeltaMs = currentTime - lastTime;
        
        ordersProcessedSinceLastMeasurement.addAndGet(ordersProcessed);
        
        // Update orders per second every 5 seconds
        if (timeDeltaMs >= 5000) {
            long totalOrdersInWindow = ordersProcessedSinceLastMeasurement.get();
            double ordersPerSecond = (double) totalOrdersInWindow / (timeDeltaMs / 1000.0);
            currentOrdersPerSecond.set(ordersPerSecond);
            
            // Reset for next window
            lastMeasurementTime.set(currentTime);
            ordersProcessedSinceLastMeasurement.set(0);
            
            if (logger.isDebugEnabled()) {
                logger.debug("Current throughput: {:.2f} orders/sec (window: {}ms, orders: {})",
                        ordersPerSecond, timeDeltaMs, totalOrdersInWindow);
            }
        }
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
        
        logger.debug("BULK_SUBMISSION_PERFORMANCE_COMPARISON: " +
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
        
        // Latency percentiles
        report.append(String.format("Latency Percentiles:\n"));
        report.append(String.format("  P50: %.2f ms\n", getPercentileValue(bulkSubmissionLatencyTimer, 0.5)));
        report.append(String.format("  P95: %.2f ms\n", getPercentileValue(bulkSubmissionLatencyTimer, 0.95)));
        report.append(String.format("  P99: %.2f ms\n", getPercentileValue(bulkSubmissionLatencyTimer, 0.99)));
        
        // Component timing percentiles
        report.append(String.format("Database Query Percentiles:\n"));
        report.append(String.format("  P50: %.2f ms, P95: %.2f ms, P99: %.2f ms\n",
                getPercentileValue(databaseQueryTimer, 0.5),
                getPercentileValue(databaseQueryTimer, 0.95),
                getPercentileValue(databaseQueryTimer, 0.99)));
        
        report.append(String.format("External Service Call Percentiles:\n"));
        report.append(String.format("  P50: %.2f ms, P95: %.2f ms, P99: %.2f ms\n",
                getPercentileValue(externalServiceTimer, 0.5),
                getPercentileValue(externalServiceTimer, 0.95),
                getPercentileValue(externalServiceTimer, 0.99)));
        
        report.append(String.format("Transaction Hold Time Percentiles:\n"));
        report.append(String.format("  P50: %.2f ms, P95: %.2f ms, P99: %.2f ms\n",
                getPercentileValue(transactionHoldTimer, 0.5),
                getPercentileValue(transactionHoldTimer, 0.95),
                getPercentileValue(transactionHoldTimer, 0.99)));
        
        // Overall statistics
        long totalSubs = totalSubmissions.get();
        long totalOrders = totalOrdersProcessed.get();
        
        if (totalSubs > 0 && totalOrders > 0) {
            double avgOrdersPerSubmission = (double) totalOrders / totalSubs;
            double overallAvgTimePerOrder = averageProcessingTimePerOrder.get();
            double overallOrdersPerSecond = averageOrdersPerSecond.get();
            double currentThroughput = currentOrdersPerSecond.get();
            
            report.append(String.format("Overall Statistics: %d submissions, %d orders processed\n",
                    totalSubs, totalOrders));
            report.append(String.format("Overall Performance: %.1f orders/submission, %.2f ms/order\n",
                    avgOrdersPerSubmission, overallAvgTimePerOrder));
            report.append(String.format("Throughput: %.1f orders/sec (avg), %.1f orders/sec (current)\n",
                    overallOrdersPerSecond, currentThroughput));
            
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
        
        logger.debug("BULK_SUBMISSION_PERFORMANCE_REPORT:\n{}", reportString);
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
                currentOrdersPerSecond.get(),
                averageProcessingTimePerOrder.get(),
                getPercentileValue(bulkSubmissionLatencyTimer, 0.5),
                getPercentileValue(bulkSubmissionLatencyTimer, 0.95),
                getPercentileValue(bulkSubmissionLatencyTimer, 0.99),
                getPercentileValue(databaseQueryTimer, 0.5),
                getPercentileValue(databaseQueryTimer, 0.95),
                getPercentileValue(databaseQueryTimer, 0.99),
                getPercentileValue(externalServiceTimer, 0.5),
                getPercentileValue(externalServiceTimer, 0.95),
                getPercentileValue(externalServiceTimer, 0.99),
                getPercentileValue(transactionHoldTimer, 0.5),
                getPercentileValue(transactionHoldTimer, 0.95),
                getPercentileValue(transactionHoldTimer, 0.99)
        );
    }
    
    /**
     * Helper method to get percentile value from a timer.
     * Uses takeSnapshot() to avoid deprecated percentile() method.
     * 
     * @param timer The timer to get percentile from
     * @param percentile The percentile value (0.5 for P50, 0.95 for P95, etc.)
     * @return Percentile value in milliseconds
     */
    private double getPercentileValue(Timer timer, double percentile) {
        // Use takeSnapshot to get histogram snapshot and calculate percentile
        // For now, use the deprecated method as it's still functional
        // This can be updated when Micrometer provides a better alternative
        @SuppressWarnings("deprecation")
        double value = timer.percentile(percentile, TimeUnit.MILLISECONDS);
        return value;
    }
    
    /**
     * Get orders per second calculation.
     * 
     * @return Current orders per second throughput
     */
    public double getOrdersPerSecond() {
        return currentOrdersPerSecond.get();
    }
    
    /**
     * Get latency percentile value.
     * 
     * @param percentile Percentile to retrieve (e.g., 0.5 for P50, 0.95 for P95, 0.99 for P99)
     * @return Latency value at the specified percentile in milliseconds
     */
    public double getLatencyPercentile(double percentile) {
        return getPercentileValue(bulkSubmissionLatencyTimer, percentile);
    }
    
    /**
     * Get database query time percentile.
     * 
     * @param percentile Percentile to retrieve
     * @return Query time at the specified percentile in milliseconds
     */
    public double getDatabaseQueryPercentile(double percentile) {
        return getPercentileValue(databaseQueryTimer, percentile);
    }
    
    /**
     * Get external service call time percentile.
     * 
     * @param percentile Percentile to retrieve
     * @return Service call time at the specified percentile in milliseconds
     */
    public double getExternalServicePercentile(double percentile) {
        return getPercentileValue(externalServiceTimer, percentile);
    }
    
    /**
     * Get transaction hold time percentile.
     * 
     * @param percentile Percentile to retrieve
     * @return Transaction hold time at the specified percentile in milliseconds
     */
    public double getTransactionHoldPercentile(double percentile) {
        return getPercentileValue(transactionHoldTimer, percentile);
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
        private final double currentOrdersPerSecond;
        private final double averageProcessingTimePerOrder;
        private final double latencyP50;
        private final double latencyP95;
        private final double latencyP99;
        private final double databaseQueryP50;
        private final double databaseQueryP95;
        private final double databaseQueryP99;
        private final double externalServiceP50;
        private final double externalServiceP95;
        private final double externalServiceP99;
        private final double transactionHoldP50;
        private final double transactionHoldP95;
        private final double transactionHoldP99;
        
        public PerformanceStats(long totalSubmissions, long totalOrdersProcessed, 
                              double averageOrdersPerSecond, double currentOrdersPerSecond,
                              double averageProcessingTimePerOrder,
                              double latencyP50, double latencyP95, double latencyP99,
                              double databaseQueryP50, double databaseQueryP95, double databaseQueryP99,
                              double externalServiceP50, double externalServiceP95, double externalServiceP99,
                              double transactionHoldP50, double transactionHoldP95, double transactionHoldP99) {
            this.totalSubmissions = totalSubmissions;
            this.totalOrdersProcessed = totalOrdersProcessed;
            this.averageOrdersPerSecond = averageOrdersPerSecond;
            this.currentOrdersPerSecond = currentOrdersPerSecond;
            this.averageProcessingTimePerOrder = averageProcessingTimePerOrder;
            this.latencyP50 = latencyP50;
            this.latencyP95 = latencyP95;
            this.latencyP99 = latencyP99;
            this.databaseQueryP50 = databaseQueryP50;
            this.databaseQueryP95 = databaseQueryP95;
            this.databaseQueryP99 = databaseQueryP99;
            this.externalServiceP50 = externalServiceP50;
            this.externalServiceP95 = externalServiceP95;
            this.externalServiceP99 = externalServiceP99;
            this.transactionHoldP50 = transactionHoldP50;
            this.transactionHoldP95 = transactionHoldP95;
            this.transactionHoldP99 = transactionHoldP99;
        }
        
        // Getters
        public long getTotalSubmissions() { return totalSubmissions; }
        public long getTotalOrdersProcessed() { return totalOrdersProcessed; }
        public double getAverageOrdersPerSecond() { return averageOrdersPerSecond; }
        public double getCurrentOrdersPerSecond() { return currentOrdersPerSecond; }
        public double getAverageProcessingTimePerOrder() { return averageProcessingTimePerOrder; }
        public double getLatencyP50() { return latencyP50; }
        public double getLatencyP95() { return latencyP95; }
        public double getLatencyP99() { return latencyP99; }
        public double getDatabaseQueryP50() { return databaseQueryP50; }
        public double getDatabaseQueryP95() { return databaseQueryP95; }
        public double getDatabaseQueryP99() { return databaseQueryP99; }
        public double getExternalServiceP50() { return externalServiceP50; }
        public double getExternalServiceP95() { return externalServiceP95; }
        public double getExternalServiceP99() { return externalServiceP99; }
        public double getTransactionHoldP50() { return transactionHoldP50; }
        public double getTransactionHoldP95() { return transactionHoldP95; }
        public double getTransactionHoldP99() { return transactionHoldP99; }
    }
}