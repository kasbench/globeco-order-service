package org.kasbench.globeco_order_service.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusPostDTO {
    private String abbreviation;
    private String description;
    private Integer version;
} 