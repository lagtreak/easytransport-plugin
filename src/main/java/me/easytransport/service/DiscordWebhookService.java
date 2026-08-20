package me.easytransport.service;

import me.easytransport.EasyTransportPlugin;
import me.easytransport.model.StopApplication;
import me.easytransport.model.TransportType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Discord Webhook integration.
 *
 * Each active application gets its own Discord message.
 * The message is removed when the application is approved or rejected.
 */
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

        // Old message IDs belong to the previous webhook.
        plugin.getConfig().set("discord.applications-message-ids", null);

        plugin.saveConfig();
    }

    public String maskedWebhookUrl() {
        String url = plugin.getConfig().getString("discord.webhook-url", "");

        if (url.isBlank()) {
            return "не настроены";
        }

        int token = url.lastIndexOf('/');

        if (token <= 0 || token == url.length() - 1) {
            return "настроены";
        }

        return url.substring(0, token) + "/***";
    }

    public void disable() {
        plugin.getConfig().set("discord.enabled", false);
        plugin.getConfig().set("discord.webhook-url", "");
        plugin.getConfig().set("discord.applications-message-ids", null);
        plugin.saveConfig();
    }

    /**
     * Synchronizes active applications with Discord.
     *
     * Existing applications keep their messages.
     * New applications receive a new message.
     * Messages belonging to removed applications are deleted.
     */
    public void syncApplications() {
        if (!isConfigured()) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                syncNow();
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Sends a separate Discord message for one application.
     */
    public void createApplicationMessage(StopApplication app) {
        if (!isConfigured()) {
            return;
        }

        // Do not create duplicate messages.
        if (getMessageId(app.id()) != null) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                createApplicationMessageNow(app);
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Deletes Discord message associated with an application.
     */
    public void deleteApplicationMessage(UUID applicationId) {
        if (!isConfigured()) {
            return;
        }

        String messageId = getMessageId(applicationId);

        if (messageId == null || messageId.isBlank()) {
            removeMessageId(applicationId);
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                deleteApplicationMessageNow(applicationId, messageId);
            }
        }.runTaskAsynchronously(plugin);
    }

    public void test() {
        if (!isConfigured()) {
            plugin.getLogger().warning("Discord webhook не настроен.");
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                String body = """
                        {
                          "username": "EasyTransport",
                          "content": "EasyTransport: webhook працуе."
                        }
                        """;

                try {
                    HttpResponse<String> response = sendPost(
                            getWebhookUrl() + "?wait=true",
                            body
                    );

                    if (response.statusCode() / 100 != 2) {
                        plugin.getLogger().warning(
                                "Discord webhook test HTTP "
                                        + response.statusCode()
                                        + ": "
                                        + response.body()
                        );
                    }

                } catch (Exception ex) {
                    plugin.getLogger().warning(
                            "Discord webhook test failed: "
                                    + ex.getMessage()
                    );
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void syncNow() {
        String webhook = getWebhookUrl();

        if (webhook.isBlank()) {
            return;
        }

        List<StopApplication> applications =
                plugin.applications().getApplications();

        Set<UUID> activeIds = new HashSet<>();

        for (StopApplication app : applications) {
            activeIds.add(app.id());

            String existingMessageId = getMessageId(app.id());

            if (existingMessageId == null || existingMessageId.isBlank()) {
                createApplicationMessageNow(app);
            } else if (!discordMessageExists(webhook, existingMessageId)) {
                removeMessageId(app.id());
                createApplicationMessageNow(app);
            }
        }

        // Delete mappings and Discord messages for applications
        // that no longer exist in applications.yml.
        ConfigurationSection section =
                plugin.getConfig().getConfigurationSection(
                        "discord.application-message-ids"
                );

        if (section == null) {
            return;
        }

        for (String uuidText : section.getKeys(false)) {
            try {
                UUID applicationId = UUID.fromString(uuidText);

                if (!activeIds.contains(applicationId)) {
                    String messageId = section.getString(uuidText, "");

                    if (!messageId.isBlank()) {
                        try {
                            deleteDiscordMessage(webhook, messageId);
                        } catch (IOException ex) {
                            plugin.getLogger().warning(
                                    "Памылка выдалення старога паведамлення з Discord: "
                                            + ex.getMessage()
                            );
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();

                            plugin.getLogger().warning(
                                    "Discord-запыт на выдаленне быў перарваны."
                            );
                        }
                    }

                    removeMessageId(applicationId);
                }

            } catch (IllegalArgumentException ignored) {
                plugin.getConfig().set(
                        "discord.application-message-ids." + uuidText,
                        null
                );
            }
        }

        saveConfigSync();
    }

    private void createApplicationMessageNow(StopApplication app) {
        String webhook = getWebhookUrl();

        if (webhook.isBlank()) {
            return;
        }

        // Double protection against duplicate messages.
        String existingMessageId = getMessageId(app.id());

        if (existingMessageId != null && !existingMessageId.isBlank()) {
            return;
        }

        String body = buildApplicationBody(app);

        try {
            HttpResponse<String> response =
                    sendPost(webhook + "?wait=true", body);

            if (response.statusCode() / 100 != 2) {
                plugin.getLogger().warning(
                        "Не атрымалася адправіць заяўку ў Discord. HTTP "
                                + response.statusCode()
                                + ": "
                                + response.body()
                );
                return;
            }

            String messageId =
                    extractJsonString(response.body(), "id");

            if (messageId == null || messageId.isBlank()) {
                plugin.getLogger().warning(
                        "Discord не вярнуў ID паведамлення для заяўкі "
                                + app.id()
                );
                return;
            }

            plugin.getServer().getScheduler().runTask(
                    plugin,
                    () -> {
                        setMessageId(app.id(), messageId);
                        plugin.saveConfig();
                    }
            );

        } catch (Exception ex) {
            plugin.getLogger().warning(
                    "Памылка адпраўкі заяўкі ў Discord: "
                            + ex.getMessage()
            );
        }
    }

    private void deleteApplicationMessageNow(
            UUID applicationId,
            String messageId
    ) {
        String webhook = getWebhookUrl();

        if (webhook.isBlank()) {
            removeMessageId(applicationId);
            return;
        }

        try {
            deleteDiscordMessage(webhook, messageId);

            plugin.getServer().getScheduler().runTask(
                    plugin,
                    () -> {
                        removeMessageId(applicationId);
                        plugin.saveConfig();
                    }
            );

        } catch (IOException ex) {
            plugin.getLogger().warning(
                    "Памылка выдалення заяўкі з Discord: "
                            + ex.getMessage()
            );

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();

            plugin.getLogger().warning(
                    "Discord-запыт быў перарваны."
            );
        }
    }

    private boolean discordMessageExists(
            String webhook,
            String messageId
    ) {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(
                                    webhook
                                            + "/messages/"
                                            + messageId
                            )
                    )
                    .GET()
                    .build();

            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            return response.statusCode() / 100 == 2;

        } catch (Exception ex) {
            plugin.getLogger().warning(
                    "Не атрымалася праверыць Discord-паведамленне: "
                            + ex.getMessage()
            );
            return false;
        }
    }

    private void deleteDiscordMessage(
            String webhook,
            String messageId
    ) throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(
                                webhook
                                        + "/messages/"
                                        + messageId
                        )
                )
                .DELETE()
                .build();

        HttpResponse<String> response =
                client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        // Already deleted — this is effectively success.
        if (response.statusCode() == 404) {
            return;
        }

        if (response.statusCode() / 100 != 2) {
            throw new IOException(
                    "Discord HTTP "
                            + response.statusCode()
                            + ": "
                            + response.body()
            );
        }
    }

    private String buildApplicationBody(StopApplication app) {
        String regionName =
                plugin.data().getRegionName(app.regionId());

        String coordinates = String.format(
                "%.2f, %.2f, %.2f",
                app.location().x(),
                app.location().y(),
                app.location().z()
        );

        String json =
                "{"
                        + "\"username\":\"EasyTransport\","
                        + "\"embeds\":[{"
                        + "\"title\":\"🚏 Новая заявка: "
                        + escape(app.cityName())
                        + "\","
                        + "\"color\":"
                        + transportColor(app.transport())
                        + ","
                        + "\"fields\":["
                        + fieldJson("Вобласць", regionName, true)
                        + ","
                        + fieldJson(
                        "Транспарт",
                        app.transport().displayName(),
                        true
                )
                        + ","
                        + fieldJson(
                        "Гулец",
                        app.playerName(),
                        true
                )
                        + ","
                        + fieldJson(
                        "Свет",
                        app.location().world(),
                        true
                )
                        + ","
                        + fieldJson(
                        "Каардынаты",
                        coordinates,
                        false
                )
                        + "]"
                        + "}]"
                        + "}";

        return json;
    }

    private String fieldJson(
            String name,
            String value,
            boolean inline
    ) {
        return "{\"name\":\""
                + escape(name)
                + "\",\"value\":\"`"
                + escape(value)
                + "`\",\"inline\":"
                + inline
                + "}";
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }

    private String extractJsonString(
            String json,
            String key
    ) {
        String marker = "\"" + key + "\":\"";

        int start = json.indexOf(marker);

        if (start < 0) {
            return null;
        }

        start += marker.length();

        StringBuilder out = new StringBuilder();
        boolean escaped = false;

        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                out.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                return out.toString();
            }

            out.append(c);
        }

        return null;
    }

    private HttpResponse<String> sendPost(
            String url,
            String body
    ) throws IOException, InterruptedException {

        HttpRequest request =
                HttpRequest.newBuilder(URI.create(url))
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .POST(
                                HttpRequest.BodyPublishers.ofString(body)
                        )
                        .build();

        return client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private String getWebhookUrl() {
        return plugin.getConfig()
                .getString("discord.webhook-url", "")
                .trim();
    }

    private String getMessageId(UUID applicationId) {
        return plugin.getConfig()
                .getString(
                        "discord.application-message-ids."
                                + applicationId,
                        ""
                )
                .trim();
    }

    private void setMessageId(
            UUID applicationId,
            String messageId
    ) {
        plugin.getConfig().set(
                "discord.application-message-ids."
                        + applicationId,
                messageId
        );
    }

    private void removeMessageId(UUID applicationId) {
        plugin.getConfig().set(
                "discord.application-message-ids."
                        + applicationId,
                null
        );
    }

    private void saveConfigSync() {
        plugin.getServer().getScheduler().runTask(
                plugin,
                plugin::saveConfig
        );
    }

    private int transportColor(TransportType type) {
        return switch (type) {
            case AIR -> 0x3498DB;
            case BUS -> 0xF1C40F;
            case TRAIN -> 0x2ECC71;
        };
    }
}