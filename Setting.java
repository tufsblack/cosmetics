package nl.cosmetics;

import java.util.LinkedHashMap;
import java.util.Map;

/** Wat een speler gekozen heeft binnen één soort, plus zijn aanpassingen. */
public final class Setting {
    public String selected;
    public int color;
    public Intensity intensity = Intensity.NORMAAL;
    public boolean visible = true;
    /** Kill-effecten: werkt het als je een speler verslaat / een mob verslaat? */
    public boolean onPlayers = true;
    public boolean onMobs = true;

    public boolean isDefault() {
        return selected == null && color == 0 && intensity == Intensity.NORMAAL && visible && onPlayers && onMobs;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (selected != null) m.put("gekozen", selected);
        m.put("kleur", color);
        m.put("sterkte", intensity.name());
        m.put("zichtbaar", visible);
        m.put("bij-spelers", onPlayers);
        m.put("bij-mobs", onMobs);
        return m;
    }

    public void load(Map<?, ?> m) {
        Object s = m.get("gekozen");
        selected = s == null || s.toString().isBlank() ? null : s.toString().trim().toLowerCase(java.util.Locale.ROOT);
        Object c = m.get("kleur");
        color = c instanceof Number n ? Math.floorMod(n.intValue(), Dye.ALL.size()) : 0;
        Object i = m.get("sterkte");
        intensity = Intensity.parse(i == null ? null : i.toString());
        Object v = m.get("zichtbaar");
        visible = !(v instanceof Boolean b) || b;
        onPlayers = !(m.get("bij-spelers") instanceof Boolean bp) || bp;
        onMobs = !(m.get("bij-mobs") instanceof Boolean bm) || bm;
    }
}
