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
    private final EasyTransportPlugin plugin;
    private final TransportMenu menu;

    public CashierListener(EasyTransportPlugin plugin, TransportMenu menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Villager villager)) return;
        String key = villager.getPersistentDataContainer().get(plugin.cashierKey(), PersistentDataType.STRING);
        if (key == null) return;
        event.setCancelled(true);
        TransportType type = TransportType.fromKey(key);
        if (type != null) menu.openRegions(event.getPlayer(), type);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Villager villager && isCashier(villager)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onTransform(EntityTransformEvent event) {
        if (event.getEntity() instanceof Villager villager && isCashier(villager)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Villager villager && isCashier(villager)) {
                villager.setAI(false);
                villager.setInvulnerable(true);
                villager.setRemoveWhenFarAway(false);
                villager.setCanPickupItems(false);
                villager.setProfession(Villager.Profession.FISHERMAN);
                villager.setAdult();
                villager.setAgeLock(true);
                villager.setBreed(false);
            }
        }
    }

    private boolean isCashier(Villager villager) {
        return villager.getPersistentDataContainer().has(plugin.cashierKey(), PersistentDataType.STRING);
    }
}
