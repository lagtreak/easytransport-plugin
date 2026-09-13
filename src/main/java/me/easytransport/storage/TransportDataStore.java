package me.easytransport.storage;

import me.easytransport.EasyTransportPlugin;
import me.easytransport.model.Stop;
import me.easytransport.model.StoredLocation;
import me.easytransport.model.TransportType;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Villager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class TransportDataStore {

    private final EasyTransportPlugin plugin;

    public TransportDataStore(EasyTransportPlugin plugin) {
        this.plugin = plugin;
    }

    public void saveCashier(TransportType transport, Villager villager) {
        String path = "cashiers." + transport.key();

        plugin.getConfig().set(
                path + ".uuid",
                villager.getUniqueId().toString()
        );

        writeLocation(
                path + ".location",
                villager.getLocation()
        );

        plugin.saveConfig();
    }

    public StoredLocation getCashierLocation(TransportType transport) {
        return readLocation("cashiers." + transport.key() + ".location");
    }

    public UUID getCashierUuid(TransportType transport) {
        String raw = plugin.getConfig().getString(
                "cashiers." + transport.key() + ".uuid"
        );

        if (raw == null) {
            return null;
        }

        try {
            return UUID.fromString(raw);
        } catch (Exception e) {
            return null;
        }
    }

    public void removeCashier(TransportType transport) {
        plugin.getConfig().set(
                "cashiers." + transport.key(),
                null
        );

        plugin.saveConfig();
    }

    public void removeAllCashiers() {
        plugin.getConfig().set("cashiers", null);
        plugin.getConfig().set("cashiers", new LinkedHashMap<>());
        plugin.saveConfig();
    }

    public void saveBetween(TransportType transport, Location location) {
        writeLocation(
                "between-teleports." + transport.key(),
                location
        );

        plugin.saveConfig();
    }

    public StoredLocation getBetween(TransportType transport) {
        return readLocation("between-teleports." + transport.key());
    }

    public void removeBetween(TransportType transport) {
        plugin.getConfig().set(
                "between-teleports." + transport.key(),
                null
        );

        plugin.saveConfig();
    }

    public void saveStop(Stop stop) {
        String path = "stops."
                + stop.regionId()
                + "."
                + stop.transport().key()
                + "."
                + cityKey(stop.cityName());

        plugin.getConfig().set(
                path + ".name",
                stop.cityName()
        );

        writeLocation(
                path + ".location",
                stop.location()
        );

        plugin.saveConfig();
        plugin.refreshPl3xMap();
    }

    public void removeStop(
            String regionId,
            TransportType transport,
            String cityName
    ) {
        plugin.getConfig().set(
                "stops."
                        + regionId
                        + "."
                        + transport.key()
                        + "."
                        + cityKey(cityName),
                null
        );

        plugin.saveConfig();
        plugin.refreshPl3xMap();
    }

    public List<Stop> getStops(
            String regionId,
            TransportType transport
    ) {
        List<Stop> stops = new ArrayList<>();

        ConfigurationSection section =
                plugin.getConfig().getConfigurationSection(
                        "stops." + regionId + "." + transport.key()
                );

        if (section == null) {
            return stops;
        }

        for (String key : section.getKeys(false)) {
            String name = section.getString(key + ".name");

            StoredLocation location =
                    readLocation(
                            section.getCurrentPath()
                                    + "."
                                    + key
                                    + ".location"
                    );

            if (name != null && location != null) {
                stops.add(
                        new Stop(
                                regionId,
                                name,
                                transport,
                                location
                        )
                );
            }
        }

        stops.sort(
                Comparator.comparing(
                        Stop::cityName,
                        String.CASE_INSENSITIVE_ORDER
                )
        );

        return stops;
    }

    public Stop findStop(
            String regionId,
            TransportType transport,
            String cityName
    ) {
        for (Stop stop : getStops(regionId, transport)) {
            if (stop.cityName().equalsIgnoreCase(cityName)) {
                return stop;
            }
        }

        return null;
    }

    public void removeAllStops() {
        plugin.getConfig().set("stops", null);
        plugin.getConfig().set("stops", new LinkedHashMap<>());
        plugin.saveConfig();
        plugin.refreshPl3xMap();
    }

    public boolean hasStopsInWorld(String worldName) {
        for (String regionId : getRegionIds()) {
            for (TransportType transport : TransportType.values()) {
                for (Stop stop : getStops(regionId, transport)) {
                    if (stop.location().world().equalsIgnoreCase(worldName)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public Set<String> getRegionIds() {
        ConfigurationSection section =
                plugin.getConfig().getConfigurationSection("regions");

        return section == null
                ? Set.of()
                : section.getKeys(false);
    }

    public String getRegionName(String regionId) {
        return plugin.getConfig().getString(
                "regions." + regionId + ".name",
                regionId
        );
    }

    public String findRegionId(String input) {
        if (input == null) {
            return null;
        }

        for (String regionId : getRegionIds()) {
            if (regionId.equalsIgnoreCase(input)
                    || getRegionName(regionId).equalsIgnoreCase(input)) {
                return regionId;
            }
        }

        return null;
    }

    private String cityKey(String cityName) {
        return cityName
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-zа-я0-9]+", "_")
                .replaceAll("_+", "_");
    }

    private void writeLocation(String path, Location location) {
        plugin.getConfig().set(
                path + ".world",
                location.getWorld().getName()
        );
        plugin.getConfig().set(path + ".x", location.getX());
        plugin.getConfig().set(path + ".y", location.getY());
        plugin.getConfig().set(path + ".z", location.getZ());
        plugin.getConfig().set(
                path + ".yaw",
                (double) location.getYaw()
        );
        plugin.getConfig().set(
                path + ".pitch",
                (double) location.getPitch()
        );
    }

    private void writeLocation(
            String path,
            StoredLocation location
    ) {
        plugin.getConfig().set(
                path + ".world",
                location.world()
        );
        plugin.getConfig().set(path + ".x", location.x());
        plugin.getConfig().set(path + ".y", location.y());
        plugin.getConfig().set(path + ".z", location.z());
        plugin.getConfig().set(
                path + ".yaw",
                (double) location.yaw()
        );
        plugin.getConfig().set(
                path + ".pitch",
                (double) location.pitch()
        );
    }

    private StoredLocation readLocation(String path) {
        String world = plugin.getConfig().getString(path + ".world");

        if (world == null || !plugin.getConfig().contains(path + ".x")) {
            return null;
        }

        return new StoredLocation(
                world,
                plugin.getConfig().getDouble(path + ".x"),
                plugin.getConfig().getDouble(path + ".y"),
                plugin.getConfig().getDouble(path + ".z"),
                (float) plugin.getConfig().getDouble(path + ".yaw"),
                (float) plugin.getConfig().getDouble(path + ".pitch")
        );
    }
}
