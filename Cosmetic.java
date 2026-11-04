package nl.cosmetics;

import org.bukkit.Material;

import java.util.List;

/** Eén cosmetic uit de catalogus. */
public final class Cosmetic {
    public final Category cat;
    public final String id;
    public final String name;
    public final Material icon;
    public final List<String> lore;
    /** Gebruikt dit effect de kleur die de speler kiest? */
    public final boolean colorable;
    /** Kill-effect, of de klap waar een boog-effect mee eindigt. Null bij hoeden. */
    public final Fx burst;
    /** Spoor achter de pijl. Alleen bij boog-effecten. */
    public final Fx trail;
    /** Naam van het 3D-model in het resourcepack ("crown_klassiek_goud"), of null voor een gewoon item. */
    public final String modelKey;

    Cosmetic(Category cat, String id, String name, Material icon, String desc, boolean colorable, Fx burst, Fx trail, String modelKey) {
        this.cat = cat;
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.lore = List.of(desc);
        this.colorable = colorable;
        this.burst = burst;
        this.trail = trail;
        this.modelKey = modelKey;
    }

    /** Heeft deze cosmetic een eigen 3D-model (en dus het resourcepack nodig)? */
    public boolean isModel() {
        return modelKey != null;
    }

    /** Uniek, bv. "hat:kroon". */
    public String key() {
        return cat.key + ":" + id;
    }

    /** De permissie waarmee een rang deze cosmetic geeft (LuckPerms), bv. "cosmetics.hat.kroon". */
    public String permission() {
        return "cosmetics." + cat.key + "." + id;
    }
}
