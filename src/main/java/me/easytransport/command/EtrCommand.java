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
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.scheduler.BukkitTask;

public final class EtrCommand implements CommandExecutor, TabCompleter {
    private final EasyTransportPlugin plugin;
    private final Map<UUID, BukkitTask> pendingCashierDeleteConfirmations = new ConcurrentHashMap<>();

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
            player.sendMessage("/etr world add/delete/list/info/name/transport/baseprice/basetime/bind ...");
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
            case "world" -> world(player, args);
            default -> player.sendMessage(ChatMessages.red("Невядомая падкаманда."));
        }
        return true;
    }

    private void cashier(Player player, String[] args) {
        if (args.length == 2 && args[1].equalsIgnoreCase("deleteall")) {
            requestDeleteAllCashiers(player);
            return;
        }
        if (args.length != 3) { player.sendMessage("Выкарыстанне: /etr cashier create/delete/deleteall bus/train/air"); return; }
        TransportType type = TransportType.fromKey(args[2]);
        if (type == null) { player.sendMessage(ChatMessages.red("Транспарт: bus, train, air.")); return; }
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
        if (args.length == 2 && args[1].equalsIgnoreCase("deleteall")) {
            plugin.data().removeAllStops();
            player.sendMessage(ChatMessages.green("Усе зарэгістраваныя прыпынкі выдалены."));
            return;
        }
        if (args.length < 5) { player.sendMessage("Выкарыстанне: /etr stop create/delete/deleteall bus/train/air <Вобласць> <Назва пункта>"); return; }
        TransportType type = TransportType.fromKey(args[2]);
        if (type == null) { player.sendMessage("Транспарт: bus, train, air."); return; }
        String region = plugin.data().findRegionId(args[3]);
        if (region == null) { player.sendMessage(ChatMessages.red("Вобласць не знойдзена.")); return; }
        String city = String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length));
        if (args[1].equalsIgnoreCase("create")) {
            String world = player.getWorld().getName();
            if (!plugin.isManagedWorld(world) || !plugin.worldAllowsTransport(world, type)) {
                player.sendMessage(ChatMessages.red("Пункт нельга стварыць: гэты свет або від транспарту не абслугоўваецца EasyTransport."));
                return;
            }
            if (plugin.isAbroad(world)) {
                if (type != TransportType.AIR || !region.equalsIgnoreCase("abroad")) {
                    player.sendMessage(ChatMessages.red("У Замежжы можна ствараць толькі авіяцыйныя пункты вобласці «Замежжа»."));
                    return;
                }
            } else if (region.equalsIgnoreCase("abroad")) {
                if (!plugin.isWorld(world) || type != TransportType.AIR) {
                    player.sendMessage(ChatMessages.red("Пункты вобласці «Замежжа» ў Беларускім краі могуць быць толькі авіяцыйнымі."));
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
            if (!plugin.isManagedWorld(world) || !plugin.worldAllowsTransport(world, type)) {
                player.sendMessage(ChatMessages.red("Прамежкавая кропка нельга стварыць: гэты свет або від транспарту не абслугоўваецца EasyTransport."));
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

    private void world(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage("Выкарыстанне: /etr world add/delete/list/info/name/transport/baseprice/basetime/bind ..."); return; }
        String sub = args[1].toLowerCase();
        if (sub.equals("bind")) {
            if (args.length != 4 || !(args[2].equalsIgnoreCase("belarus") || args[2].equalsIgnoreCase("abroad"))) {
                player.sendMessage("Выкарыстанне: /etr world bind <belarus|abroad> <ІмяСвету>");
                return;
            }
            String targetWorld = args[3];
            if (plugin.getServer().getWorld(targetWorld) == null) {
                player.sendMessage(ChatMessages.red("Мір «" + targetWorld + "» не знойдзены на серверы."));
                return;
            }
            if (plugin.bindRoleWorld(args[2], targetWorld)) {
                player.sendMessage(ChatMessages.worldRoleBound(args[2], targetWorld));
            } else {
                player.sendMessage(ChatMessages.red("Не ўдалося змяніць мір для гэтай ролі."));
            }
            return;
        }
        if (sub.equals("list")) {
            var section = plugin.getConfig().getConfigurationSection("worlds");
            if (section == null) { player.sendMessage(ChatMessages.red("Няма наладжаных светаў.")); return; }
            player.sendMessage(ChatMessages.green("Наладжаныя светы EasyTransport:"));
            for (String worldName : section.getKeys(false)) {
                player.sendMessage(ChatMessages.worldListEntry(plugin.worldDisplayName(worldName), worldName));
            }
            return;
        }
        if (sub.equals("add")) {
            if (args.length != 3) { player.sendMessage("Выкарыстанне: /etr world add <ІмяСвету>"); return; }
            String worldName = args[2];
            if (plugin.isManagedWorld(worldName)) { player.sendMessage(ChatMessages.red("Мір «" + worldName + "» ужо дададзены ў EasyTransport.")); return; }
            if (plugin.getServer().getWorld(worldName) == null) { player.sendMessage(ChatMessages.red("Мір «" + worldName + "» не знойдзены на серверы.")); return; }
            plugin.addManagedWorld(worldName);
            player.sendMessage(ChatMessages.worldAdded(worldName));
            return;
        }
        if (sub.equals("delete")) {
            if (args.length != 3) { player.sendMessage("Выкарыстанне: /etr world delete <ІмяСвету>"); return; }
            String worldName = args[2];
            if (!plugin.isManagedWorld(worldName)) { player.sendMessage(ChatMessages.red("Мір «" + worldName + "» не зарэгістраваны ў EasyTransport.")); return; }
            String belarusWorld = plugin.getConfig().getString("settings.world-belarus", "world");
            if (worldName.equalsIgnoreCase(belarusWorld) || worldName.equalsIgnoreCase(plugin.abroadWorld())) {
                player.sendMessage(ChatMessages.red("Беларускі край і Замежжа нельга выдаліць з EasyTransport.")); return;
            }
            if (plugin.data().hasStopsInWorld(worldName)) { player.sendMessage(ChatMessages.red("Нельга выдаліць мір: у ім яшчэ ёсць зарэгістраваныя прыпынкі.")); return; }
            plugin.removeManagedWorld(worldName);
            player.sendMessage(ChatMessages.worldDeleted(worldName));
            return;
        }
        if (sub.equals("info") || sub.equals("name") || sub.equals("transport") || sub.equals("baseprice") || sub.equals("basetime")) {
            if (args.length < 3) {
                player.sendMessage(ChatMessages.red("Не пазначаны свет."));
                return;
            }
            if (!plugin.isManagedWorld(args[2])) {
                player.sendMessage(ChatMessages.red("Мір «" + args[2] + "» не зарэгістраваны ў EasyTransport."));
                return;
            }
        }
        String worldName = args.length > 2 ? args[2] : "";
        switch (sub) {
            case "info" -> {
                player.sendMessage(ChatMessages.worldInfoHeader(plugin.worldDisplayName(worldName), worldName));
                player.sendMessage(ChatMessages.worldInfoTransport("Аўтобус", plugin.worldAllowsTransport(worldName, TransportType.BUS)));
                player.sendMessage(ChatMessages.worldInfoTransport("Цягнік", plugin.worldAllowsTransport(worldName, TransportType.TRAIN)));
                player.sendMessage(ChatMessages.worldInfoTransport("Самалёт", plugin.worldAllowsTransport(worldName, TransportType.AIR)));
                player.sendMessage(ChatMessages.worldInfoValue("Базавая цана", plugin.worldBasePrice(worldName) + " BYN"));
                player.sendMessage(ChatMessages.worldInfoValue("Базавы час", plugin.worldBaseTime(worldName) + " с."));
            }
            case "name" -> {
                if (args.length < 4) { player.sendMessage("Выкарыстанне: /etr world name <ІмяСвету> <БеларускаяНазва>"); return; }
                String displayName = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
                plugin.setWorldDisplayName(worldName, displayName);
                player.sendMessage(ChatMessages.worldNameChanged(displayName));
            }
            case "transport" -> {
                if (args.length != 5 || TransportType.fromKey(args[3]) == null) { player.sendMessage("Выкарыстанне: /etr world transport <ІмяСвету> <bus|train|air> <on|off>"); return; }
                TransportType type = TransportType.fromKey(args[3]);
                boolean enabled = args[4].equalsIgnoreCase("on");
                if (!enabled && !args[4].equalsIgnoreCase("off")) { player.sendMessage(ChatMessages.red("Выкарыстайце on або off.")); return; }
                plugin.setWorldTransport(worldName, type, enabled);
                player.sendMessage(ChatMessages.worldTransportChanged(type.displayName(), enabled));
            }
            case "baseprice" -> {
                if (args.length != 4) { player.sendMessage("Выкарыстанне: /etr world baseprice <ІмяСвету> <Цана>"); return; }
                try { double v = Double.parseDouble(args[3]); if (v < 0) throw new NumberFormatException(); plugin.setWorldBasePrice(worldName, v); player.sendMessage(ChatMessages.worldBasePriceChanged(v)); }
                catch (NumberFormatException ex) { player.sendMessage(ChatMessages.red("Цана павінна быць неадмоўным лікам.")); }
            }
            case "basetime" -> {
                if (args.length != 4) { player.sendMessage("Выкарыстанне: /etr world basetime <ІмяСвету> <Секунды>"); return; }
                try { long v = Long.parseLong(args[3]); if (v < 0) throw new NumberFormatException(); plugin.setWorldBaseTime(worldName, v); player.sendMessage(ChatMessages.worldBaseTimeChanged(v)); }
                catch (NumberFormatException ex) { player.sendMessage(ChatMessages.red("Базавы час павінен быць неадмоўным цэлым лікам.")); }
            }
            default -> player.sendMessage("Выкарыстанне: /etr world add/delete/list/info/name/transport/baseprice/basetime ...");
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

    private void requestDeleteAllCashiers(Player player) {
        BukkitTask old = pendingCashierDeleteConfirmations.remove(player.getUniqueId());
        if (old != null) old.cancel();
        player.sendMessage(ChatMessages.goldBold("Усе білетары будуць выдалены. Напішыце ў чат ПАЦВЕРДЖАЮ на працягу 120 секунд."));
        BukkitTask timeout = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            BukkitTask task = pendingCashierDeleteConfirmations.remove(player.getUniqueId());
            if (task != null && player.isOnline()) player.sendMessage(ChatMessages.red("Час на пацвярджэнне выдалення білетараў скончыўся."));
        }, 120L * 20L);
        pendingCashierDeleteConfirmations.put(player.getUniqueId(), timeout);
    }

    public boolean handleCashierDeleteConfirmation(Player player, String message) {
        BukkitTask pending = pendingCashierDeleteConfirmations.remove(player.getUniqueId());
        if (pending == null) return false;
        pending.cancel();
        if (!message.trim().equalsIgnoreCase("ПАЦВЕРДЖАЮ")) {
            player.sendMessage(ChatMessages.red("Выдаленне білетараў адменена. Для пацвярджэння неабходна напісаць ПАЦВЕРДЖАЮ."));
            return true;
        }
        for (World world : plugin.getServer().getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (villager.getPersistentDataContainer().has(plugin.cashierKey(), PersistentDataType.STRING)) {
                    villager.remove();
                }
            }
        }
        plugin.data().removeAllCashiers();
        plugin.rebuildCashierIndex();
        player.sendMessage(ChatMessages.green("Усе білетары выдалены."));
        return true;
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
            return partial(args[0], List.of("cashier", "stop", "betweentp", "coast", "speed", "particles", "application", "discord", "request", "world"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("cashier")
                || args[0].equalsIgnoreCase("stop")
                || args[0].equalsIgnoreCase("betweentp")))
            return partial(args[1], args[0].equalsIgnoreCase("cashier") ? List.of("create", "delete", "deleteall") : List.of("create", "delete", "deleteall"));
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
        if (args[0].equalsIgnoreCase("world") && args.length == 2)
            return partial(args[1], List.of("add", "delete", "list", "info", "name", "transport", "baseprice", "basetime", "bind"));
        if (args[0].equalsIgnoreCase("world") && args.length == 3 && List.of("delete", "info", "name", "transport", "baseprice", "basetime").contains(args[1].toLowerCase()))
            return partial(args[2], plugin.getConfig().getConfigurationSection("worlds") == null ? List.of() : plugin.getConfig().getConfigurationSection("worlds").getKeys(false).stream().toList());
        if (args[0].equalsIgnoreCase("world") && args.length == 3 && args[1].equalsIgnoreCase("add"))
            return partial(args[2], plugin.getServer().getWorlds().stream().map(w -> w.getName()).filter(w -> !plugin.isManagedWorld(w)).toList());
        if (args[0].equalsIgnoreCase("world") && args.length == 3 && args[1].equalsIgnoreCase("bind"))
            return partial(args[2], List.of("belarus", "abroad"));
        if (args[0].equalsIgnoreCase("world") && args.length == 4 && args[1].equalsIgnoreCase("bind"))
            return partial(args[3], plugin.getServer().getWorlds().stream().map(w -> w.getName()).toList());
        if (args[0].equalsIgnoreCase("world") && args.length == 4 && args[1].equalsIgnoreCase("transport"))
            return partial(args[3], List.of("bus", "train", "air"));
        if (args[0].equalsIgnoreCase("world") && args.length == 5 && args[1].equalsIgnoreCase("transport"))
            return partial(args[4], List.of("on", "off"));
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
