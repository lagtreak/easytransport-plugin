package me.easytransport.service;

import me.easytransport.EasyTransportPlugin;
import me.easytransport.model.Stop;
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
    private static final long DENIAL_TIMEOUT_TICKS = 120L * 20L;
    private final EasyTransportPlugin plugin;
    private final Map<UUID, PendingDenial> pendingDenials = new ConcurrentHashMap<>();

    public ApplicationService(EasyTransportPlugin plugin) { this.plugin = plugin; }

    public boolean hasPendingDenial(UUID uuid) { return pendingDenials.containsKey(uuid); }

    public boolean create(Player player, TransportType type, String regionId, String cityName) {
        String world = player.getWorld().getName();
        if (!plugin.isManagedWorld(world) || !plugin.worldAllowsTransport(world, type)) {
            player.sendMessage(ChatMessages.red("У гэтым свеце абраны від транспарту недаступны для EasyTransport."));
            return false;
        }
        if (plugin.isAbroad(world) && (type != TransportType.AIR || !regionId.equalsIgnoreCase("abroad"))) {
            player.sendMessage(ChatMessages.red("У Замежжы можна падаваць толькі авіяцыйныя заяўкі для вобласці «Замежжа»."));
            return false;
        }
        if (!plugin.isAbroad(world) && regionId.equalsIgnoreCase("abroad") && (!plugin.isWorld(world) || type != TransportType.AIR)) {
            player.sendMessage(ChatMessages.red("Пункты вобласці «Замежжа» ў Беларускім краі могуць быць толькі авіяцыйнымі."));
            return false;
        }
        if (plugin.data().findStop(regionId, type, cityName) != null || plugin.applications().hasPendingDuplicate(regionId, type, cityName)) {
            player.sendMessage(ChatMessages.red("Пункт з такой назвай ужо існуе або ўжо пададзены на разгляд."));
            return false;
        }
        StopApplication app = new StopApplication(
                UUID.randomUUID(), player.getUniqueId(), player.getName(), type, regionId, cityName,
                StoredLocation.from(player.getLocation()), System.currentTimeMillis()
        );
        plugin.applications().saveApplication(app);
        plugin.discord().createApplicationMessage(app);
        notifyAdminsNewApplication(app);
        player.sendMessage(ChatMessages.green("Заяўка на прыпынак «" + cityName + "» адпраўлена на разгляд."));
        return true;
    }

    public void approve(Player admin, StopApplication app) {
        if (!hasMatchingCashierNearby(app)) {
            admin.sendMessage(ChatMessages.approvalNeedsCashier(app.transport().displayName()));
            return;
        }
        if (plugin.data().findStop(app.regionId(), app.transport(), app.cityName()) != null) {
            admin.sendMessage(ChatMessages.red("Гэты пункт ужо ёсць у рэестры."));
            plugin.applications().deleteApplication(app.id());
            return;
        }
        plugin.data().saveStop(new Stop(
                app.regionId(),
                app.cityName(),
                app.transport(),
                app.location()
        ));

        plugin.discord().deleteApplicationMessage(app.id());
        plugin.applications().deleteApplication(app.id());
        deliverResult(app.playerUuid(), ChatMessages.playerApproved(app.cityName()));
        admin.sendMessage(ChatMessages.approved(app.cityName()));
    }

    public void beginDenial(Player admin, StopApplication app) {
        admin.closeInventory();
        cancelPendingDenial(admin.getUniqueId());
        admin.sendMessage(ChatMessages.denialPrompt());
        admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1.0f, 1.0f);
        BukkitTask timeout = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            PendingDenial pending = pendingDenials.remove(admin.getUniqueId());
            if (pending != null && admin.isOnline()) {
                admin.sendMessage(ChatMessages.red("Час на ўвод прычыны скончыўся. Заяўка не была адхілена."));
            }
        }, DENIAL_TIMEOUT_TICKS);
        pendingDenials.put(admin.getUniqueId(), new PendingDenial(app.id(), timeout));
    }

    public void handleDenialMessage(Player admin, String reason) {
        PendingDenial pending = pendingDenials.remove(admin.getUniqueId());
        if (pending == null) return;
        if (pending.timeout() != null) pending.timeout().cancel();
        StopApplication app = plugin.applications().getApplication(pending.applicationId());
        if (app == null) {
            admin.sendMessage(ChatMessages.red("Гэтая заяўка ўжо не існуе."));
            return;
        }
        plugin.discord().deleteApplicationMessage(app.id());
        plugin.applications().deleteApplication(app.id());
        String cleanReason = reason.isBlank() ? "Прычына не пазначаная." : reason.trim();
        deliverResult(app.playerUuid(), ChatMessages.playerRejected(app.cityName(), cleanReason));
        admin.sendMessage(ChatMessages.rejected(app.cityName()));
        admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1.0f, 1.0f);
    }

    public void cancelPendingDenial(UUID uuid) {
        PendingDenial pending = pendingDenials.remove(uuid);
        if (pending != null && pending.timeout() != null) pending.timeout().cancel();
    }

    private void deliverResult(UUID playerUuid, net.kyori.adventure.text.Component message) {
        Player player = plugin.getServer().getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            player.sendMessage(message);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1.0f, 1.0f);
        } else {
            plugin.applications().addNotification(playerUuid, ChatMessages.serialize(message));
        }
    }

    private boolean hasMatchingCashierNearby(StopApplication app) {
        StoredLocation cashier = plugin.data().getCashierLocation(app.transport());
        if (cashier == null) return false;
        if (!cashier.world().equalsIgnoreCase(app.location().world())) return false;
        double dx = cashier.x() - app.location().x();
        double dy = cashier.y() - app.location().y();
        double dz = cashier.z() - app.location().z();
        return (dx * dx + dy * dy + dz * dz) <= (50.0 * 50.0);
    }

    private void notifyAdminsNewApplication(StopApplication app) {
        String text = "Новая заяўка на прыпынак «" + app.cityName() + "» ад " + app.playerName() + ". /etr application";
        plugin.getServer().getOnlinePlayers().stream().filter(Player::isOp).forEach(admin -> {
            admin.sendMessage(ChatMessages.newApplication(app.cityName(), app.playerName()));
            admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1.0f, 1.0f);
        });
    }

    private record PendingDenial(UUID applicationId, BukkitTask timeout) {}
}
