package com.parkflow.ParkFlow.floor;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FloorRepository extends JpaRepository<Floor, Long> {
    Optional<Floor> findByBuildingIdAndLevel(Long buildingId, int level);
}
