package me.easytransport.model;

public record Stop(String regionId, String cityName, TransportType transport, StoredLocation location) {}
