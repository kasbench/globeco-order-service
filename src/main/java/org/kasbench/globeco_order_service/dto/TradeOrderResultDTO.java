package org.kasbench.globeco_order_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing individual trade order results within a bulk trade order response.
 * Contains the result of processing a single trade order within a bulk operation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TradeOrderResultDTO {
    
    @NotNull
    private Integer requestIndex; // Index of the trade order in the original request array
    
    @NotNull
    private String status; // Individual order status: "SUCCESS" or "FAILURE"
    
    @NotNull
    private String message; // Status message for this specific trade order
    
    private TradeOrderResponseDTO tradeOrder; // Created trade order details (null on failure)
    
    /**
     * Check if this individual trade order was processed successfully.
     * 
     * @return true if status is "SUCCESS", false otherwise
     */
    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }
    
    /**
     * Check if this individual trade order failed.
     * 
     * @return true if status is "FAILURE", false otherwise
     */
    public boolean isFailure() {
        return "FAILURE".equals(status);
    }
    
    /**
     * Get the trade order ID if the order was successfully created.
     * 
     * @return Trade order ID, or null if the order failed or tradeOrder is null
     */
    public Integer getTradeOrderId() {
        return tradeOrder != null ? tradeOrder.getId() : null;
    }
    
    /**
     * Factory method for successful trade order result.
     * 
     * @param requestIndex Index in the original request
     * @param tradeOrder The created trade order
     * @return TradeOrderResultDTO for success
     */
    public static TradeOrderResultDTO success(Integer requestIndex, TradeOrderResponseDTO tradeOrder) {
        return TradeOrderResultDTO.builder()
                .requestIndex(requestIndex)
                .status("SUCCESS")
                .message("Trade order created successfully")
                .tradeOrder(tradeOrder)
                .build();
    }
    
    /**
     * Factory method for failed trade order result.
     * 
     * @param requestIndex Index in the original request
     * @param errorMessage Error message describing the failure
     * @return TradeOrderResultDTO for failure
     */
    public static TradeOrderResultDTO failure(Integer requestIndex, String errorMessage) {
        return TradeOrderResultDTO.builder()
                .requestIndex(requestIndex)
                .status("FAILURE")
                .message(errorMessage)
                .tradeOrder(null)
                .build();
    }
}