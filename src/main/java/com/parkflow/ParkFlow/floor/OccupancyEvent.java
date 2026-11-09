package com.parkflow.ParkFlow.floor;

import jakarta.persistence.*;
import java.time.Instant;

/** One change of a space's state, kept as history. */
@Entity
@Table(name = "occupancy_events")
public class OccupancyEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    @Column(name = "camera_id")
    private Long cameraId;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", nullable = false, length = 10)
    private Space.Status oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 10)
    private Space.Status newStatus;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected OccupancyEvent() {}

    public OccupancyEvent(Long spaceId, Long cameraId, Space.Status oldStatus, Space.Status newStatus, Instant observedAt) {
        this.spaceId = spaceId;
        this.cameraId = cameraId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.observedAt = observedAt;
        this.receivedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getSpaceId() { return spaceId; }
    public Space.Status getNewStatus() { return newStatus; }
}
