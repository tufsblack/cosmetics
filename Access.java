package nl.cosmetics;

/** Hoe een speler aan een cosmetic komt. */
public enum Access {
    NONE, GIVEN, RANK, BOTH;

    public boolean owned() {
        return this != NONE;
    }

    public static Access of(boolean given, boolean rank) {
        if (given && rank) return BOTH;
        if (given) return GIVEN;
        if (rank) return RANK;
        return NONE;
    }
}
