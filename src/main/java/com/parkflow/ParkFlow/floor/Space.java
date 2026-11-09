package com.parkflow.ParkFlow.floor;

import jakarta.persistence.*;
import java.time.Instant;

/** A parking space: a rectangle centred on (x, z), turned by rotation degrees, in metres. */
@Entity
@Table(name = "spaces")
public class Space {
    public enum Status { FREE, OCCUPIED, UNKNOWN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "floor_id", nullable = false)
    private Long floorId;

    @Column(nullable = false, length = 20)
    private String label;

    @Column(nullable = false) private double x;
    @Column(nullable = false) private double z;

    @Column(name = "width_m", nullable = false)
    private double width;

    @Column(name = "length_m", nullable = false)
    private double length;

    @Column(name = "rotation_deg", nullable = false)
    private double rotation;

    @Column(name = "camera_id")
    private Long cameraId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.UNKNOWN;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    protected Space() {}

    public Space(Long floorId) { this.floorId = floorId; }

    public void update(String label, double x, double z, double width, double length, double rotation, Long cameraId) {
        this.label = label;
        this.x = x;
        this.z = z;
        this.width = width;
        this.length = length;
        this.rotation = rotation;
        this.cameraId = cameraId;
    }

    public void setStatus(Status status, Instant when) {
        this.status = status;
        this.statusChangedAt = when;
    }

    public Long getId() { return id; }
    public Long getFloorId() { return floorId; }
    public String getLabel() { return label; }
    public double getX() { return x; }
    public double getZ() { return z; }
    public double getWidth() { return width; }
    public double getLength() { return length; }
    public double getRotation() { return rotation; }
    public Long getCameraId() { return cameraId; }
    public Status getStatus() { return status; }
    public Instant getStatusChangedAt() { return statusChangedAt; }
}
