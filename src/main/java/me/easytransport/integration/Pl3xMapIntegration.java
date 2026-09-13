package me.easytransport.integration;

import me.easytransport.EasyTransportPlugin;
import me.easytransport.model.Stop;
import me.easytransport.model.StopApplication;
import me.easytransport.model.TransportType;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.image.IconImage;
import net.pl3x.map.core.markers.layer.Layer;
import net.pl3x.map.core.markers.layer.SimpleLayer;
import net.pl3x.map.core.markers.marker.Icon;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.markers.option.Tooltip;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Locale;

/**
 * Pl3xMap integration for EasyTransport stops and pending stop applications.
 */
public final class Pl3xMapIntegration {
    private static final String REQUEST_BUS_ICON = "easytransport_request_bus";
    private static final String REQUEST_TRAIN_ICON = "easytransport_request_train";
    private static final String REQUEST_AIR_ICON = "easytransport_request_air";

    private static final String BUS_LAYER = "easytransport:bus";
    private static final String TRAIN_LAYER = "easytransport:train";
    private static final String AIR_LAYER = "easytransport:air";

    private static final String REQUEST_BUS_LAYER = "easytransport:requests_bus";
    private static final String REQUEST_TRAIN_LAYER = "easytransport:requests_train";
    private static final String REQUEST_AIR_LAYER = "easytransport:requests_air";

    private final EasyTransportPlugin plugin;
    private BukkitTask refreshTask;

    public Pl3xMapIntegration(EasyTransportPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("pl3xmap.enabled", true)) {
            plugin.getLogger().info("Pl3xMap integration disabled by configuration.");
            return;
        }

        if (!isAvailable()) {
            plugin.getLogger().info("Pl3xMap not found. Map integration disabled.");
            return;
        }

        registerIcons();
        refresh();

        long refreshTicks = Math.max(20L, plugin.getConfig().getLong("pl3xmap.refresh-ticks", 40L));
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, refreshTicks, refreshTicks);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    public void refresh() {
        if (!isAvailable()) {
            return;
        }

        try {
            for (net.pl3x.map.core.world.World mapWorld : Pl3xMap.api().getWorldRegistry().values()) {
                refreshWorld(mapWorld);
                mapWorld.getMarkerTask().parse();
                mapWorld.getLiveDataTask().parse();
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Failed to refresh Pl3xMap markers: " + throwable.getMessage());
        }
    }

    private void refreshWorld(net.pl3x.map.core.world.World mapWorld) {
        SimpleLayer busLayer = getOrCreateLayer(BUS_LAYER, "Автобусные остановки", 50, mapWorld);
        SimpleLayer trainLayer = getOrCreateLayer(TRAIN_LAYER, "Железнодорожные станции", 51, mapWorld);
        SimpleLayer airLayer = getOrCreateLayer(AIR_LAYER, "Аэропорты", 52, mapWorld);

        SimpleLayer requestBusLayer = getOrCreateLayer(REQUEST_BUS_LAYER, "Заявки на автобусные остановки", 60, mapWorld);
        SimpleLayer requestTrainLayer = getOrCreateLayer(REQUEST_TRAIN_LAYER, "Заявки на железнодорожные станции", 61, mapWorld);
        SimpleLayer requestAirLayer = getOrCreateLayer(REQUEST_AIR_LAYER, "Заявки на аэропорты", 62, mapWorld);

        busLayer.clearMarkers();
        trainLayer.clearMarkers();
        airLayer.clearMarkers();
        requestBusLayer.clearMarkers();
        requestTrainLayer.clearMarkers();
        requestAirLayer.clearMarkers();

        for (String regionId : plugin.data().getRegionIds()) {
            addStops(mapWorld, regionId, TransportType.BUS, busLayer);
            addStops(mapWorld, regionId, TransportType.TRAIN, trainLayer);
            addStops(mapWorld, regionId, TransportType.AIR, airLayer);
        }

        for (StopApplication application : plugin.applications().getApplications()) {
            addApplication(mapWorld, application,
                    application.transport() == TransportType.BUS ? requestBusLayer
                            : application.transport() == TransportType.TRAIN ? requestTrainLayer
                            : requestAirLayer,
                    application.transport() == TransportType.BUS ? REQUEST_BUS_ICON
                            : application.transport() == TransportType.TRAIN ? REQUEST_TRAIN_ICON
                            : REQUEST_AIR_ICON);
        }
    }

    private void addStops(
            net.pl3x.map.core.world.World mapWorld,
            String regionId,
            TransportType type,
            SimpleLayer layer
    ) {
        for (Stop stop : plugin.data().getStops(regionId, type)) {
            if (!stop.location().world().equalsIgnoreCase(mapWorld.getName())) {
                continue;
            }

            String markerKey = "stop:" + regionId + ":" + type.key() + ":" + normalizeKey(stop.cityName());
            String regionName = plugin.data().getRegionName(regionId);

            String iconKey = approvedIconKey(type, regionId);

            Icon marker = Icon.icon(
                    markerKey,
                    stop.location().x(),
                    stop.location().z(),
                    iconKey,
                    iconSize()
            );

            marker.setOptions(new Options().setTooltip(new Tooltip()
                    .setContent(stopTooltip(stop.cityName(), regionName))
                    .setSticky(true)));

            layer.addMarker(marker);
        }
    }

    private void addApplication(
            net.pl3x.map.core.world.World mapWorld,
            StopApplication application,
            SimpleLayer layer,
            String iconKey
    ) {
        if (!application.location().world().equalsIgnoreCase(mapWorld.getName())) {
            return;
        }

        String markerKey = "request:" + application.id();
        String regionName = plugin.data().getRegionName(application.regionId());

        Icon marker = Icon.icon(
                markerKey,
                application.location().x(),
                application.location().z(),
                iconKey,
                iconSize()
        );

        marker.setOptions(new Options().setTooltip(new Tooltip()
                .setContent(requestTooltip(application.cityName(), regionName, application.playerName()))
                .setSticky(true)));

        layer.addMarker(marker);
    }

    private SimpleLayer getOrCreateLayer(
            String key,
            String label,
            int priority,
            net.pl3x.map.core.world.World world
    ) {
        Layer existing = world.getLayerRegistry().get(key);
        if (existing instanceof SimpleLayer simpleLayer) {
            return simpleLayer;
        }

        SimpleLayer layer = new SimpleLayer(key, () -> label);
        layer.setShowControls(true)
                .setDefaultHidden(false)
                .setPriority(priority)
                .setUpdateInterval(1)
                .setLiveUpdate(true);

        world.getLayerRegistry().register(key, layer);
        return layer;
    }

    private void registerIcons() {
        String[] regions = {
                "minsk",
                "gomel",
                "brest",
                "grodno",
                "mogilev",
                "vitebsk",
                "abroad"
        };

        for (TransportType type : TransportType.values()) {
            for (String region : regions) {
                registerIcon(
                        approvedIconKey(type, region),
                        ICON_ROOT + type.key() + "/" + region + ".png"
                );
            }
        }

        registerIcon(REQUEST_BUS_ICON, ICON_ROOT + "requests/bus.png");
        registerIcon(REQUEST_TRAIN_ICON, ICON_ROOT + "requests/train.png");
        registerIcon(REQUEST_AIR_ICON, ICON_ROOT + "requests/air.png");
    }

    private String approvedIconKey(TransportType type, String regionId) {
        return "easytransport:" + type.key() + ":" + normalizeRegion(regionId);
    }

    private String normalizeRegion(String regionId) {
        if (regionId == null || regionId.isBlank()) {
            return "minsk";
        }
        String region = regionId.toLowerCase(Locale.ROOT).trim();
        return switch (region) {
            case "minsk", "gomel", "brest", "grodno", "mogilev", "vitebsk", "abroad" -> region;
            default -> "minsk";
        };
    }

    private void registerIcon(String key, String resourcePath) {
        try (InputStream inputStream = plugin.getResource(resourcePath)) {
            if (inputStream == null) {
                plugin.getLogger().warning("Missing Pl3xMap icon resource: " + resourcePath);
                return;
            }

            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                plugin.getLogger().warning("Invalid Pl3xMap icon image: " + resourcePath);
                return;
            }

            Pl3xMap.api().getIconRegistry().register(
                    key,
                    new IconImage(key, image, "png")
            );
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to register Pl3xMap icon " + resourcePath + ": " + exception.getMessage());
        }
    }

    private int iconSize() {
        return Math.max(8, plugin.getConfig().getInt("pl3xmap.icon-size", 32));
    }

    private String stopTooltip(String city, String region) {
        return "<div><strong>Остановка</strong><br>Город: " + escapeHtml(city)
                + "<br>Область: " + escapeHtml(region) + "</div>";
    }

    private String requestTooltip(String city, String region, String playerName) {
        return "<div><strong>Заявка на остановку</strong><br>Город: " + escapeHtml(city)
                + "<br>Область: " + escapeHtml(region)
                + "<br>Подал: " + escapeHtml(playerName) + "</div>";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String normalizeKey(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-zа-я0-9]+", "_");
    }

    private boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("Pl3xMap") != null
                && Bukkit.getPluginManager().isPluginEnabled("Pl3xMap")
                && Pl3xMap.api() != null
                && Pl3xMap.api().isEnabled();
    }
}
