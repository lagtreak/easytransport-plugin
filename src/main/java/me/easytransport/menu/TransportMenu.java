package me.easytransport.menu;

import me.easytransport.EasyTransportPlugin;
import me.easytransport.model.Stop;
import me.easytransport.model.TransportType;
import me.easytransport.service.TravelService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.Color;
import org.bukkit.persistence.PersistentDataType;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TransportMenu {
    public static final String REGION_TITLE_PREFIX = "Білетар — ";
    public static final String CITY_PREFIX = "Білетар — ";
    public static final String CONFIRM_TITLE = "EasyTransport: Пацвярджэнне";
    public static final String BACK_MARKER = "back:regions";

    private static final List<String> REGION_ORDER = List.of(
            "brest", "vitebsk", "gomel", "grodno", "mogilev", "minsk", "abroad"
    );

    private final EasyTransportPlugin plugin;
    private final Map<UUID, PendingTrip> pendingTrips = new ConcurrentHashMap<>();
    private final DecimalFormat priceFormat = new DecimalFormat("0.##");

    public TransportMenu(EasyTransportPlugin plugin) {
        this.plugin = plugin;
    }

    public void openRegions(Player player, TransportType type) {
        plugin.rememberPlayerMenu(player, type);
        Inventory inv = Bukkit.createInventory(null, 9, Component.text(REGION_TITLE_PREFIX + type.displayName()));
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
        int rows = Math.min(6, cityRows + 1);
        Inventory inv = Bukkit.createInventory(null, rows * 9,
                Component.text(CITY_PREFIX + plugin.data().getRegionName(regionId)));
        for (int i = 0; i < stops.size() && i < (rows - 1) * 9; i++) {
            Stop stop = stops.get(i);
            inv.setItem(i, regionCityButton(regionId, stop.cityName(),
                    "city:" + type.key() + ":" + regionId + ":" + stop.cityName()));
        }

        int backSlot = (rows - 1) * 9 + 4;
        inv.setItem(backSlot, button(Material.ARROW, "Назад да выбару вобласці", BACK_MARKER));
        player.openInventory(inv);
    }

    public void openConfirmation(Player player, TransportType type, Stop stop) {
        TravelService.Preview preview = plugin.travel().preview(player, type, stop);
        if (preview == null) {
            player.sendMessage(me.easytransport.util.ChatMessages.red("Транспартная кропка настроена няправільна."));
            return;
        }

        pendingTrips.put(player.getUniqueId(), new PendingTrip(type, stop));
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(CONFIRM_TITLE));

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text("Падрабязнасці паездкі", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        infoMeta.getPersistentDataContainer().set(plugin.menuKey(), PersistentDataType.STRING, "confirm:info");
        infoMeta.lore(List.of(
                Component.text("Выбрана кропка адпраўлення: ", NamedTextColor.WHITE)
                        .append(Component.text(stop.cityName(), NamedTextColor.GOLD).decorate(TextDecoration.BOLD)),
                Component.text("Час у шляху: ", NamedTextColor.WHITE)
                        .append(Component.text(preview.seconds() + " сек.", NamedTextColor.WHITE).decorate(TextDecoration.BOLD)),
                Component.text("Кошт: ", NamedTextColor.WHITE)
                        .append(Component.text(priceFormat.format(preview.price()) + " BYN", NamedTextColor.WHITE).decorate(TextDecoration.BOLD)),
                Component.empty(),
                Component.text("Пачаць паездку?", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(13, info);

        inv.setItem(11, button(Material.LIME_CONCRETE, "Пачаць паездку", "confirm:start"));
        inv.setItem(15, button(Material.RED_CONCRETE, "Адмяніць", "confirm:cancel"));
        player.openInventory(inv);
    }

    public PendingTrip pendingTrip(Player player) {
        return pendingTrips.get(player.getUniqueId());
    }

    public void clearPendingTrip(Player player) {
        pendingTrips.remove(player.getUniqueId());
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
            case "minsk" -> Material.RED_CONCRETE;
            case "gomel" -> Material.MAGENTA_CONCRETE;
            case "brest" -> Material.LIGHT_BLUE_CONCRETE;
            case "vitebsk" -> Material.GREEN_CONCRETE;
            case "mogilev" -> Material.YELLOW_CONCRETE;
            case "grodno" -> Material.ORANGE_CONCRETE;
            case "abroad" -> Material.MAP;
            default -> Material.PAPER;
        };
    }

    private ItemStack regionCityButton(String regionId, String title, String marker) {
        ItemStack item = new ItemStack(Material.LEATHER_BOOTS);
        ItemMeta rawMeta = item.getItemMeta();
        if (rawMeta instanceof LeatherArmorMeta meta) {
            meta.setColor(regionLeatherColor(regionId));
            meta.displayName(Component.text(title, regionTextColor(regionId)));
            meta.getPersistentDataContainer().set(plugin.menuKey(), PersistentDataType.STRING, marker);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
            return item;
        }
        return button(Material.LEATHER_BOOTS, title, marker);
    }

    private Color regionLeatherColor(String regionId) {
        return switch (regionId.toLowerCase()) {
            case "minsk" -> Color.RED;
            case "gomel" -> Color.FUCHSIA;
            case "brest" -> Color.AQUA;
            case "vitebsk" -> Color.GREEN;
            case "mogilev" -> Color.YELLOW;
            case "grodno" -> Color.ORANGE;
            case "abroad" -> Color.WHITE;
            default -> Color.WHITE;
        };
    }

    private NamedTextColor regionTextColor(String regionId) {
        return switch (regionId.toLowerCase()) {
            case "minsk" -> NamedTextColor.RED;
            case "gomel" -> NamedTextColor.LIGHT_PURPLE;
            case "brest" -> NamedTextColor.AQUA;
            case "vitebsk" -> NamedTextColor.GREEN;
            case "mogilev" -> NamedTextColor.YELLOW;
            case "grodno" -> NamedTextColor.GOLD;
            case "abroad" -> NamedTextColor.WHITE;
            default -> NamedTextColor.WHITE;
        };
    }

    public record PendingTrip(TransportType type, Stop stop) {}
}
