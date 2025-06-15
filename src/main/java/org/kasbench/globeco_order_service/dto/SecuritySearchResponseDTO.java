package org.kasbench.globeco_order_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SecuritySearchResponseDTO {
    
    @JsonProperty("securities")
    private List<SecuritySearchItemDTO> securities;
    
    @JsonProperty("pagination")
    private PaginationDTO pagination;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecuritySearchItemDTO {
        @JsonProperty("securityId")
        private String securityId;
        
        @JsonProperty("ticker")
        private String ticker;
        
        @JsonProperty("description")
        private String description;
        
        @JsonProperty("securityTypeId")
        private String securityTypeId;
        
        @JsonProperty("version")
        private Integer version;
        
        @JsonProperty("securityType")
        private SecurityTypeDTO securityType;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityTypeDTO {
        @JsonProperty("securityTypeId")
        private String securityTypeId;
        
        @JsonProperty("abbreviation")
        private String abbreviation;
        
        @JsonProperty("description")
        private String description;
        
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