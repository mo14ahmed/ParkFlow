package com.parkflow.ParkFlow.search;

import com.parkflow.ParkFlow.building.BuildingDtos.NearbyBuilding;
import com.parkflow.ParkFlow.building.BuildingService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Customer search: find where you are going, then the closest parking buildings.
 * The website uses /api/places then /api/buildings/nearby so the customer can pick the right place.
 * Small screens (ESP32) can use /api/search, which does both in one call with the best match.
 */
@RestController
public class SearchController {
    private static final int MAX_PLACES = 5;
    private final Geocoder geocoder;
    private final BuildingService buildings;

    public SearchController(Geocoder geocoder, BuildingService buildings) {
        this.geocoder = geocoder;
        this.buildings = buildings;
    }

    public record SearchResult(Place place, List<NearbyBuilding> buildings) {}

    private static String query(String q) {
        String t = q == null ? "" : q.trim();
        if (t.length() < 2 || t.length() > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type 2 to 200 characters to search for a place.");
        return t;
    }

    /** Up to 5 places matching the text. */
    @GetMapping("/api/places")
    public List<Place> places(@RequestParam String q) { return geocoder.search(query(q), MAX_PLACES); }

    /** The closest parking buildings to a point (default 5). */
    @GetMapping("/api/buildings/nearby")
    public List<NearbyBuilding> nearby(@RequestParam double lat, @RequestParam double lng,
                                       @RequestParam(required = false) Integer limit) {
        return buildings.nearest(lat, lng, limit);
    }

    /** One call: best matching place plus the 5 closest buildings to it. */
    @GetMapping("/api/search")
    public SearchResult search(@RequestParam String q) {
        List<Place> found = geocoder.search(query(q), 1);
        if (found.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No place found. Try a different name or address.");
        Place p = found.get(0);
        return new SearchResult(p, buildings.nearest(p.latitude(), p.longitude(), BuildingService.DEFAULT_RESULTS));
    }
}
