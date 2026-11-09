package com.parkflow.ParkFlow.floor;

import jakarta.persistence.*;

/** A column (pillar), a box centred on (x, z), in metres. */
@Entity
@Table(name = "floor_columns")
public class FloorColumn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "floor_id", nullable = false)
    private Long floorId;

    @Column(nullable = false) private double x;
    @Column(nullable = false) private double z;

    @Column(name = "width_m", nullable = false)
    private double width;

    @Column(name = "depth_m", nullable = false)
    private double depth;

    protected FloorColumn() {}

    public FloorColumn(Long floorId, double x, double z, double width, double depth) {
        this.floorId = floorId;
        this.x = x; this.z = z; this.width = width; this.depth = depth;
    }

    public Long getId() { return id; }
    public double getX() { return x; }
    public double getZ() { return z; }
    public double getWidth() { return width; }
    public double getDepth() { return depth; }
}
