package com.parkflow.ParkFlow.floor;

import com.parkflow.ParkFlow.building.Building;
import com.parkflow.ParkFlow.building.BuildingRepository;
import com.parkflow.ParkFlow.floor.FloorDtos.*;
import com.parkflow.ParkFlow.user.AccountStatus;
import com.parkflow.ParkFlow.user.UserRepository;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Floor maps drawn by providers: walls, columns, cameras and parking spaces. */
@Service
public class FloorService {
    /** The website edits one floor per building for now: this level. The database allows more. */
    public static final int MAIN_LEVEL = 0;
    static final double DEFAULT_WIDTH = 60, DEFAULT_LENGTH = 40, DEFAULT_WALL = 0.3;

    private final BuildingRepository buildings;
    private final UserRepository users;
    private final FloorRepository floors;
    private final FloorWallRepository walls;
    private final FloorColumnRepository columns;
    private final CameraRepository cameras;
    private final SpaceRepository spaces;

    public FloorService(BuildingRepository buildings, UserRepository users, FloorRepository floors, FloorWallRepository walls,
                        FloorColumnRepository columns, CameraRepository cameras, SpaceRepository spaces) {
        this.buildings = buildings;
        this.users = users;
        this.floors = floors;
        this.walls = walls;
        this.columns = columns;
        this.cameras = cameras;
        this.spaces = spaces;
    }

    private static ResponseStatusException notFound(String what) { return new ResponseStatusException(HttpStatus.NOT_FOUND, what); }
    private static ResponseStatusException bad(String why) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, why); }

    /** A provider only sees their own buildings; anyone else's look like they do not exist. */
    private Building own(Long providerId, Long buildingId) {
        return buildings.findByIdAndProviderId(buildingId, providerId).orElseThrow(() -> notFound("Parking building not found."));
    }

    // ---------- provider: load and save the floor ----------

    @Transactional(readOnly = true)
    public FloorView forProvider(Long providerId, Long buildingId) {
        Building b = own(providerId, buildingId);
        return floors.findByBuildingIdAndLevel(b.getId(), MAIN_LEVEL)
                .map(f -> view(b, f, true))
                .orElseGet(() -> emptyFloor(b));
    }

    /** What the editor starts from before the first save: an empty 60 x 40 m floor with four outside walls. */
    private static FloorView emptyFloor(Building b) {
        double w = DEFAULT_WIDTH, l = DEFAULT_LENGTH, t = DEFAULT_WALL;
        List<WallView> outside = List.of(new WallView(0, 0, w, 0, t), new WallView(w, 0, w, l, t),
                new WallView(w, l, 0, l, t), new WallView(0, l, 0, 0, t));
        return new FloorView(null, b.getId(), b.getName(), MAIN_LEVEL, "Ground floor", w, l, null, false,
                outside, List.of(), List.of(), List.of(), new Counts(0, 0, 0, 0, 0));
    }

    @Transactional
    public FloorView save(Long providerId, Long buildingId, FloorLayoutRequest r) {
        Building b = own(providerId, buildingId);
        checkLayout(r);

        Floor floor = floors.findByBuildingIdAndLevel(b.getId(), MAIN_LEVEL)
                .orElseGet(() -> new Floor(b.getId(), MAIN_LEVEL));
        floor.update(r.name().trim(), r.width(), r.length());
        floor = floors.save(floor);
        Long floorId = floor.getId();

        // Walls and columns have no history, so they are simply replaced.
        walls.deleteByFloor(floorId);
        columns.deleteByFloor(floorId);
        walls.saveAll(r.walls().stream().map(w -> new FloorWall(floorId, w.x1(), w.z1(), w.x2(), w.z2(), w.thickness())).toList());
        columns.saveAll(r.columns().stream().map(c -> new FloorColumn(floorId, c.x(), c.z(), c.width(), c.depth())).toList());

        // Cameras and spaces keep their ids (the camera's device key and the space's history depend on them).
        Map<Long, Camera> oldCameras = cameras.findByFloorIdOrderById(floorId).stream()
                .collect(Collectors.toMap(Camera::getId, Function.identity()));
        Map<String, Long> cameraIdByRef = new HashMap<>();
        Set<Long> keptCameras = new HashSet<>();
        for (CameraItem c : r.cameras()) {
            Camera cam;
            if (c.id() != null) {
                cam = oldCameras.get(c.id());
                if (cam == null) throw bad("Camera " + c.id() + " is not on this floor. Reload the page and try again.");
                keptCameras.add(c.id());
            } else cam = new Camera(floorId);
            cam.update(c.name().trim(), c.mount(), c.x(), c.z(), normalizeAngle(c.direction()));
            cam = cameras.save(cam);
            cameraIdByRef.put(c.ref(), cam.getId());
        }

        Map<Long, Space> oldSpaces = spaces.findByFloorIdOrderById(floorId).stream()
                .collect(Collectors.toMap(Space::getId, Function.identity()));
        Set<Long> keptSpaces = new HashSet<>();
        for (SpaceItem s : r.spaces()) {
            Space space;
            if (s.id() != null) {
                space = oldSpaces.get(s.id());
                if (space == null) throw bad("Parking space " + s.label() + " is not on this floor. Reload the page and try again.");
                keptSpaces.add(s.id());
            } else space = new Space(floorId);
            Long cameraId = blank(s.cameraRef()) ? null : cameraIdByRef.get(s.cameraRef());
            space.update(s.label().trim(), s.x(), s.z(), s.width(), s.length(), normalizeAngle(s.rotation()), cameraId);
            // Nobody watches it any more, so its last state can't be trusted.
            if (cameraId == null && space.getStatus() != Space.Status.UNKNOWN) space.setStatus(Space.Status.UNKNOWN, java.time.Instant.now());
            spaces.save(space);
        }

        spaces.deleteAll(oldSpaces.values().stream().filter(s -> !keptSpaces.contains(s.getId())).toList());
        cameras.deleteAll(oldCameras.values().stream().filter(c -> !keptCameras.contains(c.getId())).toList());
        spaces.flush();
        return view(b, floor, true);
    }

    /** Checks the rules bean validation can't: unique names, cameras that exist, things inside the floor. */
    private static void checkLayout(FloorLayoutRequest r) {
        double w = r.width(), l = r.length(), margin = 1.0;
        Set<String> refs = new HashSet<>();
        Set<Long> ids = new HashSet<>();
        for (CameraItem c : r.cameras()) {
            if (!refs.add(c.ref())) throw bad("Two cameras have the same reference. Reload the page and try again.");
            if (c.id() != null && !ids.add(c.id())) throw bad("A camera is listed twice.");
            if (!inside(c.x(), c.z(), w, l, margin)) throw bad("Camera " + c.name().trim() + " is outside the floor.");
        }
        Set<String> labels = new HashSet<>();
        ids.clear();
        for (SpaceItem s : r.spaces()) {
            String label = s.label().trim();
            if (!labels.add(label.toLowerCase(Locale.ROOT))) throw bad("Two parking spaces are named " + label + ". Give each one its own name.");
            if (s.id() != null && !ids.add(s.id())) throw bad("Parking space " + label + " is listed twice.");
            if (!inside(s.x(), s.z(), w, l, margin)) throw bad("Parking space " + label + " is outside the floor.");
            if (!blank(s.cameraRef()) && !refs.contains(s.cameraRef()))
                throw bad("Parking space " + label + " points to a camera that is not on this floor.");
        }
        for (WallItem x : r.walls())
            if (!inside(x.x1(), x.z1(), w, l, margin) || !inside(x.x2(), x.z2(), w, l, margin)) throw bad("A wall goes outside the floor.");
        for (ColumnItem c : r.columns())
            if (!inside(c.x(), c.z(), w, l, margin)) throw bad("A column is outside the floor.");
    }

    private static boolean inside(double x, double z, double w, double l, double margin) {
        return x >= -margin && x <= w + margin && z >= -margin && z <= l + margin;
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    static double normalizeAngle(double deg) {
        double a = deg % 360;
        if (a < 0) a += 360;
        return Math.round(a * 100.0) / 100.0;
    }

    // ---------- anyone logged in: the live floor map ----------

    /** Live map of a building. Shown when the provider is approved, or to the provider who owns it. */
    @Transactional(readOnly = true)
    public FloorView live(Long viewerId, Long buildingId) {
        Building b = buildings.findById(buildingId).orElseThrow(() -> notFound("Parking building not found."));
        boolean owner = b.getProviderId().equals(viewerId);
        boolean approved = users.findById(b.getProviderId()).map(u -> u.getStatus() == AccountStatus.ACTIVE).orElse(false);
        if (!owner && !approved) throw notFound("Parking building not found.");
        Floor f = floors.findByBuildingIdAndLevel(b.getId(), MAIN_LEVEL)
                .orElseThrow(() -> notFound("This building has no floor map yet."));
        return view(b, f, owner);
    }

    private FloorView view(Building b, Floor f, boolean owner) {
        List<SpaceView> sv = spaces.findByFloorIdOrderById(f.getId()).stream().map(SpaceView::of).toList();
        return new FloorView(f.getId(), b.getId(), b.getName(), f.getLevel(), f.getName(), f.getWidth(), f.getLength(),
                f.getUpdatedAt(), true,
                walls.findByFloorIdOrderById(f.getId()).stream().map(WallView::of).toList(),
                columns.findByFloorIdOrderById(f.getId()).stream().map(ColumnView::of).toList(),
                cameras.findByFloorIdOrderById(f.getId()).stream().map(c -> CameraView.of(c, owner)).toList(),
                sv, Counts.of(sv));
    }

    // ---------- provider: camera device keys ----------

    /** Loads a camera and checks that it is on one of this provider's buildings. */
    Camera ownCamera(Long providerId, Long cameraId) {
        Camera c = cameras.findById(cameraId).orElseThrow(() -> notFound("Camera not found."));
        Floor f = floors.findById(c.getFloorId()).orElseThrow(() -> notFound("Camera not found."));
        own(providerId, f.getBuildingId());
        return c;
    }

    Long buildingOf(Camera c) {
        return floors.findById(c.getFloorId()).map(Floor::getBuildingId).orElse(null);
    }
}
