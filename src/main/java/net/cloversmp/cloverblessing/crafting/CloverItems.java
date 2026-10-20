package net.cloversmp.cloverblessing.crafting;

import net.cloversmp.cloverblessing.model.Blessing;
import net.cloversmp.cloverblessing.model.BlessingCore;
import net.cloversmp.cloverblessing.ui.Menus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The plugin's own items: Clover Seeds, blessing claims and recipe scraps. Each is a {@code PAPER} stack
 * identified only by a persistent-data tag; the name and lore are cosmetic.
 */
public class CloverItems {

    public enum Kind { SEED, CLAIM, SCRAP }

    private final NamespacedKey kindKey;
    private final NamespacedKey dataKey;

    public CloverItems(Plugin plugin) {
        this.kindKey = new NamespacedKey(plugin, "item");
        this.dataKey = new NamespacedKey(plugin, "item_data");
    }

    public ItemStack seed(int amount) {
        ItemStack item = base(Kind.SEED, null, Menus.text("Clover Seed", NamedTextColor.GREEN),
                Menus.line("Every blessing starts as a seed.", NamedTextColor.GRAY),
                Menus.line("Press it at a Pressing Table (a Fletching Table).", NamedTextColor.YELLOW));
        item.setAmount(amount);
        return item;
    }

    /** A claim on a blessing in the ledger. Carries only the blessing id. */
    public ItemStack claim(Blessing blessing, BlessingCore core) {
        List<Component> lore = new ArrayList<>();
        lore.add(Menus.line(core.getCategory().getIcon() + " " + core.getCategory().getDisplayName(),
                Menus.categoryColor(core.getCategory())));
        lore.add(Menus.line(core.getRuleText(), NamedTextColor.WHITE));
        lore.add(Menus.line("⚠ " + core.getWeaknessText(), NamedTextColor.RED));
        lore.add(Component.empty());
        lore.add(Menus.line("Right-click to add it to your herbarium.", NamedTextColor.YELLOW));
        ItemStack item = base(Kind.CLAIM, blessing.getId().toString(),
                Menus.text(core.getDisplayName() + " (blessing)", Menus.categoryColor(core.getCategory())),
                lore.toArray(Component[]::new));
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        meta.setMaxStackSize(1);
        item.setItemMeta(meta);
        return item;
    }

    /** A scrap that teaches one rare recipe. */
    public ItemStack scrap(BlessingCore core) {
        return base(Kind.SCRAP, core.getId(), Menus.text("Recipe scrap: " + core.getDisplayName(), NamedTextColor.LIGHT_PURPLE),
                Menus.line("A torn page describing a rare pressing.", NamedTextColor.GRAY),
                Menus.line("Right-click to learn it in your Field Guide.", NamedTextColor.YELLOW));
    }

    /** The kind of plugin item, or null for any other item. */
    public Kind kind(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(kindKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return Kind.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Claim → blessing id; scrap → core id. */
    public String data(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer().get(dataKey, PersistentDataType.STRING);
    }

    public UUID claimId(ItemStack item) {
        try {
            return UUID.fromString(data(item));
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private ItemStack base(Kind kind, String data, Component name, Component... lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(List.of(lore));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(kindKey, PersistentDataType.STRING, kind.name());
        if (data != null) pdc.set(dataKey, PersistentDataType.STRING, data);
        item.setItemMeta(meta);
        return item;
    }
}
