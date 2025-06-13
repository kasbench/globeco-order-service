package org.kasbench.globeco_order_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchSubmitRequestDTO {
    
    @NotNull(message = "Order IDs list is required")
    @Size(min = 1, max = 100, message = "Batch must contain between 1 and 100 order IDs")
    private List<Integer> orderIds;
} 