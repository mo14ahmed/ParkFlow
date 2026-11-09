package com.parkflow.ParkFlow.floor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

/**
 * JSON shapes for the floor editor. All sizes and positions are in metres from the floor's corner:
 * x runs along the width, z along the length. Angles are in degrees, seen from above.
 */
public final class FloorDtos {
    private FloorDtos() {}

    public static final int MAX_WALLS = 500;
    public static final int MAX_COLUMNS = 500;
    public static final int MAX_CAMERAS = 50;
    public static final int MAX_SPACES = 1000;

    // ---------- what the editor sends when the provider presses Save ----------

    public record FloorLayoutRequest(
            @NotBlank(message = "Give the floor a name.")
            @Size(max = 50, message = "Floor name is too long (50 characters at most).")
            String name,
            @NotNull(message = "Enter the floor width.")
            @DecimalMin(value = "5", message = "The floor must be at least 5 m wide.")
            @DecimalMax(value = "500", message = "The floor can be at most 500 m wide.")
            Double width,
            @NotNull(message = "Enter the floor length.")
            @DecimalMin(value = "5", message = "The floor must be at least 5 m long.")
            @DecimalMax(value = "500", message = "The floor can be at most 500 m long.")
            Double length,
            @NotNull @Size(max = MAX_WALLS, message = "Too many walls (500 at most).")
            List<@Valid @NotNull WallItem> walls,
            @NotNull @Size(max = MAX_COLUMNS, message = "Too many columns (500 at most).")
            List<@Valid @NotNull ColumnItem> columns,
            @NotNull @Size(max = MAX_CAMERAS, message = "Too many cameras (50 at most).")
            List<@Valid @NotNull CameraItem> cameras,
            @NotNull @Size(max = MAX_SPACES, message = "Too many parking spaces (1000 at most).")
            List<@Valid @NotNull SpaceItem> spaces) {}

    public record WallItem(
            @NotNull Double x1, @NotNull Double z1, @NotNull Double x2, @NotNull Double z2,
            @NotNull @DecimalMin(value = "0.05", message = "A wall must be at least 0.05 m thick.")
            @DecimalMax(value = "5", message = "A wall can be at most 5 m thick.")
            Double thickness) {}

    public record ColumnItem(
            @NotNull Double x, @NotNull Double z,
            @NotNull @DecimalMin(value = "0.1", message = "A column must be at least 0.1 m wide.")
            @DecimalMax(value = "10", message = "A column can be at most 10 m wide.")
            Double width,
            @NotNull @DecimalMin(value = "0.1", message = "A column must be at least 0.1 m deep.")
            @DecimalMax(value = "10", message = "A column can be at most 10 m deep.")
            Double depth) {}

    /**
     * id = null for a camera added since the last save. ref = a name the editor gives each camera
     * so spaces can point to it before it has an id (for saved cameras the editor uses "c" + id).
     */
    public record CameraItem(
            Long id,
            @NotBlank @Size(max = 40) String ref,
            @NotBlank(message = "Give every camera a name.")
            @Size(max = 50, message = "Camera name is too long (50 characters at most).")
            String name,
            @NotNull(message = "A camera must be on a wall or a column.") Camera.Mount mount,
            @NotNull Double x, @NotNull Double z,
            @NotNull Double direction) {}

    /** cameraRef = the ref of the camera that watches this space, or null if no camera does. */
    public record SpaceItem(
            Long id,
            @NotBlank(message = "Every parking space needs a name.")
            @Size(max = 20, message = "A parking space name can be 20 characters at most.")
            String label,
            @NotNull Double x, @NotNull Double z,
            @NotNull @DecimalMin(value = "1", message = "A parking space must be at least 1 m wide.")
            @DecimalMax(value = "20", message = "A parking space can be at most 20 m wide.")
            Double width,
            @NotNull @DecimalMin(value = "1", message = "A parking space must be at least 1 m long.")
            @DecimalMax(value = "30", message = "A parking space can be at most 30 m long.")
            Double length,
            @NotNull Double rotation,
            @Size(max = 40) String cameraRef) {}

    // ---------- what the API sends back ----------

    public record WallView(double x1, double z1, double x2, double z2, double thickness) {
        static WallView of(FloorWall w) { return new WallView(w.getX1(), w.getZ1(), w.getX2(), w.getZ2(), w.getThickness()); }
    }

    public record ColumnView(double x, double z, double width, double depth) {
        static ColumnView of(FloorColumn c) { return new ColumnView(c.getX(), c.getZ(), c.getWidth(), c.getDepth()); }
    }

    /** hasKey and lastSeenAt are only filled in for the building's owner. */
    public record CameraView(Long id, String ref, String name, Camera.Mount mount, double x, double z, double direction,
                             Boolean hasKey, Instant lastSeenAt) {
        static CameraView of(Camera c, boolean owner) {
            return new CameraView(c.getId(), refFor(c.getId()), c.getName(), c.getMount(), c.getX(), c.getZ(), c.getDirection(),
                    owner ? c.getKeyHash() != null : null, owner ? c.getLastSeenAt() : null);
        }
    }

    public record SpaceView(Long id, String label, double x, double z, double width, double length, double rotation,
                            Long cameraId, String cameraRef, Space.Status status, Instant statusChangedAt) {
        static SpaceView of(Space s) {
            return new SpaceView(s.getId(), s.getLabel(), s.getX(), s.getZ(), s.getWidth(), s.getLength(), s.getRotation(),
                    s.getCameraId(), s.getCameraId() == null ? null : refFor(s.getCameraId()), s.getStatus(), s.getStatusChangedAt());
        }
    }

    public record Counts(int total, int free, int occupied, int unknown, int withoutCamera) {
        static Counts of(List<SpaceView> spaces) {
            int free = 0, occ = 0, unk = 0, none = 0;
            for (SpaceView s : spaces) {
                switch (s.status()) {
                    case FREE -> free++;
                    case OCCUPIED -> occ++;
                    default -> unk++;
                }
                if (s.cameraId() == null) none++;
            }
            return new Counts(spaces.size(), free, occ, unk, none);
        }
    }

    /** saved = false when the provider has not saved a floor yet (the editor then starts from this default). */
    public record FloorView(Long id, Long buildingId, String buildingName, int level, String name, double width, double length,
                            Instant updatedAt, boolean saved, List<WallView> walls, List<ColumnView> columns,
                            List<CameraView> cameras, List<SpaceView> spaces, Counts counts) {}

    public record CameraKey(Long cameraId, String cameraName, String key, Instant createdAt) {}

    // ---------- camera device API (the detector program) ----------

    public record DeviceSpace(Long id, String label, Space.Status status) {
        static DeviceSpace of(Space s) { return new DeviceSpace(s.getId(), s.getLabel(), s.getStatus()); }
    }

    public record DeviceConfig(Long cameraId, String cameraName, Long buildingId, Long floorId, List<DeviceSpace> spaces) {}

    /** One space's new state. Give spaceId or label. observedAt is optional (defaults to now). */
    public record SpaceReport(
            Long spaceId,
            @Size(max = 20) String label,
            @NotNull(message = "Send occupied: true or false for every space.") Boolean occupied,
            Instant observedAt) {}

    public record OccupancyReport(
            @NotNull(message = "Send a list of spaces.")
            @Size(min = 1, max = MAX_SPACES, message = "Send between 1 and 1000 spaces.")
            List<@Valid @NotNull SpaceReport> spaces) {}

    public record OccupancyResult(int changed, int unchanged, List<DeviceSpace> spaces) {}

    static String refFor(Long cameraId) { return "c" + cameraId; }
}
