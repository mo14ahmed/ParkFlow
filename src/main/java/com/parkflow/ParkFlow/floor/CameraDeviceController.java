package com.parkflow.ParkFlow.floor;

import com.parkflow.ParkFlow.floor.FloorDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * The API for the detector program next to a camera. It logs in with the camera's device key
 * in the X-Camera-Key header (see SecurityConfig), never with a user account.
 */
@RestController
@RequestMapping("/api/devices/camera")
public class CameraDeviceController {
    private final CameraDeviceService devices;

    public CameraDeviceController(CameraDeviceService devices) { this.devices = devices; }

    /** Which spaces this camera watches, with their current state. */
    @GetMapping
    public DeviceConfig config(@AuthenticationPrincipal Long cameraId) { return devices.config(cameraId); }

    /** Send the spaces whose state changed. Spaces that are already in that state are left alone. */
    @PostMapping("/occupancy")
    public OccupancyResult report(@AuthenticationPrincipal Long cameraId, @Valid @RequestBody OccupancyReport body) {
        return devices.report(cameraId, body);
    }

    /** Lets the backend know the camera is still running. */
    @PostMapping("/heartbeat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void heartbeat(@AuthenticationPrincipal Long cameraId) { devices.heartbeat(cameraId); }
}
