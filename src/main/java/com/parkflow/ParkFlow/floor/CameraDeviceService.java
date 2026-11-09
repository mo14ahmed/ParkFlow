package com.parkflow.ParkFlow.floor;

import com.parkflow.ParkFlow.floor.FloorDtos.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Device keys for cameras, and what the detector program (YOLO, later) can do with one:
 * read which spaces it watches, and report when a space's state changes.
 * A key looks like "pfc_<cameraId>_<secret>". Only its SHA-256 hash is stored, so a lost key can't be shown again.
 */
@Service
public class CameraDeviceService {
    static final String PREFIX = "pfc_";
    private final SecureRandom random = new SecureRandom();

    private final FloorService floorService;
    private final CameraRepository cameras;
    private final SpaceRepository spaces;
    private final OccupancyEventRepository events;

    public CameraDeviceService(FloorService floorService, CameraRepository cameras, SpaceRepository spaces,
                               OccupancyEventRepository events) {
        this.floorService = floorService;
        this.cameras = cameras;
        this.spaces = spaces;
        this.events = events;
    }

    /** Makes a new key for the camera. The old key stops working. The key is only returned this once. */
    @Transactional
    public CameraKey newKey(Long providerId, Long cameraId) {
        Camera c = floorService.ownCamera(providerId, cameraId);
        byte[] secret = new byte[24];
        random.nextBytes(secret);
        String key = PREFIX + c.getId() + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        c.setKey(sha256(key));
        cameras.save(c);
        return new CameraKey(c.getId(), c.getName(), key, c.getKeyCreatedAt());
    }

    /** Returns the camera id for a valid key, or empty. Used by the security filter for /api/devices/**. */
    @Transactional(readOnly = true)
    public Optional<Long> authenticate(String key) {
        if (key == null || !key.startsWith(PREFIX)) return Optional.empty();
        int sep = key.indexOf('_', PREFIX.length());
        if (sep < 0) return Optional.empty();
        long id;
        try { id = Long.parseLong(key.substring(PREFIX.length(), sep)); } catch (NumberFormatException e) { return Optional.empty(); }
        return cameras.findById(id)
                .filter(c -> c.getKeyHash() != null && MessageDigest.isEqual(
                        c.getKeyHash().getBytes(StandardCharsets.US_ASCII), sha256(key).getBytes(StandardCharsets.US_ASCII)))
                .map(Camera::getId);
    }

    private Camera camera(Long cameraId) {
        return cameras.findById(cameraId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown camera."));
    }

    @Transactional
    public DeviceConfig config(Long cameraId) {
        Camera c = camera(cameraId);
        c.seen();
        return new DeviceConfig(c.getId(), c.getName(), floorService.buildingOf(c), c.getFloorId(),
                spaces.findByCameraIdOrderById(c.getId()).stream().map(DeviceSpace::of).toList());
    }

    @Transactional
    public void heartbeat(Long cameraId) { camera(cameraId).seen(); }

    /**
     * Applies the states the camera sends. Only spaces assigned to this camera are accepted; if any space
     * is not, nothing is changed. A change is saved (and logged) only when it differs from the current state.
     */
    @Transactional
    public OccupancyResult report(Long cameraId, OccupancyReport r) {
        Camera c = camera(cameraId);
        c.seen();
        List<Space> watched = spaces.findByCameraIdOrderById(c.getId());
        Map<Long, Space> byId = watched.stream().collect(Collectors.toMap(Space::getId, Function.identity()));
        Map<String, Space> byLabel = watched.stream()
                .collect(Collectors.toMap(s -> s.getLabel().toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));

        List<Space> targets = new ArrayList<>();
        for (SpaceReport s : r.spaces()) {
            Space space;
            if (s.spaceId() != null) space = byId.get(s.spaceId());
            else if (s.label() != null && !s.label().isBlank()) space = byLabel.get(s.label().trim().toLowerCase(Locale.ROOT));
            else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Give a spaceId or a label for every space.");
            if (space == null) {
                String name = s.spaceId() != null ? "id " + s.spaceId() : s.label().trim();
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Space " + name + " is not watched by this camera.");
            }
            targets.add(space);
        }

        Instant now = Instant.now();
        int changed = 0;
        for (int i = 0; i < targets.size(); i++) {
            Space space = targets.get(i);
            SpaceReport s = r.spaces().get(i);
            Space.Status next = s.occupied() ? Space.Status.OCCUPIED : Space.Status.FREE;
            if (space.getStatus() == next) continue;
            Instant observed = s.observedAt() == null || s.observedAt().isAfter(now) ? now : s.observedAt();
            events.save(new OccupancyEvent(space.getId(), c.getId(), space.getStatus(), next, observed));
            space.setStatus(next, observed);
            changed++;
        }
        spaces.saveAll(targets);
        return new OccupancyResult(changed, targets.size() - changed,
                spaces.findByCameraIdOrderById(c.getId()).stream().map(DeviceSpace::of).toList());
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
