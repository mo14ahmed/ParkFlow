package com.parkflow.ParkFlow.search;

/** A place found by the geocoder: a readable name and its coordinates. */
public record Place(String name, double latitude, double longitude) {}
