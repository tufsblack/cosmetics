package nl.cosmetics;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Alle cosmetics die de plugin kent. */
public final class Catalog {
    private final List<Cosmetic> all = new ArrayList<>();

    public List<Cosmetic> all() {
        return Collections.unmodifiableList(all);
    }

    public List<Cosmetic> of(Category c) {
        List<Cosmetic> out = new ArrayList<>();
        for (Cosmetic x : all) if (x.cat == c) out.add(x);
        return out;
    }

    public Cosmetic find(Category c, String id) {
        if (id == null) return null;
        for (Cosmetic x : all) if (x.cat == c && x.id.equalsIgnoreCase(id.trim())) return x;
        return null;
    }

    /** Zoekt op sleutel ("hat:kroon"). */
    public Cosmetic findKey(String key) {
        if (key == null) return null;
        for (Cosmetic x : all) if (x.key().equalsIgnoreCase(key.trim())) return x;
        return null;
    }

    /** Zoekt op "hat:kroon" of alleen "kroon". Bij een korte naam die in meer soorten voorkomt komt er niets terug. */
    public List<Cosmetic> findLoose(String text) {
        List<Cosmetic> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;
        String t = text.trim().toLowerCase(Locale.ROOT);
        Cosmetic exact = findKey(t);
        if (exact != null) {
            out.add(exact);
            return out;
        }
        for (Cosmetic x : all) if (x.id.equals(t)) out.add(x);
        return out;
    }

    private void add(Category c, String id, String name, Material icon, String desc, boolean colorable, Fx burst, Fx trail) {
        all.add(new Cosmetic(c, id, name, icon, desc, colorable, burst, trail, null));
    }

    /** Een kroon of hoed met eigen 3D-model. Het icoon is de vervanging voor spelers zonder resourcepack. */
    void addModel(Category c, String id, String name, String desc, Material fallback) {
        all.add(new Cosmetic(c, id, name, fallback, desc, false, null, null, c.key + "_" + id));
    }

    public static Catalog standard() {
        Catalog k = new Catalog();
        // 3D-kronen en 3D-hoeden uit het resourcepack.
        Models.addTo(k);
        // Gewone hoeden van vanilla-items: puur visueel, staat niet in je inventaris.
        k.add(Category.HAT, "pompoen", "Pompoen", Material.CARVED_PUMPKIN, "Een uitgesneden pompoen op je hoofd.", false, null, null);
        k.add(Category.HAT, "draak", "Drakenkop", Material.DRAGON_HEAD, "Pas op, hij bijt.", false, null, null);
        k.add(Category.HAT, "creeper", "Creeper", Material.CREEPER_HEAD, "Ssssss...", false, null, null);
        k.add(Category.HAT, "skelet", "Skelet", Material.SKELETON_SKULL, "Een grijnzende schedel.", false, null, null);
        k.add(Category.HAT, "zombie", "Zombie", Material.ZOMBIE_HEAD, "Hersens...", false, null, null);
        k.add(Category.HAT, "wither", "Wither-skelet", Material.WITHER_SKELETON_SKULL, "Donker en gevaarlijk.", false, null, null);
        k.add(Category.HAT, "piglin", "Piglin", Material.PIGLIN_HEAD, "Goud, goud, goud.", false, null, null);
        k.add(Category.HAT, "tnt", "TNT", Material.TNT, "Niet aansteken!", false, null, null);
        k.add(Category.HAT, "beacon", "Baken", Material.BEACON, "Je straalt gewoon.", false, null, null);
        k.add(Category.HAT, "diamant", "Diamantblok", Material.DIAMOND_BLOCK, "Een hoofd vol diamant.", false, null, null);
        k.add(Category.HAT, "glas", "Glazen bol", Material.GLASS, "Een doorzichtige helm.", false, null, null);

        // Kill-effecten: spelen op de plek waar iemand doodgaat.
        k.add(Category.KILL, "bliksem", "Bliksem", Material.LIGHTNING_ROD, "Elektrische vonken en een donderslag.", false, Effects.KILL_BLIKSEM, null);
        k.add(Category.KILL, "harten", "Harten", Material.POPPY, "Een wolk van hartjes.", false, Effects.KILL_HARTEN, null);
        k.add(Category.KILL, "vlammen", "Vlammen", Material.BLAZE_POWDER, "Een uitbarsting van vuur.", false, Effects.KILL_VLAMMEN, null);
        k.add(Category.KILL, "zielen", "Zielen", Material.SOUL_LANTERN, "Zielen stijgen op.", false, Effects.KILL_ZIELEN, null);
        k.add(Category.KILL, "explosie", "Explosie", Material.FIRE_CHARGE, "Een knal zonder schade.", false, Effects.KILL_EXPLOSIE, null);
        k.add(Category.KILL, "totem", "Totem", Material.TOTEM_OF_UNDYING, "Het totem-effect.", false, Effects.KILL_TOTEM, null);
        k.add(Category.KILL, "sterren", "Sterren", Material.END_ROD, "Glinsterende sterren.", false, Effects.KILL_STERREN, null);
        k.add(Category.KILL, "vuurwerk", "Vuurwerk", Material.FIREWORK_ROCKET, "Vonken van vuurwerk.", false, Effects.KILL_VUURWERK, null);
        k.add(Category.KILL, "kleur", "Kleurbol", Material.FIREWORK_STAR, "Een bol in jouw eigen kleur.", true, Effects.KILL_KLEUR, null);
        k.add(Category.KILL, "spiraal", "Spiraal", Material.AMETHYST_SHARD, "Een draaiende spiraal in jouw kleur.", true, Effects.KILL_SPIRAAL, null);

        // Boog-effecten: een spoor achter je pijl.
        k.add(Category.BOW, "vlam", "Vuurspoor", Material.BLAZE_POWDER, "Je pijl laat vlammetjes achter.", false, Effects.HIT_VLAM, Effects.TRAIL_VLAM);
        k.add(Category.BOW, "harten", "Hartjesspoor", Material.POPPY, "Hartjes achter je pijl.", false, Effects.HIT_HARTEN, Effects.TRAIL_HARTEN);
        k.add(Category.BOW, "kleur", "Kleurspoor", Material.FIREWORK_STAR, "Een spoor in jouw eigen kleur.", true, Effects.HIT_KLEUR, Effects.TRAIL_KLEUR);
        k.add(Category.BOW, "regenboog", "Regenboogspoor", Material.NETHER_STAR, "Alle kleuren tegelijk.", false, Effects.HIT_REGENBOOG, Effects.TRAIL_REGENBOOG);
        k.add(Category.BOW, "sterren", "Sterrenspoor", Material.END_ROD, "Een glinsterend spoor.", false, Effects.HIT_STERREN, Effects.TRAIL_STERREN);
        k.add(Category.BOW, "noten", "Muzieknoten", Material.NOTE_BLOCK, "Je pijl zingt.", false, Effects.HIT_NOTEN, Effects.TRAIL_NOTEN);
        k.add(Category.BOW, "wolk", "Wolkenspoor", Material.FEATHER, "Een lichte witte sliert.", false, Effects.HIT_WOLK, Effects.TRAIL_WOLK);
        k.add(Category.BOW, "elektrisch", "Elektrisch", Material.LIGHTNING_ROD, "Knetterende vonken.", false, Effects.HIT_ELEKTRISCH, Effects.TRAIL_ELEKTRISCH);
        k.add(Category.BOW, "ender", "Ender", Material.ENDER_PEARL, "Paarse portaaldeeltjes.", false, Effects.HIT_ENDER, Effects.TRAIL_ENDER);
        k.add(Category.BOW, "zielen", "Zielenvuur", Material.SOUL_LANTERN, "Blauw zielenvuur.", false, Effects.HIT_ZIELEN, Effects.TRAIL_ZIELEN);
        return k;
    }
}
