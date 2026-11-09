package com.parkflow.ParkFlow.floor;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CameraRepository extends JpaRepository<Camera, Long> {
    List<Camera> findByFloorIdOrderById(Long floorId);
}
