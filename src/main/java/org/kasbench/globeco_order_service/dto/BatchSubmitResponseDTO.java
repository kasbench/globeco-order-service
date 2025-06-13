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
public class BatchSubmitResponseDTO {
    
    @NotNull
    private String status; // Overall batch status: "SUCCESS" (all succeeded), "FAILURE" (all failed), or "PARTIAL" (mixed results)
    
    @NotNull
    private String message; // Summary message describing the overall result
    
    @NotNull
    private Integer totalRequested; // Total number of orders requested for submission
    
    @NotNull
    private Integer successful; // Number of orders successfully submitted
    
    @NotNull
    private Integer failed; // Number of orders that failed to submit
    
    @Valid
    @Builder.Default
    private List<OrderSubmitResultDTO> results = new ArrayList<>(); // Individual results for each order in the batch
    
    // Factory method for complete success scenario
    public static BatchSubmitResponseDTO success(List<OrderSubmitResultDTO> orderResults) {
        int totalRequested = orderResults.size();
        return BatchSubmitResponseDTO.builder()
                .status("SUCCESS")
                .message(String.format("All %d orders submitted successfully", totalRequested))
                .totalRequested(totalRequested)
                .successful(totalRequested)
                .failed(0)
                .results(orderResults)
                .build();
    }
    
    // Factory method for complete failure scenario
    public static BatchSubmitResponseDTO failure(String errorMessage, int totalRequested, List<OrderSubmitResultDTO> orderResults) {
        return BatchSubmitResponseDTO.builder()
                .status("FAILURE")
                .message(errorMessage)
                .totalRequested(totalRequested)
                .successful(0)
                .failed(totalRequested)
                .results(orderResults != null ? orderResults : new ArrayList<>())
                .build();
    }
    
    // Factory method for validation failure scenario (before processing)
    public static BatchSubmitResponseDTO validationFailure(String errorMessage) {
        return BatchSubmitResponseDTO.builder()
                .status("FAILURE")
                .message(errorMessage)
                .totalRequested(0)
                .successful(0)
                .failed(0)
                .results(new ArrayList<>())
                .build();
    }
    
    // Factory method for partial success scenario
    public static BatchSubmitResponseDTO partial(List<OrderSubmitResultDTO> orderResults) {
        int totalRequested = orderResults.size();
        long successful = orderResults.stream().filter(OrderSubmitResultDTO::isSuccess).count();
        long failed = orderResults.stream().filter(OrderSubmitResultDTO::isFailure).count();
        
        return BatchSubmitResponseDTO.builder()
                .status("PARTIAL")
                .message(String.format("%d of %d orders submitted successfully, %d failed", successful, totalRequested, failed))
                .totalRequested(totalRequested)
                .successful((int) successful)
                .failed((int) failed)
                .results(orderResults)
                .build();
    }
    
    // Factory method that automatically determines status based on results
    public static BatchSubmitResponseDTO fromResults(List<OrderSubmitResultDTO> orderResults) {
        if (orderResults.isEmpty()) {
            return validationFailure("No orders to process");
        }
        
        long successful = orderResults.stream().filter(OrderSubmitResultDTO::isSuccess).count();
        long failed = orderResults.stream().filter(OrderSubmitResultDTO::isFailure).count();
        
        if (successful == orderResults.size()) {
            return success(orderResults);
        } else if (failed == orderResults.size()) {
            return failure("All orders failed to submit", orderResults.size(), orderResults);
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
        if (totalRequested == null || totalRequested == 0) {
            return 0.0;
        }
        return (double) successful / totalRequested;
    }
} 