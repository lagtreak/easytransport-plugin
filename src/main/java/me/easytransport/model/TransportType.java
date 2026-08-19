package me.easytransport.model;

import org.bukkit.Material;
import org.bukkit.entity.Villager;

import java.util.Locale;

public enum TransportType {
    BUS("bus", "Аўтобус", 202.5, 1.0, Villager.Type.JUNGLE, Material.BRICK),
    TRAIN("train", "Цягнік", 337.5, 3.0, Villager.Type.SAVANNA, Material.MINECART),
    AIR("air", "Самалёт", 675.0, 6.0, Villager.Type.SNOW, Material.ELYTRA);

    private final String key;
    private final String displayName;
    private final double defaultSpeed;
    private final double defaultPrice;
    private final Villager.Type villagerType;
    private final Material menuMaterial;

    TransportType(String key, String displayName, double defaultSpeed, double defaultPrice,
                  Villager.Type villagerType, Material menuMaterial) {
        this.key = key;
        this.displayName = displayName;
        this.defaultSpeed = defaultSpeed;
        this.defaultPrice = defaultPrice;
        this.villagerType = villagerType;
        this.menuMaterial = menuMaterial;
    }

    public String key() { return key; }
    public String displayName() { return displayName; }
    public double defaultSpeed() { return defaultSpeed; }
    public double defaultPrice() { return defaultPrice; }
    public Villager.Type villagerType() { return villagerType; }
    public Material menuMaterial() { return menuMaterial; }

    public static TransportType fromKey(String input) {
        if (input == null) return null;
        String normalized = input.toLowerCase(Locale.ROOT);
        for (TransportType type : values()) {
            if (type.key.equals(normalized)) return type;
        }
        return null;
    }
}
