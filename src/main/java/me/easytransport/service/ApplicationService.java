package me.easytransport.service;

import me.easytransport.EasyTransportPlugin;
import me.easytransport.model.StopApplication;
import me.easytransport.model.StoredLocation;
import me.easytransport.model.TransportType;
import me.easytransport.util.ChatMessages;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ApplicationService {

    private static final long DENIAL_TIMEOUT_TICKS = 2400L;

    private final EasyTransportPlugin plugin;
    private final Map<UUID, PendingDenial> pendingDenials = new ConcurrentHashMap<>();

    public ApplicationService(EasyTransportPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean hasPendingDenial(UUID uuid) {
        return pendingDenials.containsKey(uuid);
    }

    public boolean create(Player player, TransportType transport, String regionId, String cityName) {
        String worldName = player.getWorld().getName();

        if (!plugin.isManagedWorld(worldName)
                || !plugin.worldAllowsTransport(worldName, transport)) {
            player.sendMessage(ChatMessages.red(
                    "У гэтым свеце абраны від транспарту недаступны для EasyTransport."
            ));
            return false;
        }

        if (plugin.isAbroad(worldName)
                && (transport != TransportType.AIR || !regionId.equalsIgnoreCase("abroad"))) {
            player.sendMessage(ChatMessages.red(
                    "У Замежжы можна падаваць толькі авіяцыйныя заяўкі для вобласці «Замежжа»."
            ));
            return false;
        }

        if (!plugin.isAbroad(worldName)
                && regionId.equalsIgnoreCase("abroad")
                && (!plugin.isWorld(worldName) || transport != TransportType.AIR)) {
            player.sendMessage(ChatMessages.red(
                    "Пункты вобласці «Замежжа» ў Беларускім краі могуць быць толькі авіяцыйнымі."
            ));
            return false;
        }

        if (plugin.data().findStop(regionId, transport, cityName) != null
                || plugin.applications().hasPendingDuplicate(regionId, transport, cityName)) {
            player.sendMessage(ChatMessages.red(
                    "Пункт з такой назвай ужо існуе або ўжо пададзены на разгляд."
            ));
            return false;
        }

        StopApplication application = new StopApplication(
                UUID.randomUUID(),
                player.getUniqueId(),
                player.getName(),
                transport,
                regionId,
                cityName,
                StoredLocation.from(player.getLocation()),
                System.currentTimeMillis()
        );

        plugin.applications().saveApplication(application);
        plugin.discord().createApplicationMessage(application);
        notifyAdminsNewApplication(application);
        plugin.refreshPl3xMap();

        player.sendMessage(ChatMessages.green(
                "Заяўка на прыпынак «" + cityName + "» адпраўлена на разгляд."
        ));

        return true;
    }

    public void approve(Player admin, StopApplication application) {
        if (!hasMatchingCashierNearby(application)) {
            admin.sendMessage(
                    ChatMessages.approvalNeedsCashier(application.transport().displayName())
            );
            return;
        }

        if (plugin.data().findStop(
                application.regionId(),
                application.transport(),
                application.cityName()
        ) != null) {
            admin.sendMessage(ChatMessages.red("Гэты пункт ужо ёсць у рэестры."));
            plugin.applications().deleteApplication(application.id());
            plugin.refreshPl3xMap();
            return;
        }

        plugin.data().saveStop(
                new me.easytransport.model.Stop(
                        application.regionId(),
                        application.cityName(),
                        application.transport(),
                        application.location()
                )
        );

        plugin.discord().deleteApplicationMessage(application.id());
        plugin.applications().deleteApplication(application.id());
        plugin.refreshPl3xMap();

        deliverResult(
                application.playerUuid(),
                ChatMessages.playerApproved(application.cityName())
        );

        admin.sendMessage(ChatMessages.approved(application.cityName()));
    }

    public void beginDenial(Player admin, StopApplication application) {
        admin.closeInventory();
        cancelPendingDenial(admin.getUniqueId());

        admin.sendMessage(ChatMessages.denialPrompt());
        admin.playSound(
                admin.getLocation(),
                Sound.BLOCK_NOTE_BLOCK_PLING,
                SoundCategory.MASTER,
                1f,
                1f
        );

        BukkitTask timeout = plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> {
                    PendingDenial pending = pendingDenials.remove(admin.getUniqueId());

                    if (pending != null && admin.isOnline()) {
                        admin.sendMessage(ChatMessages.red(
                                "Час на ўвод прычыны скончыўся. Заяўка не была адхілена."
                        ));
                    }
                },
                DENIAL_TIMEOUT_TICKS
        );

        pendingDenials.put(
                admin.getUniqueId(),
                new PendingDenial(application.id(), timeout)
        );
    }

    public void handleDenialMessage(Player admin, String reason) {
        PendingDenial pending = pendingDenials.remove(admin.getUniqueId());

        if (pending == null) {
            return;
        }

        if (pending.timeout() != null) {
            pending.timeout().cancel();
        }

        StopApplication application =
                plugin.applications().getApplication(pending.applicationId());

        if (application == null) {
            admin.sendMessage(ChatMessages.red("Гэтая заяўка ўжо не існуе."));
            return;
        }

        plugin.discord().deleteApplicationMessage(application.id());
        plugin.applications().deleteApplication(application.id());
        plugin.refreshPl3xMap();

        String actualReason = reason.isBlank()
                ? "Прычына не пазначаная."
                : reason.trim();

        deliverResult(
                application.playerUuid(),
                ChatMessages.playerRejected(application.cityName(), actualReason)
        );

        admin.sendMessage(ChatMessages.rejected(application.cityName()));
        admin.playSound(
                admin.getLocation(),
                Sound.BLOCK_NOTE_BLOCK_PLING,
                SoundCategory.MASTER,
                1f,
                1f
        );
    }

    public void cancelPendingDenial(UUID uuid) {
        PendingDenial pending = pendingDenials.remove(uuid);

        if (pending != null && pending.timeout() != null) {
            pending.timeout().cancel();
        }
    }

    private void deliverResult(
            UUID uuid,
            net.kyori.adventure.text.Component message
    ) {
        Player player = plugin.getServer().getPlayer(uuid);

        if (player != null && player.isOnline()) {
            player.sendMessage(message);
            player.playSound(
                    player.getLocation(),
                    Sound.BLOCK_NOTE_BLOCK_PLING,
                    SoundCategory.MASTER,
                    1f,
                    1f
            );
        } else {
            plugin.applications().addNotification(
                    uuid,
                    ChatMessages.serialize(message)
            );
        }
    }

    private boolean hasMatchingCashierNearby(StopApplication application) {
        StoredLocation cashier =
                plugin.data().getCashierLocation(application.transport());

        if (cashier == null
                || !cashier.world().equalsIgnoreCase(application.location().world())) {
            return false;
        }

        double dx = cashier.x() - application.location().x();
        double dy = cashier.y() - application.location().y();
        double dz = cashier.z() - application.location().z();

        return dx * dx + dy * dy + dz * dz <= 2500.0;
    }

    private void notifyAdminsNewApplication(StopApplication application) {
        plugin.getServer().getOnlinePlayers()
                .stream()
                .filter(Player::isOp)
                .forEach(player -> {
                    player.sendMessage(
                            ChatMessages.newApplication(
                                    application.cityName(),
                                    application.playerName()
                            )
                    );

                    player.playSound(
                            player.getLocation(),
                            Sound.BLOCK_NOTE_BLOCK_PLING,
                            SoundCategory.MASTER,
                            1f,
                            1f
                    );
                });
    }

    private record PendingDenial(UUID applicationId, BukkitTask timeout) {
    }
}
