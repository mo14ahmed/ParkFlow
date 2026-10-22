package com.parkflow.ParkFlow.building;

import com.parkflow.ParkFlow.building.BuildingDtos.*;
import com.parkflow.ParkFlow.user.AccountStatus;
import com.parkflow.ParkFlow.user.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BuildingService {
    public static final int DEFAULT_RESULTS = 5;
    public static final int MAX_RESULTS = 20;

    private final BuildingRepository buildings;
    private final UserRepository users;

    public BuildingService(BuildingRepository buildings, UserRepository users) {
        this.buildings = buildings;
        this.users = users;
    }

    private boolean approved(Long providerId) {
        return users.findById(providerId).map(u -> u.getStatus() == AccountStatus.ACTIVE).orElse(false);
    }

    @Transactional(readOnly = true)
    public List<BuildingView> mine(Long providerId) {
        boolean visible = approved(providerId);
        return buildings.findByProviderIdOrderByCreatedAtDesc(providerId).stream()
                .map(b -> BuildingView.of(b, visible)).toList();
    }

    @Transactional
    public BuildingView create(Long providerId, BuildingRequest r) {
        Building b = buildings.save(new Building(providerId, r.name().trim(), r.latitude(), r.longitude()));
        return BuildingView.of(b, approved(providerId));
    }

    @Transactional
    public BuildingView update(Long providerId, Long id, BuildingRequest r) {
        Building b = own(providerId, id);
        b.update(r.name().trim(), r.latitude(), r.longitude());
        return BuildingView.of(buildings.saveAndFlush(b), approved(providerId));
    }

    @Transactional
    public void delete(Long providerId, Long id) { buildings.delete(own(providerId, id)); }

    /** A provider can only see and change their own buildings; anyone else's look like they do not exist. */
    private Building own(Long providerId, Long id) {
        return buildings.findByIdAndProviderId(id, providerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parking building not found."));
    }

    @Transactional(readOnly = true)
    public List<NearbyBuilding> nearest(double lat, double lng, Integer limit) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Latitude must be -90 to 90 and longitude -180 to 180.");
        int n = limit == null ? DEFAULT_RESULTS : Math.max(1, Math.min(MAX_RESULTS, limit));
        return buildings.findNearest(lat, lng, n).stream().map(NearbyBuilding::of).toList();
    }
}
