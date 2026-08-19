package me.easytransport.service;

import me.easytransport.EasyTransportPlugin;
import me.easytransport.model.Stop;
import me.easytransport.model.StoredLocation;
import me.easytransport.model.TransportType;
import me.easytransport.util.ChatMessages;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TravelService {
    private static final double PARTICLE_RADIUS = 0.45;
    private static final double PARTICLE_HEIGHT = 2.15;
    private static final int PARTICLE_POINTS = 24;

    private final EasyTransportPlugin plugin;
    private final Map<UUID, Trip> active = new ConcurrentHashMap<>();

    public TravelService(EasyTransportPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isTravelling(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    public void start(Player player, TransportType type, Stop stop) {
        UUID uuid = player.getUniqueId();
        if (active.containsKey(uuid)) {
            player.sendMessage(ChatMessages.red("Вы ўжо знаходзіцеся ў паездцы."));
            return;
        }

        StoredLocation middleStored = plugin.data().getBetween(type);
        Location middle = middleStored == null ? null : middleStored.toLocation();
        Location destination = stop.location().toLocation();
        if (middle == null || destination == null) {
            player.sendMessage(ChatMessages.red("Транспартная кропка настроена няправільна."));
            return;
        }

        Location origin = player.getLocation().clone();
        if (!routeAllowed(origin, destination, type)) {
            player.sendMessage(ChatMessages.red("Замежныя напрамкі даступныя толькі на самалёце."));
            return;
        }
        double distance = travelDistance(origin, destination);
        double speed = plugin.speed(type);
        double price = calculatePrice(origin, destination, type, distance);
        long seconds = calculateTravelSeconds(origin, destination, type, speed);

        if (!plugin.economy().has(player, price)) {
            player.sendMessage(ChatMessages.insufficientFunds("Недастаткова сродкаў. Кошт: ", plugin.economy().format(price)));
            return;
        }

        Trip trip = new Trip(uuid, origin, destination, middle, type, price, seconds, regionId(stop), stop.cityName());
        active.put(uuid, trip);
        player.closeInventory();
        player.teleport(middle);
        startTimer(player, trip);
        startParticles(player, trip);
    }

    private void startTimer(Player player, Trip trip) {
        BossBar bar = BossBar.bossBar(
                Component.text("Паездка: " + trip.type.displayName()),
                1.0f,
                BossBar.Color.BLUE,
                BossBar.Overlay.PROGRESS
        );
        player.showBossBar(bar);
        trip.bar = bar;

        long checkEvery = Math.max(1, plugin.moneyCheckSeconds());
        trip.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                finish(player, trip, false);
                return;
            }

            trip.elapsed++;
            double progress = 1.0 - ((double) trip.elapsed / trip.seconds);
            bar.progress(Math.max(0.0f, Math.min(1.0f, (float) progress)));
            long remaining = Math.max(0L, trip.seconds - trip.elapsed);
            bar.name(Component.text("Да прыбыцця: " + remaining + " с."));

            if (trip.elapsed % checkEvery == 0 && !plugin.economy().has(player, trip.price)) {
                player.sendMessage(ChatMessages.red("Паездка перарваная: сродкаў больш не хапае."));
                finish(player, trip, true);
                return;
            }

            if (trip.elapsed >= trip.seconds) {
                finishAtDestination(player, trip);
            }
        }, 20L, 20L);
    }

    private void startParticles(Player player, Trip trip) {
        if (!plugin.particlesEnabled(player)) return;
        Color color = regionColor(trip.regionId);
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.2f);
        trip.particleTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || active.get(player.getUniqueId()) != trip) return;
            Location center = player.getLocation().clone().add(0, PARTICLE_HEIGHT, 0);
            World world = center.getWorld();
            if (world == null) return;

            for (int i = 0; i < PARTICLE_POINTS; i++) {
                double angle = (Math.PI * 2.0 * i) / PARTICLE_POINTS;
                double x = Math.cos(angle) * PARTICLE_RADIUS;
                double z = Math.sin(angle) * PARTICLE_RADIUS;
                world.spawnParticle(Particle.DUST, center.getX() + x, center.getY(), center.getZ() + z,
                        1, 0, 0, 0, 0, dust);
            }
        }, 0L, 2L);
    }

    private void finishAtDestination(Player player, Trip trip) {
        player.teleport(trip.destination);
        if (!plugin.economy().withdraw(player, trip.price)) {
            player.teleport(trip.origin);
            player.sendMessage(ChatMessages.red("Аплата не прайшла. Вы вярнуліся ў зыходную кропку."));
        } else {
            player.sendMessage(Component.text("Вы прыбылі ў «" + trip.destinationName + "».", NamedTextColor.GREEN));
        }
        finish(player, trip, false);
    }

    private void finish(Player player, Trip trip, boolean returnToOrigin) {
        if (trip.task != null) trip.task.cancel();
        if (trip.particleTask != null) trip.particleTask.cancel();
        if (trip.bar != null && player.isOnline()) player.hideBossBar(trip.bar);
        if (returnToOrigin && player.isOnline()) player.teleport(trip.origin);
        active.remove(player.getUniqueId());
    }

    public void cancelAll() {
        for (Trip trip : active.values()) {
            if (trip.task != null) trip.task.cancel();
            if (trip.particleTask != null) trip.particleTask.cancel();
            Player player = plugin.getServer().getPlayer(trip.uuid);
            if (player != null && trip.bar != null) player.hideBossBar(trip.bar);
        }
        active.clear();
    }

    private boolean routeAllowed(Location origin, Location destination, TransportType type) {
        boolean originAbroad = isAbroad(origin);
        boolean destinationAbroad = isAbroad(destination);
        return (!originAbroad && !destinationAbroad) || type == TransportType.AIR;
    }

    private long calculateTravelSeconds(Location origin, Location destination, TransportType type, double speed) {
        boolean originAbroad = isAbroad(origin);
        boolean destinationAbroad = isAbroad(destination);
        if (originAbroad || destinationAbroad) {
            double abroadDistance = Math.hypot(destination.getX(), destination.getZ());
            return Math.max(30L, 30L + Math.round(abroadDistance / speed));
        }
        return Math.max(1L, Math.round(travelDistance(origin, destination) / speed));
    }

    private double calculatePrice(Location origin, Location destination, TransportType type, double distance) {
        boolean originAbroad = isAbroad(origin);
        boolean destinationAbroad = isAbroad(destination);
        if (!originAbroad && !destinationAbroad) {
            return Math.ceil(distance / plugin.distancePerPrice()) * plugin.price(type);
        }

        Location abroadPoint = destinationAbroad ? destination : origin;
        double abroadDistance = Math.hypot(abroadPoint.getX(), abroadPoint.getZ());
        return plugin.abroadBasePrice()
                + Math.ceil(abroadDistance / plugin.distancePerPrice()) * plugin.price(type);
    }

    private double travelDistance(Location origin, Location destination) {
        if (isAbroad(origin) || isAbroad(destination)) {
            Location abroadPoint = isAbroad(destination) ? destination : origin;
            return Math.hypot(abroadPoint.getX(), abroadPoint.getZ());
        }
        return horizontalDistance(origin, destination);
    }

    private boolean isAbroad(Location location) {
        return location.getWorld() != null && location.getWorld().getName().equalsIgnoreCase(plugin.abroadWorld());
    }

    private static double horizontalDistance(Location a, Location b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    private static String regionId(Stop stop) {
        return stop.regionId();
    }

    private static Color regionColor(String regionId) {
        return switch (regionId.toLowerCase()) {
            case "minsk" -> Color.RED;
            case "gomel" -> Color.FUCHSIA;
            case "brest" -> Color.AQUA;
            case "vitebsk" -> Color.GREEN;
            case "mogilev" -> Color.YELLOW;
            case "grodno" -> Color.ORANGE;
            case "abroad" -> Color.WHITE;
            default -> Color.WHITE;
        };
    }

    private static final class Trip {
        private final UUID uuid;
        private final Location origin;
        private final Location destination;
        private final Location middle;
        private final TransportType type;
        private final double price;
        private final long seconds;
        private final String regionId;
        private final String destinationName;
        private long elapsed;
        private BossBar bar;
        private BukkitTask task;
        private BukkitTask particleTask;

        private Trip(UUID uuid, Location origin, Location destination, Location middle,
                     TransportType type, double price, long seconds, String regionId, String destinationName) {
            this.uuid = uuid;
            this.origin = origin;
            this.destination = destination;
            this.middle = middle;
            this.type = type;
            this.price = price;
            this.seconds = seconds;
            this.regionId = regionId;
            this.destinationName = destinationName;
        }
    }
}
