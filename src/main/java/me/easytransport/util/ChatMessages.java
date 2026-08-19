package me.easytransport.util;

import me.easytransport.model.TransportType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class ChatMessages {
    private ChatMessages() {}
    public static Component red(String text) { return Component.text(text, NamedTextColor.RED); }
    public static Component green(String text) { return Component.text(text, NamedTextColor.GREEN); }
    public static Component gold(String text) { return Component.text(text, NamedTextColor.GOLD); }
    public static Component white(String text) { return Component.text(text, NamedTextColor.WHITE); }
    public static Component yellow(String text) { return Component.text(text, NamedTextColor.YELLOW); }
    public static Component stopCreated(String city, String transport, String regionId) {
        return Component.text("Пункт «", NamedTextColor.GREEN)
                .append(Component.text(city, regionColor(regionId)))
                .append(Component.text("» створаны для ", NamedTextColor.GREEN))
                .append(Component.text(transport, NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                .append(Component.text(".", NamedTextColor.GREEN));
    }

    public static Component stopDeleted(String city, String transport, String regionId) {
        return Component.text("Пункт «", NamedTextColor.GREEN)
                .append(Component.text(city, regionColor(regionId)))
                .append(Component.text("» выдалены для ", NamedTextColor.GREEN))
                .append(Component.text(transport, NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                .append(Component.text(".", NamedTextColor.GREEN));
    }
    public static Component price(String transport, String value, String suffix) {
        return Component.text("Цана ", NamedTextColor.GREEN)
                .append(Component.text(transport, NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
                .append(Component.text(": ", NamedTextColor.GREEN))
                .append(Component.text(value, NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
                .append(Component.text(suffix, NamedTextColor.GREEN));
    }
    public static Component speed(String transport, String value) {
        return Component.text("Хуткасць ", NamedTextColor.GREEN)
                .append(Component.text(transport, NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
                .append(Component.text(": ", NamedTextColor.GREEN))
                .append(Component.text(value, NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
                .append(Component.text(" блокаў/с.", NamedTextColor.GREEN));
    }
    public static Component approved(String city) {
        return Component.text("Заяўка «", NamedTextColor.GREEN)
                .append(Component.text(city, NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
                .append(Component.text("» ухваленая.", NamedTextColor.GREEN));
    }
    public static Component rejected(String city) {
        return Component.text("Заяўка «", NamedTextColor.RED)
                .append(Component.text(city, NamedTextColor.RED).decorate(TextDecoration.BOLD))
                .append(Component.text("» адхілена.", NamedTextColor.RED));
    }
    public static Component playerApproved(String city) {
        return Component.text("Ваша заяўка на прыпынак «", NamedTextColor.GOLD)
                .append(Component.text(city, NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                .append(Component.text("» была ўхвалена і пункт цяпер даступны для паездак.", NamedTextColor.GOLD));
    }
    public static Component playerRejected(String city, String reason) {
        return Component.text("Ваша заяўка на прыпынак «", NamedTextColor.RED).decorate(TextDecoration.BOLD)
                .append(Component.text(city, NamedTextColor.RED).decorate(TextDecoration.BOLD))
                .append(Component.text("» была адхілена. Прычына: ", NamedTextColor.RED).decorate(TextDecoration.BOLD))
                .append(Component.text(reason, NamedTextColor.RED).decorate(TextDecoration.BOLD));
    }
    public static Component approvalNeedsCashier(String transport) {
        return Component.text("Нельга ўхваліць заяўку: у радыусе 50 блокаў ад пункта няма білетара патрэбнага тыпу (", NamedTextColor.RED)
                .append(Component.text(transport, NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
                .append(Component.text(").", NamedTextColor.RED));
    }
    public static Component newApplication(String city, String playerName) {
        return Component.text("Новая заяўка на прыпынак «", NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
                .append(Component.text(city, NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
                .append(Component.text("» ад ", NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                .append(Component.text(playerName, NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
                .append(Component.text(". ", NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                .append(Component.text("/etr application", NamedTextColor.WHITE));
    }
    public static Component activeApplications(int count) {
        return Component.text("Зараз актыўна ", NamedTextColor.GOLD)
                .append(Component.text(String.valueOf(count), NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                .append(Component.text(" заявак на прыпынкі. Увядзіце ", NamedTextColor.GOLD))
                .append(Component.text("/etr application", NamedTextColor.WHITE));
    }

    public static Component priceValueOnly(String prefix, String value, String suffix) {
        return Component.text(prefix, NamedTextColor.GREEN)
                .append(Component.text(value, NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
                .append(Component.text(suffix, NamedTextColor.GREEN));
    }

    public static Component insufficientFunds(String prefix, String price) {
        return Component.text(prefix, NamedTextColor.RED)
                .append(Component.text(price, NamedTextColor.RED).decorate(TextDecoration.BOLD));
    }

    public static Component denialPrompt() {
        return Component.text("Напішыце ў чат прычыну адмовы. Яна будзе бачная толькі вам і заяўніку. У вас ёсць 120 секунд.", NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
    }

    public static Component activeApplications(int count, boolean unused) { return activeApplications(count); }

    public static Component discordStatus(boolean enabled, String url) {
        return Component.text("Discord webhook: ", NamedTextColor.WHITE)
                .append(Component.text(enabled ? "уключаны" : "выключаны", enabled ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text(". URL: ", NamedTextColor.WHITE))
                .append(Component.text(url, NamedTextColor.WHITE));
    }
    public static String serialize(Component component) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(component);
    }

    public static Component deserialize(String legacy) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(legacy);
    }

    public static TextColor transportColor(TransportType type) {
        return switch (type) { case AIR -> NamedTextColor.BLUE; case BUS -> NamedTextColor.YELLOW; case TRAIN -> NamedTextColor.GREEN; };
    }
    public static TextColor regionColor(String regionId) {
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
}
