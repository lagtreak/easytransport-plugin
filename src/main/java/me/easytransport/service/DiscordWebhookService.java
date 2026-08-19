package me.easytransport.service;

import me.easytransport.EasyTransportPlugin;
import me.easytransport.model.StopApplication;
import me.easytransport.model.TransportType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/** Synchronizes active EasyTransport stop applications to one Discord webhook message. */
public final class DiscordWebhookService {
    private final EasyTransportPlugin plugin;
    private final HttpClient client = HttpClient.newHttpClient();

    public DiscordWebhookService(EasyTransportPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isConfigured() {
        return plugin.getConfig().getBoolean("discord.enabled", false)
                && !plugin.getConfig().getString("discord.webhook-url", "").isBlank();
    }

    public void setWebhookUrl(String url) {
        String normalized = url == null ? "" : url.trim();
        plugin.getConfig().set("discord.webhook-url", normalized);
        plugin.getConfig().set("discord.enabled", !normalized.isBlank());
        plugin.getConfig().set("discord.applications-message-id", "");
        plugin.saveConfig();
    }

    public String maskedWebhookUrl() {
        String url = plugin.getConfig().getString("discord.webhook-url", "");
        if (url.isBlank()) return "не настроены";
        int token = url.lastIndexOf('/');
        if (token <= 0 || token == url.length() - 1) return "настроены";
        return url.substring(0, Math.min(token, url.length())) + "/***";
    }

    public void disable() {
        plugin.getConfig().set("discord.enabled", false);
        plugin.getConfig().set("discord.webhook-url", "");
        plugin.getConfig().set("discord.applications-message-id", "");
        plugin.saveConfig();
    }

    public void syncApplications() {
        if (!isConfigured()) return;
        new BukkitRunnable() {
            @Override public void run() {
                syncNow();
            }
        }.runTaskAsynchronously(plugin);
    }

    public void test() {
        if (!isConfigured()) {
            plugin.getLogger().warning("Discord webhook не настроен.");
            return;
        }
        new BukkitRunnable() {
            @Override public void run() {
                String body = "{\"username\":\"EasyTransport\",\"content\":\"EasyTransport: webhook працуе.\"}";
                try {
                    HttpResponse<String> response = sendPost(plugin.getConfig().getString("discord.webhook-url", "").trim() + "?wait=true", body);
                    if (response.statusCode() / 100 != 2) {
                        plugin.getLogger().warning("Discord webhook test HTTP " + response.statusCode() + ": " + response.body());
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("Discord webhook test failed: " + ex.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void syncNow() {
        String webhook = plugin.getConfig().getString("discord.webhook-url", "").trim();
        if (webhook.isBlank()) return;
        String messageId = plugin.getConfig().getString("discord.applications-message-id", "").trim();
        List<StopApplication> apps = plugin.applications().getApplications();
        String body = buildBody(apps);

        try {
            if (messageId.isBlank()) {
                HttpResponse<String> response = sendPost(webhook + "?wait=true", body);
                if (response.statusCode() / 100 == 2) {
                    String id = extractJsonString(response.body(), "id");
                    if (id != null && !id.isBlank()) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            plugin.getConfig().set("discord.applications-message-id", id);
                            plugin.saveConfig();
                        });
                    }
                } else {
                    plugin.getLogger().warning("Discord webhook HTTP " + response.statusCode() + ": " + response.body());
                }
            } else {
                HttpRequest request = HttpRequest.newBuilder(URI.create(webhook + "/messages/" + messageId))
                        .header("Content-Type", "application/json")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 404) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        plugin.getConfig().set("discord.applications-message-id", "");
                        plugin.saveConfig();
                        syncApplications();
                    });
                } else if (response.statusCode() / 100 != 2) {
                    plugin.getLogger().warning("Discord webhook update HTTP " + response.statusCode() + ": " + response.body());
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Не атрымалася абнавіць Discord webhook: " + ex.getMessage());
        }
    }

    private String buildBody(List<StopApplication> apps) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"username\":\"EasyTransport\",\"embeds\":[");
        sb.append("{\"title\":\"🚏 EASYTRANSPORT — ЗАЯЎКІ\",\"description\":\"Актыўных заявак: **")
                .append(apps.size()).append("**\",\"color\":5763719},");
        int limit = Math.min(apps.size(), 9);
        for (int i = 0; i < limit; i++) {
            StopApplication app = apps.get(i);
            if (i > 0) sb.append(',');
            String fields = "\"fields\":["
                    + fieldJson("Вобласць", plugin.data().getRegionName(app.regionId()), true) + ','
                    + fieldJson("Транспарт", app.transport().displayName(), true) + ','
                    + fieldJson("Гулец", app.playerName(), true) + ','
                    + fieldJson("Свет", app.location().world(), true) + ','
                    + fieldJson("Каардынаты", String.format("%.2f, %.2f, %.2f", app.location().x(), app.location().y(), app.location().z()), false)
                    + "]";
            sb.append("{\"title\":\"").append(escape((i + 1) + ". " + app.cityName()))
                    .append("\",\"color\":").append(transportColor(app.transport()))
                    .append(',').append(fields).append('}');
        }
        if (apps.isEmpty()) {
            sb.append("{\"description\":\"Няма актыўных заявак.\",\"color\":8421504}");
        } else if (apps.size() > limit) {
            sb.append("{\"description\":\"Паказана ").append(limit).append(" з ").append(apps.size())
                    .append(" заявак. Поўны спіс даступны ў Minecraft.\",\"color\":8421504}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String fieldJson(String name, String value, boolean inline) {
        return "{\"name\":\"" + escape(name) + "\",\"value\":\"`" + escape(value) + "`\",\"inline\":" + inline + "}";
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    private String extractJsonString(String json, String key) {
        String marker = "\"" + key + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) return null;
        start += marker.length();
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) { out.append(c); escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '\"') return out.toString();
            out.append(c);
        }
        return null;
    }

    private HttpResponse<String> sendPost(String url, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private int transportColor(TransportType type) {
        return switch (type) {
            case AIR -> 0x3498DB;
            case BUS -> 0xF1C40F;
            case TRAIN -> 0x2ECC71;
        };
    }

}
