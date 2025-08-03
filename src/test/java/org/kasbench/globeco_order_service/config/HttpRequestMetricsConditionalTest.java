package org.kasbench.globeco_order_service.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.kasbench.globeco_order_service.service.HttpRequestMetricsInterceptor;
import org.kasbench.globeco_order_service.service.HttpRequestMetricsService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for conditional enablement of HTTP request metrics components.
 * Verifies that components are properly created or excluded based on configuration properties.
 */
class HttpRequestMetricsConditionalTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @SpringBootTest(classes = {
        HttpRequestMetricsConfiguration.class,
        HttpRequestMetricsService.class,
        HttpRequestMetricsInterceptor.class,
        MetricsProperties.class,
        TestConfig.class
    })
    @TestPropertySource(properties = {
        "metrics.custom.enabled=true",
        "metrics.custom.http.enabled=true",
        "metrics.custom.http.request.enabled=true"
    })
    static class EnabledConfigurationTest {

        @Autowired
        private ApplicationContext applicationContext;

        @Test
        @DisplayName("Should create HTTP request metrics components when enabled")
        void shouldCreateComponentsWhenEnabled() {
            // Then
            assertThat(applicationContext.containsBean("httpRequestMetricsService")).isTrue();
            assertThat(applicationContext.containsBean("httpRequestMetricsInterceptor")).isTrue();
            assertThat(applicationContext.containsBean("httpRequestMetricsConfiguration")).isTrue();
        }
    }

    @SpringBootTest(classes = {
        HttpRequestMetricsConfiguration.class,
        HttpRequestMetricsService.class,
        HttpRequestMetricsInterceptor.class,
        MetricsProperties.class,
        TestConfig.class
    })
    @TestPropertySource(properties = {
        "metrics.custom.enabled=false"
    })
    static class DisabledGlobalConfigurationTest {

        @Autowired
        private ApplicationContext applicationContext;

        @Test
        @DisplayName("Should not create HTTP request metrics components when globally disabled")
        void shouldNotCreateComponentsWhenGloballyDisabled() {
            // Then
            assertThat(applicationContext.containsBean("httpRequestMetricsService")).isFalse();
            assertThat(applicationContext.containsBean("httpRequestMetricsInterceptor")).isFalse();
            assertThat(applicationContext.containsBean("httpRequestMetricsConfiguration")).isFalse();
        }
    }

    @SpringBootTest(classes = {
        HttpRequestMetricsConfiguration.class,
        HttpRequestMetricsService.class,
        HttpRequestMetricsInterceptor.class,
        MetricsProperties.class,
        TestConfig.class
    })
    @TestPropertySource(properties = {
        "metrics.custom.enabled=true",
        "metrics.custom.http.enabled=false"
    })
    static class DisabledHttpConfigurationTest {

        @Autowired
        private ApplicationContext applicationContext;

        @Test
        @DisplayName("Should not create HTTP request metrics components when HTTP metrics disabled")
        void shouldNotCreateComponentsWhenHttpDisabled() {
            // Then
            assertThat(applicationContext.containsBean("httpRequestMetricsService")).isFalse();
            assertThat(applicationContext.containsBean("httpRequestMetricsInterceptor")).isFalse();
            assertThat(applicationContext.containsBean("httpRequestMetricsConfiguration")).isFalse();
        }
    }

    @SpringBootTest(classes = {
        HttpRequestMetricsConfiguration.class,
        HttpRequestMetricsService.class,
        HttpRequestMetricsInterceptor.class,
        MetricsProperties.class,
        TestConfig.class
    })
    @TestPropertySource(properties = {
        "metrics.custom.enabled=true",
        "metrics.custom.http.enabled=true",
        "metrics.custom.http.request.enabled=false"
    })
    static class DisabledHttpRequestConfigurationTest {

        @Autowired
        private ApplicationContext applicationContext;

        @Test
        @DisplayName("Should not create HTTP request metrics components when HTTP request metrics disabled")
        void shouldNotCreateComponentsWhenHttpRequestDisabled() {
            // Then
            assertThat(applicationContext.containsBean("httpRequestMetricsService")).isFalse();
            assertThat(applicationContext.containsBean("httpRequestMetricsInterceptor")).isFalse();
            assertThat(applicationContext.containsBean("httpRequestMetricsConfiguration")).isFalse();
        }
    }
}