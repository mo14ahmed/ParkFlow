package com.parkflow.ParkFlow.floor;

import com.parkflow.ParkFlow.floor.FloorDtos.FloorView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** The live floor map with each space's state. Any logged-in user can see it for now. */
@RestController
@RequestMapping("/api/buildings")
public class FloorViewController {
    private final FloorService floors;

    public FloorViewController(FloorService floors) { this.floors = floors; }

    @GetMapping("/{buildingId}/floor")
    public FloorView live(@AuthenticationPrincipal Jwt jwt, @PathVariable Long buildingId) {
        return floors.live(Long.valueOf(jwt.getSubject()), buildingId);
    }
}
