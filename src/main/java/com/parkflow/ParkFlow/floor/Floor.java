package com.parkflow.ParkFlow.floor;

import jakarta.persistence.*;
import java.time.Instant;

/** One level of a parking building. Sizes are in metres. */
@Entity
@Table(name = "floors")
public class Floor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    @Column(nullable = false)
    private int level;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "width_m", nullable = false)
    private double width;

    @Column(name = "length_m", nullable = false)
    private double length;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Floor() {}

    public Floor(Long buildingId, int level) {
        this.buildingId = buildingId;
        this.level = level;
    }

    public void update(String name, double width, double length) {
        this.name = name;
        this.width = width;
        this.length = length;
        this.updatedAt = Instant.now(); // also bumps the time when only walls or spaces changed
    }

    @PrePersist
    void onCreate() { createdAt = updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getBuildingId() { return buildingId; }
    public int getLevel() { return level; }
    public String getName() { return name; }
    public double getWidth() { return width; }
    public double getLength() { return length; }
    public Instant getUpdatedAt() { return updatedAt; }
}
