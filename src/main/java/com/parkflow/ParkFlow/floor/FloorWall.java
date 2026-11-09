package com.parkflow.ParkFlow.floor;

import jakarta.persistence.*;

/** A straight wall from (x1, z1) to (x2, z2), in metres. */
@Entity
@Table(name = "floor_walls")
public class FloorWall {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "floor_id", nullable = false)
    private Long floorId;

    @Column(nullable = false) private double x1;
    @Column(nullable = false) private double z1;
    @Column(nullable = false) private double x2;
    @Column(nullable = false) private double z2;

    @Column(name = "thickness_m", nullable = false)
    private double thickness;

    protected FloorWall() {}

    public FloorWall(Long floorId, double x1, double z1, double x2, double z2, double thickness) {
        this.floorId = floorId;
        this.x1 = x1; this.z1 = z1; this.x2 = x2; this.z2 = z2;
        this.thickness = thickness;
    }

    public Long getId() { return id; }
    public double getX1() { return x1; }
    public double getZ1() { return z1; }
    public double getX2() { return x2; }
    public double getZ2() { return z2; }
    public double getThickness() { return thickness; }
}
