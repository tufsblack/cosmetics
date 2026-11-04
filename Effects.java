package nl.cosmetics;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;

import java.util.Random;

/**
 * De effecten zelf. Elk effect gebruikt alleen deeltjes en geluid: er verandert niets in de wereld.
 * <p>
 * Kill-effecten spelen één keer. Boog-sporen worden voor elk stukje van de pijlbaan aangeroepen (ongeveer elke
 * 0,35 blok), zodat het spoor ook bij een snelle pijl een doorlopende lijn is. De klap bij inslag is een eigen effect.
 */
final class Effects {
    private Effects() {
    }

    static int n(int base, double mult) {
        return Math.max(1, (int) Math.round(base * mult));
    }

    /** Willekeurige punten op een bol met gekleurd stof. Bij een regenboog-kleur krijgt elk punt een eigen kleur. */
    static void sphere(Sink s, Location l, Dye d, int count, double radius, float size, Random r) {
        for (int i = 0; i < count; i++) {
            double x = r.nextGaussian(), y = r.nextGaussian(), z = r.nextGaussian();
            double len = Math.sqrt(x * x + y * y + z * z);
            if (len < 1e-6) continue;
            s.dust(l.clone().add(x / len * radius, y / len * radius, z / len * radius), 1, 0, 0, 0, d.color(r), size);
        }
    }

    // ---------------------------------------------------------------- kill-effecten

    static final Fx KILL_BLIKSEM = (s, l, d, m, r) -> {
        for (double y = -0.8; y <= 3.2; y += 0.25) {
            s.particle(Particle.ELECTRIC_SPARK, l.clone().add(0, y, 0), n(4, m), 0.25, 0.05, 0.25, 0.4);
        }
        s.particle(Particle.END_ROD, l.clone().add(0, 3.2, 0), n(14, m), 0.3, 0.1, 0.3, 0.12);
        s.particle(Particle.CLOUD, l, n(8, m), 0.4, 0.3, 0.4, 0.05);
        s.sound(l, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.5f);
    };

    static final Fx KILL_HARTEN = (s, l, d, m, r) -> {
        s.particle(Particle.HEART, l, n(18, m), 0.7, 0.7, 0.7, 0);
        s.particle(Particle.HEART, l.clone().add(0, 1.0, 0), n(8, m), 0.4, 0.3, 0.4, 0);
        s.sound(l, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
    };

    static final Fx KILL_VLAMMEN = (s, l, d, m, r) -> {
        s.particle(Particle.FLAME, l, n(80, m), 0.5, 0.7, 0.5, 0.12);
        s.particle(Particle.SMALL_FLAME, l, n(30, m), 0.7, 0.5, 0.7, 0.05);
        s.particle(Particle.LAVA, l, n(10, m), 0.4, 0.4, 0.4, 0);
        s.sound(l, Sound.ENTITY_BLAZE_SHOOT, 0.6f, 1.2f);
    };

    static final Fx KILL_ZIELEN = (s, l, d, m, r) -> {
        s.particle(Particle.SOUL, l, n(30, m), 0.4, 0.9, 0.4, 0.06);
        s.particle(Particle.SOUL_FIRE_FLAME, l, n(30, m), 0.4, 0.7, 0.4, 0.06);
        s.sound(l, Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 1f);
    };

    static final Fx KILL_EXPLOSIE = (s, l, d, m, r) -> {
        s.particle(Particle.EXPLOSION, l, n(4, m), 0.7, 0.7, 0.7, 0);
        s.particle(Particle.CLOUD, l, n(40, m), 0.5, 0.5, 0.5, 0.2);
        s.particle(Particle.SMOKE, l, n(25, m), 0.5, 0.5, 0.5, 0.1);
        s.sound(l, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.3f);
    };

    static final Fx KILL_TOTEM = (s, l, d, m, r) -> {
        s.particle(Particle.TOTEM_OF_UNDYING, l, n(70, m), 0.4, 0.8, 0.4, 0.55);
        s.sound(l, Sound.ITEM_TOTEM_USE, 0.5f, 1.2f);
    };

    static final Fx KILL_STERREN = (s, l, d, m, r) -> {
        s.particle(Particle.END_ROD, l, n(50, m), 0.4, 0.9, 0.4, 0.15);
        s.particle(Particle.ENCHANT, l, n(40, m), 0.6, 1.0, 0.6, 0.5);
        s.sound(l, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
    };

    static final Fx KILL_VUURWERK = (s, l, d, m, r) -> {
        s.particle(Particle.FIREWORK, l, n(60, m), 0.15, 0.15, 0.15, 0.22);
        s.particle(Particle.FIREWORK, l.clone().add(0, 0.8, 0), n(25, m), 0.1, 0.1, 0.1, 0.12);
        s.sound(l, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1f);
        s.sound(l, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.8f, 1f);
    };

    /** Bol van gekleurde stofdeeltjes in de kleur van de speler, met een tweede kleinere bol erbinnen. */
    static final Fx KILL_KLEUR = (s, l, d, m, r) -> {
        sphere(s, l, d, n(90, m), 1.1, 1.6f, r);
        sphere(s, l, d, n(40, m), 0.55, 1.3f, r);
        s.sound(l, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.9f, 1.1f);
    };

    /** Dubbele spiraal omhoog in de kleur van de speler. */
    static final Fx KILL_SPIRAAL = (s, l, d, m, r) -> {
        int steps = n(36, m);
        for (int i = 0; i < steps; i++) {
            double t = (double) i / steps;
            double ang = t * Math.PI * 5;
            double y = -0.6 + t * 2.6;
            s.dust(l.clone().add(Math.cos(ang) * 0.8, y, Math.sin(ang) * 0.8), 1, 0, 0, 0, d.color(r), 1.5f);
            s.dust(l.clone().add(Math.cos(ang + Math.PI) * 0.8, y, Math.sin(ang + Math.PI) * 0.8), 1, 0, 0, 0, d.color(r), 1.5f);
        }
        s.particle(Particle.END_ROD, l.clone().add(0, 2.0, 0), n(10, m), 0.2, 0.2, 0.2, 0.08);
        s.sound(l, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 0.9f);
    };

    // ---------------------------------------------------------------- boog-sporen (voor elk stukje van de pijlbaan)

    static final Fx TRAIL_VLAM = (s, l, d, m, r) -> {
        s.particle(Particle.FLAME, l, n(3, m), 0.05, 0.05, 0.05, 0.01);
        if (r.nextDouble() < 0.25 * m) s.particle(Particle.SMOKE, l, 1, 0.05, 0.05, 0.05, 0.01);
    };

    static final Fx TRAIL_HARTEN = (s, l, d, m, r) -> {
        if (r.nextDouble() < 0.3 * m) s.particle(Particle.HEART, l, 1, 0.12, 0.12, 0.12, 0);
    };

    static final Fx TRAIL_KLEUR = (s, l, d, m, r) -> s.dust(l, n(3, m), 0.07, 0.07, 0.07, d.color(r), 1.4f);

    static final Fx TRAIL_REGENBOOG = (s, l, d, m, r) -> {
        int c = n(3, m);
        for (int i = 0; i < c; i++) s.dust(l, 1, 0.07, 0.07, 0.07, Dye.RAINBOW.color(r), 1.4f);
    };

    static final Fx TRAIL_STERREN = (s, l, d, m, r) -> {
        s.particle(Particle.END_ROD, l, n(1, m), 0.05, 0.05, 0.05, 0.02);
        if (r.nextDouble() < 0.4 * m) s.particle(Particle.ENCHANT, l, 2, 0.2, 0.2, 0.2, 0.2);
    };

    static final Fx TRAIL_NOTEN = (s, l, d, m, r) -> {
        if (r.nextDouble() < 0.35 * m) s.particle(Particle.NOTE, l, 1, 0.12, 0.12, 0.12, 0);
    };

    static final Fx TRAIL_WOLK = (s, l, d, m, r) -> s.particle(Particle.CLOUD, l, n(2, m), 0.05, 0.05, 0.05, 0.005);

    static final Fx TRAIL_ELEKTRISCH = (s, l, d, m, r) -> s.particle(Particle.ELECTRIC_SPARK, l, n(3, m), 0.12, 0.12, 0.12, 0.3);

    static final Fx TRAIL_ENDER = (s, l, d, m, r) -> s.particle(Particle.PORTAL, l, n(6, m), 0.15, 0.15, 0.15, 0.4);

    static final Fx TRAIL_ZIELEN = (s, l, d, m, r) -> {
        s.particle(Particle.SOUL_FIRE_FLAME, l, n(2, m), 0.05, 0.05, 0.05, 0.01);
        if (r.nextDouble() < 0.2 * m) s.particle(Particle.SOUL, l, 1, 0.1, 0.1, 0.1, 0.02);
    };

    // ---------------------------------------------------------------- klap bij inslag van de pijl

    static final Fx HIT_VLAM = (s, l, d, m, r) -> {
        s.particle(Particle.FLAME, l, n(30, m), 0.3, 0.3, 0.3, 0.08);
        s.particle(Particle.LAVA, l, n(4, m), 0.2, 0.2, 0.2, 0);
        s.sound(l, Sound.ENTITY_BLAZE_SHOOT, 0.4f, 1.4f);
    };

    static final Fx HIT_HARTEN = (s, l, d, m, r) -> s.particle(Particle.HEART, l, n(8, m), 0.4, 0.4, 0.4, 0);

    static final Fx HIT_KLEUR = (s, l, d, m, r) -> {
        sphere(s, l, d, n(40, m), 0.6, 1.4f, r);
        s.sound(l, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.5f, 1.4f);
    };

    static final Fx HIT_REGENBOOG = HIT_KLEUR;

    static final Fx HIT_STERREN = (s, l, d, m, r) -> {
        s.particle(Particle.END_ROD, l, n(25, m), 0.3, 0.3, 0.3, 0.1);
        s.sound(l, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.6f);
    };

    static final Fx HIT_NOTEN = (s, l, d, m, r) -> {
        s.particle(Particle.NOTE, l, n(8, m), 0.4, 0.4, 0.4, 0);
        s.sound(l, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.2f);
    };

    static final Fx HIT_WOLK = (s, l, d, m, r) -> s.particle(Particle.CLOUD, l, n(20, m), 0.3, 0.3, 0.3, 0.06);

    static final Fx HIT_ELEKTRISCH = (s, l, d, m, r) -> {
        s.particle(Particle.ELECTRIC_SPARK, l, n(40, m), 0.35, 0.35, 0.35, 0.5);
        s.sound(l, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.3f, 1.8f);
    };

    static final Fx HIT_ENDER = (s, l, d, m, r) -> {
        s.particle(Particle.PORTAL, l, n(50, m), 0.4, 0.4, 0.4, 0.8);
        s.sound(l, Sound.ENTITY_ENDERMAN_TELEPORT, 0.3f, 1.6f);
    };

    static final Fx HIT_ZIELEN = (s, l, d, m, r) -> {
        s.particle(Particle.SOUL, l, n(12, m), 0.3, 0.4, 0.3, 0.05);
        s.particle(Particle.SOUL_FIRE_FLAME, l, n(20, m), 0.3, 0.3, 0.3, 0.05);
        s.sound(l, Sound.PARTICLE_SOUL_ESCAPE, 0.6f, 1.2f);
    };
}
