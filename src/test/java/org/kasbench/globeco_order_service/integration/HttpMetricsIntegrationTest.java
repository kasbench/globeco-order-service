package org.kasbench.globeco_order_service.integration;

import org.junit.jupiter.api.Test;
import org.kasbench.globeco_order_service.service.HttpMetricsService;
import org.kasbench.globeco_order_service.service.PortfolioServiceClient;
import org.kasbench.globeco_order_service.service.SecurityServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "metrics.custom.enabled=true",
    "metrics.http.enabled=true"
})
class HttpMetricsIntegrationTest {

    @Autowired(required = false)
    private HttpMetricsService httpMetricsService;

    @Autowired
    private PortfolioServiceClient portfolioServiceClient;

    @Autowired
    private SecurityServiceClient securityServiceClient;

    @Test
    void testHttpMetricsServiceIsAvailable() {
        // Given metrics are enabled
        // Then HttpMetricsService should be available
        assertThat(httpMetricsService).isNotNull();
    }

    @Test
    void testServiceClientsAreConfiguredWithHttpMetrics() {
        // Given metrics are enabled
        // Then service clients should be available
        assertThat(portfolioServiceClient).isNotNull();
        assertThat(securityServiceClient).isNotNull();
        
        // And HttpMetricsService should have registered services
        if (httpMetricsService != null) {
            // The services should be registered during @PostConstruct
            assertThat(httpMetricsService.getRegisteredServices()).isNotEmpty();
        }
    }

    @Test
    void testHttpMetricsProtocolDetection() {
        if (httpMetricsService != null) {
            // Test that protocol detection works for the registered services
            for (String serviceName : httpMetricsService.getRegisteredServices()) {
                String protocol = httpMetricsService.getServiceProtocol(serviceName);
                assertThat(protocol).isIn("http", "https", "unknown");
            }
        }
    }
}