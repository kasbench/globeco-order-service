package org.kasbench.globeco_order_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderSubmitResultDTO {
    
    @NotNull
    private Integer orderId; // The ID of the order that was processed
    
    @NotNull
    private String status; // "SUCCESS" or "FAILURE"
    
    @NotNull
    private String message; // Descriptive message (error details for failures, success confirmation for success)
    
    private Integer tradeOrderId; // The trade order ID (only present when status is "SUCCESS")
    
    @NotNull
    private Integer requestIndex; // The zero-based index of this order in the original request array
    
    // Factory method for successful order submission
    public static OrderSubmitResultDTO success(Integer orderId, Integer tradeOrderId, Integer requestIndex) {
        return OrderSubmitResultDTO.builder()
                .orderId(orderId)
                .status("SUCCESS")
                .message("Order submitted successfully")
                .tradeOrderId(tradeOrderId)
                .requestIndex(requestIndex)
                .build();
    }
    
    // Factory method for failed order submission
    public static OrderSubmitResultDTO failure(Integer orderId, String errorMessage, Integer requestIndex) {
        return OrderSubmitResultDTO.builder()
                .orderId(orderId)
                .status("FAILURE")
                .message(errorMessage)
                .requestIndex(requestIndex)
                .build();
    }
    
    // Convenience method to check if this order was submitted successfully
    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }
    
    // Convenience method to check if this order failed
    public boolean isFailure() {
        return "FAILURE".equals(status);
    }
} 