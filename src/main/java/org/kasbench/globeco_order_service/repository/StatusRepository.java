package org.kasbench.globeco_order_service.repository;

import org.kasbench.globeco_order_service.entity.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StatusRepository extends JpaRepository<Status, Integer> {
} 