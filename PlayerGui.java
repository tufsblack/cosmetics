package nl.cosmetics;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Het menu waarin een speler zijn eigen cosmetics kiest en aanpast. */
final class PlayerGui {

    enum Screen { MAIN, CATEGORY, CUSTOM }

    private static final String T = "<gradient:#c471f5:#fa71cd><bold>Cosmetics</bold></gradient> <dark_gray>» ";
    private static final int PAGE = 45;

    final class Holder implements Menu {
        final Screen screen;
        final Category cat;
        int page;
        Inventory inv;

        Holder(Screen screen, Category cat) {
            this.screen = screen;
            this.cat = cat;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }

        @Override
        public void click(InventoryClickEvent e, Player p) {
            PlayerGui.this.click(this, e, p);
        }
    }

    private final CosmeticsPlugin pl;
    private final Kit k;

    PlayerGui(CosmeticsPlugin pl) {
        this.pl = pl;
        this.k = new Kit(pl);
    }

    // ------------------------------------------------------------ openen

    void openMain(Player p) {
        open(p, new Holder(Screen.MAIN, null), 36, "<gray>Jouw cosmetics");
    }

    void openCategory(Player p, Category cat, int page) {
        Holder h = new Holder(Screen.CATEGORY, cat);
        int pages = Kit.pageCount(listFor(p, cat).size(), PAGE);
        h.page = Math.max(0, Math.min(page, pages - 1));
        open(p, h, 54, "<gray>" + cat.label + " <dark_gray>(" + (h.page + 1) + "/" + pages + ")");
    }

    void openCustom(Player p, Category cat) {
        if (!cat.hasSettings()) {
            openCategory(p, cat, 0);
            return;
        }
        open(p, new Holder(Screen.CUSTOM, cat), 36, "<gray>" + cat.label + " aanpassen");
    }

    private void open(Player p, Holder h, int size, String title) {
        h.inv = Bukkit.createInventory(h, size, k.c(T + title));
        build(p, h);
        p.openInventory(h.inv);
    }

    private void build(Player p, Holder h) {
        Inventory inv = h.inv;
        inv.clear();
        ItemStack f = k.filler();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, f);
        switch (h.screen) {
            case MAIN -> buildMain(p, inv);
            case CATEGORY -> buildCategory(p, h, inv);
            case CUSTOM -> buildCustom(p, h, inv);
        }
    }

    // ------------------------------------------------------------ lijsten

    /** Wat de speler in deze soort ziet: eerst wat hij heeft, daarna (als de config dat wil) wat nog op slot zit. */
    private List<Cosmetic> listFor(Player p, Category cat) {
        Profile pf = pl.profile(p);
        List<Cosmetic> owned = new ArrayList<>();
        List<Cosmetic> locked = new ArrayList<>();
        for (Cosmetic c : pl.catalog.of(cat)) {
            if (pl.disabled(c)) continue;
            if (pl.access(p, pf, c).owned()) owned.add(c);
            else locked.add(c);
        }
        if (pl.toonVergrendeld()) owned.addAll(locked);
        return owned;
    }

    private int ownedCount(Player p, Category cat) {
        Profile pf = pl.profile(p);
        int n = 0;
        for (Cosmetic c : pl.catalog.of(cat)) if (pl.access(p, pf, c).owned()) n++;
        return n;
    }

    private int totalCount(Category cat) {
        int n = 0;
        for (Cosmetic c : pl.catalog.of(cat)) if (!pl.disabled(c)) n++;
        return n;
    }

    // ------------------------------------------------------------ opbouw

    private static final int[] MAIN_SLOTS = {10, 12, 14, 16};

    private void buildMain(Player p, Inventory inv) {
        Profile pf = pl.profile(p);
        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            Category cat = cats[i];
            Cosmetic sel = pl.selected(pf, cat);
            boolean active = sel != null && pl.access(p, pf, sel).owned();
            List<String> lore = new ArrayList<>();
            lore.add("<gray>In bezit: <white>" + ownedCount(p, cat) + "<gray>/<white>" + totalCount(cat));
            lore.add("<gray>Actief: " + (active ? "<green>" + Kit.esc(sel.name) : "<red>niets"));
            if (cat == Category.CROWN || cat == Category.HAT) lore.add("<dark_gray>Kroon en hoed passen niet samen.");
            lore.add("");
            lore.add("<yellow>Klik <gray>» openen");
            ItemStack it = k.icon(cat.icon, "<gradient:#c471f5:#fa71cd><bold>" + cat.label + "</bold></gradient>", lore);
            inv.setItem(MAIN_SLOTS[i], active ? k.glow(it) : it);
        }
        inv.setItem(4, k.icon(Material.NETHER_STAR, "<gradient:#c471f5:#fa71cd><bold>Jouw cosmetics</bold></gradient>",
                "<gray>Kies hieronder wat je wilt dragen", "<gray>of laten zien."));
        inv.setItem(28, k.icon(Material.BARRIER, "<red><bold>Alles uitzetten", "<gray>Je draagt en toont niets meer.", "<gray>Wat je bezit blijft gewoon van jou.", "", "<yellow>Klik <gray>» uitzetten"));
        boolean on = pf.enabled;
        inv.setItem(31, k.icon(on ? Material.LIME_DYE : Material.GRAY_DYE, (on ? "<green><bold>Cosmetics staan aan" : "<red><bold>Cosmetics staan uit"),
                "<gray>Zet al je cosmetics tegelijk aan of uit.", "<gray>Je keuzes blijven bewaard.", "", "<yellow>Klik <gray>» wisselen"));
        if (!pl.enabled()) {
            inv.setItem(8, k.icon(Material.REDSTONE_TORCH, "<red><bold>Tijdelijk uitgeschakeld", "<gray>Een admin heeft alle cosmetics", "<gray>even uitgezet."));
        }
        if (pl.packConfigured() && !pl.cfgBool("pack.extern", false)) {
            boolean ok = pl.packOk(p);
            inv.setItem(33, k.icon(ok ? Material.LIME_STAINED_GLASS : Material.ORANGE_STAINED_GLASS,
                    ok ? "<green><bold>Resourcepack geladen" : "<gold><bold>Resourcepack niet geladen",
                    ok ? new String[]{"<gray>Je ziet de 3D-kronen en 3D-hoeden.", "", "<yellow>Klik <gray>» opnieuw laden"}
                       : new String[]{"<gray>Zonder pack zie je een gewone helm", "<gray>in plaats van de 3D-kroon of -hoed.", "", "<yellow>Klik <gray>» pack laden"}));
        }
        if (p.hasPermission(CosmeticsPlugin.STAFF)) {
            inv.setItem(34, k.icon(Material.COMPARATOR, "<gold><bold>Staff-menu", "<gray>Cosmetics geven en afpakken.", "", "<yellow>Klik <gray>» openen"));
        }
    }

    private void buildCategory(Player p, Holder h, Inventory inv) {
        Profile pf = pl.profile(p);
        List<Cosmetic> list = listFor(p, h.cat);
        int pages = Kit.pageCount(list.size(), PAGE);
        h.page = Math.max(0, Math.min(h.page, pages - 1));
        Cosmetic sel = pl.selected(pf, h.cat);
        for (int i = 0; i < PAGE; i++) {
            int idx = h.page * PAGE + i;
            if (idx >= list.size()) break;
            inv.setItem(i, cosmeticIcon(p, pf, list.get(idx), sel));
        }
        if (list.isEmpty()) {
            inv.setItem(22, k.icon(Material.BARREL, "<gray><bold>Hier heb je nog niets van", "<gray>Vraag een admin of koop een rang", "<gray>om cosmetics te krijgen."));
        }
        if (h.cat.isHead() && pl.packConfigured() && !pl.packOk(p) && !pl.cfgBool("pack.extern", false)) {
            inv.setItem(52, k.icon(Material.ORANGE_STAINED_GLASS, "<gold><bold>Resourcepack niet geladen",
                    "<gray>Je ziet nu gewone helmen als vervanging.", "<gray>Laad het pack om de 3D-versie te zien.", "", "<yellow>Klik <gray>» pack laden"));
        }
        inv.setItem(45, h.page > 0 ? k.icon(Material.ARROW, "<yellow>« Vorige pagina") : k.filler());
        inv.setItem(47, k.icon(Material.BARRIER, "<red><bold>Niets dragen", "<gray>Zet " + h.cat.label.toLowerCase() + " uit.", "", "<yellow>Klik <gray>» uitzetten"));
        inv.setItem(48, k.icon(Material.OAK_DOOR, "<gray><bold>Terug", "<gray>Pagina <white>" + (h.page + 1) + "<gray>/<white>" + pages, "", "<yellow>Klik <gray>» hoofdmenu"));
        if (h.cat.hasSettings()) {
            inv.setItem(49, k.icon(Material.COMPARATOR, "<gold><bold>Aanpassen", "<gray>Kleur, sterkte en wie het ziet.", "", "<yellow>Klik <gray>» openen"));
            inv.setItem(50, k.icon(Material.FIREWORK_ROCKET, "<aqua><bold>Testen", "<gray>Laat je gekozen effect nu zien.", "", "<yellow>Klik <gray>» testen"));
        } else if (sel != null) {
            inv.setItem(49, k.glow(k.icon(pl.showItem(p, sel), "<green><bold>Je draagt: " + Kit.esc(sel.name), "<gray>Zichtbaar voor iedereen in de buurt.")));
        }
        inv.setItem(53, h.page < pages - 1 ? k.icon(Material.ARROW, "<yellow>Volgende pagina »") : k.filler());
    }

    private ItemStack cosmeticIcon(Player p, Profile pf, Cosmetic c, Cosmetic sel) {
        boolean owned = pl.access(p, pf, c).owned();
        if (!owned) {
            return k.icon(Material.GRAY_DYE, "<dark_gray><bold>" + Kit.esc(c.name), "<gray>" + c.lore.get(0), "", "<red>✖ Nog niet in bezit");
        }
        boolean active = sel != null && sel.id.equals(c.id);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + c.lore.get(0));
        if (c.colorable) lore.add("<dark_gray>Gebruikt jouw gekozen kleur.");
        lore.add("");
        lore.add(active ? "<green>● Actief" : "<gray>○ Niet actief");
        lore.add("<yellow>Klik <gray>» " + (active ? "uitzetten" : "kiezen"));
        ItemStack it = k.icon(pl.showItem(p, c), (active ? "<green><bold>" : "<white><bold>") + Kit.esc(c.name), lore);
        return active ? k.glow(it) : it;
    }

    private void buildCustom(Player p, Holder h, Inventory inv) {
        Profile pf = pl.profile(p);
        Setting st = pf.setting(h.cat);
        Cosmetic sel = pl.selected(pf, h.cat);
        if (sel != null) inv.setItem(4, k.glow(k.icon(sel.icon, "<green><bold>" + Kit.esc(sel.name), "<gray>Dit effect pas je hier aan.")));
        else inv.setItem(4, k.icon(Material.BARRIER, "<red><bold>Geen effect gekozen", "<gray>Kies eerst een effect in de lijst."));

        Dye dye = Dye.at(st.color);
        List<String> colorLore = new ArrayList<>();
        colorLore.add("<gray>Kleur voor effecten die een kleur gebruiken");
        colorLore.add("<gray>(zoals Kleurbol, Spiraal en Kleurspoor).");
        if (sel != null && !sel.colorable) colorLore.add("<dark_gray>Je huidige effect gebruikt geen eigen kleur.");
        colorLore.add("");
        colorLore.add("<yellow>Links <gray>» volgende  <yellow>Rechts <gray>» vorige");
        inv.setItem(10, k.icon(dye.icon(), "<aqua><bold>Kleur: <white>" + dye.name(), colorLore));

        inv.setItem(12, k.icon(Material.GLOWSTONE_DUST, "<aqua><bold>Sterkte: <white>" + st.intensity.label,
                "<gray>Hoeveel deeltjes je effect gebruikt.", "<gray>Laag is rustiger, hoog is drukker.", "", "<yellow>Links <gray>» hoger  <yellow>Rechts <gray>» lager"));
        inv.setItem(14, k.icon(st.visible ? Material.LIME_DYE : Material.GRAY_DYE,
                st.visible ? "<green><bold>Zichtbaar voor iedereen" : "<gray><bold>Alleen voor jou zichtbaar",
                "<gray>Wie de deeltjes en geluiden ziet.", "", "<yellow>Klik <gray>» wisselen"));
        inv.setItem(16, k.icon(Material.FIREWORK_ROCKET, "<aqua><bold>Testen", "<gray>Laat je effect nu zien met", "<gray>deze instellingen.", "", "<yellow>Klik <gray>» testen"));
        if (h.cat == Category.KILL) {
            boolean srvP = pl.cfgBool("kill-effecten.bij-spelers", true), srvM = pl.cfgBool("kill-effecten.bij-mobs", true);
            inv.setItem(20, toggle("Bij spelers", "als je een speler verslaat", st.onPlayers, srvP, Material.PLAYER_HEAD));
            inv.setItem(24, toggle("Bij mobs", "als je een mob verslaat", st.onMobs, srvM, Material.ZOMBIE_HEAD));
        }
        inv.setItem(31, k.icon(Material.OAK_DOOR, "<gray><bold>Terug", "<gray>Naar " + h.cat.label.toLowerCase(), "", "<yellow>Klik <gray>» terug"));
    }

    /** Aan/uit-knop voor 'bij spelers' of 'bij mobs'. Als de server het voor iedereen uitzet, staat dat erbij. */
    private ItemStack toggle(String title, String when, boolean on, boolean serverAllows, Material icon) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Speelt je kill-effect " + when + "?");
        lore.add("<gray>Nu: " + (on ? "<green>ja" : "<red>nee"));
        if (!serverAllows) lore.add("<red>De server heeft dit voor iedereen uitgezet.");
        lore.add("");
        lore.add("<yellow>Klik <gray>» " + (on ? "uitzetten" : "aanzetten"));
        ItemStack it = k.icon(icon, (on ? "<green><bold>" : "<red><bold>") + title + (on ? ": aan" : ": uit"), lore);
        return on && serverAllows ? k.glow(it) : it;
    }

    // ------------------------------------------------------------ klikken

    private void click(Holder h, InventoryClickEvent e, Player p) {
        if (!pl.canUse(p)) {
            p.closeInventory();
            return;
        }
        Inventory top = e.getView().getTopInventory();
        Inventory clicked = e.getClickedInventory();
        if (clicked == null || !clicked.equals(top)) return;
        int slot = e.getSlot();
        ClickType ct = e.getClick();
        switch (h.screen) {
            case MAIN -> clickMain(p, h, slot);
            case CATEGORY -> clickCategory(p, h, slot);
            case CUSTOM -> clickCustom(p, h, slot, ct);
        }
    }

    private void clickMain(Player p, Holder h, int slot) {
        switch (slot) {
            case 10 -> openCategory(p, Category.CROWN, 0);
            case 12 -> openCategory(p, Category.HAT, 0);
            case 14 -> openCategory(p, Category.KILL, 0);
            case 16 -> openCategory(p, Category.BOW, 0);
            case 28 -> {
                pl.deselectAll(p);
                pl.say(p, "<yellow>Alles staat uit.");
                build(p, h);
            }
            case 33 -> {
                if (!pl.packConfigured()) return;
                pl.sendPack(p);
                pl.say(p, "<green>Resourcepack wordt verstuurd. <gray>Accepteer hem als je Minecraft dat vraagt.");
            }
            case 31 -> {
                Profile pf = pl.profile(p);
                pf.enabled = !pf.enabled;
                pl.markDirty();
                pl.refreshHat(p);
                build(p, h);
            }
            case 34 -> {
                if (!p.hasPermission(CosmeticsPlugin.STAFF)) return;
                k.tick(p);
                pl.staff.openMain(p);
                return;
            }
            default -> {
                return;
            }
        }
        k.tick(p);
    }

    private void clickCategory(Player p, Holder h, int slot) {
        if (slot < PAGE) {
            List<Cosmetic> list = listFor(p, h.cat);
            int idx = h.page * PAGE + slot;
            if (idx >= list.size()) return;
            Cosmetic c = list.get(idx);
            Profile pf = pl.profile(p);
            if (!pl.access(p, pf, c).owned()) {
                k.deny(p);
                return;
            }
            Cosmetic sel = pl.selected(pf, h.cat);
            if (sel != null && sel.id.equals(c.id)) {
                pl.deselect(p, h.cat);
                p.sendActionBar(k.c("<gray>" + Kit.esc(c.name) + " <red>uit"));
            } else {
                pl.select(p, c);
                p.sendActionBar(k.c("<gray>Actief: <green>" + Kit.esc(c.name)));
            }
            k.tick(p);
            build(p, h);
            return;
        }
        switch (slot) {
            case 45 -> {
                if (h.page > 0) openCategory(p, h.cat, h.page - 1);
            }
            case 53 -> openCategory(p, h.cat, h.page + 1);
            case 47 -> {
                pl.deselect(p, h.cat);
                build(p, h);
            }
            case 48 -> openMain(p);
            case 52 -> {
                if (!h.cat.isHead() || !pl.packConfigured()) return;
                pl.sendPack(p);
                pl.say(p, "<green>Resourcepack wordt verstuurd. <gray>Accepteer hem als je Minecraft dat vraagt.");
            }
            case 49 -> {
                if (!h.cat.hasSettings()) return;
                openCustom(p, h.cat);
            }
            case 50 -> {
                if (!h.cat.hasSettings()) return;
                pl.preview(p, h.cat);
            }
            default -> {
                return;
            }
        }
        k.tick(p);
    }

    private void clickCustom(Player p, Holder h, int slot, ClickType ct) {
        Profile pf = pl.profile(p);
        Setting st = pf.setting(h.cat);
        switch (slot) {
            case 10 -> {
                st.color = Math.floorMod(st.color + (ct.isRightClick() ? -1 : 1), Dye.ALL.size());
                pl.markDirty();
            }
            case 12 -> {
                st.intensity = ct.isRightClick() ? st.intensity.prev() : st.intensity.next();
                pl.markDirty();
            }
            case 14 -> {
                st.visible = !st.visible;
                pl.markDirty();
            }
            case 16 -> {
                pl.preview(p, h.cat);
                return;
            }
            case 20 -> {
                if (h.cat != Category.KILL) return;
                st.onPlayers = !st.onPlayers;
                pl.markDirty();
            }
            case 24 -> {
                if (h.cat != Category.KILL) return;
                st.onMobs = !st.onMobs;
                pl.markDirty();
            }
            case 31 -> {
                k.tick(p);
                openCategory(p, h.cat, 0);
                return;
            }
            default -> {
                return;
            }
        }
        k.tick(p);
        build(p, h);
    }
}
