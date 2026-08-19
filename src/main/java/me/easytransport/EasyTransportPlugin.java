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
        return getConfig().getDouble("settings.abroad-base-price", 500.0);
    }

    public void setAbroadBasePrice(double value) {
        getConfig().set("settings.abroad-base-price", value);
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
        return worldName != null && worldName.equalsIgnoreCase("world");
    }

    public boolean isAbroad(String worldName) {
        return worldName != null && worldName.equalsIgnoreCase(abroadWorld());
    }

    public boolean isRegionAvailableForTransport(TransportType type, String regionId) {
        if ("abroad".equalsIgnoreCase(regionId)) return type == TransportType.AIR;
        return true;
    }
}
