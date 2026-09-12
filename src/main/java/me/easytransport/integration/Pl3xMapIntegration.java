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
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

/**
 * Pl3xMap integration for EasyTransport.
 *
 * Approved stops use a region-specific icon.
 * Pending applications use a single gray icon per transport type.
 */
public final class Pl3xMapIntegration {
    private static final String MAP_PLUGIN_NAME = "Pl3xMap";
    private static final String ICON_ROOT = "pl3xmap/icons/";
    private static final String LAYER_ROOT = "easytransport";

    private final EasyTransportPlugin plugin;
    private final Map<String, Map<TransportType, SimpleLayer>> stopLayersByWorld = new HashMap<>();
    private final Map<String, Map<TransportType, SimpleLayer>> requestLayersByWorld = new HashMap<>();
    private boolean enabled;

    public Pl3xMapIntegration(EasyTransportPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
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
                refresh();
                enabled = true;
                plugin.getLogger().info("Pl3xMap integration enabled.");
            });
        } catch (Throwable t) {
            plugin.getLogger().severe("Failed to initialize Pl3xMap integration: " + t.getMessage());
            t.printStackTrace();
        }
    }

    public void stop() {
        stopLayersByWorld.clear();
        requestLayersByWorld.clear();
        enabled = false;
    }

    public void refresh() {
        if (!enabled) {
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
                continue;
            }

            for (TransportType type : TransportType.values()) {
                SimpleLayer stopLayer = stopLayers.get(type);
                SimpleLayer requestLayer = requestLayers.get(type);

                for (String regionId : getRegionIds()) {
                    for (Stop stop : plugin.data().getStops(regionId, type)) {
                        if (!sameWorld(stop.location().world(), worldName)) {
                            continue;
                        }
                        addStopMarker(stopLayer, stop);
                    }
                }

                for (StopApplication app : plugin.applications().getApplications()) {
                    if (app.transport() != type || !sameWorld(app.location().world(), worldName)) {
                        continue;
                    }
                    addRequestMarker(requestLayer, app);
                }
            }
        }
    }

    private void createLayers() {
        stopLayersByWorld.clear();
        requestLayersByWorld.clear();

        for (net.pl3x.map.core.world.World world : Pl3xMap.api().getWorldRegistry().values()) {
            String worldName = world.getName();
            String worldKey = worldName.toLowerCase(Locale.ROOT);
            Map<TransportType, SimpleLayer> worldStops = new EnumMap<>(TransportType.class);
            Map<TransportType, SimpleLayer> worldRequests = new EnumMap<>(TransportType.class);

            for (TransportType type : TransportType.values()) {
                SimpleLayer stopLayer = new SimpleLayer(
                        LAYER_ROOT + ".stops." + type.key(),
                        () -> "Прыпынкі: " + belarusianTransportName(type));
                SimpleLayer requestLayer = new SimpleLayer(
                        LAYER_ROOT + ".requests." + type.key(),
                        () -> "Заяўкі: " + belarusianTransportName(type));

                world.getLayerRegistry().register(stopLayer.getKey(), stopLayer);
                world.getLayerRegistry().register(requestLayer.getKey(), requestLayer);
                worldStops.put(type, stopLayer);
                worldRequests.put(type, requestLayer);
            }

            stopLayersByWorld.put(worldKey, worldStops);
            requestLayersByWorld.put(worldKey, worldRequests);
        }
    }

    private void addStopMarker(SimpleLayer layer, Stop stop) {
        String iconKey = approvedIconKey(stop.transport(), stop.regionId());
        String markerKey = "stop." + stop.regionId() + "." + stop.transport().key() + "." + slug(stop.cityName());
        String regionName = getRegionName(stop.regionId());
        String tooltip = tooltip(
                "<strong>" + escape(stop.cityName()) + "</strong>",
                isAbroad(stop.regionId())
                        ? "Замежжа"
                        : "Вобласць: " + escape(regionName),
                "Від: " + escape(belarusianTransportName(stop.transport()))
        );

        Marker<?> marker = Marker.icon(
                markerKey,
                stop.location().x(),
                stop.location().z(),
                iconKey,
                32.0
        ).setOptions(Options.builder()
                .tooltipContent(tooltip)
                .build());

        layer.addMarker(marker);
    }

    private void addRequestMarker(SimpleLayer layer, StopApplication app) {
        String markerKey = "request." + app.id();
        String regionName = getRegionName(app.regionId());
        String tooltip = tooltip(
                "<strong>Заяўка на прыпынак</strong>",
                "Горад: " + escape(app.cityName()),
                isAbroad(app.regionId())
                        ? "Замежжа"
                        : "Вобласць: " + escape(regionName),
                "Тып: " + escape(belarusianTransportName(app.transport())),
                "Гулец: " + escape(app.playerName())
        );

        Marker<?> marker = Marker.icon(
                markerKey,
                app.location().x(),
                app.location().z(),
                requestIconKey(app.transport()),
                32.0
        ).setOptions(Options.builder()
                .tooltipContent(tooltip)
                .build());

        layer.addMarker(marker);
    }

    private void registerIcons() {
        for (TransportType type : TransportType.values()) {
            for (String region : new String[]{"minsk", "gomel", "brest", "grodno", "mogilev", "vitebsk"}) {
                registerIcon(approvedIconKey(type, region), ICON_ROOT + type.key() + "/" + region + ".png");
            }
        }
        registerIcon(approvedIconKey(TransportType.BUS, "abroad"), ICON_ROOT + "bus/abroad.png");
        registerIcon(approvedIconKey(TransportType.TRAIN, "abroad"), ICON_ROOT + "train/abroad.png");
        registerIcon(approvedIconKey(TransportType.AIR, "abroad"), ICON_ROOT + "air/abroad.png");

        for (TransportType type : TransportType.values()) {
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
                throw new IllegalStateException("Invalid PNG resource " + resourcePath);
            }
            Pl3xMap.api().getIconRegistry().register(key, new IconImage(key, image, "png"));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to register icon " + key + " from " + resourcePath, e);
        }
    }

    private String approvedIconKey(TransportType type, String regionId) {
        String region = normalizeRegion(regionId);
        if (region.equals("abroad")) {
            return "easytransport." + type.key() + ".abroad";
        }
        if (isBelarusRegion(region)) {
            return "easytransport." + type.key() + "." + region;
        }
        return "easytransport." + type.key() + ".minsk";
    }

    private String requestIconKey(TransportType type) {
        return "easytransport.requests." + type.key();
    }


    private boolean isAbroad(String regionId) {
        return "abroad".equalsIgnoreCase(normalizeRegion(regionId));
    }

    private String belarusianTransportName(TransportType type) {
        return switch (type) {
            case BUS -> "Аўтобус";
            case TRAIN -> "Цягнік";
            case AIR -> "Самалёт";
        };
    }

    private boolean isBelarusRegion(String region) {
        return switch (region) {
            case "minsk", "gomel", "brest", "grodno", "mogilev", "vitebsk" -> true;
            default -> false;
        };
    }

    private boolean sameWorld(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private String normalizeRegion(String regionId) {
        return regionId == null ? "" : regionId.toLowerCase(Locale.ROOT).trim();
    }

    private String slug(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9а-яё_-]+", "_")
                .replaceAll("_+", "_");
    }

    private java.util.Set<String> getRegionIds() {
        var section = plugin.getConfig().getConfigurationSection("regions");
        if (section == null) {
            return java.util.Set.of();
        }
        return section.getKeys(false);
    }

    private String getRegionName(String regionId) {
        if (regionId == null || regionId.isBlank()) {
            return "";
        }
        return plugin.getConfig().getString("regions." + regionId + ".name", regionId);
    }

    private String tooltip(String... lines) {
        return String.join("<br>", lines);
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
