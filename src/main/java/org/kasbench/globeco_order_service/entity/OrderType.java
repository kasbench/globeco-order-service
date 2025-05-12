package org.kasbench.globeco_order_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "order_type")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 10, nullable = false)
    private String abbreviation;

    @Column(length = 60, nullable = false)
    private String description;

    @Column(nullable = false)
    private Integer version;
} 