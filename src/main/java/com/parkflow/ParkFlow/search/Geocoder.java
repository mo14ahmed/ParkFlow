package com.parkflow.ParkFlow.search;

import java.util.List;

/** Turns text such as "CN Tower" into places on the map. The real one uses OpenStreetMap (Nominatim). */
public interface Geocoder {
    List<Place> search(String query, int limit);
}
