package me.easytransport.integration;

import me.easytransport.EasyTransportPlugin;
import me.easytransport.model.Stop;
import me.easytransport.model.StopApplication;
import me.easytransport.model.TransportType;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.image.IconImage;
import net.pl3x.map.core.markers.layer.SimpleLayer;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.option.Options;
import org.bukkit.Bukkit;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Pl3xMapIntegration {
    private static final String MAP_PLUGIN_NAME = "Pl3xMap";
    private static final String ICON_ROOT = "pl3xmap/icons/";
    private static final String LAYER_ROOT = "easytransport";

    private final EasyTransportPlugin plugin;
    private final Map<String, Map<TransportType, SimpleLayer>> stopLayersByWorld = new HashMap<>();
    private final Map<String, Map<TransportType, SimpleLayer>> requestLayersByWorld = new HashMap<>();
    private boolean enabled;
    private boolean debugLogs;

    public Pl3xMapIntegration(EasyTransportPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        debugLogs = plugin.getConfig().getBoolean("pl3xmap.debug", true);
        debug("Starting Pl3xMap integration. debug=" + debugLogs);

        if (!Bukkit.getPluginManager().isPluginEnabled(MAP_PLUGIN_NAME)) {
            plugin.getLogger().info("Pl3xMap not found or disabled; map integration skipped.");
            return;
        }

        try {
            registerIcons();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!Bukkit.getPluginManager().isPluginEnabled(MAP_PLUGIN_NAME)) {
                    return;
                }

                createLayers();
                enabled = true;
                refresh();
                plugin.getLogger().info("Pl3xMap integration enabled.");
            });
        } catch (Throwable t) {
            plugin.getLogger().severe("Failed to initialize Pl3xMap integration: " + t.getMessage());
            if (debugLogs) t.printStackTrace();
        }
    }

    public void stop() {
        stopLayersByWorld.clear();
        requestLayersByWorld.clear();
        enabled = false;
    }

    public void refresh() {
        if (!enabled) {
            debug("Refresh skipped: integration is not enabled yet.");
            return;
        }

        for (Map<TransportType, SimpleLayer> layers : stopLayersByWorld.values()) {
            for (SimpleLayer layer : layers.values()) {
                layer.clearMarkers();
            }
        }
        for (Map<TransportType, SimpleLayer> layers : requestLayersByWorld.values()) {
            for (SimpleLayer layer : layers.values()) {
                layer.clearMarkers();
            }
        }

        for (net.pl3x.map.core.world.World mapWorld : Pl3xMap.api().getWorldRegistry().values()) {
            String worldName = mapWorld.getName();
            if (worldName == null || worldName.isBlank()) {
                continue;
            }

            Map<TransportType, SimpleLayer> stopLayers = stopLayersByWorld.get(worldName.toLowerCase(Locale.ROOT));
            Map<TransportType, SimpleLayer> requestLayers = requestLayersByWorld.get(worldName.toLowerCase(Locale.ROOT));
            if (stopLayers == null || requestLayers == null) {
                debug("No EasyTransport layers registered for map world: " + worldName);
                continue;
            }

            int totalStops = 0;
            int totalRequests = 0;

            for (TransportType type : TransportType.values()) {
                SimpleLayer stopLayer = stopLayers.get(type);
                SimpleLayer requestLayer = requestLayers.get(type);

                for (String regionId : getRegionIds()) {
                    for (Stop stop : plugin.data().getStops(regionId, type)) {
                        if (!sameWorld(stop.location().world(), worldName)) {
                            continue;
                        }
                        addStopMarker(stopLayer, stop);
                        totalStops++;
                    }
                }

                for (StopApplication app : plugin.applications().getApplications()) {
                    if (app.transport() != type || !sameWorld(app.location().world(), worldName)) {
                        continue;
                    }
                    addRequestMarker(requestLayer, app);
                    totalRequests++;
                }
            }

            try {
                mapWorld.getMarkerTask().parse();
                mapWorld.getLiveDataTask().parse();
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to force Pl3xMap marker update for world '" + worldName + "': " + t.getMessage());
                if (debugLogs) t.printStackTrace();
            }

            debug("World '" + worldName + "': stops=" + totalStops + ", requests=" + totalRequests);
        }
    }

    private void createLayers() {
        stopLayersByWorld.clear();
        requestLayersByWorld.clear();

        for (net.pl3x.map.core.world.World world : Pl3xMap.api().getWorldRegistry().values()) {
            String worldKey = world.getName().toLowerCase(Locale.ROOT);
            Map<TransportType, SimpleLayer> stopLayers = new EnumMap<>(TransportType.class);
            Map<TransportType, SimpleLayer> requestLayers = new EnumMap<>(TransportType.class);

            for (TransportType type : TransportType.values()) {
                SimpleLayer stopLayer = new SimpleLayer(
                        LAYER_ROOT + ".stops." + type.key(),
                        () -> "Прыпынкі: " + belarusianTransportName(type)
                );
                stopLayer.setUpdateInterval(1);
                stopLayer.setLiveUpdate(true);

                SimpleLayer requestLayer = new SimpleLayer(
                        LAYER_ROOT + ".requests." + type.key(),
                        () -> "Заяўкі: " + belarusianTransportName(type)
                );
                requestLayer.setUpdateInterval(1);
                requestLayer.setLiveUpdate(true);

                world.getLayerRegistry().register(stopLayer.getKey(), stopLayer);
                world.getLayerRegistry().register(requestLayer.getKey(), requestLayer);
                stopLayers.put(type, stopLayer);
                requestLayers.put(type, requestLayer);
            }

            stopLayersByWorld.put(worldKey, stopLayers);
            requestLayersByWorld.put(worldKey, requestLayers);
        }
    }

    private void addStopMarker(SimpleLayer layer, Stop stop) {
        String markerKey = "stop." + stop.regionId() + "." + stop.transport().key() + "." + slug(stop.cityName());
        String tooltip = tooltip(
                "<strong>" + escape(stop.cityName()) + "</strong>",
                isAbroad(stop.regionId()) ? "Замежжа" : "Вобласць: " + escape(getRegionName(stop.regionId())),
                "Від: " + escape(belarusianTransportName(stop.transport()))
        );

        Marker<?> marker = Marker.icon(
                markerKey,
                stop.location().x(),
                stop.location().z(),
                approvedIconKey(stop.transport(), stop.regionId()),
                32.0,
                32.0 * 281.0 / 229.0
        ).setOptions(Options.builder().tooltipContent(tooltip).build());

        layer.addMarker(marker);
    }

    private void addRequestMarker(SimpleLayer layer, StopApplication app) {
        String markerKey = "request." + app.id();
        String tooltip = tooltip(
                "<strong>Заяўка на прыпынак</strong>",
                "Горад: " + escape(app.cityName()),
                isAbroad(app.regionId()) ? "Замежжа" : "Вобласць: " + escape(getRegionName(app.regionId())),
                "Тып: " + escape(belarusianTransportName(app.transport())),
                "Гулец: " + escape(app.playerName())
        );

        Marker<?> marker = Marker.icon(
                markerKey,
                app.location().x(),
                app.location().z(),
                requestIconKey(app.transport()),
                32.0,
                32.0 * 281.0 / 229.0
        ).setOptions(Options.builder().tooltipContent(tooltip).build());

        layer.addMarker(marker);
    }

    private void registerIcons() {
        String[] regions = {"minsk", "gomel", "brest", "grodno", "mogilev", "vitebsk"};
        for (TransportType type : TransportType.values()) {
            for (String region : regions) {
                registerIcon(approvedIconKey(type, region), ICON_ROOT + type.key() + "/" + region + ".png");
            }
            registerIcon(approvedIconKey(type, "abroad"), ICON_ROOT + type.key() + "/abroad.png");
            registerIcon(requestIconKey(type), ICON_ROOT + "requests/" + type.key() + ".png");
        }
    }

    private void registerIcon(String key, String resourcePath) {
        try (InputStream input = plugin.getResource(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Missing resource " + resourcePath);
            }
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IllegalStateException("Invalid image resource " + resourcePath);
            }
            Pl3xMap.api().getIconRegistry().register(key, new IconImage(key, image, "png"));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to register icon " + key + " from " + resourcePath, e);
        }
    }

    private String approvedIconKey(TransportType type, String regionId) {
        String region = normalizeRegion(regionId);
        if ("abroad".equals(region)) {
            return "easytransport." + type.key() + ".abroad";
        }
        return switch (region) {
            case "minsk", "gomel", "brest", "grodno", "mogilev", "vitebsk" ->
                    "easytransport." + type.key() + "." + region;
            default -> "easytransport." + type.key() + ".minsk";
        };
    }

    private String requestIconKey(TransportType type) {
        return "easytransport.requests." + type.key();
    }

    private boolean isAbroad(String regionId) {
        return "abroad".equals(normalizeRegion(regionId));
    }

    private String belarusianTransportName(TransportType type) {
        return switch (type) {
            case BUS -> "Аўтобус";
            case TRAIN -> "Цягнік";
            case AIR -> "Самалёт";
        };
    }

    private boolean sameWorld(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private String normalizeRegion(String regionId) {
        return regionId == null ? "" : regionId.toLowerCase(Locale.ROOT).trim();
    }

    private Set<String> getRegionIds() {
        var section = plugin.getConfig().getConfigurationSection("regions");
        return section == null ? Set.of() : section.getKeys(false);
    }

    private String getRegionName(String regionId) {
        return plugin.getConfig().getString("regions." + regionId + ".name", regionId);
    }

    private String slug(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9а-яё_-]+", "_")
                .replaceAll("_+", "_");
    }

    private String tooltip(String... lines) {
        return String.join("<br>", lines);
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void debug(String message) {
        if (debugLogs) {
            plugin.getLogger().info("[Pl3xMap] " + message);
        }
    }
}
