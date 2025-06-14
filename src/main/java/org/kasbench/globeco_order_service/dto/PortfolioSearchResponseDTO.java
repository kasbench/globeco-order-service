package org.kasbench.globeco_order_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioSearchResponseDTO {
    
    @JsonProperty("portfolios")
    private List<PortfolioSearchItemDTO> portfolios;
    
    @JsonProperty("pagination")
    private PaginationDTO pagination;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PortfolioSearchItemDTO {
        @JsonProperty("portfolioId")
        private String portfolioId;
        
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("dateCreated")
        private String dateCreated;
        
        @JsonProperty("version")
        private Integer version;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaginationDTO {
        @JsonProperty("totalElements")
        private Integer totalElements;
        
        @JsonProperty("totalPages")
        private Integer totalPages;
        
        @JsonProperty("currentPage")
        private Integer currentPage;
        
        @JsonProperty("pageSize")
        private Integer pageSize;
        
        @JsonProperty("hasNext")
        private Boolean hasNext;
        
        @JsonProperty("hasPrevious")
        private Boolean hasPrevious;
    }
} 