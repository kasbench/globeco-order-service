package org.kasbench.globeco_order_service.controller;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.kasbench.globeco_order_service.config.MetricsProperties;
import org.kasbench.globeco_order_service.service.HttpRequestMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Debug controller for inspecting HTTP request metrics.
 * This endpoint is for debugging purposes only and should not be scraped by monitoring systems.
 * 
 * Provides detailed information about:
 * - HTTP request counters
 * - HTTP request timers
 * - In-flight requests gauge
 * - Configuration status
 * - Cache statistics
 */
@Slf4j
@RestController
@RequestMapping("/debug/metrics")
@ConditionalOnProperty(name = "metrics.debug.enabled", havingValue = "true", matchIfMissing = false)
public class MetricsDebugController {

    private final MeterRegistry meterRegistry;
    private final MetricsProperties metricsProperties;
    private final HttpRequestMetricsService httpRequestMetricsService;

    @Autowired
    public MetricsDebugController(
            MeterRegistry meterRegistry,
            MetricsProperties metricsProperties,
            HttpRequestMetricsService httpRequestMetricsService) {
        
        this.meterRegistry = meterRegistry;
        this.metricsProperties = metricsProperties;
        this.httpRequestMetricsService = httpRequestMetricsService;
        
        log.info("MetricsDebugController initialized - debug endpoint available at /debug/metrics");
    }

    /**
     * Returns a comprehensive overview of all HTTP request metrics.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getMetricsOverview() {
        Map<String, Object> response = new HashMap<>();
        
        response.put("timestamp", Instant.now().toString());
        response.put("configuration", getConfigurationInfo());
        response.put("httpRequestCounters", getHttpRequestCounters());
        response.put("httpRequestTimers", getHttpRequestTimers());
        response.put("inFlightRequests", getInFlightRequests());
        response.put("cacheStatistics", getCacheStatistics());
        response.put("serviceStatus", getServiceStatus());
        
        return response;
    }

    /**
     * Returns only HTTP request counter metrics.
     */
    @GetMapping(value = "/counters", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getHttpRequestCounters() {
        Map<String, Object> counters = new HashMap<>();
        
        List<Counter> httpCounters = meterRegistry.getMeters().stream()
                .filter(meter -> meter instanceof Counter)
                .map(meter -> (Counter) meter)
                .filter(counter -> counter.getId().getName().equals("http_requests_total"))
                .collect(Collectors.toList());
        
        for (Counter counter : httpCounters) {
            String key = buildCounterKey(counter);
            Map<String, Object> counterInfo = new HashMap<>();
            counterInfo.put("count", counter.count());
            counterInfo.put("tags", counter.getId().getTags().stream()
                    .collect(Collectors.toMap(
                            tag -> tag.getKey(),
                            tag -> tag.getValue()
                    )));
            counterInfo.put("description", counter.getId().getDescription());
            counters.put(key, counterInfo);
        }
        
        return counters;
    }

    /**
     * Returns only HTTP request timer metrics.
     */
    @GetMapping(value = "/timers", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getHttpRequestTimers() {
        Map<String, Object> timers = new HashMap<>();
        
        List<Timer> httpTimers = meterRegistry.getMeters().stream()
                .filter(meter -> meter instanceof Timer)
                .map(meter -> (Timer) meter)
                .filter(timer -> timer.getId().getName().equals("http_request_duration"))
                .collect(Collectors.toList());
        
        for (Timer timer : httpTimers) {
            String key = buildTimerKey(timer);
            Map<String, Object> timerInfo = new HashMap<>();
            timerInfo.put("count", timer.count());
            timerInfo.put("totalTimeSeconds", timer.totalTime(java.util.concurrent.TimeUnit.SECONDS));
            timerInfo.put("meanSeconds", timer.mean(java.util.concurrent.TimeUnit.SECONDS));
            timerInfo.put("maxSeconds", timer.max(java.util.concurrent.TimeUnit.SECONDS));
            timerInfo.put("tags", timer.getId().getTags().stream()
                    .collect(Collectors.toMap(
                            tag -> tag.getKey(),
                            tag -> tag.getValue()
                    )));
            timerInfo.put("description", timer.getId().getDescription());
            timers.put(key, timerInfo);
        }
        
        return timers;
    }

    /**
     * Returns in-flight requests gauge information.
     */
    @GetMapping(value = "/in-flight", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getInFlightRequests() {
        Map<String, Object> inFlight = new HashMap<>();
        
        Gauge inFlightGauge = meterRegistry.find("http_requests_in_flight").gauge();
        if (inFlightGauge != null) {
            inFlight.put("current", inFlightGauge.value());
            inFlight.put("description", inFlightGauge.getId().getDescription());
        } else {
            inFlight.put("current", "not_found");
            inFlight.put("error", "http_requests_in_flight gauge not registered");
        }
        
        return inFlight;
    }

    /**
     * Returns configuration information.
     */
    @GetMapping(value = "/config", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getConfigurationInfo() {
        Map<String, Object> config = new HashMap<>();
        
        config.put("globalEnabled", metricsProperties.isEnabled());
        config.put("httpEnabled", metricsProperties.getHttp().isEnabled());
        config.put("httpRequestEnabled", metricsProperties.getHttp().getRequest().isEnabled());
        config.put("routeSanitizationEnabled", metricsProperties.getHttp().getRequest().isRouteSanitizationEnabled());
        config.put("maxPathSegments", metricsProperties.getHttp().getRequest().getMaxPathSegments());
        config.put("metricCachingEnabled", metricsProperties.getHttp().getRequest().isMetricCachingEnabled());
        config.put("maxCacheSize", metricsProperties.getHttp().getRequest().getMaxCacheSize());
        config.put("detailedLoggingEnabled", metricsProperties.getHttp().getRequest().isDetailedLoggingEnabled());
        config.put("collectionInterval", metricsProperties.getEffectiveHttpCollectionInterval().toString());
        
        return config;
    }

    /**
     * Returns cache statistics from the HTTP request metrics service.
     */
    @GetMapping(value = "/cache", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> cache = new HashMap<>();
        
        if (httpRequestMetricsService != null) {
            try {
                String cacheStats = httpRequestMetricsService.getCacheStatistics();
                cache.put("statistics", cacheStats);
                cache.put("serviceAvailable", true);
            } catch (Exception e) {
                cache.put("error", e.getMessage());
                cache.put("serviceAvailable", false);
            }
        } else {
            cache.put("error", "HttpRequestMetricsService not available");
            cache.put("serviceAvailable", false);
        }
        
        return cache;
    }

    /**
     * Returns service status information.
     */
    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getServiceStatus() {
        Map<String, Object> status = new HashMap<>();
        
        if (httpRequestMetricsService != null) {
            try {
                status.put("initialized", httpRequestMetricsService.isInitialized());
                status.put("inFlightRequests", httpRequestMetricsService.getInFlightRequests());
                status.put("serviceStatus", httpRequestMetricsService.getServiceStatus());
                status.put("serviceAvailable", true);
            } catch (Exception e) {
                status.put("error", e.getMessage());
                status.put("serviceAvailable", false);
            }
        } else {
            status.put("error", "HttpRequestMetricsService not available");
            status.put("serviceAvailable", false);
        }
        
        return status;
    }

    /**
     * Returns all registered meters for debugging.
     */
    @GetMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getAllMeters() {
        Map<String, Object> allMeters = new HashMap<>();
        
        List<Meter> meters = meterRegistry.getMeters().stream()
                .filter(meter -> meter.getId().getName().startsWith("http_"))
                .collect(Collectors.toList());
        
        for (Meter meter : meters) {
            String key = meter.getId().getName() + "_" + meter.getId().getTags().stream()
                    .map(tag -> tag.getKey() + "=" + tag.getValue())
                    .collect(Collectors.joining(","));
            
            Map<String, Object> meterInfo = new HashMap<>();
            meterInfo.put("name", meter.getId().getName());
            meterInfo.put("type", meter.getId().getType().name());
            meterInfo.put("description", meter.getId().getDescription());
            meterInfo.put("tags", meter.getId().getTags().stream()
                    .collect(Collectors.toMap(
                            tag -> tag.getKey(),
                            tag -> tag.getValue()
                    )));
            
            // Add type-specific information
            if (meter instanceof Counter) {
                meterInfo.put("count", ((Counter) meter).count());
            } else if (meter instanceof Timer) {
                Timer timer = (Timer) meter;
                meterInfo.put("count", timer.count());
                meterInfo.put("totalTimeSeconds", timer.totalTime(java.util.concurrent.TimeUnit.SECONDS));
                meterInfo.put("meanSeconds", timer.mean(java.util.concurrent.TimeUnit.SECONDS));
            } else if (meter instanceof Gauge) {
                meterInfo.put("value", ((Gauge) meter).value());
            }
            
            allMeters.put(key, meterInfo);
        }
        
        return allMeters;
    }

    private String buildCounterKey(Counter counter) {
        return counter.getId().getTags().stream()
                .map(tag -> tag.getKey() + "=" + tag.getValue())
                .collect(Collectors.joining(","));
    }

    private String buildTimerKey(Timer timer) {
        return timer.getId().getTags().stream()
                .map(tag -> tag.getKey() + "=" + tag.getValue())
                .collect(Collectors.joining(","));
    }
}