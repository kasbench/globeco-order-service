package org.kasbench.globeco_order_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Entity
@Table(name = "blotter")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Blotter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 60, nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer version;
} 