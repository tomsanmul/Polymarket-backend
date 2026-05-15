package com.polymarket.polymarket_backend.repository;

import com.polymarket.polymarket_backend.model.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRepository extends JpaRepository<Position, String> {

    List<Position> findByClosedFalse();

    List<Position> findByClosedTrueOrderByCloseTimeDesc();

    Optional<Position> findByIdAndClosedFalse(String id);

    void deleteAll();
}
