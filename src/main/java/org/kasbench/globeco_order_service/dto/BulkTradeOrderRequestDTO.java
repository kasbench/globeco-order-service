package org.kasbench.globeco_order_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for bulk trade order requests to the trade service.
 * Represents the request structure for POST /api/v1/tradeOrders/bulk endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkTradeOrderRequestDTO {
    
    @NotNull(message = "Trade orders list is required")
    @Size(min = 1, max = 1000, message = "Trade orders list must contain between 1 and 1000 orders")
    @Valid
    private List<TradeOrderPostDTO> tradeOrders;
    
    /**
     * Factory method to create a bulk request from a list of trade orders.
     * 
     * @param tradeOrders List of trade orders to include in the bulk request
     * @return BulkTradeOrderRequestDTO instance
     */
    public static BulkTradeOrderRequestDTO of(List<TradeOrderPostDTO> tradeOrders) {
        return BulkTradeOrderRequestDTO.builder()
                .tradeOrders(tradeOrders)
                .build();
    }
    
    /**
     * Get the number of trade orders in this bulk request.
     * 
     * @return Number of trade orders, or 0 if the list is null
     */
    public int getOrderCount() {
        return tradeOrders != null ? tradeOrders.size() : 0;
    }
}