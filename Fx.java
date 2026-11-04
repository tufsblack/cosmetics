package nl.cosmetics;

import org.bukkit.Location;

import java.util.Random;

/** Eén effect. mult is de sterkte (0.5 laag, 1 normaal, 2 hoog). */
@FunctionalInterface
public interface Fx {
    void play(Sink s, Location at, Dye dye, double mult, Random r);
}
