package com.parkflow.ParkFlow.floor;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpaceRepository extends JpaRepository<Space, Long> {
    List<Space> findByFloorIdOrderById(Long floorId);
    List<Space> findByCameraIdOrderById(Long cameraId);
}
