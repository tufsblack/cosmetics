package nl.cosmetics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/** Bouwstenen die beide menu's delen. */
final class Kit {
    private final CosmeticsPlugin pl;

    Kit(CosmeticsPlugin pl) {
        this.pl = pl;
    }

    Component c(String mini) {
        return pl.parse(mini).decoration(TextDecoration.ITALIC, false);
    }

    ItemStack icon(Material m, String name, String... lore) {
        return icon(new ItemStack(m), name, lore);
    }

    ItemStack icon(ItemStack it, String name, String... lore) {
        it.editMeta(meta -> {
            meta.displayName(c(name));
            if (lore.length > 0) {
                List<Component> l = new ArrayList<>();
                for (String s : lore) l.add(c(s));
                meta.lore(l);
            }
        });
        return it;
    }

    ItemStack icon(Material m, String name, List<String> lore) {
        return icon(m, name, lore.toArray(new String[0]));
    }

    ItemStack icon(ItemStack it, String name, List<String> lore) {
        return icon(it, name, lore.toArray(new String[0]));
    }

    ItemStack glow(ItemStack it) {
        it.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        return it;
    }

    ItemStack filler() {
        ItemStack it = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        it.editMeta(meta -> meta.displayName(Component.text(" ")));
        return it;
    }

    ItemStack head(OfflinePlayer owner, String name, List<String> lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        it.editMeta(SkullMeta.class, meta -> {
            try {
                meta.setOwningPlayer(owner);
            } catch (RuntimeException ignored) {
                // Een hoofd zonder skin is prima.
            }
            meta.displayName(c(name));
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(c(s));
            meta.lore(l);
        });
        return it;
    }

    void tick(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
    }

    void deny(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
    }

    static String esc(String s) {
        return s == null ? "" : s.replace("<", "‹").replace(">", "›");
    }

    static int pageCount(int n, int per) {
        return Math.max(1, (n + per - 1) / per);
    }
}
