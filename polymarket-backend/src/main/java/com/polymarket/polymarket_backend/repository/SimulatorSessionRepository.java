package com.polymarket.polymarket_backend.repository;

import com.polymarket.polymarket_backend.model.entity.SimulatorSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SimulatorSessionRepository extends JpaRepository<SimulatorSession, UUID> {

    Optional<SimulatorSession> findFirstByEnabledTrue();
}
