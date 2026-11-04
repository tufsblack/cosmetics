package nl.cosmetics;

import org.bukkit.Material;

import java.util.Locale;

/** De soorten cosmetics. Kronen en hoeden delen het hoofd: je draagt er maar één tegelijk. */
public enum Category {
    CROWN("crown", "Kronen", Material.GOLDEN_HELMET),
    HAT("hat", "Hoeden", Material.LEATHER_HELMET),
    KILL("kill", "Kill-effecten", Material.DIAMOND_SWORD),
    BOW("bow", "Boog-effecten", Material.BOW);

    public final String key;
    public final String label;
    public final Material icon;

    Category(String key, String label, Material icon) {
        this.key = key;
        this.label = label;
        this.icon = icon;
    }

    /** Wordt dit op het hoofd gedragen? */
    public boolean isHead() {
        return this == CROWN || this == HAT;
    }

    /** Heeft deze soort instellingen (kleur, sterkte, zichtbaarheid)? Dragen niet. */
    public boolean hasSettings() {
        return !isHead();
    }

    public static Category parse(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "crown", "crowns", "kroon", "kronen" -> CROWN;
            case "hat", "hats", "hoed", "hoeden" -> HAT;
            case "kill", "kills", "killeffect", "kill-effecten" -> KILL;
            case "bow", "boog", "bogen", "boog-effecten" -> BOW;
            default -> null;
        };
    }
}
