package com.parkflow.ParkFlow.floor;

import com.parkflow.ParkFlow.floor.FloorDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** The provider's floor editor: load and save a building's floor, and make device keys for its cameras. */
@RestController
@RequestMapping("/api/provider")
public class ProviderFloorController {
    private final FloorService floors;
    private final CameraDeviceService devices;

    public ProviderFloorController(FloorService floors, CameraDeviceService devices) {
        this.floors = floors;
        this.devices = devices;
    }

    private static Long me(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }

    @GetMapping("/buildings/{buildingId}/floor")
    public FloorView get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long buildingId) {
        return floors.forProvider(me(jwt), buildingId);
    }

    @PutMapping("/buildings/{buildingId}/floor")
    public FloorView save(@AuthenticationPrincipal Jwt jwt, @PathVariable Long buildingId, @Valid @RequestBody FloorLayoutRequest body) {
        return floors.save(me(jwt), buildingId, body);
    }

    /** Creates a new device key for a saved camera. The previous key stops working. */
    @PostMapping("/cameras/{cameraId}/key")
    public CameraKey newKey(@AuthenticationPrincipal Jwt jwt, @PathVariable Long cameraId) {
        return devices.newKey(me(jwt), cameraId);
    }
}
