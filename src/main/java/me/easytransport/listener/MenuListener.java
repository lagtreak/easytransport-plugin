package me.easytransport.listener;

import me.easytransport.EasyTransportPlugin;
import me.easytransport.menu.TransportMenu;
import me.easytransport.model.Stop;
import me.easytransport.model.TransportType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
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
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null) return;
        ItemStack item = event.getCurrentItem();
        String marker = item.getItemMeta() == null ? null : item.getItemMeta().getPersistentDataContainer()
                .get(plugin.menuKey(), PersistentDataType.STRING);
        if (marker == null) return;
        event.setCancelled(true);

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
            if (stop != null) plugin.travel().start(player, type, stop);
        }
    }

    private TransportType findTypeFromRegionsView(Player player) {
        return plugin.cashierTypeByPlayerMenu(player);
    }
}
