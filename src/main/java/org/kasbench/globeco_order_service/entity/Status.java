package org.kasbench.globeco_order_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "status")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Status {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 20, nullable = false)
    private String abbreviation;

    @Column(length = 60, nullable = false)
    private String description;

    @Column(nullable = false)
    private Integer version = 1;
} 