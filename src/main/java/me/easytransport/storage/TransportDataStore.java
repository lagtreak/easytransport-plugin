package me.easytransport.storage;

import me.easytransport.EasyTransportPlugin;
import me.easytransport.model.Stop;
import me.easytransport.model.StoredLocation;
import me.easytransport.model.TransportType;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;

import java.util.*;

public final class TransportDataStore {
    private final EasyTransportPlugin plugin;

    public TransportDataStore(EasyTransportPlugin plugin) {
        this.plugin = plugin;
    }

    public void saveCashier(TransportType type, Villager villager) {
        String path = "cashiers." + type.key();
        plugin.getConfig().set(path + ".uuid", villager.getUniqueId().toString());
        writeLocation(path + ".location", villager.getLocation());
        plugin.saveConfig();
    }

    public StoredLocation getCashierLocation(TransportType type) {
        return readLocation("cashiers." + type.key() + ".location");
    }

    public UUID getCashierUuid(TransportType type) {
        String raw = plugin.getConfig().getString("cashiers." + type.key() + ".uuid");
        if (raw == null) return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException e) { return null; }
    }

    public void removeCashier(TransportType type) {
        plugin.getConfig().set("cashiers." + type.key(), null);
        plugin.saveConfig();
    }

    public void saveBetween(TransportType type, Location location) {
        writeLocation("between-teleports." + type.key(), location);
        plugin.saveConfig();
    }

    public StoredLocation getBetween(TransportType type) {
        return readLocation("between-teleports." + type.key());
    }

    public void removeBetween(TransportType type) {
        plugin.getConfig().set("between-teleports." + type.key(), null);
        plugin.saveConfig();
    }

    public void saveStop(Stop stop) {
        String path = "stops." + stop.regionId() + "." + stop.transport().key() + "." + cityKey(stop.cityName());
        plugin.getConfig().set(path + ".name", stop.cityName());
        writeLocation(path + ".location", stop.location());
        plugin.saveConfig();
    }

    public void removeStop(String regionId, TransportType type, String cityName) {
        plugin.getConfig().set("stops." + regionId + "." + type.key() + "." + cityKey(cityName), null);
        plugin.saveConfig();
    }

    public List<Stop> getStops(String regionId, TransportType type) {
        List<Stop> result = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("stops." + regionId + "." + type.key());
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            String name = section.getString(key + ".name");
            StoredLocation location = readLocation(section.getCurrentPath() + "." + key + ".location");
            if (name != null && location != null) result.add(new Stop(regionId, name, type, location));
        }
        result.sort(Comparator.comparing(Stop::cityName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public Stop findStop(String regionId, TransportType type, String cityName) {
        for (Stop stop : getStops(regionId, type)) {
            if (stop.cityName().equalsIgnoreCase(cityName)) return stop;
        }
        return null;
    }

    public Set<String> getRegionIds() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("regions");
        return section == null ? Set.of() : section.getKeys(false);
    }

    public String getRegionName(String id) {
        return plugin.getConfig().getString("regions." + id + ".name", id);
    }

    public String findRegionId(String input) {
        if (input == null) return null;
        for (String id : getRegionIds()) {
            if (id.equalsIgnoreCase(input) || getRegionName(id).equalsIgnoreCase(input)) return id;
        }
        return null;
    }

    private String cityKey(String city) {
        return city.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-zа-я0-9]+", "_")
                .replaceAll("_+", "_");
    }

    private void writeLocation(String path, Location location) {
        plugin.getConfig().set(path + ".world", location.getWorld().getName());
        plugin.getConfig().set(path + ".x", location.getX());
        plugin.getConfig().set(path + ".y", location.getY());
        plugin.getConfig().set(path + ".z", location.getZ());
        plugin.getConfig().set(path + ".yaw", (double) location.getYaw());
        plugin.getConfig().set(path + ".pitch", (double) location.getPitch());
    }

    private void writeLocation(String path, StoredLocation location) {
        plugin.getConfig().set(path + ".world", location.world());
        plugin.getConfig().set(path + ".x", location.x());
        plugin.getConfig().set(path + ".y", location.y());
        plugin.getConfig().set(path + ".z", location.z());
        plugin.getConfig().set(path + ".yaw", (double) location.yaw());
        plugin.getConfig().set(path + ".pitch", (double) location.pitch());
    }

    private StoredLocation readLocation(String path) {
        String world = plugin.getConfig().getString(path + ".world");
        if (world == null || !plugin.getConfig().contains(path + ".x")) return null;
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
