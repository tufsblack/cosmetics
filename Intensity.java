package nl.cosmetics;

import java.util.Locale;

/** Hoeveel deeltjes een effect gebruikt. */
public enum Intensity {
    LAAG("Laag", 0.5), NORMAAL("Normaal", 1.0), HOOG("Hoog", 2.0);

    public final String label;
    public final double factor;

    Intensity(String label, double factor) {
        this.label = label;
        this.factor = factor;
    }

    public Intensity next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public Intensity prev() {
        return values()[(ordinal() + values().length - 1) % values().length];
    }

    public static Intensity parse(String raw) {
        if (raw == null) return NORMAAL;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return NORMAAL;
        }
    }
}
