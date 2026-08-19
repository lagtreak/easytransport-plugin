package me.easytransport.menu;

import me.easytransport.EasyTransportPlugin;
import me.easytransport.model.StopApplication;
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

public final class ApplicationMenu {
    public static final String LIST_TITLE = "Заяўкі на прыпынкі";
    public static final String DETAIL_PREFIX = "Заяўка: ";
    public static final String LIST_MARKER_PREFIX = "application:list:";
    public static final String APPROVE_MARKER_PREFIX = "application:approve:";
    public static final String DENY_MARKER_PREFIX = "application:deny:";
    public static final String TELEPORT_MARKER_PREFIX = "application:teleport:";
    public static final String PAGE_NEXT_PREFIX = "application:next:";
    public static final String PAGE_PREV_PREFIX = "application:prev:";
    public static final String BACK_MARKER = "application:back";
    private final EasyTransportPlugin plugin;

    public ApplicationMenu(EasyTransportPlugin plugin) { this.plugin = plugin; }

    public void openList(Player player, int page) {
        List<StopApplication> apps = plugin.applications().getApplications();
        int perPage = 45;
        int maxPages = Math.max(1, (apps.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, maxPages - 1));

        int start = page * perPage;
        int pageCount = Math.min(perPage, Math.max(0, apps.size() - start));
        int applicationRows = Math.max(1, (pageCount + 8) / 9);
        int size = (applicationRows + 1) * 9;
        Inventory inv = Bukkit.createInventory(null, size, Component.text(LIST_TITLE + " — " + (page + 1) + "/" + maxPages));

        for (int i = 0; i < pageCount; i++) {
            StopApplication app = apps.get(start + i);
            ItemStack item = new ItemStack(regionWool(app.regionId()));
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(app.cityName(), NamedTextColor.WHITE));
            meta.lore(List.of(
                    Component.text("Назва: " + app.cityName(), NamedTextColor.GRAY),
                    Component.text("Вобласць: " + plugin.data().getRegionName(app.regionId()), NamedTextColor.GRAY),
                    Component.text("Транспарт: " + app.transport().displayName(), transportColor(app.transport())),
                    Component.text("Гулец: " + app.playerName(), NamedTextColor.GRAY),
                    Component.text(String.format("Каардынаты: %.2f %.2f %.2f", app.location().x(), app.location().y(), app.location().z()), NamedTextColor.GRAY),
                    Component.text("Свет: " + app.location().world(), NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Націсніце, каб адкрыць", NamedTextColor.YELLOW)
            ));
            meta.getPersistentDataContainer().set(plugin.menuKey(), PersistentDataType.STRING, LIST_MARKER_PREFIX + app.id());
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        int controlRow = applicationRows * 9;
        if (page > 0) inv.setItem(controlRow + 0, button(Material.ARROW, "Папярэдняя старонка", PAGE_PREV_PREFIX + (page - 1)));
        inv.setItem(controlRow + 4, button(Material.BARRIER, "Закрыць", "application:close"));
        if (page < maxPages - 1) inv.setItem(controlRow + 8, button(Material.ARROW, "Наступная старонка", PAGE_NEXT_PREFIX + (page + 1)));

        player.openInventory(inv);
    }

    public void openDetail(Player player, StopApplication app) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(DETAIL_PREFIX + app.cityName()));
        inv.setItem(10, button(Material.LIME_CONCRETE, "Ухваліць", APPROVE_MARKER_PREFIX + app.id()));
        inv.setItem(13, button(Material.NAME_TAG, "Тэлепартавацца", TELEPORT_MARKER_PREFIX + app.id()));
        inv.setItem(16, button(Material.RED_CONCRETE, "Адмовіць", DENY_MARKER_PREFIX + app.id()));
        inv.setItem(22, button(Material.ARROW, "Назад да заявак", BACK_MARKER));
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

    private NamedTextColor transportColor(TransportType type) {
        return switch (type) {
            case AIR -> NamedTextColor.BLUE;
            case BUS -> NamedTextColor.YELLOW;
            case TRAIN -> NamedTextColor.GREEN;
        };
    }

    private Material regionWool(String regionId) {
        return switch (regionId.toLowerCase()) {
            case "minsk" -> Material.RED_WOOL;
            case "gomel" -> Material.MAGENTA_WOOL;
            case "brest" -> Material.LIGHT_BLUE_WOOL;
            case "vitebsk" -> Material.GREEN_WOOL;
            case "mogilev" -> Material.YELLOW_WOOL;
            case "grodno" -> Material.ORANGE_WOOL;
            case "abroad" -> Material.WHITE_WOOL;
            default -> Material.WHITE_WOOL;
        };
    }
}
