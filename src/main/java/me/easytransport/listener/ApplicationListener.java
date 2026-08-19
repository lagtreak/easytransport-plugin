package me.easytransport.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.easytransport.EasyTransportPlugin;
import me.easytransport.menu.ApplicationMenu;
import me.easytransport.model.StopApplication;
import me.easytransport.util.ChatMessages;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class ApplicationListener implements Listener {
    private final EasyTransportPlugin plugin;
    private final ApplicationMenu menu;

    public ApplicationListener(EasyTransportPlugin plugin, ApplicationMenu menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.applicationsService().hasPendingDenial(player.getUniqueId())) return;
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.applicationsService().handleDenialMessage(player, message));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            List<String> messages = plugin.applications().getNotifications(player.getUniqueId());
            if (!messages.isEmpty()) {
                for (String message : messages) {
                    player.sendMessage(ChatMessages.deserialize(message));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1.0f, 1.0f);
                }
                plugin.applications().clearNotifications(player.getUniqueId());
            }
        }, 20L * 20L);

        if (!player.isOp()) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            int count = plugin.applications().getApplications().size();
            if (count <= 0) return;
            player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, SoundCategory.MASTER, 1.0f, 1.0f);
        }, 20L * 15L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            int count = plugin.applications().getApplications().size();
            if (count <= 0) return;
            player.sendMessage(ChatMessages.activeApplications(count));
            player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, SoundCategory.MASTER, 1.4f, 1.0f);
        }, 20L * 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.applicationsService().cancelPendingDenial(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getItemMeta() == null) return;
        String marker = item.getItemMeta().getPersistentDataContainer().get(plugin.menuKey(), PersistentDataType.STRING);
        if (marker == null || !marker.startsWith("application:")) return;
        event.setCancelled(true);

        if (marker.equals(ApplicationMenu.BACK_MARKER)) {
            menu.openList(player, 0);
            return;
        }
        if (marker.equals("application:close")) {
            player.closeInventory();
            return;
        }
        if (marker.startsWith(ApplicationMenu.PAGE_PREV_PREFIX) || marker.startsWith(ApplicationMenu.PAGE_NEXT_PREFIX)) {
            int page = Integer.parseInt(marker.substring(marker.lastIndexOf(':') + 1));
            menu.openList(player, page);
            return;
        }
        if (marker.startsWith(ApplicationMenu.LIST_MARKER_PREFIX)) {
            StopApplication app = plugin.applications().getApplication(java.util.UUID.fromString(marker.substring(ApplicationMenu.LIST_MARKER_PREFIX.length())));
            if (app != null) menu.openDetail(player, app);
            return;
        }
        if (marker.startsWith(ApplicationMenu.TELEPORT_MARKER_PREFIX)) {
            StopApplication app = plugin.applications().getApplication(java.util.UUID.fromString(marker.substring(ApplicationMenu.TELEPORT_MARKER_PREFIX.length())));
            if (app != null && app.location().toLocation() != null) {
                player.closeInventory();
                player.teleport(app.location().toLocation());
            }
            return;
        }
        if (marker.startsWith(ApplicationMenu.APPROVE_MARKER_PREFIX)) {
            StopApplication app = plugin.applications().getApplication(java.util.UUID.fromString(marker.substring(ApplicationMenu.APPROVE_MARKER_PREFIX.length())));
            if (app != null) {
                player.closeInventory();
                plugin.applicationsService().approve(player, app);
            }
            return;
        }
        if (marker.startsWith(ApplicationMenu.DENY_MARKER_PREFIX)) {
            StopApplication app = plugin.applications().getApplication(java.util.UUID.fromString(marker.substring(ApplicationMenu.DENY_MARKER_PREFIX.length())));
            if (app != null) plugin.applicationsService().beginDenial(player, app);
        }
    }
}
