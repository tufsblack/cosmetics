package nl.cosmetics;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Alles wat de plugin over één speler onthoudt. */
public final class Profile {
    public final UUID id;
    public String name;
    public boolean enabled = true;
    /** Cosmetics die een admin gegeven heeft, als "soort:id". Rechten via rangen staan hier niet in. */
    public final Set<String> granted = new LinkedHashSet<>();
    public final Map<Category, Setting> settings = new EnumMap<>(Category.class);

    public Profile(UUID id, String name) {
        this.id = id;
        this.name = name == null ? "" : name;
        for (Category c : Category.values()) settings.put(c, new Setting());
    }

    public Setting setting(Category c) {
        return settings.get(c);
    }

    public boolean isGranted(Cosmetic c) {
        return granted.contains(c.key());
    }

    public boolean give(Cosmetic c) {
        return granted.add(c.key());
    }

    public boolean take(Cosmetic c) {
        return granted.remove(c.key());
    }

    /** Is er niets bijzonders om op te slaan? */
    public boolean isDefault() {
        if (!enabled || !granted.isEmpty()) return false;
        for (Setting s : settings.values()) if (!s.isDefault()) return false;
        return true;
    }

    /** Zet alle aanpassingen terug. Gegeven cosmetics blijven. */
    public void resetSettings() {
        enabled = true;
        for (Category c : Category.values()) settings.put(c, new Setting());
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("uuid", id.toString());
        m.put("naam", name);
        m.put("aan", enabled);
        m.put("bezit", new ArrayList<>(granted));
        for (Category c : Category.values()) m.put(c.key, settings.get(c).toMap());
        return m;
    }

    public static Profile fromMap(Map<?, ?> m, List<String> problems) {
        Object u = m.get("uuid");
        UUID id;
        try {
            id = UUID.fromString(String.valueOf(u));
        } catch (IllegalArgumentException ex) {
            problems.add("ongeldige uuid '" + u + "'");
            return null;
        }
        Object n = m.get("naam");
        Profile p = new Profile(id, n == null ? "" : n.toString());
        p.enabled = !(m.get("aan") instanceof Boolean b) || b;
        if (m.get("bezit") instanceof List<?> l) {
            for (Object o : l) {
                if (o != null && !o.toString().isBlank()) p.granted.add(o.toString().trim().toLowerCase(Locale.ROOT));
            }
        }
        for (Category c : Category.values()) {
            if (m.get(c.key) instanceof Map<?, ?> sm) p.settings.get(c).load(sm);
        }
        return p;
    }

    /** Nieuwe lege lijst van de gegeven sleutels, voor gebruik bij het opruimen. */
    public Set<String> grantedCopy() {
        return new LinkedHashSet<>(granted);
    }
}
