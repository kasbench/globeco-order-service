package org.kasbench.globeco_order_service.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderTypeDTO {
    private Integer id;
    private String abbreviation;
    private String description;
    private Integer version;
} 