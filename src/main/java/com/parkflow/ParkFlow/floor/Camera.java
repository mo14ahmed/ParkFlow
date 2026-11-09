package com.parkflow.ParkFlow.floor;

import jakarta.persistence.*;
import java.time.Instant;

/** A camera mounted on a wall or column. It watches the spaces whose cameraId points to it. */
@Entity
@Table(name = "cameras")
public class Camera {
    public enum Mount { WALL, COLUMN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "floor_id", nullable = false)
    private Long floorId;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Mount mount;

    @Column(nullable = false) private double x;
    @Column(nullable = false) private double z;

    @Column(name = "direction_deg", nullable = false)
    private double direction;

    @Column(name = "key_hash", length = 64)
    private String keyHash;

    @Column(name = "key_created_at")
    private Instant keyCreatedAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Camera() {}

    public Camera(Long floorId) { this.floorId = floorId; }

    public void update(String name, Mount mount, double x, double z, double direction) {
        this.name = name;
        this.mount = mount;
        this.x = x;
        this.z = z;
        this.direction = direction;
    }

    public void setKey(String hash) {
        this.keyHash = hash;
        this.keyCreatedAt = Instant.now();
    }

    public void seen() { this.lastSeenAt = Instant.now(); }

    @PrePersist
    void onCreate() { createdAt = updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getFloorId() { return floorId; }
    public String getName() { return name; }
    public Mount getMount() { return mount; }
    public double getX() { return x; }
    public double getZ() { return z; }
    public double getDirection() { return direction; }
    public String getKeyHash() { return keyHash; }
    public Instant getKeyCreatedAt() { return keyCreatedAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
}
