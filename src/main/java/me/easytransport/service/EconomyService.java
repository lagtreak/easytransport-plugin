package me.easytransport.service;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

public final class EconomyService {
    private final Economy economy;

    public EconomyService(Economy economy) {
        this.economy = economy;
    }

    public double balance(Player player) {
        return economy.getBalance(player);
    }

    public boolean has(Player player, double amount) {
        return balance(player) + 1.0e-9 >= amount;
    }

    public boolean withdraw(Player player, double amount) {
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public String format(double amount) {
        return economy.format(amount);
    }
}
