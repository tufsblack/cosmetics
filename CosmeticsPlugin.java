package nl.cosmetics;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CosmeticsPlugin extends JavaPlugin implements Listener {

    public static final String VERSION = "1.2.0";
    static final String USE = "cosmetics.use";
    static final String STAFF = "cosmetics.staff";
    static final String PREFIX = "<dark_gray>[<gradient:#c471f5:#fa71cd>Cosmetics</gradient><dark_gray>] <gray>";

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Random rnd = new Random();
    final Catalog catalog = Catalog.standard();
    ProfileStore store = new ProfileStore();
    PlayerGui gui;
    StaffGui staff;

    /** Het pack dat deze plugin verstuurt heeft altijd dit id, zodat een nieuwe versie het oude vervangt. */
    static final UUID PACK_ID = UUID.nameUUIDFromBytes("nl.cosmetics:pack".getBytes(StandardCharsets.UTF_8));

    private final Set<UUID> hatShown = new HashSet<>();
    private final Set<UUID> packLoaded = ConcurrentHashMap.newKeySet();
    /** Spelers die al iets van hun client hebben teruggemeld over het pack (geaccepteerd, geladen, geweigerd, mislukt...). */
    private final Set<UUID> packAnswered = ConcurrentHashMap.newKeySet();
    private final Set<String> failed = new HashSet<>();
    private final Map<UUID, Track> tracks = new HashMap<>();
    private final Map<UUID, String> prompts = new ConcurrentHashMap<>();
    private BukkitTask hatTask, trailTask;
    private boolean saveScheduled, stopping;

    // gedeelde database (optioneel): zie het kopje "database" in config.yml
    private Sync sync;
    private ExecutorService dbExec;
    private BukkitTask syncTask;
    /** Wat er volgens ons in de database staat, per speler (alleen aanraken vanaf de hoofd-thread). */
    private final Map<UUID, String> synced = new HashMap<>();
    private final AtomicBoolean pulling = new AtomicBoolean();
    private volatile boolean dbReady;
    private boolean firstPullDone, dbWarned;
    private long lastPull;

    /** Een pijl met een spoor. */
    private static final class Track {
        final AbstractArrow arrow;
        final UUID owner;
        final Cosmetic cos;
        final Dye dye;
        final double mult;
        final boolean visible;
        Location last;
        boolean seen;
        int age;

        Track(AbstractArrow arrow, UUID owner, Cosmetic cos, Dye dye, double mult, boolean visible) {
            this.last = arrow.getLocation().clone();
            this.seen = arrow.isValid();
            this.arrow = arrow;
            this.owner = owner;
            this.cos = cos;
            this.dye = dye;
            this.mult = mult;
            this.visible = visible;
        }
    }

    // ------------------------------------------------------------ aan en uit

    @Override
    public void onEnable() {
        saveDefaultConfig();
        gui = new PlayerGui(this);
        staff = new StaffGui(this);
        loadProfiles();
        getServer().getPluginManager().registerEvents(this, this);
        startTasks();
        startSync();
        getLogger().info("Cosmetics " + VERSION + " aan. Cosmetics: " + catalog.all().size() + ", spelers met data: " + store.size() + ".");
    }

    @Override
    public void onDisable() {
        stopping = true;
        if (hatTask != null) hatTask.cancel();
        if (trailTask != null) trailTask.cancel();
        // Laat iedereen weer zijn echte helm zien.
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (hatShown.contains(p.getUniqueId())) sendHat(p, null);
        }
        hatShown.clear();
        packLoaded.clear();
        packAnswered.clear();
        tracks.clear();
        prompts.clear();
        saveProfilesNow();
        stopSync();
    }

    void reloadAll() {
        reloadConfig();
        startTasks();
        boolean want = cfgBool("database.aan", false);
        if (want && sync == null) startSync();
        else if (!want && sync != null) stopSync();
        refreshAllHats();
    }

    private void startTasks() {
        if (hatTask != null) hatTask.cancel();
        if (trailTask != null) trailTask.cancel();
        long hatEvery = Math.max(5, cfgInt("hoeden.ververs-ticks", 20));
        hatTask = Bukkit.getScheduler().runTaskTimer(this, this::refreshAllHats, hatEvery, hatEvery);
        long trailEvery = Math.max(1, cfgInt("boog-effecten.interval-ticks", 1));
        trailTask = Bukkit.getScheduler().runTaskTimer(this, this::tickTrails, trailEvery, trailEvery);
    }

    // ------------------------------------------------------------ opslag

    private File profilesFile() {
        return new File(getDataFolder(), "spelers.yml");
    }

    void loadProfiles() {
        File f = profilesFile();
        if (!f.exists()) {
            store = new ProfileStore();
            return;
        }
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.load(f);
        } catch (IOException | InvalidConfigurationException | RuntimeException ex) {
            File backup = new File(getDataFolder(), "spelers.yml.kapot-" + System.currentTimeMillis());
            boolean moved = f.renameTo(backup);
            getLogger().severe("spelers.yml is kapot (" + ex.getMessage() + "). " + (moved ? "Back-up: " + backup.getName() + ". " : "")
                    + "Er wordt met een lege lijst verder gegaan.");
            if (store == null) store = new ProfileStore();
            return;
        }
        List<String> problems = new ArrayList<>();
        store = ProfileStore.fromMaps(y.getMapList("spelers"), problems);
        for (String p : problems) getLogger().warning("spelers.yml: " + p + " (overgeslagen)");
    }

    void markDirty() {
        if (stopping || saveScheduled) return;
        saveScheduled = true;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            saveScheduled = false;
            saveProfilesNow();
        }, sync != null ? 10L : 40L);
    }

    void saveProfilesNow() {
        try {
            File dir = getDataFolder();
            if (dir != null && !dir.exists()) dir.mkdirs();
            YamlConfiguration y = new YamlConfiguration();
            y.set("spelers", store.toMaps());
            y.save(profilesFile());
        } catch (IOException | RuntimeException ex) {
            getLogger().warning("Kon spelers.yml niet opslaan: " + ex.getMessage());
        }
        pushChanges();
    }

    // ------------------------------------------------------------ gedeelde database

    String cfgStr(String path, String def) {
        String v = getConfig().getString(path, def);
        return v == null ? def : v;
    }

    boolean syncActive() {
        return sync != null;
    }

    /** Zet de verbinding met de database op (als dat in de config staat) en haalt alle spelers op. */
    void startSync() {
        if (sync != null || !cfgBool("database.aan", false)) return;
        String host = cfgStr("database.host", "localhost").trim();
        int port = cfgInt("database.poort", 3306);
        String db = cfgStr("database.naam", "minecraft").trim();
        String override = cfgStr("database.jdbc-url", "").trim();
        String url, alt;
        if (!override.isEmpty()) {
            url = override;
            alt = null;
        } else {
            url = "jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8";
            alt = "jdbc:mariadb://" + host + ":" + port + "/" + db;
        }
        sync = new Sync(getLogger(), url, alt, cfgStr("database.gebruiker", ""), cfgStr("database.wachtwoord", ""),
                Sync.cleanTable(cfgStr("database.tabel", "cosmetics_spelers")));
        dbExec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Cosmetics-database");
            t.setDaemon(true);
            return t;
        });
        dbReady = false;
        firstPullDone = false;
        dbWarned = false;
        lastPull = 0;
        synced.clear();
        // Eenmalig een kopie van het lokale bestand, voor het geval de database andere gegevens bevat.
        try {
            File f = profilesFile();
            File copy = new File(getDataFolder(), "spelers.yml.voor-database");
            if (f.exists() && !copy.exists()) java.nio.file.Files.copy(f.toPath(), copy.toPath());
        } catch (IOException | RuntimeException ex) {
            getLogger().fine("Kopie van spelers.yml maken mislukt: " + ex.getMessage());
        }
        long every = Math.max(20, cfgInt("database.controle-ticks", 100));
        syncTask = Bukkit.getScheduler().runTaskTimer(this, this::pull, 1L, every);
        getLogger().info("Database-sync staat aan. Verbinden met " + (override.isEmpty() ? host + ":" + port + "/" + db : "de opgegeven jdbc-url") + "...");
    }

    void stopSync() {
        if (syncTask != null) {
            syncTask.cancel();
            syncTask = null;
        }
        ExecutorService ex = dbExec;
        Sync s = sync;
        sync = null;
        dbExec = null;
        if (ex != null) {
            ex.shutdown();
            try {
                if (!ex.awaitTermination(5, TimeUnit.SECONDS)) getLogger().warning("De database was niet op tijd klaar met opslaan.");
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        if (s != null) s.close();
        synced.clear();
    }

    private void dbWarn(String what, Exception ex) {
        if (!dbWarned) {
            dbWarned = true;
            getLogger().warning("Database: " + what + " (" + ex.getMessage() + "). Lokaal werkt alles gewoon door; er wordt opnieuw geprobeerd.");
        }
    }

    private void dbOk() {
        if (dbWarned) {
            dbWarned = false;
            getLogger().info("Database: verbinding weer in orde.");
        }
    }

    /** Haalt gewijzigde spelers uit de database (draait de zware kant op een eigen thread). */
    void pull() {
        Sync s = sync;
        ExecutorService ex = dbExec;
        if (s == null || ex == null || !pulling.compareAndSet(false, true)) return;
        long since = firstPullDone ? Math.max(0, lastPull - 60_000L) : 0L;
        try {
            ex.execute(() -> {
                try {
                    if (!dbReady) {
                        s.init();
                        dbReady = true;
                    }
                    List<Sync.Row> rows = s.since(since);
                    runMain(() -> {
                        dbOk();
                        for (Sync.Row r : rows) lastPull = Math.max(lastPull, r.updated());
                        firstPullDone = true;
                        mergeRows(rows);
                        pushChanges();
                    });
                } catch (java.sql.SQLException | RuntimeException e) {
                    runMain(() -> dbWarn(dbReady ? "ophalen mislukt" : "verbinden mislukt", e));
                } finally {
                    pulling.set(false);
                }
            });
        } catch (RuntimeException ex2) {
            pulling.set(false);
        }
    }

    private void runMain(Runnable r) {
        try {
            Bukkit.getScheduler().runTask(this, r);
        } catch (RuntimeException ex) {
            // De plugin wordt waarschijnlijk uitgezet; niets meer te doen.
        }
    }

    /** Haalt één speler meteen op, bijvoorbeeld als hij net van een andere server komt. */
    void pullPlayer(UUID id) {
        Sync s = sync;
        ExecutorService ex = dbExec;
        if (s == null || ex == null) return;
        try {
            ex.execute(() -> {
                try {
                    if (!dbReady) return;
                    Sync.Row row = s.one(id);
                    if (row != null) runMain(() -> mergeRows(List.of(row)));
                } catch (java.sql.SQLException | RuntimeException e) {
                    runMain(() -> dbWarn("speler ophalen mislukt", e));
                }
            });
        } catch (RuntimeException ignored) {
            // wordt afgesloten
        }
    }

    /** Verwerkt rijen uit de database in het geheugen. De database wint, behalve bij wijzigingen die nog niet verstuurd zijn. */
    void mergeRows(List<Sync.Row> rows) {
        boolean applied = false;
        for (Sync.Row r : rows) {
            try {
                if (r.json() == null || r.json().isBlank()) continue;
                if (!(Json.parse(r.json()) instanceof Map<?, ?> m)) continue;
                List<String> problems = new ArrayList<>();
                Profile in = Profile.fromMap(m, problems);
                if (in == null || !in.id.equals(r.id())) continue;
                if (r.name() != null && !r.name().isBlank()) in.name = r.name();
                String inJson = Json.write(in.toMap());
                Profile local = store.get(r.id());
                String localJson = local == null ? null : Json.write(local.toMap());
                String cur = synced.get(r.id());
                if (local != null && cur != null && !localJson.equals(cur)) continue; // eigen wijziging gaat nog naar de database
                if (localJson != null && localJson.equals(inJson)) {
                    synced.put(r.id(), inJson);
                    continue;
                }
                store.put(in);
                synced.put(r.id(), inJson);
                applied = true;
                Player pp = Bukkit.getPlayer(r.id());
                if (pp != null && pp.isOnline()) refreshSoon(pp, 1L);
            } catch (RuntimeException ex) {
                getLogger().warning("Database: kapotte rij voor " + r.id() + " overgeslagen (" + ex.getMessage() + ")");
            }
        }
        if (applied) markDirty();
    }

    /** Stuurt alles wat lokaal veranderd is naar de database. */
    void pushChanges() {
        Sync s = sync;
        ExecutorService ex = dbExec;
        if (s == null || ex == null || !firstPullDone) return;
        for (Profile p : store.all()) {
            String json = Json.write(p.toMap());
            String prev = synced.get(p.id);
            if (prev == null ? p.isDefault() : prev.equals(json)) continue;
            UUID id = p.id;
            String name = p.name.length() > 32 ? p.name.substring(0, 32) : p.name;
            long now = System.currentTimeMillis();
            try {
                ex.execute(() -> {
                    try {
                        s.save(id, name, json, now);
                        runMain(() -> {
                            synced.put(id, json);
                            dbOk();
                        });
                    } catch (java.sql.SQLException | RuntimeException e) {
                        runMain(() -> dbWarn("opslaan mislukt", e));
                    }
                });
            } catch (RuntimeException rejected) {
                return;
            }
        }
    }

    // ------------------------------------------------------------ instellingen

    boolean cfgBool(String path, boolean def) {
        return getConfig().getBoolean(path, def);
    }

    int cfgInt(String path, int def) {
        return getConfig().getInt(path, def);
    }

    void setCfg(String path, Object value) {
        getConfig().set(path, value);
        saveConfig();
    }

    /** Staat het hele systeem aan? */
    boolean enabled() {
        return cfgBool("actief", true);
    }

    boolean toonVergrendeld() {
        return cfgBool("toon-vergrendeld", false);
    }

    boolean disabled(Cosmetic c) {
        for (String s : getConfig().getStringList("uitgeschakeld")) {
            if (s.trim().equalsIgnoreCase(c.key())) return true;
        }
        return false;
    }

    boolean canUse(Player p) {
        return p.hasPermission(USE) || p.hasPermission(STAFF);
    }

    // ------------------------------------------------------------ tekst

    Component parse(String raw) {
        try {
            return mm.deserialize(raw);
        } catch (RuntimeException ex) {
            return Component.text(raw);
        }
    }

    void say(CommandSender to, String mini) {
        to.sendMessage(parse(PREFIX + mini));
    }

    String esc(String s) {
        return mm.escapeTags(s == null ? "" : s);
    }

    // ------------------------------------------------------------ bezit

    Profile profile(Player p) {
        return store.getOrCreate(p.getUniqueId(), p.getName());
    }

    /** Hoe een online speler aan deze cosmetic komt: gegeven door een admin, via een rang, of beide. */
    Access access(Player p, Profile pf, Cosmetic c) {
        if (disabled(c)) return Access.NONE;
        return Access.of(pf.isGranted(c), p.hasPermission(c.permission()));
    }

    /** Voor spelers die offline zijn: rang-rechten zijn dan niet te lezen. */
    Access accessOffline(Profile pf, Cosmetic c) {
        if (disabled(c)) return Access.NONE;
        return Access.of(pf.isGranted(c), false);
    }

    boolean owns(Player p, Cosmetic c) {
        return access(p, profile(p), c).owned();
    }

    Cosmetic selected(Profile pf, Category cat) {
        String id = pf.setting(cat).selected;
        return id == null ? null : catalog.find(cat, id);
    }

    /** Haalt alles weg waar een speler geen recht meer op heeft en ververst zijn hoed. */
    void enforce(Player p) {
        Profile pf = profile(p);
        boolean changed = false;
        for (Category cat : Category.values()) {
            Setting st = pf.setting(cat);
            if (st.selected == null) continue;
            Cosmetic c = catalog.find(cat, st.selected);
            if (c == null || !access(p, pf, c).owned()) {
                st.selected = null;
                changed = true;
            }
        }
        if (changed) markDirty();
        refreshHat(p);
    }

    void select(Player p, Cosmetic c) {
        Profile pf = profile(p);
        if (!access(p, pf, c).owned()) return;
        if (c.cat.isHead()) {
            // Kroon en hoed passen niet samen op één hoofd.
            for (Category o : Category.values()) if (o.isHead() && o != c.cat) pf.setting(o).selected = null;
        }
        pf.setting(c.cat).selected = c.id;
        markDirty();
        if (c.cat.isHead()) refreshHat(p);
    }

    void deselect(Player p, Category cat) {
        Profile pf = profile(p);
        if (pf.setting(cat).selected == null) return;
        pf.setting(cat).selected = null;
        markDirty();
        if (cat.isHead()) refreshHat(p);
    }

    void deselectAll(Player p) {
        Profile pf = profile(p);
        for (Category cat : Category.values()) pf.setting(cat).selected = null;
        markDirty();
        refreshHat(p);
    }

    // ------------------------------------------------------------ geven en afpakken (staff)

    /** Zoekt een speler op naam: online, daarna onze eigen lijst, daarna de cache van de server. Geen Mojang-opzoeking. */
    record Target(UUID id, String name) {
    }

    Target resolveTarget(String name) {
        if (name == null || name.isBlank()) return null;
        Player on = Bukkit.getPlayerExact(name.trim());
        if (on != null) return new Target(on.getUniqueId(), on.getName());
        Profile pf = store.findByName(name.trim());
        if (pf != null) return new Target(pf.id, pf.name);
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name.trim());
        if (op != null && op.getName() != null) return new Target(op.getUniqueId(), op.getName());
        return null;
    }

    Access accessOf(Target t, Profile pf, Cosmetic c) {
        Player on = Bukkit.getPlayer(t.id());
        return on != null ? access(on, pf, c) : accessOffline(pf, c);
    }

    /** Geeft een cosmetic. Geeft true als er iets veranderde. */
    boolean give(Target t, Cosmetic c) {
        Profile pf = store.getOrCreate(t.id(), t.name());
        boolean changed = pf.give(c);
        if (changed) afterChange(t.id());
        return changed;
    }

    /** Pakt een cosmetic af (alleen wat een admin gaf; rang-rechten blijven bij LuckPerms). */
    boolean take(Target t, Cosmetic c) {
        Profile pf = store.getOrCreate(t.id(), t.name());
        boolean changed = pf.take(c);
        if (changed) afterChange(t.id());
        return changed;
    }

    void afterChange(UUID id) {
        markDirty();
        Player on = Bukkit.getPlayer(id);
        if (on != null) enforce(on);
    }

    void notify(UUID id, String mini) {
        Player on = Bukkit.getPlayer(id);
        if (on != null) say(on, mini);
    }

    // ------------------------------------------------------------ hoeden

    private ItemStack realHelmet(Player p) {
        ItemStack h = p.getInventory().getHelmet();
        return h == null ? new ItemStack(Material.AIR) : h;
    }

    /** Welke kroon of hoed deze speler nu draagt (of null). */
    Cosmetic headCosmetic(Player p) {
        if (!enabled() || !p.isOnline()) return null;
        Profile pf = store.get(p.getUniqueId());
        if (pf == null || !pf.enabled) return null;
        for (Category cat : Category.values()) {
            if (!cat.isHead()) continue;
            Cosmetic c = selected(pf, cat);
            if (c != null && access(p, pf, c).owned()) return c;
        }
        return null;
    }

    // ------------------------------------------------------------ resourcepack

    boolean packConfigured() {
        return !getConfig().getString("pack.url", "").isBlank();
    }

    /** Ziet deze speler de 3D-modellen? Dat kan als het pack geladen is, of als de server het zelf al verstuurt. */
    boolean packOk(Player p) {
        return cfgBool("pack.extern", false) || packLoaded.contains(p.getUniqueId());
    }

    int packLoadedCount() {
        return packLoaded.size();
    }

    private static byte[] parseHash(String hex) {
        if (hex == null) return null;
        String h = hex.trim();
        if (h.length() != 40) return null;
        byte[] out = new byte[20];
        for (int i = 0; i < 20; i++) {
            int hi = Character.digit(h.charAt(i * 2), 16), lo = Character.digit(h.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) return null;
            out[i] = (byte) (hi * 16 + lo);
        }
        return out;
    }

    /** Stuurt het resourcepack naar een speler. Geeft false als er geen url ingesteld is. */
    boolean sendPack(Player p) {
        String url = getConfig().getString("pack.url", "").trim();
        if (url.isEmpty()) return false;
        String hex = getConfig().getString("pack.sha1", "");
        byte[] hash = parseHash(hex);
        if (hash == null && !hex.isBlank()) getLogger().warning("pack.sha1 is geen geldige sha1 (40 tekens). Het pack wordt zonder controle verstuurd.");
        String msg = getConfig().getString("pack.bericht", "Voor de 3D-kronen en 3D-hoeden heb je dit resourcepack nodig.");
        try {
            p.setResourcePack(PACK_ID, url, hash, parse(msg), cfgBool("pack.verplicht", false));
            return true;
        } catch (RuntimeException ex) {
            getLogger().warning("Resourcepack sturen naar " + p.getName() + " mislukt: " + ex.getMessage());
            return false;
        }
    }

    /** Het item dat bij deze cosmetic hoort, zoals deze speler het kan zien: 3D-model met pack, anders de vervangende helm. */
    ItemStack showItem(Player viewer, Cosmetic c) {
        if (!c.isModel() || viewer == null || !packOk(viewer)) return new ItemStack(c.icon);
        ItemStack it = new ItemStack(Material.PAPER);
        NamespacedKey key = NamespacedKey.fromString("cosmetics:" + c.modelKey);
        if (key != null) it.editMeta(meta -> meta.setItemModel(key));
        return it;
    }

    /** Laat iedereen in de buurt de hoed zien (of weer je echte helm bij null). Er komt niets in je inventaris. */
    private void sendHat(Player target, Cosmetic hat) {
        int dist = cfgInt("hoeden.afstand", 64);
        long max = (long) dist * dist;
        ItemStack real = hat == null ? realHelmet(target) : null;
        for (Player v : Bukkit.getOnlinePlayers()) {
            boolean self = v.getUniqueId().equals(target.getUniqueId());
            // De hoed wordt nooit naar de drager zelf gestuurd (tenzij hoeden.zelf-zien aan staat): de client zou hem
            // anders als echt item in het helm-slot van je inventaris tonen, en dan kun je hem pakken of dupen.
            // Alleen het terugzetten van je echte helm gaat wel naar jezelf, zodat een eventueel spook-item verdwijnt.
            if (self && hat != null && !cfgBool("hoeden.zelf-zien", false)) continue;
            if (!self) {
                if (!v.getWorld().equals(target.getWorld())) continue;
                if (!v.canSee(target)) continue;
                if (dist > 0 && v.getLocation().distanceSquared(target.getLocation()) > max) continue;
            }
            try {
                v.sendEquipmentChange(target, EquipmentSlot.HEAD, hat == null ? real : showItem(v, hat));
            } catch (RuntimeException ex) {
                getLogger().fine("Hoed sturen mislukt: " + ex.getMessage());
            }
        }
    }

    void refreshHat(Player p) {
        if (!p.isOnline()) return;
        Cosmetic hat = headCosmetic(p);
        if (hat != null) {
            hatShown.add(p.getUniqueId());
            sendHat(p, hat);
        } else if (hatShown.remove(p.getUniqueId())) {
            sendHat(p, null);
        }
    }

    void refreshAllHats() {
        for (Player p : Bukkit.getOnlinePlayers()) refreshHat(p);
    }

    private void refreshSoon(Player p, long delay) {
        UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player pp = Bukkit.getPlayer(id);
            if (pp != null && pp.isOnline()) refreshHat(pp);
        }, delay);
    }

    // ------------------------------------------------------------ deeltjes

    /** Deeltjes voor iedereen in de wereld. */
    private static final class WorldSink implements Sink {
        private final World w;

        WorldSink(World w) {
            this.w = w;
        }

        public void particle(Particle p, Location l, int count, double ox, double oy, double oz, double speed) {
            w.spawnParticle(p, l, count, ox, oy, oz, speed);
        }

        public void dust(Location l, int count, double ox, double oy, double oz, Color color, float size) {
            w.spawnParticle(Particle.DUST, l, count, ox, oy, oz, 0, new Particle.DustOptions(color, size));
        }

        public void sound(Location l, Sound s, float volume, float pitch) {
            w.playSound(l, s, volume, pitch);
        }
    }

    private static final class PlayerSink implements Sink {
        private final Player pl;

        PlayerSink(Player pl) {
            this.pl = pl;
        }

        public void particle(Particle p, Location l, int count, double ox, double oy, double oz, double speed) {
            pl.spawnParticle(p, l, count, ox, oy, oz, speed);
        }

        public void dust(Location l, int count, double ox, double oy, double oz, Color color, float size) {
            pl.spawnParticle(Particle.DUST, l, count, ox, oy, oz, 0, new Particle.DustOptions(color, size));
        }

        public void sound(Location l, Sound s, float volume, float pitch) {
            pl.playSound(l, s, volume, pitch);
        }
    }

    Sink sinkFor(Player owner, Setting st, World w) {
        return st.visible ? new WorldSink(w) : new PlayerSink(owner);
    }

    /** Speelt een effect af. Een fout in één effect mag nooit de server raken; we melden hem één keer. */
    private boolean safePlay(Cosmetic c, Fx fx, Sink s, Location at, Dye dye, double mult) {
        try {
            fx.play(s, at, dye, mult, rnd);
            return true;
        } catch (RuntimeException ex) {
            if (failed.add(c.key())) getLogger().warning("Effect '" + c.key() + "' mislukt: " + ex);
            return false;
        }
    }

    // ------------------------------------------------------------ kill-effecten

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        LivingEntity victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || !enabled()) return;
        if (killer.getUniqueId().equals(victim.getUniqueId())) return;
        boolean victimIsPlayer = victim instanceof Player;
        // De server kan een soort slachtoffer voor iedereen uitzetten; daarbinnen kiest elke speler zelf.
        if (victimIsPlayer ? !cfgBool("kill-effecten.bij-spelers", true) : !cfgBool("kill-effecten.bij-mobs", true)) return;
        playKill(killer, victim.getLocation().add(0, victim.getHeight() / 2, 0), victimIsPlayer);
    }

    void playKill(Player killer, Location at, boolean victimIsPlayer) {
        Profile pf = store.get(killer.getUniqueId());
        if (pf == null || !pf.enabled) return;
        Cosmetic c = selected(pf, Category.KILL);
        if (c == null || c.burst == null) return;
        if (!access(killer, pf, c).owned()) {
            pf.setting(Category.KILL).selected = null;
            markDirty();
            return;
        }
        Setting st = pf.setting(Category.KILL);
        if (victimIsPlayer ? !st.onPlayers : !st.onMobs) return;
        safePlay(c, c.burst, sinkFor(killer, st, at.getWorld()), at, Dye.at(st.color), st.intensity.factor);
    }

    // ------------------------------------------------------------ boog-effecten

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (e.getEntity() instanceof Player p && e.getProjectile() instanceof AbstractArrow arrow) startTrack(p, arrow);
    }

    /** Vangt ook pijlen die niet via EntityShootBowEvent komen (kruisboog, trident, andere plugins). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (e.getEntity() instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player p) startTrack(p, arrow);
    }

    private void startTrack(Player p, AbstractArrow arrow) {
        if (!enabled() || tracks.containsKey(arrow.getUniqueId())) return;
        Profile pf = store.get(p.getUniqueId());
        if (pf == null || !pf.enabled) return;
        Cosmetic c = selected(pf, Category.BOW);
        if (c == null || c.trail == null) return;
        if (!access(p, pf, c).owned()) {
            pf.setting(Category.BOW).selected = null;
            markDirty();
            return;
        }
        if (tracks.size() >= Math.max(10, cfgInt("boog-effecten.max-actief", 200))) return;
        Setting st = pf.setting(Category.BOW);
        tracks.put(arrow.getUniqueId(), new Track(arrow, p.getUniqueId(), c, Dye.at(st.color), st.intensity.factor, st.visible));
    }

    private Sink trackSink(Track t, World w) {
        if (t.visible) return new WorldSink(w);
        Player owner = Bukkit.getPlayer(t.owner);
        return owner == null ? null : new PlayerSink(owner);
    }

    /** Trekt het spoor over het stuk tussen twee punten, om de 0,35 blok, zodat er ook bij een snelle pijl geen gaten vallen. */
    private boolean trailSegment(Cosmetic c, Sink s, Location from, Location to, Dye dye, double mult) {
        double dx = to.getX() - from.getX(), dy = to.getY() - from.getY(), dz = to.getZ() - from.getZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int points = (int) Math.min(40, Math.max(1, Math.ceil(len / 0.35)));
        for (int i = 1; i <= points; i++) {
            double f = (double) i / points;
            Location at = new Location(to.getWorld(), from.getX() + dx * f, from.getY() + dy * f, from.getZ() + dz * f);
            if (!safePlay(c, c.trail, s, at, dye, mult)) return false;
        }
        return true;
    }

    void tickTrails() {
        if (tracks.isEmpty()) return;
        int maxAge = Math.max(20, cfgInt("boog-effecten.max-levensduur-ticks", 200));
        int step = Math.max(1, cfgInt("boog-effecten.interval-ticks", 1));
        Iterator<Track> it = tracks.values().iterator();
        while (it.hasNext()) {
            Track t = it.next();
            t.age += step;
            // De eerste tikken is de pijl nog niet altijd 'valid'; daarna betekent het dat hij weg is.
            if (t.arrow.isValid()) t.seen = true;
            boolean gone = !t.arrow.isValid() && (t.seen || t.age > 2);
            if (t.age > maxAge || gone || t.arrow.isInBlock() || !enabled()) {
                it.remove();
                continue;
            }
            Location now = t.arrow.getLocation();
            Sink s = trackSink(t, now.getWorld());
            if (s == null) {
                it.remove();
                continue;
            }
            Location from = t.last != null && now.getWorld().equals(t.last.getWorld()) ? t.last : now;
            t.last = now.clone();
            if (!trailSegment(t.cos, s, from, now, t.dye, t.mult)) it.remove();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHit(ProjectileHitEvent e) {
        Track t = tracks.remove(e.getEntity().getUniqueId());
        if (t == null || t.cos.burst == null || !enabled()) return;
        Location l = e.getEntity().getLocation();
        Sink s = trackSink(t, l.getWorld());
        if (s == null) return;
        // Het laatste stukje van het spoor tot aan de inslag, en dan de klap.
        if (t.last != null && l.getWorld().equals(t.last.getWorld())) trailSegment(t.cos, s, t.last, l, t.dye, t.mult);
        safePlay(t.cos, t.cos.burst, s, l, t.dye, t.mult);
    }

    /**
     * Laat een effect zien alsof het echt gebeurt: bij een kill-effect één uitbarsting een stukje voor je,
     * bij een boog-effect een onzichtbare pijl die 30 blokken wegvliegt (even snel als een echte) en dan inslaat.
     */
    void simulate(Player p, Cosmetic c, Setting st, boolean everyone) {
        Dye dye = Dye.at(st.color);
        double mult = st.intensity.factor;
        Location eye = p.getEyeLocation();
        org.bukkit.util.Vector dir = eye.getDirection().clone().normalize();
        World w = eye.getWorld();
        Sink s = everyone && st.visible ? new WorldSink(w) : new PlayerSink(p);
        if (c.cat == Category.KILL) {
            if (c.burst != null) safePlay(c, c.burst, s, eye.clone().add(dir.clone().multiply(3.5)), dye, mult);
            return;
        }
        if (c.cat != Category.BOW || c.trail == null) return;
        UUID id = p.getUniqueId();
        final double speed = 2.5;
        final int ticks = 12;
        Location start = eye.clone().add(dir.clone().multiply(1.5)).add(0, -0.3, 0);
        for (int i = 1; i <= ticks; i++) {
            final int step = i;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                Player pp = Bukkit.getPlayer(id);
                if (pp == null || !pp.isOnline() || !pp.getWorld().equals(w)) return;
                Sink ss = everyone && st.visible ? new WorldSink(w) : new PlayerSink(pp);
                Location from = start.clone().add(dir.clone().multiply(speed * (step - 1)));
                Location to = start.clone().add(dir.clone().multiply(speed * step));
                trailSegment(c, ss, from, to, dye, mult);
                if (step == ticks && c.burst != null) safePlay(c, c.burst, ss, to, dye, mult);
            }, (long) i);
        }
    }

    /** Het testknopje van een speler: zijn gekozen effect met zijn eigen instellingen. */
    void preview(Player p, Category cat) {
        Profile pf = profile(p);
        Cosmetic c = selected(pf, cat);
        if (c == null || !access(p, pf, c).owned()) {
            say(p, "<red>Kies eerst een effect om te testen.");
            return;
        }
        simulate(p, c, pf.setting(cat), true);
    }

    /** Staff-test van elk effect, ook als je het zelf niet bezit. Alleen jij ziet het. */
    void adminTest(Player p, Cosmetic c) {
        simulate(p, c, new Setting(), false);
    }

    // ------------------------------------------------------------ events

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Profile pf = store.get(p.getUniqueId());
        if (pf != null && !pf.name.equals(p.getName())) {
            pf.name = p.getName();
            markDirty();
        }
        UUID id = p.getUniqueId();
        if (sync != null) {
            pullPlayer(id);
            Bukkit.getScheduler().runTaskLater(this, () -> pullPlayer(id), 60L);
        }
        if (packConfigured() && cfgBool("pack.stuur-bij-join", true)) {
            packAnswered.remove(id);
            // Niet te vroeg sturen: een client die nog aan het laden is (zeker na een wissel via Velocity) kan het pack missen.
            Bukkit.getScheduler().runTaskLater(this, () -> {
                Player pp = Bukkit.getPlayer(id);
                if (pp != null && pp.isOnline()) {
                    getLogger().info("Resourcepack gestuurd naar " + pp.getName() + " (bij join).");
                    sendPack(pp);
                }
            }, 40L);
            // Heeft de client na 8 seconden nog niets teruggemeld, dan sturen we het nog een keer.
            Bukkit.getScheduler().runTaskLater(this, () -> {
                Player pp = Bukkit.getPlayer(id);
                if (pp != null && pp.isOnline() && !packAnswered.contains(id)) {
                    getLogger().info("Geen antwoord van " + pp.getName() + " op het resourcepack, opnieuw gestuurd.");
                    sendPack(pp);
                }
            }, 200L);
        }
        // Even wachten: de client moet klaar zijn met laden voordat hij hoeden wil zien.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player pp = Bukkit.getPlayer(id);
            if (pp == null || !pp.isOnline()) return;
            enforce(pp);
            refreshAllHats();
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        prompts.remove(id);
        hatShown.remove(id);
        packLoaded.remove(id);
        packAnswered.remove(id);
        if (sync != null) pushChanges();
        tracks.values().removeIf(t -> t.owner.equals(id) && !t.visible);
        markDirty();
    }

    @EventHandler
    public void onPack(PlayerResourcePackStatusEvent e) {
        if (!PACK_ID.equals(e.getID())) return;
        Player p = e.getPlayer();
        String status = e.getStatus().name();
        packAnswered.add(p.getUniqueId());
        getLogger().info("Resourcepack-status van " + p.getName() + ": " + status);
        if (status.equals("SUCCESSFULLY_LOADED")) {
            packLoaded.add(p.getUniqueId());
            Bukkit.getScheduler().runTaskLater(this, this::refreshAllHats, 2L);
        } else if (status.equals("DECLINED") || status.equals("FAILED_DOWNLOAD") || status.equals("INVALID_URL")
                || status.equals("FAILED_RELOAD") || status.equals("DISCARDED")) {
            packLoaded.remove(p.getUniqueId());
            Bukkit.getScheduler().runTaskLater(this, this::refreshAllHats, 2L);
        }
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        refreshSoon(e.getPlayer(), 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        refreshSoon(e.getPlayer(), 3L);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        // Helm aan of uit gedaan? Dan klopt de hoed straks weer.
        if (e.getPlayer() instanceof Player p && hatShown.contains(p.getUniqueId())) refreshSoon(p, 1L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu m)) return;
        e.setCancelled(true);
        if (e.getWhoClicked() instanceof Player p) m.click(e, p);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMenuDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    // ------------------------------------------------------------ invoer via chat

    void prompt(Player p, String type, String help) {
        prompts.put(p.getUniqueId(), type);
        p.closeInventory();
        say(p, help);
        say(p, "<dark_gray>Typ <white>annuleer</white> om te stoppen.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        String type = prompts.remove(p.getUniqueId());
        if (type == null) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(this, () -> handlePrompt(p, type, text));
    }

    private void handlePrompt(Player p, String type, String text) {
        if (!p.isOnline() || !p.hasPermission(STAFF)) return;
        if (text.equalsIgnoreCase("annuleer") || text.equalsIgnoreCase("cancel")) {
            say(p, "<gray>Geannuleerd.");
            staff.openMain(p);
            return;
        }
        if (type.equals("SEARCH")) {
            Target t = resolveTarget(text);
            if (t == null) {
                say(p, "<red>Die speler ken ik niet. <gray>Hij moet minstens één keer gejoind zijn.");
                staff.openMain(p);
                return;
            }
            staff.openTarget(p, t, 0);
        }
    }

    // ------------------------------------------------------------ commando's

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("cosmeticsadmin")) return adminCommand(sender, args);
        if (!(sender instanceof Player p)) {
            say(sender, "<red>Dit commando is alleen voor spelers.");
            return true;
        }
        if (!canUse(p)) {
            say(p, "<red>Je hebt hier geen toegang toe. <gray>Cosmetics zijn voor spelers met een speciale rang.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("pack")) {
            if (!packConfigured()) say(p, "<yellow>Er is op deze server geen resourcepack ingesteld. <gray>Je ziet de gewone helmen als vervanging.");
            else {
                sendPack(p);
                say(p, "<green>Resourcepack wordt verstuurd. <gray>Accepteer hem als je Minecraft dat vraagt.");
            }
            return true;
        }
        gui.openMain(p);
        return true;
    }

    private boolean adminCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(STAFF)) {
            say(sender, "<red>Je hebt hier geen permissie voor.");
            return true;
        }
        String sub = args.length == 0 ? (sender instanceof Player ? "gui" : "hulp") : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "gui", "menu" -> {
                if (sender instanceof Player p) staff.openMain(p);
                else say(sender, "<red>Het menu kan alleen in het spel geopend worden.");
            }
            case "geef", "give", "neem", "take" -> {
                boolean give = sub.equals("geef") || sub.equals("give");
                if (args.length < 3) {
                    say(sender, "<gray>Gebruik: <white>/cosadmin " + sub + " <speler> <hat:kroon | kroon | alle>");
                    return true;
                }
                Target t = resolveTarget(args[1]);
                if (t == null) {
                    say(sender, "<red>Die speler ken ik niet.");
                    return true;
                }
                changeCommand(sender, t, args[2], give);
            }
            case "lijst", "list" -> {
                if (args.length >= 2) {
                    Target t = resolveTarget(args[1]);
                    if (t == null) {
                        say(sender, "<red>Die speler ken ik niet.");
                        return true;
                    }
                    Profile pf = store.getOrCreate(t.id(), t.name());
                    say(sender, "<gold>" + esc(t.name()) + "<gray> heeft gekregen: <white>" + (pf.granted.isEmpty() ? "niets" : esc(String.join(", ", pf.granted))));
                } else {
                    say(sender, "<gold>Alle cosmetics <gray>(" + catalog.all().size() + ")");
                    for (Cosmetic c : catalog.all()) {
                        say(sender, "<dark_gray> - <white>" + c.key() + " <gray>" + esc(c.name) + " <dark_gray>(" + c.permission() + ")" + (disabled(c) ? " <red>uitgeschakeld" : ""));
                    }
                }
            }
            case "reset" -> {
                if (args.length < 2) {
                    say(sender, "<gray>Gebruik: <white>/cosadmin reset <speler>");
                    return true;
                }
                Target t = resolveTarget(args[1]);
                if (t == null) {
                    say(sender, "<red>Die speler ken ik niet.");
                    return true;
                }
                Profile pf = store.getOrCreate(t.id(), t.name());
                pf.resetSettings();
                afterChange(t.id());
                say(sender, "<green>Instellingen van <white>" + esc(t.name()) + "<green> zijn teruggezet. Gegeven cosmetics blijven.");
            }
            case "effect", "test" -> {
                if (!(sender instanceof Player p)) {
                    say(sender, "<red>Dit kan alleen in het spel.");
                    return true;
                }
                if (args.length < 2) {
                    say(p, "<gray>Gebruik: <white>/cosadmin effect <kill:bliksem | bow:vlam | alle-boog | alle-kill>");
                    return true;
                }
                effectCommand(p, args[1]);
            }
            case "pack" -> {
                if (args.length < 2) {
                    say(sender, "<gold>Resourcepack <gray>- url ingesteld: " + (packConfigured() ? "<green>ja" : "<red>nee")
                            + "<gray>, extern: <white>" + cfgBool("pack.extern", false) + "<gray>, geladen bij <white>" + packLoadedCount() + "<gray> spelers.");
                    say(sender, "<gray>Opnieuw sturen: <white>/cosadmin pack <speler|alle>");
                    return true;
                }
                if (!packConfigured()) {
                    say(sender, "<red>Er staat geen pack.url in config.yml.");
                    return true;
                }
                if (args[1].equalsIgnoreCase("alle") || args[1].equalsIgnoreCase("all")) {
                    for (Player on : Bukkit.getOnlinePlayers()) sendPack(on);
                    say(sender, "<green>Pack naar iedereen gestuurd.");
                } else {
                    Player on = Bukkit.getPlayerExact(args[1]);
                    if (on == null) say(sender, "<red>Die speler is niet online.");
                    else {
                        sendPack(on);
                        say(sender, "<green>Pack naar " + esc(on.getName()) + " gestuurd.");
                    }
                }
            }
            case "reload" -> {
                reloadAll();
                say(sender, "<green>Config opnieuw geladen.");
            }
            default -> {
                say(sender, "<gold>Commando's <gray>(alles kan ook in <white>/cosadmin<gray>)");
                say(sender, "<white>/cosadmin</white> <gray>- staff-menu");
                say(sender, "<white>/cosadmin geef <speler> <cosmetic|alle></white> <gray>/ <white>neem");
                say(sender, "<white>/cosadmin lijst [speler]</white> <gray>- alle cosmetics of wat een speler kreeg");
                say(sender, "<white>/cosadmin effect <soort:naam | alle-kill | alle-boog></white> <gray>- test een effect (alleen jij ziet het)");
                say(sender, "<white>/cosadmin pack [speler|alle]</white> <gray>- resourcepack status of opnieuw sturen");
                say(sender, "<white>/cosadmin reset <speler></white> <gray>/ <white>reload");
            }
        }
        return true;
    }

    private void effectCommand(Player p, String what) {
        List<Cosmetic> list = new ArrayList<>();
        String w = what.toLowerCase(Locale.ROOT);
        if (w.equals("alle-kill") || w.equals("alle-boog")) {
            Category cat = w.equals("alle-kill") ? Category.KILL : Category.BOW;
            list = catalog.of(cat);
            say(p, "<gray>Ik speel " + list.size() + " effecten na elkaar af, één per seconde" + (cat == Category.BOW ? " (kijk de lucht in)." : "."));
            UUID id = p.getUniqueId();
            for (int i = 0; i < list.size(); i++) {
                final Cosmetic c = list.get(i);
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    Player pp = Bukkit.getPlayer(id);
                    if (pp == null || !pp.isOnline()) return;
                    pp.sendActionBar(parse("<gold>" + esc(c.name) + " <dark_gray>(" + c.key() + ")"));
                    adminTest(pp, c);
                }, i * 30L);
            }
            return;
        }
        list = catalog.findLoose(w);
        if (list.size() != 1 || !(list.get(0).cat == Category.KILL || list.get(0).cat == Category.BOW)) {
            say(p, "<red>Geen effect met die naam. <gray>Gebruik bv. <white>kill:bliksem<gray> of <white>bow:vlam<gray>.");
            return;
        }
        p.sendActionBar(parse("<gold>" + esc(list.get(0).name)));
        adminTest(p, list.get(0));
    }

    private void changeCommand(CommandSender sender, Target t, String what, boolean give) {
        List<Cosmetic> list = new ArrayList<>();
        boolean all = what.equalsIgnoreCase("alle") || what.equalsIgnoreCase("all");
        if (all) {
            for (Cosmetic c : catalog.all()) if (!disabled(c)) list.add(c);
        } else {
            list = catalog.findLoose(what);
            if (list.isEmpty()) {
                say(sender, "<red>Onbekende cosmetic. <gray>Zie <white>/cosadmin lijst");
                return;
            }
            if (list.size() > 1) {
                say(sender, "<red>Die naam bestaat in meer soorten. <gray>Gebruik bv. <white>" + list.get(0).key() + "<gray> of <white>" + list.get(1).key());
                return;
            }
            if (disabled(list.get(0))) {
                say(sender, "<red>Deze cosmetic is uitgeschakeld in de config.");
                return;
            }
        }
        int changed = 0;
        for (Cosmetic c : list) if (give ? give(t, c) : take(t, c)) changed++;
        String what2 = all ? "alle cosmetics" : esc(list.get(0).name);
        if (changed == 0) {
            say(sender, "<yellow>Er is niets veranderd. <gray>" + esc(t.name()) + (give ? " had " + what2 + " al." : " had " + what2 + " niet van jou gekregen."));
            return;
        }
        say(sender, "<green>" + esc(t.name()) + (give ? " heeft " + what2 + " gekregen." : " is " + what2 + " kwijt."));
        notify(t.id(), give ? "<green>Je hebt <white>" + what2 + "<green> gekregen! <gray>Open <white>/cosmetics" : "<yellow>Je bent <white>" + what2 + "<yellow> kwijtgeraakt.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!command.getName().equalsIgnoreCase("cosmeticsadmin") || !sender.hasPermission(STAFF)) return out;
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            for (String s : List.of("gui", "geef", "neem", "lijst", "reset", "effect", "pack", "reload", "hulp")) if (s.startsWith(last)) out.add(s);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (List.of("geef", "give", "neem", "take", "lijst", "list", "reset", "pack").contains(sub)) {
                for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
                if (sub.equals("pack") && "alle".startsWith(last)) out.add("alle");
            } else if (sub.equals("effect") || sub.equals("test")) {
                for (String s : List.of("alle-kill", "alle-boog")) if (s.startsWith(last)) out.add(s);
                for (Cosmetic c : catalog.all()) if ((c.cat == Category.KILL || c.cat == Category.BOW) && c.key().startsWith(last)) out.add(c.key());
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (List.of("geef", "give", "neem", "take").contains(sub)) {
                if ("alle".startsWith(last)) out.add("alle");
                for (Cosmetic c : catalog.all()) if (c.key().startsWith(last)) out.add(c.key());
            }
        }
        return out;
    }
}
