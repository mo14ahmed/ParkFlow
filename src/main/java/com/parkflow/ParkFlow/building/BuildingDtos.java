package com.parkflow.ParkFlow.building;

import jakarta.validation.constraints.*;
import java.time.Instant;

public final class BuildingDtos {
    private BuildingDtos() {}

    public record BuildingRequest(
            @NotBlank(message = "Enter a name for the parking building.")
            @Size(max = 100, message = "Name is too long (100 characters at most).")
            String name,
            @NotNull(message = "Drop a pin on the map.")
            @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90.")
            @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90.")
            Double latitude,
            @NotNull(message = "Drop a pin on the map.")
            @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180.")
            @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180.")
            Double longitude) {}

    /** What a provider sees for their own building. visible = shown to customers (the provider is approved). */
    public record BuildingView(Long id, String name, double latitude, double longitude,
                               Instant createdAt, Instant updatedAt, boolean visible) {
        static BuildingView of(Building b, boolean visible) {
            return new BuildingView(b.getId(), b.getName(), b.getLatitude(), b.getLongitude(),
                    b.getCreatedAt(), b.getUpdatedAt(), visible);
        }
    }

    /** What a customer sees in search results. */
    public record NearbyBuilding(Long id, String name, double latitude, double longitude, String company, double distanceKm) {
        static NearbyBuilding of(BuildingRepository.Nearby n) {
            return new NearbyBuilding(n.getId(), n.getName(), n.getLatitude(), n.getLongitude(), n.getCompany(),
                    Math.round(n.getDistanceKm() * 100.0) / 100.0);
        }
    }
}
