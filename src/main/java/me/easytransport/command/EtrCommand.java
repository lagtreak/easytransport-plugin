package me.easytransport.command;

import me.easytransport.EasyTransportPlugin;
import me.easytransport.model.Stop;
import me.easytransport.model.StoredLocation;
import me.easytransport.model.TransportType;
import me.easytransport.util.ChatMessages;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.List;

public final class EtrCommand implements CommandExecutor, TabCompleter {
    private final EasyTransportPlugin plugin;

    public EtrCommand(EasyTransportPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatMessages.red("Толькі гулец можа выкарыстоўваць гэтую каманду."));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("particles")) {
            particles(player, args);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("request")
                && args.length > 1 && args[1].equalsIgnoreCase("create")) {
            applicationRequest(player, args);
            return true;
        }
        if (!player.isOp()) {
            player.sendMessage(ChatMessages.red("Толькі аператар можа выкарыстоўваць гэтую падкаманду."));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("/etr cashier create/delete bus/train/air");
            player.sendMessage("/etr stop create/delete bus/train/air <Вобласць> <Назва пункта>");
            player.sendMessage("/etr betweentp create/delete bus/train/air");
            player.sendMessage("/etr coast bus/train/air <Цана>");
            player.sendMessage("/etr coast abroadbase <Цана>");
            player.sendMessage("/etr speed bus/train/air <Хуткасць>");
            player.sendMessage("/etr particles - уключыць/адключыць часціцы над галавой");
            player.sendMessage("/etr application - спіс актыўных заявак");
            player.sendMessage("/etr request create bus/train/air <Вобласць> <Назва> - падаць заяўку");
            player.sendMessage("/etr discord webhook <URL> | status | test | off | sync");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "cashier" -> cashier(player, args);
            case "stop" -> stop(player, args);
            case "betweentp" -> between(player, args);
            case "coast" -> coast(player, args);
            case "speed" -> speed(player, args);
            case "discord" -> discord(player, args);
            case "particles" -> particles(player, args);
            case "application" -> application(player, args);
            case "request" -> applicationRequest(player, args);
            default -> player.sendMessage(ChatMessages.red("Невядомая падкаманда."));
        }
        return true;
    }

    private void cashier(Player player, String[] args) {
        if (args.length != 3) { player.sendMessage("Выкарыстанне: /etr cashier create/delete bus/train/air"); return; }
        TransportType type = TransportType.fromKey(args[2]);
        if (type == null) { player.sendMessage("Транспарт: bus, train, air."); return; }
        if (args[1].equalsIgnoreCase("create")) {
            Location playerLocation = player.getLocation();
            Location spawnLocation = playerLocation.getBlock().getLocation().add(0.5, 0.0, 0.5);
            float snappedYaw = Math.round(normalizeYaw(playerLocation.getYaw()) / 90.0f) * 90.0f;
            spawnLocation.setYaw(snappedYaw);
            spawnLocation.setPitch(0.0f);
            Villager villager = player.getWorld().spawn(spawnLocation, Villager.class);
            villager.getPersistentDataContainer().set(plugin.cashierKey(), PersistentDataType.STRING, type.key());
            villager.setVillagerType(type.villagerType());
            villager.setProfession(Villager.Profession.FISHERMAN);
            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setRemoveWhenFarAway(false);
            villager.setCanPickupItems(false);
            villager.setAdult();
            villager.setAgeLock(true);
            villager.setBreed(false);
            villager.customName(net.kyori.adventure.text.Component.text("Білетар", net.kyori.adventure.text.format.NamedTextColor.GOLD));
            villager.setCustomNameVisible(true);
            plugin.data().saveCashier(type, villager);
            plugin.rebuildCashierIndex();
            player.sendMessage(ChatMessages.green("Білетар створаны."));
        } else if (args[1].equalsIgnoreCase("delete")) {
            Villager target = null;
            double best = 5.0 * 5.0;
            for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
                if (!(entity instanceof Villager villager)) continue;
                String value = villager.getPersistentDataContainer().get(plugin.cashierKey(), PersistentDataType.STRING);
                if (!type.key().equals(value)) continue;
                double d = villager.getLocation().distanceSquared(player.getLocation());
                if (d < best) { best = d; target = villager; }
            }
            if (target == null) { player.sendMessage(ChatMessages.red("Побач няма патрэбнага білетара.")); return; }
            target.remove();
            plugin.data().removeCashier(type);
            plugin.rebuildCashierIndex();
            player.sendMessage(ChatMessages.green("Білетар выдалены."));
        } else player.sendMessage("Выкарыстанне: /etr cashier create/delete bus/train/air");
    }

    private void stop(Player player, String[] args) {
        if (args.length < 5) { player.sendMessage("Выкарыстанне: /etr stop create/delete bus/train/air <Вобласць> <Назва пункта>"); return; }
        TransportType type = TransportType.fromKey(args[2]);
        if (type == null) { player.sendMessage("Транспарт: bus, train, air."); return; }
        String region = plugin.data().findRegionId(args[3]);
        if (region == null) { player.sendMessage(ChatMessages.red("Вобласць не знойдзена.")); return; }
        String city = String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length));
        if (args[1].equalsIgnoreCase("create")) {
            String world = player.getWorld().getName();
            if (!plugin.isWorld(world) && !plugin.isAbroad(world)) {
                player.sendMessage(ChatMessages.red("Пункты можна ствараць толькі ў свеце world або ў Замежжы для самалётаў."));
                return;
            }
            if (plugin.isAbroad(world)) {
                if (type != TransportType.AIR || !region.equalsIgnoreCase("abroad")) {
                    player.sendMessage(ChatMessages.red("У Замежжы можна ствараць толькі авіяцыйныя пункты вобласці «Замежжа»."));
                    return;
                }
            } else {
                if (!plugin.isWorld(world)) return;
                if (region.equalsIgnoreCase("abroad")) {
                    player.sendMessage(ChatMessages.red("Пункт вобласці «Замежжа» можна ствараць толькі ў свеце abroad."));
                    return;
                }
            }
            plugin.data().saveStop(new Stop(region, city, type, StoredLocation.from(player.getLocation())));
            player.sendMessage(ChatMessages.stopCreated(city, type.displayName(), region));
        } else if (args[1].equalsIgnoreCase("delete")) {
            if (plugin.data().findStop(region, type, city) == null) { player.sendMessage(ChatMessages.red("Пункт не знойдзены.")); return; }
            plugin.data().removeStop(region, type, city);
            player.sendMessage(ChatMessages.stopDeleted(city, type.displayName(), region));
        } else player.sendMessage("Выкарыстанне: /etr stop create/delete bus/train/air <Вобласць> <Назва пункта>");
    }

    private void between(Player player, String[] args) {
        if (args.length != 3) { player.sendMessage("Выкарыстанне: /etr betweentp create/delete bus/train/air"); return; }
        TransportType type = TransportType.fromKey(args[2]);
        if (type == null) { player.sendMessage("Транспарт: bus, train, air."); return; }
        if (args[1].equalsIgnoreCase("create")) {
            String world = player.getWorld().getName();
            if (!plugin.isWorld(world) && !(type == TransportType.AIR && plugin.isAbroad(world))) {
                player.sendMessage(ChatMessages.red("Прамежкавыя пункты можна ствараць у world, а для самалёта таксама ў abroad."));
                return;
            }
            plugin.data().saveBetween(type, player.getLocation());
            player.sendMessage(ChatMessages.green("Прамежкавая кропка захаваная."));
        } else if (args[1].equalsIgnoreCase("delete")) {
            plugin.data().removeBetween(type);
            player.sendMessage(ChatMessages.green("Прамежкавая кропка выдалена."));
        } else player.sendMessage("Выкарыстанне: /etr betweentp create/delete bus/train/air");
    }

    private void coast(Player player, String[] args) {
        if (args.length == 3 && args[1].equalsIgnoreCase("abroadbase")) {
            setAbroadBasePrice(player, args[2]);
            return;
        }
        if (args.length != 3) {
            player.sendMessage("Выкарыстанне: /etr coast bus/train/air <Цана> або /etr coast abroadbase <Цана>");
            return;
        }
        TransportType type = TransportType.fromKey(args[1]);
        if (type == null) {
            player.sendMessage("Транспарт: bus, train, air.");
            return;
        }
        try {
            double price = Double.parseDouble(args[2]);
            if (price < 0) throw new NumberFormatException();
            plugin.setPrice(type, price);
            player.sendMessage(ChatMessages.price(type.displayName(), String.valueOf(price), " за 200 блокаў."));
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatMessages.red("Цана павінна быць неадмоўным лікам."));
        }
    }

    private void setAbroadBasePrice(Player player, String raw) {
        try {
            double price = Double.parseDouble(raw);
            if (price < 0) throw new NumberFormatException();
            plugin.setAbroadBasePrice(price);
            player.sendMessage(ChatMessages.priceValueOnly("Базавая цана Замежжа: ", String.valueOf(price), "."));
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatMessages.red("Цана павінна быць неадмоўным лікам."));
        }
    }

    private void speed(Player player, String[] args) {
        if (args.length != 3) {
            player.sendMessage("Выкарыстанне: /etr speed bus/train/air <Хуткасць>");
            return;
        }
        TransportType type = TransportType.fromKey(args[1]);
        if (type == null) {
            player.sendMessage("Транспарт: bus, train, air.");
            return;
        }
        try {
            double speed = Double.parseDouble(args[2]);
            if (speed <= 0) throw new NumberFormatException();
            plugin.setSpeed(type, speed);
            player.sendMessage(ChatMessages.speed(type.displayName(), String.valueOf(speed)));
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatMessages.red("Хуткасць павінна быць лікам большым за 0."));
        }
    }

    private void application(Player player, String[] args) {
        if (args.length == 1) {
            plugin.getApplicationMenu().openList(player, 0);
            return;
        }
        plugin.getApplicationMenu().openList(player, 0);
    }

    private void applicationRequest(Player player, String[] args) {
        if (args.length < 5 || !args[1].equalsIgnoreCase("create")) {
            player.sendMessage("Выкарыстанне: /etr request create bus/train/air <Вобласць> <Назва пункта>");
            return;
        }
        TransportType type = TransportType.fromKey(args[2]);
        if (type == null) {
            player.sendMessage("Транспарт: bus, train, air.");
            return;
        }
        String region = plugin.data().findRegionId(args[3]);
        if (region == null) {
            player.sendMessage(ChatMessages.red("Вобласць не знойдзена."));
            return;
        }
        String city = String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length));
        plugin.applicationsService().create(player, type, region, city);
    }

    private void discord(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage(ChatMessages.red("Толькі аператар можа кіраваць Discord webhook."));
            return;
        }
        if (args.length == 1 || args[1].equalsIgnoreCase("status")) {
            player.sendMessage(ChatMessages.discordStatus(plugin.discord().isConfigured(), plugin.discord().maskedWebhookUrl()));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "webhook" -> {
                if (args.length != 3) {
                    player.sendMessage("Выкарыстанне: /etr discord webhook <URL>");
                    return;
                }
                String url = args[2].trim();
                if (!url.startsWith("https://discord.com/api/webhooks/") && !url.startsWith("https://discordapp.com/api/webhooks/")) {
                    player.sendMessage(ChatMessages.red("Няправільны URL Discord webhook."));
                    return;
                }
                plugin.discord().setWebhookUrl(url);
                player.sendMessage(ChatMessages.green("Discord webhook URL захаваны. Сінхранізацыя запушчана."));
                plugin.discord().syncApplications();
            }
            case "test" -> {
                if (!plugin.discord().isConfigured()) {
                    player.sendMessage(ChatMessages.red("Discord webhook не настроены."));
                    return;
                }
                player.sendMessage(ChatMessages.green("Тэставы запыт у Discord адпраўлены."));
                plugin.discord().test();
            }
            case "sync" -> {
                if (!plugin.discord().isConfigured()) {
                    player.sendMessage(ChatMessages.red("Discord webhook не настроены."));
                    return;
                }
                player.sendMessage(ChatMessages.green("Сінхранізацыя Discord запушчана."));
                plugin.discord().syncApplications();
            }
            case "off" -> {
                plugin.discord().disable();
                player.sendMessage(ChatMessages.red("Discord webhook адключаны, URL выдалены з config.yml."));
            }
            default -> player.sendMessage("Выкарыстанне: /etr discord webhook <URL> | status | test | off | sync");
        }
    }

    private void particles(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage("Выкарыстанне: /etr particles");
            return;
        }
        boolean enabled = plugin.toggleParticles(player);
        player.sendMessage(enabled
                ? ChatMessages.green("Часціцы над галавой уключаныя.")
                : ChatMessages.green("Часціцы над галавой адключаныя."));
    }

    private float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0f;
        if (normalized < 0.0f) normalized += 360.0f;
        return normalized;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            if (!(sender instanceof Player p) || !p.isOp()) {
                return partial(args[0], List.of("request", "particles"));
            }
            return partial(args[0], List.of("cashier", "stop", "betweentp", "coast", "speed", "particles", "application", "discord", "request"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("cashier")
                || args[0].equalsIgnoreCase("stop")
                || args[0].equalsIgnoreCase("betweentp")))
            return partial(args[1], List.of("create", "delete"));
        if ((args[0].equalsIgnoreCase("cashier") || args[0].equalsIgnoreCase("betweentp") || args[0].equalsIgnoreCase("speed")) && args.length == 3)
            return partial(args[2], List.of("bus", "train", "air"));
        if (args[0].equalsIgnoreCase("coast") && args.length == 2)
            return partial(args[1], List.of("bus", "train", "air", "abroadbase"));
        if (args[0].equalsIgnoreCase("stop") && args.length == 3)
            return partial(args[2], List.of("bus", "train", "air"));
        if (args[0].equalsIgnoreCase("stop") && args.length == 4)
            return partial(args[3], plugin.data().getRegionIds().stream().map(plugin.data()::getRegionName).toList());
        if (args[0].equalsIgnoreCase("discord") && args.length == 2)
            return partial(args[1], List.of("webhook", "status", "test", "sync", "off"));
        if (args[0].equalsIgnoreCase("request") && args.length == 2)
            return partial(args[1], List.of("create"));
        if (args[0].equalsIgnoreCase("request") && args.length == 3 && args[1].equalsIgnoreCase("create"))
            return partial(args[2], List.of("bus", "train", "air"));
        if (args[0].equalsIgnoreCase("request") && args.length == 4 && args[1].equalsIgnoreCase("create"))
            return partial(args[3], plugin.data().getRegionIds().stream()
                    .filter(id -> plugin.isRegionAvailableForTransport(TransportType.fromKey(args[2]), id))
                    .map(plugin.data()::getRegionName).toList());
        return Collections.emptyList();
    }

    private List<String> partial(String token, List<String> values) {
        String lower = token.toLowerCase();
        return values.stream().filter(v -> v.toLowerCase().startsWith(lower)).toList();
    }
}
