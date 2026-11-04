package nl.cosmetics;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Het menu voor staff.
 * <p>
 * Hoofdmenu → speler kiezen/zoeken → de cosmetics van die speler, met een tab per soort (kronen, hoeden, kill, boog).
 * Links klikken geeft of pakt af, rechts klikken laat de speler stoppen met dragen. Gevaarlijke knoppen vragen om een tweede klik.
 */
final class StaffGui {

    enum Screen { MAIN, PICK, TARGET, CATALOG }

    private static final String T = "<gradient:#ffb347:#ff5f6d><bold>Cosmetics Staff</bold></gradient> <dark_gray>» ";
    /** Aantal lijstplekken per pagina in een tab-scherm: 4 rijen van 9. */
    private static final int PER = 36;
    private static final int LIST_START = 9;
    private static final int PICK_PAGE = 45;
    private static final int[] TAB_SLOTS = {2, 3, 4, 5};
    private static final long CONFIRM_MS = 6000;

    final class Holder implements Menu {
        final Screen screen;
        CosmeticsPlugin.Target target;
        Category cat = Category.CROWN;
        int page;
        Inventory inv;
        String confirmKey;
        long confirmAt;

        Holder(Screen screen) {
            this.screen = screen;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }

        @Override
        public void click(InventoryClickEvent e, Player p) {
            StaffGui.this.click(this, e, p);
        }
    }

    private final CosmeticsPlugin pl;
    private final Kit k;

    StaffGui(CosmeticsPlugin pl) {
        this.pl = pl;
        this.k = new Kit(pl);
    }

    // ------------------------------------------------------------ openen

    void openMain(Player p) {
        open(p, new Holder(Screen.MAIN), 36, "<gray>Beheer");
    }

    void openPick(Player p, int page) {
        Holder h = new Holder(Screen.PICK);
        int pages = Kit.pageCount(Bukkit.getOnlinePlayers().size(), PICK_PAGE);
        h.page = Math.max(0, Math.min(page, pages - 1));
        open(p, h, 54, "<gray>Kies een speler <dark_gray>(" + (h.page + 1) + "/" + pages + ")");
    }

    void openTarget(Player p, CosmeticsPlugin.Target t, int page) {
        openTarget(p, t, Category.CROWN, page);
    }

    void openTarget(Player p, CosmeticsPlugin.Target t, Category cat, int page) {
        Holder h = new Holder(Screen.TARGET);
        h.target = t;
        h.cat = cat;
        h.page = clampPage(cat, page);
        open(p, h, 54, "<white>" + Kit.esc(t.name()) + " <dark_gray>» <gray>" + cat.label);
    }

    void openCatalog(Player p, Category cat, int page) {
        Holder h = new Holder(Screen.CATALOG);
        h.cat = cat;
        h.page = clampPage(cat, page);
        open(p, h, 54, "<gray>Alle cosmetics <dark_gray>» <gray>" + cat.label);
    }

    private int clampPage(Category cat, int page) {
        int pages = Kit.pageCount(pl.catalog.of(cat).size(), PER);
        return Math.max(0, Math.min(page, pages - 1));
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
            case MAIN -> buildMain(inv);
            case PICK -> buildPick(h, inv);
            case TARGET, CATALOG -> buildList(p, h, inv);
        }
    }

    // ------------------------------------------------------------ hoofdmenu

    private void buildMain(Inventory inv) {
        inv.setItem(4, k.icon(Material.NETHER_STAR, "<gradient:#ffb347:#ff5f6d><bold>Cosmetics beheer</bold></gradient>",
                "<gray>Spelers met gegeven cosmetics: <white>" + pl.store.withGrants(),
                "<gray>Cosmetics in totaal: <white>" + pl.catalog.all().size()));
        inv.setItem(10, k.icon(Material.PLAYER_HEAD, "<green><bold>1. Speler kiezen", "<gray>Kies uit de spelers die online zijn.", "<gray>Daarna geef of pak je cosmetics.", "", "<yellow>Klik <gray>» openen"));
        inv.setItem(12, k.icon(Material.NAME_TAG, "<aqua><bold>2. Speler zoeken", "<gray>Typ de naam van een speler in de chat,", "<gray>ook als hij offline is.", "", "<yellow>Klik <gray>» typen in de chat"));
        inv.setItem(14, k.icon(Material.BOOK, "<gold><bold>3. Alle cosmetics bekijken", "<gray>Per soort, met de naam voor commando's", "<gray>en de permissie voor LuckPerms.", "", "<yellow>Klik <gray>» openen"));
        int online = Bukkit.getOnlinePlayers().size();
        if (pl.packConfigured() && !pl.cfgBool("pack.extern", false)) {
            inv.setItem(16, k.icon(Material.ORANGE_STAINED_GLASS, "<gold><bold>Resourcepack",
                    "<gray>Geladen bij <white>" + pl.packLoadedCount() + "<gray> van <white>" + online + "<gray> spelers.",
                    "<gray>Zonder pack zien spelers vervangende helmen.", "", "<yellow>Klik <gray>» opnieuw naar iedereen sturen"));
        } else {
            inv.setItem(16, k.icon(Material.GRAY_STAINED_GLASS, "<gray><bold>Resourcepack",
                    pl.cfgBool("pack.extern", false) ? "<gray>Wordt elders verstuurd (extern)." : "<gray>Geen url ingesteld in config.yml.",
                    "<dark_gray>Zie pack.url in de config."));
        }
        inv.setItem(22, k.icon(Material.WRITABLE_BOOK, "<yellow><bold>Zo werkt het",
                "<gray>Kies een speler, kies een tab (soort) en klik:", "<green>Linksklik <gray>» geven of afpakken",
                "<yellow>Rechtsklik <gray>» laten stoppen met dragen", "", "<dark_gray>Rang-rechten (LuckPerms) pak je daar af."));
        boolean on = pl.enabled();
        inv.setItem(28, k.icon(on ? Material.LIME_DYE : Material.RED_DYE, on ? "<green><bold>Cosmetics staan AAN" : "<red><bold>Cosmetics staan UIT",
                "<gray>Voor iedereen op de server.", on ? "<gray>Klik om alles uit te zetten." : "<gray>Klik om alles weer aan te zetten.", "", "<yellow>Klik <gray>» wisselen"));
        inv.setItem(30, k.icon(Material.CLOCK, "<yellow><bold>Config opnieuw laden", "<gray>Leest config.yml opnieuw in.", "", "<yellow>Klik <gray>» laden"));
        inv.setItem(32, k.icon(Material.PAPER, "<aqua><bold>Commando's", "<white>/cosadmin geef <speler> <cosmetic|alle>", "<white>/cosadmin neem <speler> <cosmetic|alle>",
                "<white>/cosadmin effect <soort:naam | alle-kill | alle-boog>", "<white>/cosadmin pack [speler|alle]", "<white>/cosadmin lijst [speler]"));
        inv.setItem(34, k.icon(Material.OAK_DOOR, "<gray><bold>Terug", "<gray>Naar je eigen cosmetics.", "", "<yellow>Klik <gray>» terug"));
    }

    // ------------------------------------------------------------ spelers

    private List<Player> sortedOnline() {
        List<Player> l = new ArrayList<>(Bukkit.getOnlinePlayers());
        l.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        return l;
    }

    private String equippedLine(Profile pf) {
        List<String> parts = new ArrayList<>();
        for (Category c : Category.values()) {
            Cosmetic sel = pl.selected(pf, c);
            if (sel != null) parts.add(Kit.esc(sel.name));
        }
        return parts.isEmpty() ? "niets" : String.join(", ", parts);
    }

    private void buildPick(Holder h, Inventory inv) {
        List<Player> online = sortedOnline();
        int pages = Kit.pageCount(online.size(), PICK_PAGE);
        h.page = Math.max(0, Math.min(h.page, pages - 1));
        for (int i = 0; i < PICK_PAGE; i++) {
            int idx = h.page * PICK_PAGE + i;
            if (idx >= online.size()) break;
            Player t = online.get(idx);
            Profile pf = pl.store.getOrCreate(t.getUniqueId(), t.getName());
            inv.setItem(i, k.head(t, "<white><bold>" + Kit.esc(t.getName()), List.of(
                    "<gray>Gegeven door staff: <white>" + pf.granted.size(),
                    "<gray>Draagt nu: <white>" + equippedLine(pf),
                    "", "<yellow>Klik <gray>» cosmetics beheren")));
        }
        inv.setItem(45, h.page > 0 ? k.icon(Material.ARROW, "<yellow>« Vorige pagina") : k.filler());
        inv.setItem(48, k.icon(Material.NAME_TAG, "<aqua><bold>Speler zoeken", "<gray>Ook voor spelers die offline zijn.", "", "<yellow>Klik <gray>» typen in de chat"));
        inv.setItem(49, k.icon(Material.OAK_DOOR, "<gray><bold>Terug", "<gray>Naar het beheermenu"));
        inv.setItem(53, h.page < pages - 1 ? k.icon(Material.ARROW, "<yellow>Volgende pagina »") : k.filler());
    }

    // ------------------------------------------------------------ tab-scherm (speler of catalogus)

    private String statusLine(Access a) {
        return switch (a) {
            case BOTH -> "<green><bold>✔ IN BEZIT <dark_gray>(gegeven én via rang)";
            case GIVEN -> "<green><bold>✔ IN BEZIT <dark_gray>(gegeven door staff)";
            case RANK -> "<aqua><bold>✔ IN BEZIT <dark_gray>(via rang of permissie)";
            default -> "<red><bold>✖ NIET IN BEZIT";
        };
    }

    private int ownedIn(CosmeticsPlugin.Target t, Profile pf, Category cat) {
        int n = 0;
        for (Cosmetic c : pl.catalog.of(cat)) if (pl.accessOf(t, pf, c).owned()) n++;
        return n;
    }

    private void buildTabs(Holder h, Inventory inv, Profile pf) {
        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            Category cat = cats[i];
            boolean sel = cat == h.cat;
            List<String> lore = new ArrayList<>();
            int total = pl.catalog.of(cat).size();
            if (h.screen == Screen.TARGET) lore.add("<gray>Heeft: <white>" + ownedIn(h.target, pf, cat) + "<gray>/<white>" + total);
            else lore.add("<gray>Aantal: <white>" + total);
            lore.add(sel ? "<gold>Je bekijkt deze soort" : "<yellow>Klik <gray>» bekijken");
            ItemStack it = k.icon(cat.icon, (sel ? "<gold><bold>▶ " : "<gray><bold>") + cat.label, lore);
            inv.setItem(TAB_SLOTS[i], sel ? k.glow(it) : it);
        }
    }

    private void buildList(Player viewer, Holder h, Inventory inv) {
        boolean target = h.screen == Screen.TARGET;
        CosmeticsPlugin.Target t = h.target;
        Profile pf = target ? pl.store.getOrCreate(t.id(), t.name()) : null;
        List<Cosmetic> list = pl.catalog.of(h.cat);
        int pages = Kit.pageCount(list.size(), PER);
        h.page = Math.max(0, Math.min(h.page, pages - 1));
        buildTabs(h, inv, pf);

        for (int i = 0; i < PER; i++) {
            int idx = h.page * PER + i;
            if (idx >= list.size()) break;
            Cosmetic c = list.get(idx);
            inv.setItem(LIST_START + i, target ? targetIcon(viewer, h, pf, c) : catalogIcon(viewer, c));
        }

        inv.setItem(7, k.icon(Material.WRITABLE_BOOK, "<yellow><bold>Uitleg",
                target ? new String[]{"<green>Groen <gray>» speler heeft het", "<red>Rood <gray>» speler heeft het niet", "",
                        "<green>Linksklik <gray>» geven / afpakken", "<yellow>Rechtsklik <gray>» laten stoppen met dragen"}
                        : new String[]{"<gray>Dit is alleen een overzicht.", "<gray>Geef cosmetics via een speler.", "",
                        "<white>cosmetics." + h.cat.key + ".<naam> <gray>» één cosmetic", "<white>cosmetics." + h.cat.key + ".* <gray>» hele soort"}));
        inv.setItem(8, k.icon(Material.OAK_DOOR, "<gray><bold>Terug", "<gray>Naar het beheermenu", "", "<yellow>Klik <gray>» terug"));

        inv.setItem(45, h.page > 0 ? k.icon(Material.ARROW, "<yellow>« Vorige pagina") : k.filler());
        inv.setItem(49, k.icon(Material.PAPER, "<gray><bold>Pagina " + (h.page + 1) + " van " + pages, "<gray>Soort: <white>" + h.cat.label, "<gray>Aantal: <white>" + list.size()));
        inv.setItem(53, h.page < pages - 1 ? k.icon(Material.ARROW, "<yellow>Volgende pagina »") : k.filler());

        if (!target) return;
        boolean online = Bukkit.getPlayer(t.id()) != null;
        OfflinePlayer op = Bukkit.getOfflinePlayer(t.id());
        List<String> info = new ArrayList<>();
        info.add("<gray>Status: " + (online ? "<green>online" : "<red>offline"));
        info.add("<gray>Gegeven door staff: <white>" + pf.granted.size());
        info.add("<gray>Draagt nu: <white>" + equippedLine(pf));
        info.add("<gray>Cosmetics: " + (pf.enabled ? "<green>aan" : "<red>uit"));
        if (!online) info.add("<dark_gray>Offline: rang-rechten zijn nu niet te zien.");
        inv.setItem(0, k.head(op, "<white><bold>" + Kit.esc(t.name()), info));

        String tabName = h.cat.label.toLowerCase();
        inv.setItem(46, k.icon(Material.LIME_CONCRETE, "<green><bold>Alle " + tabName + " geven", "<gray>Geeft " + Kit.esc(t.name()) + " alles in deze tab.", "", "<yellow>Klik <gray>» geven"));
        boolean confTab = confirming(h, "takeTab");
        inv.setItem(47, k.icon(confTab ? Material.TNT : Material.RED_CONCRETE, confTab ? "<red><bold>Klik nogmaals om af te pakken!" : "<red><bold>Alle " + tabName + " afpakken",
                "<gray>Pakt alles in deze tab af wat staff gaf.", "<dark_gray>Rang-rechten blijven bij LuckPerms.", "", "<yellow>Klik <gray>» " + (confTab ? "bevestigen" : "afpakken (2 keer klikken)")));
        inv.setItem(48, k.icon(Material.BARRIER, "<gold><bold>Stoppen met dragen (" + tabName + ")", "<gray>" + Kit.esc(t.name()) + " draagt of toont dit niet meer.", "<gray>Hij houdt zijn cosmetics.", "", "<yellow>Klik <gray>» uitzetten"));
        inv.setItem(50, k.icon(Material.HOPPER, "<yellow><bold>Instellingen resetten", "<gray>Zet alle keuzes, kleuren en schakelaars terug.", "<gray>Gegeven cosmetics blijven.", "", "<yellow>Klik <gray>» resetten"));
        inv.setItem(51, k.icon(Material.EMERALD_BLOCK, "<green><bold>ALLES geven", "<gray>Alle soorten, ook de andere tabs.", "", "<yellow>Klik <gray>» geven"));
        boolean confAll = confirming(h, "takeAll");
        inv.setItem(52, k.icon(confAll ? Material.TNT : Material.REDSTONE_BLOCK, confAll ? "<red><bold>Klik nogmaals om ALLES af te pakken!" : "<red><bold>ALLES afpakken",
                "<gray>Alle soorten, ook de andere tabs.", "", "<yellow>Klik <gray>» " + (confAll ? "bevestigen" : "afpakken (2 keer klikken)")));
    }

    private ItemStack targetIcon(Player viewer, Holder h, Profile pf, Cosmetic c) {
        CosmeticsPlugin.Target t = h.target;
        Access a = pl.accessOf(t, pf, c);
        Cosmetic sel = pl.selected(pf, c.cat);
        boolean worn = sel != null && sel.id.equals(c.id) && a.owned();
        List<String> lore = new ArrayList<>();
        lore.add(statusLine(a));
        if (worn) lore.add("<gold>● Wordt nu gedragen");
        if (pl.disabled(c)) lore.add("<red>Uitgeschakeld in de config");
        lore.add("<dark_gray>" + c.lore.get(0));
        lore.add("<dark_gray>Permissie: " + c.permission());
        lore.add("");
        lore.add(pf.isGranted(c) ? "<red>Linksklik <gray>» afpakken" : "<green>Linksklik <gray>» geven");
        if (a == Access.RANK) lore.add("<dark_gray>Rang-rechten haal je weg in LuckPerms.");
        if (worn) lore.add("<yellow>Rechtsklik <gray>» laten stoppen met dragen");
        ItemStack it = k.icon(pl.showItem(viewer, c), (a.owned() ? "<green><bold>" : "<red><bold>") + Kit.esc(c.name), lore);
        return worn ? k.glow(it) : it;
    }

    private ItemStack catalogIcon(Player viewer, Cosmetic c) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + c.lore.get(0));
        lore.add("");
        lore.add("<gray>Naam voor commando: <white>" + c.key());
        lore.add("<gray>Permissie: <white>" + c.permission());
        if (c.isModel()) lore.add("<gray>Heeft 3D-model: <white>" + c.modelKey);
        if (pl.disabled(c)) lore.add("<red>Uitgeschakeld in de config");
        return k.icon(pl.showItem(viewer, c), "<white><bold>" + Kit.esc(c.name), lore);
    }

    // ------------------------------------------------------------ bevestigen

    private boolean confirming(Holder h, String key) {
        return key.equals(h.confirmKey) && System.currentTimeMillis() - h.confirmAt < CONFIRM_MS;
    }

    /** Eerste klik: vraagt om bevestiging (geeft false). Tweede klik binnen een paar seconden: geeft true. */
    private boolean confirm(Holder h, String key) {
        if (confirming(h, key)) {
            h.confirmKey = null;
            return true;
        }
        h.confirmKey = key;
        h.confirmAt = System.currentTimeMillis();
        return false;
    }

    // ------------------------------------------------------------ klikken

    private void click(Holder h, InventoryClickEvent e, Player p) {
        if (!p.hasPermission(CosmeticsPlugin.STAFF)) {
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
            case PICK -> clickPick(p, h, slot);
            case TARGET, CATALOG -> clickList(p, h, slot, ct);
        }
    }

    private void clickMain(Player p, Holder h, int slot) {
        switch (slot) {
            case 10 -> openPick(p, 0);
            case 12 -> {
                k.tick(p);
                pl.prompt(p, "SEARCH", "<gray>Typ de naam van de speler (hij moet minstens één keer gejoind zijn).");
                return;
            }
            case 14 -> openCatalog(p, Category.CROWN, 0);
            case 16 -> {
                if (!pl.packConfigured()) return;
                int n = 0;
                for (Player on : Bukkit.getOnlinePlayers()) if (pl.sendPack(on)) n++;
                pl.say(p, "<green>Resourcepack naar <white>" + n + "<green> spelers gestuurd.");
            }
            case 28 -> {
                pl.setCfg("actief", !pl.enabled());
                pl.refreshAllHats();
                build(p, h);
            }
            case 30 -> {
                pl.reloadAll();
                pl.say(p, "<green>Config opnieuw geladen.");
                build(p, h);
            }
            case 34 -> pl.gui.openMain(p);
            default -> {
                return;
            }
        }
        k.tick(p);
    }

    private void clickPick(Player p, Holder h, int slot) {
        if (slot < PICK_PAGE) {
            List<Player> online = sortedOnline();
            int idx = h.page * PICK_PAGE + slot;
            if (idx >= online.size()) return;
            Player t = online.get(idx);
            k.tick(p);
            openTarget(p, new CosmeticsPlugin.Target(t.getUniqueId(), t.getName()), Category.CROWN, 0);
            return;
        }
        switch (slot) {
            case 45 -> {
                if (h.page > 0) openPick(p, h.page - 1);
            }
            case 53 -> openPick(p, h.page + 1);
            case 48 -> {
                k.tick(p);
                pl.prompt(p, "SEARCH", "<gray>Typ de naam van de speler (hij moet minstens één keer gejoind zijn).");
                return;
            }
            case 49 -> openMain(p);
            default -> {
                return;
            }
        }
        k.tick(p);
    }

    /** Klik in een tab-scherm. Geeft de nieuwe pagina/tab door door het scherm opnieuw te openen. */
    private void reopen(Player p, Holder h, Category cat, int page) {
        if (h.screen == Screen.TARGET) openTarget(p, h.target, cat, page);
        else openCatalog(p, cat, page);
    }

    private void clickList(Player p, Holder h, int slot, ClickType ct) {
        boolean target = h.screen == Screen.TARGET;
        // tabs
        for (int i = 0; i < TAB_SLOTS.length; i++) {
            if (slot == TAB_SLOTS[i]) {
                k.tick(p);
                reopen(p, h, Category.values()[i], 0);
                return;
            }
        }
        if (slot == 8) {
            k.tick(p);
            openMain(p);
            return;
        }
        if (slot == 45) {
            if (h.page > 0) reopen(p, h, h.cat, h.page - 1);
            k.tick(p);
            return;
        }
        if (slot == 53) {
            reopen(p, h, h.cat, h.page + 1);
            k.tick(p);
            return;
        }
        if (!target) return;

        CosmeticsPlugin.Target t = h.target;
        Profile pf = pl.store.getOrCreate(t.id(), t.name());
        if (slot >= LIST_START && slot < LIST_START + PER) {
            List<Cosmetic> list = pl.catalog.of(h.cat);
            int idx = h.page * PER + (slot - LIST_START);
            if (idx >= list.size()) return;
            clickCosmetic(p, h, t, pf, list.get(idx), ct);
            return;
        }
        switch (slot) {
            case 46 -> {
                int n = 0;
                for (Cosmetic c : pl.catalog.of(h.cat)) if (!pl.disabled(c) && pl.give(t, c)) n++;
                pl.say(p, "<green>" + Kit.esc(t.name()) + " kreeg <white>" + n + "<green> " + h.cat.label.toLowerCase() + " erbij.");
                if (n > 0) pl.notify(t.id(), "<green>Je hebt <white>" + n + "<green> " + h.cat.label.toLowerCase() + " gekregen! <gray>Open <white>/cosmetics");
            }
            case 47 -> {
                if (!confirm(h, "takeTab")) {
                    pl.say(p, "<yellow>Klik nogmaals binnen een paar seconden om alle " + h.cat.label.toLowerCase() + " van " + Kit.esc(t.name()) + " af te pakken.");
                    build(p, h);
                    k.tick(p);
                    return;
                }
                int n = 0;
                for (Cosmetic c : pl.catalog.of(h.cat)) if (pl.take(t, c)) n++;
                pl.say(p, "<yellow>" + Kit.esc(t.name()) + " is <white>" + n + "<yellow> " + h.cat.label.toLowerCase() + " kwijt.");
                if (n > 0) pl.notify(t.id(), "<yellow>Je bent <white>" + n + "<yellow> " + h.cat.label.toLowerCase() + " kwijtgeraakt.");
            }
            case 48 -> {
                pf.setting(h.cat).selected = null;
                pl.afterChange(t.id());
                pl.say(p, "<yellow>" + Kit.esc(t.name()) + " gebruikt geen " + h.cat.label.toLowerCase() + " meer.");
            }
            case 50 -> {
                pf.resetSettings();
                pl.afterChange(t.id());
                pl.say(p, "<yellow>Instellingen van " + Kit.esc(t.name()) + " zijn teruggezet.");
            }
            case 51 -> {
                int n = 0;
                for (Cosmetic c : pl.catalog.all()) if (!pl.disabled(c) && pl.give(t, c)) n++;
                pl.say(p, "<green>" + Kit.esc(t.name()) + " kreeg <white>" + n + "<green> cosmetics erbij.");
                if (n > 0) pl.notify(t.id(), "<green>Je hebt <white>" + n + "<green> cosmetics gekregen! <gray>Open <white>/cosmetics");
            }
            case 52 -> {
                if (!confirm(h, "takeAll")) {
                    pl.say(p, "<yellow>Klik nogmaals binnen een paar seconden om ALLES van " + Kit.esc(t.name()) + " af te pakken.");
                    build(p, h);
                    k.tick(p);
                    return;
                }
                int n = 0;
                for (Cosmetic c : pl.catalog.all()) if (pl.take(t, c)) n++;
                pl.say(p, "<yellow>" + Kit.esc(t.name()) + " is <white>" + n + "<yellow> cosmetics kwijt.");
                if (n > 0) pl.notify(t.id(), "<yellow>Je bent <white>" + n + "<yellow> cosmetics kwijtgeraakt.");
            }
            default -> {
                return;
            }
        }
        h.confirmKey = null;
        k.tick(p);
        build(p, h);
    }

    private void clickCosmetic(Player p, Holder h, CosmeticsPlugin.Target t, Profile pf, Cosmetic c, ClickType ct) {
        if (ct.isRightClick()) {
            Cosmetic sel = pl.selected(pf, c.cat);
            if (sel != null && sel.id.equals(c.id)) {
                pf.setting(c.cat).selected = null;
                pl.afterChange(t.id());
                pl.notify(t.id(), "<yellow>Een admin heeft <white>" + pl.esc(c.name) + "<yellow> bij je uitgezet.");
            } else {
                k.deny(p);
                return;
            }
        } else if (pf.isGranted(c)) {
            pl.take(t, c);
            pl.notify(t.id(), "<yellow>Je bent <white>" + pl.esc(c.name) + "<yellow> kwijtgeraakt.");
        } else if (pl.disabled(c)) {
            pl.say(p, "<red>Deze cosmetic is uitgeschakeld in de config.");
            k.deny(p);
            return;
        } else {
            pl.give(t, c);
            pl.notify(t.id(), "<green>Je hebt <white>" + pl.esc(c.name) + "<green> gekregen! <gray>Open <white>/cosmetics");
        }
        h.confirmKey = null;
        k.tick(p);
        build(p, h);
    }
}
