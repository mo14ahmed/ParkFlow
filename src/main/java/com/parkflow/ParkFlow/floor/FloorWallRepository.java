package com.parkflow.ParkFlow.floor;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FloorWallRepository extends JpaRepository<FloorWall, Long> {
    List<FloorWall> findByFloorIdOrderById(Long floorId);

    @Modifying
    @Query("delete from FloorWall w where w.floorId = :floorId")
    void deleteByFloor(@Param("floorId") Long floorId);
}
