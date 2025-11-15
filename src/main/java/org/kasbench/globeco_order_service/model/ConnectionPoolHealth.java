package org.kasbench.globeco_order_service.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ConnectionPoolHealth {
    private int active;
    private int idle;
    private int waiting;
    private int total;
    private double utilization;
    private long timestamp;
    
    public boolean isHealthy() {
        return utilization < 0.75 && waiting < 3;
    }
    
    public String getStatus() {
        if (utilization >= 0.90) {
            return "CRITICAL";
        }
        if (utilization >= 0.75) {
            return "WARNING";
        }
        return "HEALTHY";
    }
    
    public static ConnectionPoolHealth fromMetrics(int active, int idle, int waiting, int total) {
        double utilization = total > 0 ? (double) active / total : 0.0;
        return ConnectionPoolHealth.builder()
                .active(active)
                .idle(idle)
                .waiting(waiting)
                .total(total)
                .utilization(utilization)
                .timestamp(Instant.now().toEpochMilli())
                .build();
    }
}
