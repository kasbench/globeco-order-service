package org.kasbench.globeco_order_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "\"order\"")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "blotter_id")
    private Blotter blotter;

    @ManyToOne(optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private Status status;

    @Column(name = "portfolio_id", length = 24, nullable = false)
    private String portfolioId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_type_id", nullable = false)
    private OrderType orderType;

    @Column(name = "security_id", length = 24, nullable = false)
    private String securityId;

    @Column(precision = 18, scale = 8, nullable = false)
    private BigDecimal quantity;

    @Column(name = "limit_price", precision = 18, scale = 8)
    private BigDecimal limitPrice;

    @Column(name = "order_timestamp", nullable = false)
    private OffsetDateTime orderTimestamp;

    @Column(nullable = false)
    private Integer version;
} 