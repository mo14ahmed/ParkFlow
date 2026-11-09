package com.parkflow.ParkFlow.floor;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OccupancyEventRepository extends JpaRepository<OccupancyEvent, Long> {
    List<OccupancyEvent> findBySpaceIdOrderByIdDesc(Long spaceId);
}
