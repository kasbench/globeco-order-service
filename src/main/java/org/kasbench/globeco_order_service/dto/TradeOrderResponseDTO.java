package org.kasbench.globeco_order_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * DTO representing a trade order response from the trade service.
 * Used in bulk trade order responses to represent individual created trade orders.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TradeOrderResponseDTO {
    
    private Integer id; // Trade order ID assigned by the trade service
    private Integer orderId; // Original order ID from the order service
    private String portfolioId; // Portfolio identifier
    private String orderType; // Order type (BUY/SELL)
    private String securityId; // Security identifier
    private BigDecimal quantity; // Quantity of securities
    private BigDecimal limitPrice; // Limit price for the order
    private OffsetDateTime tradeTimestamp; // Timestamp of the trade
    private BigDecimal quantitySent; // Quantity sent for execution (typically 0.00 initially)
    private Boolean submitted; // Whether the order has been submitted for execution
    private Integer version; // Version for optimistic locking
    private BlotterDTO blotter; // Associated blotter information
    
    /**
     * Check if this trade order has been submitted for execution.
     * 
     * @return true if submitted is true, false otherwise
     */
    public boolean isSubmitted() {
        return Boolean.TRUE.equals(submitted);
    }
    
    /**
     * Get the quantity remaining to be sent (quantity - quantitySent).
     * 
     * @return Remaining quantity, or null if either quantity or quantitySent is null
     */
    public BigDecimal getRemainingQuantity() {
        if (quantity == null || quantitySent == null) {
            return null;
        }
        return quantity.subtract(quantitySent);
    }
}