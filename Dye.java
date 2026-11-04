package nl.cosmetics;

import org.bukkit.Color;
import org.bukkit.Material;

import java.util.List;
import java.util.Random;

/** Een kleur die een speler voor zijn effecten kan kiezen. */
public record Dye(String name, Material icon, int rgb, boolean rainbow) {

    public static final List<Dye> ALL = List.of(
            new Dye("Wit", Material.WHITE_DYE, 0xFFFFFF, false),
            new Dye("Rood", Material.RED_DYE, 0xFF3B3B, false),
            new Dye("Oranje", Material.ORANGE_DYE, 0xFF9A2E, false),
            new Dye("Geel", Material.YELLOW_DYE, 0xFFE62E, false),
            new Dye("Groen", Material.LIME_DYE, 0x4DFF4D, false),
            new Dye("Aqua", Material.CYAN_DYE, 0x2EE6E6, false),
            new Dye("Blauw", Material.BLUE_DYE, 0x3B6BFF, false),
            new Dye("Paars", Material.PURPLE_DYE, 0xA83BFF, false),
            new Dye("Roze", Material.PINK_DYE, 0xFF6BC1, false),
            new Dye("Regenboog", Material.NETHER_STAR, 0xFFFFFF, true));

    /** De regenboog-kleur, voor effecten die altijd regenboog zijn. */
    public static final Dye RAINBOW = ALL.get(ALL.size() - 1);

    /** Kleur op een plek in de lijst; loopt netjes rond. */
    public static Dye at(int index) {
        return ALL.get(Math.floorMod(index, ALL.size()));
    }

    /** De echte kleur voor één deeltje (bij regenboog een willekeurige tint). */
    public Color color(Random r) {
        if (!rainbow) return Color.fromRGB(rgb);
        return Color.fromRGB(hsv(r.nextFloat()));
    }

    static int hsv(float h) {
        float x = (h % 1f) * 6f;
        int i = (int) Math.floor(x);
        float f = x - i;
        float q = 1f - f;
        float rr, gg, bb;
        switch (i % 6) {
            case 0 -> { rr = 1; gg = f; bb = 0; }
            case 1 -> { rr = q; gg = 1; bb = 0; }
            case 2 -> { rr = 0; gg = 1; bb = f; }
            case 3 -> { rr = 0; gg = q; bb = 1; }
            case 4 -> { rr = f; gg = 0; bb = 1; }
            default -> { rr = 1; gg = 0; bb = q; }
        }
        return (Math.round(rr * 255) << 16) | (Math.round(gg * 255) << 8) | Math.round(bb * 255);
    }
}
