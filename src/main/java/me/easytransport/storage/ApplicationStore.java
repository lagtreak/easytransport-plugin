package me.easytransport.storage;

import me.easytransport.EasyTransportPlugin;
import me.easytransport.model.StoredLocation;
import me.easytransport.model.StopApplication;
import me.easytransport.model.TransportType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class ApplicationStore {
    private final EasyTransportPlugin plugin;
    private final File applicationsFile;
    private final File notificationsFile;
    private YamlConfiguration applications;
    private YamlConfiguration notifications;

    public ApplicationStore(EasyTransportPlugin plugin) {
        this.plugin = plugin;
        this.applicationsFile = new File(plugin.getDataFolder(), "applications.yml");
        this.notificationsFile = new File(plugin.getDataFolder(), "notifications.yml");
        reload();
    }

    public synchronized void reload() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        applications = YamlConfiguration.loadConfiguration(applicationsFile);
        notifications = YamlConfiguration.loadConfiguration(notificationsFile);
    }

    public synchronized List<StopApplication> getApplications() {
        List<StopApplication> result = new ArrayList<>();
        ConfigurationSection root = applications.getConfigurationSection("applications");
        if (root == null) return result;
        for (String id : root.getKeys(false)) {
            StopApplication app = readApplication(root.getConfigurationSection(id));
            if (app != null) result.add(app);
        }
        result.sort(Comparator.comparingLong(StopApplication::createdAt));
        return result;
    }

    public synchronized StopApplication getApplication(UUID id) {
        return readApplication(applications.getConfigurationSection("applications." + id));
    }

    public synchronized void saveApplication(StopApplication app) {
        String path = "applications." + app.id();
        applications.set(path + ".player-uuid", app.playerUuid().toString());
        applications.set(path + ".player-name", app.playerName());
        applications.set(path + ".transport", app.transport().key());
        applications.set(path + ".region", app.regionId());
        applications.set(path + ".city", app.cityName());
        applications.set(path + ".created-at", app.createdAt());
        writeLocation(applications, path + ".location", app.location());
        save(applications, applicationsFile);
    }

    public synchronized void deleteApplication(UUID id) {
        applications.set("applications." + id, null);
        save(applications, applicationsFile);
    }

    public synchronized boolean hasPendingDuplicate(String regionId, TransportType type, String cityName) {
        for (StopApplication app : getApplications()) {
            if (app.transport() == type
                    && app.regionId().equalsIgnoreCase(regionId)
                    && app.cityName().equalsIgnoreCase(cityName)) return true;
        }
        return false;
    }

    public synchronized void addNotification(UUID playerUuid, String message) {
        List<String> messages = getNotifications(playerUuid);
        messages.add(message);
        notifications.set("notifications." + playerUuid, messages);
        save(notifications, notificationsFile);
    }

    public synchronized List<String> getNotifications(UUID playerUuid) {
        return new ArrayList<>(notifications.getStringList("notifications." + playerUuid));
    }

    public synchronized void clearNotifications(UUID playerUuid) {
        notifications.set("notifications." + playerUuid, null);
        save(notifications, notificationsFile);
    }

    private StopApplication readApplication(ConfigurationSection s) {
        if (s == null) return null;
        try {
            UUID id = UUID.fromString(s.getName());
            UUID playerUuid = UUID.fromString(s.getString("player-uuid", ""));
            TransportType type = TransportType.fromKey(s.getString("transport"));
            StoredLocation location = readLocation(s, "location");
            if (type == null || location == null) return null;
            return new StopApplication(
                    id, playerUuid,
                    s.getString("player-name", "Unknown"),
                    type,
                    s.getString("region", ""),
                    s.getString("city", ""),
                    location,
                    s.getLong("created-at", 0L)
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private void writeLocation(YamlConfiguration cfg, String path, StoredLocation loc) {
        cfg.set(path + ".world", loc.world());
        cfg.set(path + ".x", loc.x());
        cfg.set(path + ".y", loc.y());
        cfg.set(path + ".z", loc.z());
        cfg.set(path + ".yaw", (double) loc.yaw());
        cfg.set(path + ".pitch", (double) loc.pitch());
    }

    private StoredLocation readLocation(ConfigurationSection parent, String key) {
        ConfigurationSection s = parent.getConfigurationSection(key);
        if (s == null || !s.contains("x")) return null;
        String world = s.getString("world");
        if (world == null) return null;
        return new StoredLocation(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    private void save(YamlConfiguration cfg, File file) {
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Не атрымалася захаваць " + file.getName() + ": " + e.getMessage());
        }
    }
}
