package com.parkflow.ParkFlow.building;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BuildingRepository extends JpaRepository<Building, Long> {
    List<Building> findByProviderIdOrderByCreatedAtDesc(Long providerId);
    Optional<Building> findByIdAndProviderId(Long id, Long providerId);
    long countByProviderId(Long providerId);

    /** What the nearby query returns for each building. */
    interface Nearby {
        Long getId();
        String getName();
        double getLatitude();
        double getLongitude();
        String getCompany();
        double getDistanceKm();
    }

    /**
     * Closest buildings of approved providers, by straight-line (great-circle) distance using the Haversine formula.
     * Plain SQL that runs on both PostgreSQL and H2, so no database extension is needed.
     */
    @Query(nativeQuery = true, value = """
            SELECT id, name, latitude, longitude, company, distance_km AS distanceKm FROM (
              SELECT b.id, b.name, b.latitude, b.longitude, u.company_name AS company,
                     2 * 6371.0 * ASIN(LEAST(1.0, SQRT(
                         POWER(SIN(RADIANS(b.latitude - :lat) / 2), 2)
                       + COS(RADIANS(:lat)) * COS(RADIANS(b.latitude))
                       * POWER(SIN(RADIANS(b.longitude - :lng) / 2), 2)))) AS distance_km
              FROM buildings b JOIN users u ON u.id = b.provider_id
              WHERE u.role = 'PROVIDER' AND u.status = 'ACTIVE'
            ) d
            ORDER BY distance_km, id
            LIMIT :limit
            """)
    List<Nearby> findNearest(@Param("lat") double lat, @Param("lng") double lng, @Param("limit") int limit);
}
