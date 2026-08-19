package me.easytransport.model;

import java.util.UUID;

public record StopApplication(
        UUID id,
        UUID playerUuid,
        String playerName,
        TransportType transport,
        String regionId,
        String cityName,
        StoredLocation location,
        long createdAt
) {}
