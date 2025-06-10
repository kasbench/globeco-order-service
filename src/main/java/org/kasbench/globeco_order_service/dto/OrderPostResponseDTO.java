package org.kasbench.globeco_order_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
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
public class OrderPostResponseDTO {
    
    @NotNull
    private String status; // "SUCCESS" or "FAILURE"
    
    @NotNull
    private String message; // Descriptive message (error details for failures, success confirmation for success)
    
    @Valid
    private OrderWithDetailsDTO order; // The created order details (only present when status is "SUCCESS")
    
    private Long orderId; // The ID of the created order (only present when status is "SUCCESS")
    
    @NotNull
    private Integer requestIndex; // The zero-based index of this order in the original request array
    
    // Factory method for successful order processing
    public static OrderPostResponseDTO success(OrderWithDetailsDTO order, Long orderId, Integer requestIndex) {
        return OrderPostResponseDTO.builder()
                .status("SUCCESS")
                .message("Order created successfully")
                .order(order)
                .orderId(orderId)
                .requestIndex(requestIndex)
                .build();
    }
    
    // Factory method for failed order processing
    public static OrderPostResponseDTO failure(String errorMessage, Integer requestIndex) {
        return OrderPostResponseDTO.builder()
                .status("FAILURE")
                .message(errorMessage)
                .requestIndex(requestIndex)
                .build();
    }
    
    // Convenience method to check if this order was processed successfully
    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }
    
    // Convenience method to check if this order failed
    public boolean isFailure() {
        return "FAILURE".equals(status);
    }
} 