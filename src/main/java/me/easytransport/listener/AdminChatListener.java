package me.easytransport.listener;

import me.easytransport.command.EtrCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class AdminChatListener implements Listener {
    private final EtrCommand command;

    public AdminChatListener(EtrCommand command) {
        this.command = command;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (command.handleCashierDeleteConfirmation(event.getPlayer(), event.getMessage())) {
            event.setCancelled(true);
        }
    }
}
