package org.kasbench.globeco_order_service.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDTO {
    private Integer id;
    private Integer blotterId;
    private Integer statusId;
    private String portfolioId;
    private Integer orderTypeId;
    private String securityId;
    private BigDecimal quantity;
    private BigDecimal limitPrice;
    private Integer tradeOrderId;
    private OffsetDateTime orderTimestamp;
    private Integer version;
} 