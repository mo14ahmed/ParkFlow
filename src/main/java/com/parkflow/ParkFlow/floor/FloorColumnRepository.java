package com.parkflow.ParkFlow.floor;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FloorColumnRepository extends JpaRepository<FloorColumn, Long> {
    List<FloorColumn> findByFloorIdOrderById(Long floorId);

    @Modifying
    @Query("delete from FloorColumn c where c.floorId = :floorId")
    void deleteByFloor(@Param("floorId") Long floorId);
}
