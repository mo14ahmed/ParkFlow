package com.parkflow.ParkFlow.search;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriBuilder;

/**
 * Free place search from OpenStreetMap's Nominatim service.
 * Its usage policy asks for at most 1 request per second and an identifying User-Agent, so this class
 * waits between calls and remembers recent answers. For heavy use, run your own Nominatim server and
 * point PARKFLOW_GEOCODER_URL at it.
 */
@Component
public class NominatimGeocoder implements Geocoder {
    private static final Logger log = LoggerFactory.getLogger(NominatimGeocoder.class);
    private static final int CACHE_SIZE = 500;

    record Result(String lat, String lon, String display_name) {}

    private final RestClient client;
    private final String countryCodes;
    private final Map<String, List<Place>> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Place>> e) { return size() > CACHE_SIZE; }
    };
    private long lastCall;

    public NominatimGeocoder(@Value("${parkflow.geocoder.url:https://nominatim.openstreetmap.org}") String baseUrl,
                             @Value("${parkflow.geocoder.user-agent:ParkFlow/1.0 (https://www.parkflow.tech)}") String userAgent,
                             @Value("${parkflow.geocoder.country-codes:}") String countryCodes) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("User-Agent", userAgent).build();
        this.countryCodes = countryCodes.trim();
    }

    @Override
    public List<Place> search(String query, int limit) {
        List<Place> all = searchCached(query);
        return all.size() <= limit ? all : all.subList(0, limit);
    }

    /** Always asks for 5 results and caches them, so "best match" and "pick a place" share one request. */
    private List<Place> searchCached(String query) {
        String key = query.trim().toLowerCase(Locale.ROOT);
        synchronized (this) {
            List<Place> hit = cache.get(key);
            if (hit != null) return hit;
            long wait = 1000 - (System.currentTimeMillis() - lastCall);
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastCall = System.currentTimeMillis();
            List<Place> places = fetch(query.trim(), 5);
            cache.put(key, places);
            return places;
        }
    }

    private List<Place> fetch(String query, int limit) {
        try {
            Result[] results = client.get().uri(u -> {
                UriBuilder b = u.path("/search").queryParam("q", query).queryParam("format", "jsonv2")
                        .queryParam("limit", limit);
                if (!countryCodes.isEmpty()) b.queryParam("countrycodes", countryCodes);
                return b.build();
            }).retrieve().body(Result[].class);
            List<Place> places = new ArrayList<>();
            if (results != null) {
                for (Result r : results) {
                    try {
                        places.add(new Place(r.display_name(), Double.parseDouble(r.lat()), Double.parseDouble(r.lon())));
                    } catch (RuntimeException ignored) {
                        // skip a result without usable coordinates
                    }
                }
            }
            return List.copyOf(places);
        } catch (RestClientException e) {
            log.warn("Place search failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Place search is not available right now. Try again in a minute.");
        }
    }
}
