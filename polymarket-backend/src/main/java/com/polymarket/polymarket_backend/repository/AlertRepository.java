package com.polymarket.polymarket_backend.repository;

import com.polymarket.polymarket_backend.model.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByUserId(Long userId);

    List<Alert> findByActiveTrueAndTriggeredFalse();
}
