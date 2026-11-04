package nl.cosmetics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Alle profielen in het geheugen. */
public final class ProfileStore {
    private final Map<UUID, Profile> map = new LinkedHashMap<>();

    public Profile get(UUID id) {
        return map.get(id);
    }

    public Profile getOrCreate(UUID id, String name) {
        Profile p = map.get(id);
        if (p == null) {
            p = new Profile(id, name);
            map.put(id, p);
        } else if (name != null && !name.isBlank()) {
            p.name = name;
        }
        return p;
    }

    /** Zet (of vervangt) een profiel, bijvoorbeeld als het uit de gedeelde database komt. */
    public void put(Profile p) {
        map.put(p.id, p);
    }

    /** Een kopie van alle profielen, veilig om doorheen te lopen. */
    public List<Profile> all() {
        return new ArrayList<>(map.values());
    }

    public Profile findByName(String name) {
        if (name == null) return null;
        for (Profile p : map.values()) if (p.name.equalsIgnoreCase(name)) return p;
        return null;
    }

    public int size() {
        return map.size();
    }

    /** Aantal spelers met minstens één gegeven cosmetic. */
    public int withGrants() {
        int n = 0;
        for (Profile p : map.values()) if (!p.granted.isEmpty()) n++;
        return n;
    }

    /** Alleen profielen waar iets in zit, zodat het bestand klein blijft. */
    public List<Map<String, Object>> toMaps() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Profile p : map.values()) if (!p.isDefault()) out.add(p.toMap());
        return out;
    }

    public static ProfileStore fromMaps(List<? extends Map<?, ?>> maps, List<String> problems) {
        ProfileStore s = new ProfileStore();
        for (Map<?, ?> m : maps) {
            try {
                Profile p = Profile.fromMap(m, problems);
                if (p != null) s.map.put(p.id, p);
            } catch (RuntimeException ex) {
                problems.add("kapotte speler-regel: " + ex.getMessage());
            }
        }
        return s;
    }

    public static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
