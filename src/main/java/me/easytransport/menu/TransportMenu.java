package me.easytransport.menu;

import me.easytransport.EasyTransportPlugin;
import me.easytransport.model.Stop;
import me.easytransport.model.TransportType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class TransportMenu {
    public static final String REGION_TITLE = "EasyTransport: Выбар вобласці";
    public static final String CITY_PREFIX = "EasyTransport: ";
    public static final String BACK_MARKER = "back:regions";

    private static final List<String> REGION_ORDER = List.of(
            "brest", "vitebsk", "gomel", "grodno", "mogilev", "minsk", "abroad"
    );

    private final EasyTransportPlugin plugin;

    public TransportMenu(EasyTransportPlugin plugin) {
        this.plugin = plugin;
    }

    public void openRegions(Player player, TransportType type) {
        plugin.rememberPlayerMenu(player, type);
        Inventory inv = Bukkit.createInventory(null, 9, Component.text(REGION_TITLE));
        int slot = 0;
        for (String regionId : REGION_ORDER) {
            if (!plugin.isRegionAvailableForTransport(type, regionId)) continue;
            if (plugin.data().getStops(regionId, type).isEmpty()) continue;
            inv.setItem(slot++, button(regionMaterial(regionId), plugin.data().getRegionName(regionId), "region:" + regionId));
        }
        if (slot == 0) {
            inv.setItem(4, button(Material.BARRIER, "Няма даступных напрамкаў", "noop"));
        }
        player.openInventory(inv);
    }

    public void openCities(Player player, TransportType type, String regionId) {
        List<Stop> stops = plugin.data().getStops(regionId, type);
        int cityRows = Math.max(1, (stops.size() + 8) / 9);
        int rows = Math.min(6, cityRows + 1); // заўсёды +1 радок пад кнопку назад
        Inventory inv = Bukkit.createInventory(null, rows * 9,
                Component.text(CITY_PREFIX + plugin.data().getRegionName(regionId)));
        Material panel = regionPanelMaterial(regionId);
        for (int i = 0; i < stops.size() && i < (rows - 1) * 9; i++) {
            Stop stop = stops.get(i);
            inv.setItem(i, button(panel, stop.cityName(),
                    "city:" + type.key() + ":" + regionId + ":" + stop.cityName()));
        }

        int backSlot = (rows - 1) * 9 + 4;
        inv.setItem(backSlot, button(Material.ARROW, "Назад да выбару вобласці", BACK_MARKER));
        player.openInventory(inv);
    }

    private ItemStack button(Material material, String title, String marker) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(title, NamedTextColor.WHITE));
        meta.getPersistentDataContainer().set(plugin.menuKey(), PersistentDataType.STRING, marker);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private Material regionMaterial(String regionId) {
        return switch (regionId.toLowerCase()) {
            case "minsk" -> Material.RED_DYE;
            case "gomel" -> Material.MAGENTA_DYE;
            case "brest" -> Material.LIGHT_BLUE_DYE;
            case "vitebsk" -> Material.GREEN_DYE;
            case "mogilev" -> Material.YELLOW_DYE;
            case "grodno" -> Material.ORANGE_DYE;
            case "abroad" -> Material.MAP;
            default -> Material.PAPER;
        };
    }

    private Material regionPanelMaterial(String regionId) {
        return switch (regionId.toLowerCase()) {
            case "minsk" -> Material.RED_STAINED_GLASS_PANE;
            case "gomel" -> Material.MAGENTA_STAINED_GLASS_PANE;
            case "brest" -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case "vitebsk" -> Material.GREEN_STAINED_GLASS_PANE;
            case "mogilev" -> Material.YELLOW_STAINED_GLASS_PANE;
            case "grodno" -> Material.ORANGE_STAINED_GLASS_PANE;
            case "abroad" -> Material.WHITE_STAINED_GLASS_PANE;
            default -> Material.GLASS_PANE;
        };
    }
}
