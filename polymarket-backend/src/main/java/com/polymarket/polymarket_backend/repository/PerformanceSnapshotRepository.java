package com.polymarket.polymarket_backend.repository;

import com.polymarket.polymarket_backend.model.entity.PerformanceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PerformanceSnapshotRepository extends JpaRepository<PerformanceSnapshot, Long> {

    List<PerformanceSnapshot> findAllByOrderByTimestampAsc();

    void deleteAll();
}
