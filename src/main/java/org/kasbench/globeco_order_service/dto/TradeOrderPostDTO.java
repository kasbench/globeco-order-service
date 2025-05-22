package org.kasbench.globeco_order_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeOrderPostDTO {
    private Integer orderId;
    private String portfolioId;
    private String orderType;
    private String securityId;
    private BigDecimal quantity;
    private BigDecimal limitPrice;
    private OffsetDateTime tradeTimestamp;
    private Integer blotterId;
} 