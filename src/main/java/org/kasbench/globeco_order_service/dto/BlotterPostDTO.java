package org.kasbench.globeco_order_service.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlotterPostDTO {
    private String name;
    private Integer version;
} 