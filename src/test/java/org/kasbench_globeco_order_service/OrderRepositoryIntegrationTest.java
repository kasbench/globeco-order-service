package org.kasbench_globeco_order_service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kasbench.globeco_order_service.GlobecoOrderServiceApplication;
import org.kasbench.globeco_order_service.entity.*;
import org.kasbench.globeco_order_service.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = GlobecoOrderServiceApplication.class)
@Testcontainers
@ExtendWith(SpringExtension.class)
public class OrderRepositoryIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired BlotterRepository blotterRepository;
    @Autowired StatusRepository statusRepository;
    @Autowired OrderTypeRepository orderTypeRepository;
    @Autowired OrderRepository orderRepository;

    private Blotter blotter;
    private Status status;
    private OrderType orderType;

    @BeforeEach
    void setUp() {
        blotter = blotterRepository.save(Blotter.builder().name("Default").version(1).build());
        status = statusRepository.save(Status.builder().abbreviation("NEW").description("New").version(1).build());
        orderType = orderTypeRepository.save(OrderType.builder().abbreviation("BUY").description("Buy").version(1).build());
    }

    @Test
    void testOrderCrud() {
        Order order = Order.builder()
                .blotter(blotter)
                .status(status)
                .portfolioId("PORT12345678901234567890")
                .orderType(orderType)
                .securityId("SEC12345678901234567890")
                .quantity(new BigDecimal("100.00000000"))
                .limitPrice(new BigDecimal("50.25000000"))
                .orderTimestamp(OffsetDateTime.now())
                .version(1)
                .build();
        Order saved = orderRepository.save(order);
        assertThat(saved.getId()).isNotNull();

        Optional<Order> found = orderRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getPortfolioId()).isEqualTo("PORT12345678901234567890");

        saved.setQuantity(new BigDecimal("200.00000000"));
        orderRepository.save(saved);
        Order updated = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getQuantity()).isEqualTo(new BigDecimal("200.00000000"));

        orderRepository.deleteById(saved.getId());
        assertThat(orderRepository.findById(saved.getId())).isEmpty();
    }
} 