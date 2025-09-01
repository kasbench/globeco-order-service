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

/**
 * DTO for bulk trade order responses from the trade service.
 * Represents the response structure from POST /api/v1/tradeOrders/bulk endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BulkTradeOrderResponseDTO {
    
    @NotNull
    private String status; // Overall operation status: "SUCCESS" or "FAILURE"
    
    @NotNull
    private String message; // Human-readable message describing the operation result
    
    @NotNull
    private Integer totalRequested; // Total number of trade orders in the request
    
    @NotNull
    private Integer successful; // Number of successfully created trade orders
    
    @NotNull
    private Integer failed; // Number of failed trade orders
    
    @Valid
    @Builder.Default
    private List<TradeOrderResultDTO> results = new ArrayList<>(); // Individual results for each trade order
    
    /**
     * Check if the overall bulk operation was successful.
     * 
     * @return true if status is "SUCCESS", false otherwise
     */
    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }
    
    /**
     * Check if the overall bulk operation failed.
     * 
     * @return true if status is "FAILURE", false otherwise
     */
    public boolean isFailure() {
        return "FAILURE".equals(status);
    }
    
    /**
     * Get the success rate of the bulk operation.
     * 
     * @return Success rate as a decimal between 0.0 and 1.0
     */
    public double getSuccessRate() {
        if (totalRequested == null || totalRequested == 0) {
            return 0.0;
        }
        return (double) successful / totalRequested;
    }
    
    /**
     * Check if all orders in the bulk operation were successful.
     * 
     * @return true if all orders succeeded, false otherwise
     */
    public boolean isCompleteSuccess() {
        return isSuccess() && successful != null && successful.equals(totalRequested);
    }
    
    /**
     * Check if all orders in the bulk operation failed.
     * 
     * @return true if all orders failed, false otherwise
     */
    public boolean isCompleteFailure() {
        return isFailure() && failed != null && failed.equals(totalRequested);
    }
}