package org.kasbench.globeco_order_service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kasbench.globeco_order_service.entity.*;
import org.kasbench.globeco_order_service.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class OrderControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private BlotterRepository blotterRepository;

    @Autowired
    private StatusRepository statusRepository;

    @Autowired
    private OrderTypeRepository orderTypeRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        setupTestData();
    }

    @Test
    void getAllOrders_DefaultPagination() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(lessThanOrEqualTo(50)))) // Default limit
                .andExpect(jsonPath("$.pagination.pageSize", is(50)))
                .andExpect(jsonPath("$.pagination.totalElements", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.pagination.totalPages", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.pagination.currentPage", is(0)))
                .andExpect(jsonPath("$.pagination.hasNext", isA(Boolean.class)))
                .andExpect(jsonPath("$.pagination.hasPrevious", is(false)));
    }

    @Test
    void getAllOrders_CustomPagination() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .param("limit", "10")
                        .param("offset", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(lessThanOrEqualTo(10))))
                .andExpect(jsonPath("$.pagination.pageSize", is(10)));
    }

    @Test
    void getAllOrders_InvalidPaginationParameters() throws Exception {
        // Test limit too high
        mockMvc.perform(get("/api/v1/orders")
                        .param("limit", "1001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Limit must be between 1 and 1000")));

        // Test negative offset
        mockMvc.perform(get("/api/v1/orders")
                        .param("offset", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Offset must be >= 0")));
    }

    @Test
    void getAllOrders_SortingSingleField() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .param("sort", "quantity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(0))));
    }

    @Test
    void getAllOrders_SortingMultipleFields() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .param("sort", "status.abbreviation,-orderTimestamp,quantity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(0))));
    }

    @Test
    void getAllOrders_SortingInvalidField() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .param("sort", "invalidField"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid sort field")))
                .andExpect(jsonPath("$.validSortFields", hasItem("quantity")))
                .andExpect(jsonPath("$.validSortFields", hasItem("orderTimestamp")));
    }

    @Test
    void getAllOrders_FilteringByStatus() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .param("status.abbreviation", "NEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(0))));
    }

    @Test
    void getAllOrders_FilteringMultipleValues() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .param("status.abbreviation", "NEW,SENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(0))));
    }

    @Test
    void getAllOrders_FilteringMultipleFields() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .param("status.abbreviation", "NEW")
                        .param("orderType.abbreviation", "BUY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(0))));
    }

    @Test
    void getAllOrders_FilteringInvalidField() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .param("invalidField", "value"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid filter field")))
                .andExpect(jsonPath("$.validFilterFields", hasItem("status.abbreviation")))
                .andExpect(jsonPath("$.validFilterFields", hasItem("orderType.abbreviation")));
    }

    @Test
    void getAllOrders_CombinedSortingAndFiltering() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .param("limit", "20")
                        .param("offset", "0")
                        .param("sort", "orderTimestamp,-quantity")
                        .param("status.abbreviation", "NEW,SENT")
                        .param("orderType.abbreviation", "BUY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(lessThanOrEqualTo(20))))
                .andExpect(jsonPath("$.pagination.pageSize", is(20)));
    }

    @Test
    void getAllOrders_ExternalServiceFields() throws Exception {
        // Test filtering by external service fields
        mockMvc.perform(get("/api/v1/orders")
                        .param("security.ticker", "AAPL,MSFT")
                        .param("portfolio.name", "TestPortfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(0))));

        // Test sorting by external service fields
        mockMvc.perform(get("/api/v1/orders")
                        .param("sort", "security.ticker,-portfolio.name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(0))));
    }

    @Test
    void getAllOrders_ResponseStructure() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.pagination").exists())
                .andExpect(jsonPath("$.pagination.pageSize").exists())
                .andExpect(jsonPath("$.pagination.totalElements").exists())
                .andExpect(jsonPath("$.pagination.totalPages").exists())
                .andExpect(jsonPath("$.pagination.currentPage").exists())
                .andExpect(jsonPath("$.pagination.hasNext").exists())
                .andExpect(jsonPath("$.pagination.hasPrevious").exists());
    }

    @Test
    void getAllOrders_OrderDTOStructure() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].quantity").exists())
                .andExpect(jsonPath("$.content[0].orderTimestamp").exists())
                .andExpect(jsonPath("$.content[0].version").exists())
                .andExpect(jsonPath("$.content[0].status").exists())
                .andExpect(jsonPath("$.content[0].status.id").exists())
                .andExpect(jsonPath("$.content[0].status.abbreviation").exists())
                .andExpect(jsonPath("$.content[0].orderType").exists())
                .andExpect(jsonPath("$.content[0].orderType.id").exists())
                .andExpect(jsonPath("$.content[0].orderType.abbreviation").exists())
                // Portfolio and Security should be objects with external service data
                .andExpect(jsonPath("$.content[0].portfolio").exists())
                .andExpect(jsonPath("$.content[0].security").exists());
    }

    @Test
    void getAllOrders_EmptyResult() throws Exception {
        // Clear all orders
        orderRepository.deleteAll();

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.pagination.totalElements", is(0)))
                .andExpect(jsonPath("$.pagination.totalPages", is(0)))
                .andExpect(jsonPath("$.pagination.hasNext", is(false)))
                .andExpect(jsonPath("$.pagination.hasPrevious", is(false)));
    }

    @Test
    void getAllOrders_LargeOffset() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .param("offset", "1000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.pagination.currentPage", greaterThanOrEqualTo(0)));
    }

    private void setupTestData() {
        // Create test blotters
        Blotter blotter1 = Blotter.builder()
                .name("Equity Blotter")
                .version(1)
                .build();
        blotterRepository.save(blotter1);

        Blotter blotter2 = Blotter.builder()
                .name("Fixed Income Blotter")
                .version(1)
                .build();
        blotterRepository.save(blotter2);

        // Create test statuses
        Status statusNew = Status.builder()
                .abbreviation("NEW")
                .description("New Order")
                .version(1)
                .build();
        statusRepository.save(statusNew);

        Status statusSent = Status.builder()
                .abbreviation("SENT")
                .description("Sent Order")
                .version(1)
                .build();
        statusRepository.save(statusSent);

        // Create test order types
        OrderType buyType = OrderType.builder()
                .abbreviation("BUY")
                .description("Buy Order")
                .version(1)
                .build();
        orderTypeRepository.save(buyType);

        OrderType sellType = OrderType.builder()
                .abbreviation("SELL")
                .description("Sell Order")
                .version(1)
                .build();
        orderTypeRepository.save(sellType);

        // Create test orders
        for (int i = 1; i <= 25; i++) {
            Order order = Order.builder()
                    .blotter(i % 2 == 0 ? blotter1 : blotter2)
                    .status(i % 2 == 0 ? statusNew : statusSent)
                    .portfolioId("PORTFOLIO_" + (i % 5 + 1))
                    .orderType(i % 2 == 0 ? buyType : sellType)
                    .securityId("SECURITY_" + (i % 10 + 1))
                    .quantity(new BigDecimal(100 + i))
                    .limitPrice(new BigDecimal(50 + i * 0.5))
                    .orderTimestamp(OffsetDateTime.now().minusHours(i))
                    .version(1)
                    .build();
            orderRepository.save(order);
        }
    }
} 