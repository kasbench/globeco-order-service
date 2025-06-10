package org.kasbench.globeco_order_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderListResponseDTO {
    
    @NotNull
    private String status; // Overall batch status: "SUCCESS" (all succeeded), "FAILURE" (all failed), or "PARTIAL" (mixed results)
    
    @NotNull
    private String message; // Summary message describing the overall result
    
    @NotNull
    private Integer totalReceived; // Total number of orders in the request
    
    @NotNull
    private Integer successful; // Number of orders successfully processed
    
    @NotNull
    private Integer failed; // Number of orders that failed to process
    
    @Valid
    @Builder.Default
    private List<OrderPostResponseDTO> orders = new ArrayList<>(); // Individual results for each order in the batch
    
    // Factory method for complete success scenario
    public static OrderListResponseDTO success(List<OrderPostResponseDTO> orderResults) {
        int totalReceived = orderResults.size();
        return OrderListResponseDTO.builder()
                .status("SUCCESS")
                .message(String.format("All %d orders processed successfully", totalReceived))
                .totalReceived(totalReceived)
                .successful(totalReceived)
                .failed(0)
                .orders(orderResults)
                .build();
    }
    
    // Factory method for complete failure scenario
    public static OrderListResponseDTO failure(String errorMessage, int totalReceived) {
        return OrderListResponseDTO.builder()
                .status("FAILURE")
                .message(errorMessage)
                .totalReceived(totalReceived)
                .successful(0)
                .failed(totalReceived)
                .orders(new ArrayList<>())
                .build();
    }
    
    // Factory method for validation failure scenario (before processing)
    public static OrderListResponseDTO validationFailure(String errorMessage) {
        return OrderListResponseDTO.builder()
                .status("FAILURE")
                .message(errorMessage)
                .totalReceived(0)
                .successful(0)
                .failed(0)
                .orders(new ArrayList<>())
                .build();
    }
    
    // Factory method for partial success scenario
    public static OrderListResponseDTO partial(List<OrderPostResponseDTO> orderResults) {
        int totalReceived = orderResults.size();
        long successful = orderResults.stream().filter(OrderPostResponseDTO::isSuccess).count();
        long failed = orderResults.stream().filter(OrderPostResponseDTO::isFailure).count();
        
        return OrderListResponseDTO.builder()
                .status("PARTIAL")
                .message(String.format("%d of %d orders processed successfully", successful, totalReceived))
                .totalReceived(totalReceived)
                .successful((int) successful)
                .failed((int) failed)
                .orders(orderResults)
                .build();
    }
    
    // Factory method that automatically determines status based on results
    public static OrderListResponseDTO fromResults(List<OrderPostResponseDTO> orderResults) {
        if (orderResults.isEmpty()) {
            return validationFailure("No orders to process");
        }
        
        long successful = orderResults.stream().filter(OrderPostResponseDTO::isSuccess).count();
        long failed = orderResults.stream().filter(OrderPostResponseDTO::isFailure).count();
        
        if (successful == orderResults.size()) {
            return success(orderResults);
        } else if (failed == orderResults.size()) {
            return failure("All orders failed to process", orderResults.size());
        } else {
            return partial(orderResults);
        }
    }
    
    // Convenience methods for status checking
    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }
    
    public boolean isFailure() {
        return "FAILURE".equals(status);
    }
    
    public boolean isPartial() {
        return "PARTIAL".equals(status);
    }
    
    // Convenience method to get success rate
    public double getSuccessRate() {
        if (totalReceived == null || totalReceived == 0) {
            return 0.0;
        }
        return (double) successful / totalReceived;
    }
} 