package me.easytransport.listener;

import me.easytransport.EasyTransportPlugin;
import me.easytransport.model.TransportType;
import me.easytransport.menu.TransportMenu;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;

public final class CashierListener implements Listener {
    private static final String CASHIER_NAME = "Білетэр";
    private static final String[] OLD_NAMES = {"Бiлетар", "Билетар", "Білетар", "Бiлетэр"};
    private final EasyTransportPlugin plugin;
    private final TransportMenu menu;

    public CashierListener(EasyTransportPlugin plugin, TransportMenu menu) { this.plugin = plugin; this.menu = menu; }

    @EventHandler public void onInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Villager villager)) return;
        String key = villager.getPersistentDataContainer().get(plugin.cashierKey(), PersistentDataType.STRING);
        if (key == null) return;
        renameIfNeeded(villager);
        event.setCancelled(true);
        TransportType type = TransportType.fromKey(key);
        if (type != null) menu.openRegions(event.getPlayer(), type);
    }

    @EventHandler public void onDamage(EntityDamageEvent event) { if (event.getEntity() instanceof Villager v && isCashier(v)) event.setCancelled(true); }
    @EventHandler public void onTransform(EntityTransformEvent event) { if (event.getEntity() instanceof Villager v && isCashier(v)) event.setCancelled(true); }

    @EventHandler public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Villager v && isCashier(v)) { configure(v); renameIfNeeded(v); }
        }
    }

    private void configure(Villager v) {
        v.setAI(false); v.setInvulnerable(true); v.setRemoveWhenFarAway(false); v.setCanPickupItems(false);
        v.setProfession(Villager.Profession.FISHERMAN); v.setAdult(); v.setAgeLock(true); v.setBreed(false);
    }

    private void renameIfNeeded(Villager v) {
        String name = v.customName() == null ? "" : v.customName().toString();
        for (String oldName : OLD_NAMES) {
            if (name.contains(oldName)) { v.setCustomName(CASHIER_NAME); v.setCustomNameVisible(true); return; }
        }
        if (name.isBlank()) { v.setCustomName(CASHIER_NAME); v.setCustomNameVisible(true); }
    }

    private boolean isCashier(Villager v) { return v.getPersistentDataContainer().has(plugin.cashierKey(), PersistentDataType.STRING); }
}
