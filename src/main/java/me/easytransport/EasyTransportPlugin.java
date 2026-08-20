package me.easytransport;

import me.easytransport.command.EtrCommand;
import me.easytransport.listener.CashierListener;
import me.easytransport.listener.MenuListener;
import me.easytransport.menu.TransportMenu;
import me.easytransport.menu.ApplicationMenu;
import me.easytransport.service.ApplicationService;
import me.easytransport.service.DiscordWebhookService;
import me.easytransport.storage.ApplicationStore;
import me.easytransport.model.TransportType;
import me.easytransport.service.EconomyService;
import me.easytransport.service.TravelService;
import me.easytransport.storage.TransportDataStore;
import net.milkbowl.vault.economy.Economy;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class EasyTransportPlugin extends JavaPlugin {
    private NamespacedKey cashierKey;
    private NamespacedKey menuKey;
    private TransportDataStore data;
    private EconomyService economy;
    private TravelService travel;
    private TransportMenu menu;
    private ApplicationStore applications;
    private ApplicationService applicationsService;
    private ApplicationMenu applicationMenu;
    private DiscordWebhookService discord;
    private final Map<UUID, TransportType> menuTypeByPlayer = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        initializeWorldSettings();
        cashierKey = new NamespacedKey(this, "cashier_type");
        menuKey = new NamespacedKey(this, "menu_action");
        data = new TransportDataStore(this);
        applications = new ApplicationStore(this);
        updateBelarusianRegionNames();

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("Пастаўшчык Vault Economy не знойдзены. EasyTransport адключаны.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        economy = new EconomyService(rsp.getProvider());
        travel = new TravelService(this);
        menu = new TransportMenu(this);
        applicationsService = new ApplicationService(this);
        applicationMenu = new ApplicationMenu(this);
        discord = new DiscordWebhookService(this);
        Bukkit.getScheduler().runTaskLater(this, () -> discord.syncApplications(), 20L);

        EtrCommand command = new EtrCommand(this);
        getCommand("etr").setExecutor(command);
        getCommand("etr").setTabCompleter(command);

        Bukkit.getPluginManager().registerEvents(new CashierListener(this, menu), this);
        Bukkit.getPluginManager().registerEvents(new MenuListener(this, menu), this);
        Bukkit.getPluginManager().registerEvents(new me.easytransport.listener.ApplicationListener(this, applicationMenu), this);
        Bukkit.getPluginManager().registerEvents(new me.easytransport.listener.AdminChatListener(command), this);
        getLogger().info("EasyTransport enabled.");
    }

    @Override
    public void onDisable() {
        if (travel != null) travel.cancelAll();
    }

    private void updateBelarusianRegionNames() {
        Map<String, String> names = Map.of(
                "brest", "Брэсцкая",
                "vitebsk", "Віцебская",
                "gomel", "Гомельская",
                "grodno", "Гродзенская",
                "mogilev", "Магілёўская",
                "minsk", "Мінская",
                "abroad", "Замежжа"
        );
        boolean changed = false;
        for (Map.Entry<String, String> entry : names.entrySet()) {
            String path = "regions." + entry.getKey() + ".name";
            if (!entry.getValue().equals(getConfig().getString(path))) {
                getConfig().set(path, entry.getValue());
                changed = true;
            }
        }
        if (changed) saveConfig();
    }

    public NamespacedKey cashierKey() { return cashierKey; }
    public NamespacedKey menuKey() { return menuKey; }
    public TransportDataStore data() { return data; }
    public ApplicationStore applications() { return applications; }
    public ApplicationService applicationsService() { return applicationsService; }
    public ApplicationMenu getApplicationMenu() { return applicationMenu; }
    public DiscordWebhookService discord() { return discord; }
    public EconomyService economy() { return economy; }
    public TravelService travel() { return travel; }
    public double distancePerPrice() { return getConfig().getDouble("settings.distance-per-price", 200.0); }
    public int moneyCheckSeconds() { return Math.max(1, getConfig().getInt("settings.money-check-seconds", 5)); }

    public double speed(TransportType type) {
        return getConfig().getDouble("transport." + type.key() + ".speed", type.defaultSpeed());
    }

    public double price(TransportType type) {
        return getConfig().getDouble("transport." + type.key() + ".price-per-distance", type.defaultPrice());
    }

    public void setSpeed(TransportType type, double value) {
        getConfig().set("transport." + type.key() + ".speed", value);
        saveConfig();
    }

    public void setPrice(TransportType type, double value) {
        getConfig().set("transport." + type.key() + ".price-per-distance", value);
        saveConfig();
    }

    public double abroadBasePrice() {
        return worldBasePrice(abroadWorld());
    }

    public void setAbroadBasePrice(double value) {
        getConfig().set("settings.abroad-base-price", value);
        getConfig().set("worlds." + abroadWorld() + ".base-price", value);
        saveConfig();
    }

    public boolean particlesEnabled(Player player) {
        return getConfig().getBoolean("player-settings." + player.getUniqueId() + ".particles-enabled", true);
    }

    public boolean toggleParticles(Player player) {
        boolean enabled = !particlesEnabled(player);
        getConfig().set("player-settings." + player.getUniqueId() + ".particles-enabled", enabled);
        saveConfig();
        return enabled;
    }

    public String abroadWorld() {
        return getConfig().getString("settings.world-abroad", "abroad");
    }

    public boolean isManagedWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) return false;
        String actual = findManagedWorldKey(worldName);
        return actual != null;
    }

    public String managedWorldKey(String worldName) {
        return findManagedWorldKey(worldName);
    }

    private String findManagedWorldKey(String worldName) {
        var section = getConfig().getConfigurationSection("worlds");
        if (section == null || worldName == null) return null;
        for (String key : section.getKeys(false)) {
            if (key.equalsIgnoreCase(worldName)) return key;
        }
        return null;
    }

    public String worldDisplayName(String worldName) {
        String key = managedWorldKey(worldName);
        String fallback = worldName == null ? "" : worldName;
        return getConfig().getString("worlds." + (key == null ? fallback : key) + ".display-name", fallback);
    }

    public boolean worldAllowsTransport(String worldName, TransportType type) {
        String key = managedWorldKey(worldName);
        return key != null && getConfig().getBoolean("worlds." + key + ".transports." + type.key(), false);
    }

    public double worldBasePrice(String worldName) {
        String key = managedWorldKey(worldName);
        return getConfig().getDouble("worlds." + (key == null ? worldName : key) + ".base-price", 500.0);
    }

    public long worldBaseTime(String worldName) {
        String key = managedWorldKey(worldName);
        return Math.max(0L, getConfig().getLong("worlds." + (key == null ? worldName : key) + ".base-time", 30L));
    }

    public boolean addManagedWorld(String worldName) {
        if (isManagedWorld(worldName)) return false;
        getConfig().set("worlds." + worldName + ".display-name", worldName);
        getConfig().set("worlds." + worldName + ".base-price", 500.0);
        getConfig().set("worlds." + worldName + ".base-time", 30L);
        for (TransportType type : TransportType.values()) {
            getConfig().set("worlds." + worldName + ".transports." + type.key(), true);
        }
        saveConfig();
        return true;
    }

    public void removeManagedWorld(String worldName) {
        String key = managedWorldKey(worldName);
        if (key == null) return;
        getConfig().set("worlds." + key, null);
        saveConfig();
    }

    public void setWorldDisplayName(String worldName, String displayName) {
        String key = managedWorldKey(worldName);
        if (key == null) return;
        getConfig().set("worlds." + key + ".display-name", displayName);
        saveConfig();
    }

    public void setWorldTransport(String worldName, TransportType type, boolean enabled) {
        String key = managedWorldKey(worldName);
        if (key == null) return;
        getConfig().set("worlds." + key + ".transports." + type.key(), enabled);
        saveConfig();
    }

    public void setWorldBasePrice(String worldName, double value) {
        String key = managedWorldKey(worldName);
        if (key == null) return;
        getConfig().set("worlds." + key + ".base-price", value);
        saveConfig();
    }

    public void setWorldBaseTime(String worldName, long value) {
        String key = managedWorldKey(worldName);
        if (key == null) return;
        getConfig().set("worlds." + key + ".base-time", value);
        saveConfig();
    }

    private void initializeWorldSettings() {
        boolean changed = false;
        String belarusWorld = getConfig().getString("settings.world-belarus", "world");
        String abroad = abroadWorld();

        if (!isManagedWorld(belarusWorld)) {
            String key = ensureWorldKey(belarusWorld);
            getConfig().set("worlds." + key + ".display-name", "Беларускі край");
            getConfig().set("worlds." + key + ".base-price", 0.0);
            getConfig().set("worlds." + key + ".base-time", 30L);
            for (TransportType type : TransportType.values()) {
                getConfig().set("worlds." + key + ".transports." + type.key(), true);
            }
            changed = true;
        }

        if (!isManagedWorld(abroad)) {
            String key = ensureWorldKey(abroad);
            getConfig().set("worlds." + key + ".display-name", "Замежжа");
            getConfig().set("worlds." + key + ".base-price", getConfig().getDouble("settings.abroad-base-price", 500.0));
            getConfig().set("worlds." + key + ".base-time", 30L);
            getConfig().set("worlds." + key + ".transports.bus", false);
            getConfig().set("worlds." + key + ".transports.train", false);
            getConfig().set("worlds." + key + ".transports.air", true);
            changed = true;
        }

        // Normalize legacy built-in world entries if they exist under a different case.
        ensureBuiltInWorldDefaults(belarusWorld, "Беларускі край", 0.0, 30L, true, true, true);
        ensureBuiltInWorldDefaults(abroad, "Замежжа", getConfig().getDouble("settings.abroad-base-price", 500.0), 30L, false, false, true);

        if (changed) saveConfig();
    }

    private String ensureWorldKey(String worldName) {
        var worlds = getConfig().getConfigurationSection("worlds");
        if (worlds != null) {
            for (String key : worlds.getKeys(false)) {
                if (key.equalsIgnoreCase(worldName)) return key;
            }
        }
        return worldName;
    }

    private void ensureBuiltInWorldDefaults(String worldName, String displayName, double basePrice, long baseTime,
                                             boolean bus, boolean train, boolean air) {
        String key = ensureWorldKey(worldName);
        getConfig().set("worlds." + key + ".display-name", displayName);
        getConfig().set("worlds." + key + ".base-price", getConfig().getDouble("worlds." + key + ".base-price", basePrice));
        getConfig().set("worlds." + key + ".base-time", getConfig().getLong("worlds." + key + ".base-time", baseTime));
        getConfig().set("worlds." + key + ".transports.bus", getConfig().getBoolean("worlds." + key + ".transports.bus", bus));
        getConfig().set("worlds." + key + ".transports.train", getConfig().getBoolean("worlds." + key + ".transports.train", train));
        getConfig().set("worlds." + key + ".transports.air", getConfig().getBoolean("worlds." + key + ".transports.air", air));
    }



    public boolean bindRoleWorld(String role, String targetWorld) {
        String settingPath;
        String roleDisplay;
        boolean isAbroadRole;
        if (role.equalsIgnoreCase("belarus")) {
            settingPath = "settings.world-belarus";
            roleDisplay = "Беларускі край";
            isAbroadRole = false;
        } else if (role.equalsIgnoreCase("abroad")) {
            settingPath = "settings.world-abroad";
            roleDisplay = "Замежжа";
            isAbroadRole = true;
        } else {
            return false;
        }

        if (getServer().getWorld(targetWorld) == null) return false;

        String currentWorld = getConfig().getString(settingPath, isAbroadRole ? "abroad" : "world");
        String otherRolePath = isAbroadRole ? "settings.world-belarus" : "settings.world-abroad";
        String otherRoleWorld = getConfig().getString(otherRolePath, isAbroadRole ? "world" : "abroad");
        if (!currentWorld.equalsIgnoreCase(targetWorld) && otherRoleWorld.equalsIgnoreCase(targetWorld)) return false;
        if (currentWorld.equalsIgnoreCase(targetWorld)) return true;

        String oldKey = managedWorldKey(currentWorld);
        String targetKey = managedWorldKey(targetWorld);
        if (oldKey == null) oldKey = currentWorld;

        double oldBasePrice = getConfig().getDouble("worlds." + oldKey + ".base-price", isAbroadRole ? 500.0 : 0.0);
        long oldBaseTime = getConfig().getLong("worlds." + oldKey + ".base-time", 30L);
        boolean oldBus = getConfig().getBoolean("worlds." + oldKey + ".transports.bus", !isAbroadRole);
        boolean oldTrain = getConfig().getBoolean("worlds." + oldKey + ".transports.train", !isAbroadRole);
        boolean oldAir = getConfig().getBoolean("worlds." + oldKey + ".transports.air", true);

        if (targetKey == null) targetKey = targetWorld;
        getConfig().set("worlds." + targetKey + ".display-name", roleDisplay);
        getConfig().set("worlds." + targetKey + ".base-price", oldBasePrice);
        getConfig().set("worlds." + targetKey + ".base-time", oldBaseTime);
        getConfig().set("worlds." + targetKey + ".transports.bus", oldBus);
        getConfig().set("worlds." + targetKey + ".transports.train", oldTrain);
        getConfig().set("worlds." + targetKey + ".transports.air", oldAir);

        getConfig().set(settingPath, targetWorld);
        // The previous physical role-world is no longer an EasyTransport-managed world.
        if (!oldKey.equalsIgnoreCase(targetKey)) {
            getConfig().set("worlds." + oldKey, null);
        }
        saveConfig();
        return true;
    }

    public void rebuildCashierIndex() {
        menuTypeByPlayer.clear();
    }

    public void rememberPlayerMenu(Player player, TransportType type) {
        menuTypeByPlayer.put(player.getUniqueId(), type);
    }

    public TransportType cashierTypeByPlayerMenu(Player player) {
        return menuTypeByPlayer.get(player.getUniqueId());
    }

    public boolean isWorld(String worldName) {
        return worldName != null && worldName.equalsIgnoreCase(getConfig().getString("settings.world-belarus", "world"));
    }

    public boolean isAbroad(String worldName) {
        return worldName != null && worldName.equalsIgnoreCase(abroadWorld());
    }

    public boolean isRegionAvailableForTransport(TransportType type, String regionId) {
        if ("abroad".equalsIgnoreCase(regionId)) return type == TransportType.AIR;
        return true;
    }
}
