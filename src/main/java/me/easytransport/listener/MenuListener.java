package me.easytransport.listener;

import me.easytransport.EasyTransportPlugin;
import me.easytransport.menu.TransportMenu;
import me.easytransport.model.Stop;
import me.easytransport.model.TransportType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Player;

public final class MenuListener implements Listener {
    private final EasyTransportPlugin plugin;
    private final TransportMenu menu;

    public MenuListener(EasyTransportPlugin plugin, TransportMenu menu) {
        this.plugin = plugin;
        this.menu = menu;
    }


    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (menu.pendingTrip(player) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null) return;
        ItemStack item = event.getCurrentItem();
        String marker = item.getItemMeta() == null ? null : item.getItemMeta().getPersistentDataContainer()
                .get(plugin.menuKey(), PersistentDataType.STRING);
        if (marker == null) return;
        event.setCancelled(true);

        if ("confirm:start".equals(marker)) {
            TransportMenu.PendingTrip pending = menu.pendingTrip(player);
            if (pending == null) return;
            menu.clearPendingTrip(player);
            player.closeInventory();
            plugin.travel().start(player, pending.type(), pending.stop());
            return;
        }

        if ("confirm:cancel".equals(marker)) {
            TransportMenu.PendingTrip pending = menu.pendingTrip(player);
            menu.clearPendingTrip(player);
            if (pending != null) {
                menu.openRegions(player, pending.type());
            } else {
                player.closeInventory();
            }
            return;
        }

        if (TransportMenu.BACK_MARKER.equals(marker)) {
            TransportType type = findTypeFromRegionsView(player);
            if (type != null) menu.openRegions(player, type);
            return;
        }

        if (marker.startsWith("region:")) {
            String regionId = marker.substring("region:".length());
            TransportType type = findTypeFromRegionsView(player);
            if (type == null) return;
            menu.openCities(player, type, regionId);
            return;
        }

        if (marker.startsWith("city:")) {
            String[] parts = marker.split(":", 4);
            if (parts.length != 4) return;
            TransportType type = TransportType.fromKey(parts[1]);
            String regionId = parts[2];
            String city = parts[3];
            if (type == null) return;
            Stop stop = plugin.data().findStop(regionId, type, city);
            if (stop != null) menu.openConfirmation(player, type, stop);
        }
    }

    private TransportType findTypeFromRegionsView(Player player) {
        return plugin.cashierTypeByPlayerMenu(player);
    }
}
