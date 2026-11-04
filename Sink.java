package nl.cosmetics;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;

/** Waar de deeltjes en geluiden heen gaan: naar de hele wereld of alleen naar één speler. */
public interface Sink {
    void particle(Particle p, Location l, int count, double ox, double oy, double oz, double speed);

    void dust(Location l, int count, double ox, double oy, double oz, Color color, float size);

    void sound(Location l, Sound s, float volume, float pitch);
}
