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
public class OrderWithDetailsDTO {
    private Integer id;
    private BlotterDTO blotter;
    private StatusDTO status;
    private String portfolioId;
    private OrderTypeDTO orderType;
    private String securityId;
    private BigDecimal quantity;
    private BigDecimal limitPrice;
    private OffsetDateTime orderTimestamp;
    private Integer version;
} 