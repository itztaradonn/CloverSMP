package net.cloversmp.cloverblessing.ui;

import net.cloversmp.cloverblessing.CloverPlugin;
import net.cloversmp.cloverblessing.blessing.BlessingManager;
import net.cloversmp.cloverblessing.blessing.BlessingRegistry;
import net.cloversmp.cloverblessing.blessing.CooldownManager;
import net.cloversmp.cloverblessing.charge.ChargeManager;
import net.cloversmp.cloverblessing.crafting.ClaimService;
import net.cloversmp.cloverblessing.crafting.RecipeRegistry;
import net.cloversmp.cloverblessing.model.*;
import net.cloversmp.cloverblessing.storage.DataManager;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * The three milestone-1 menus: the Clover (loadout cross plus leaf 5), the Herbarium (owned blessings)
 * and the leaf picker. Every click inside these menus is cancelled, so items can never be taken out.
 */
public class Menus implements Listener {

    private static final Map<LeafSlot, Integer> LEAF_POSITIONS = Map.of(
            LeafSlot.LEAF_2, 13, LeafSlot.LEAF_1, 21, LeafSlot.LEAF_3, 23, LeafSlot.LEAF_4, 31, LeafSlot.CURSE, 40);
    private static final int INFO_SLOT = 4, CENTER_SLOT = 22, HERBARIUM_SLOT = 49, GUIDE_SLOT = 47, BACK_SLOT = 49;
    private static final int[] PICKER_POSITIONS = {10, 12, 14, 16};
    private static final int HERBARIUM_PAGE = 45;

    private final CloverPlugin plugin;
    private final DataManager data;
    private final BlessingRegistry registry;
    private final ChargeManager charge;
    private final CooldownManager cooldowns;
    private final BlessingManager blessings;
    private final ClaimService claims;
    private final RecipeRegistry recipes;

    public Menus(CloverPlugin plugin, DataManager data, BlessingRegistry registry, ChargeManager charge,
                 CooldownManager cooldowns, BlessingManager blessings,
                 ClaimService claims, RecipeRegistry recipes) {
        this.plugin = plugin;
        this.data = data;
        this.registry = registry;
        this.charge = charge;
        this.cooldowns = cooldowns;
        this.blessings = blessings;
        this.claims = claims;
        this.recipes = recipes;
    }

    // =====================================================================
    // Clover menu
    // =====================================================================

    public void openClover(Player player) {
        Clover clover = data.getClover(player.getUniqueId());
        if (clover == null) return;
        MenuHolder holder = new MenuHolder(MenuHolder.Kind.CLOVER, null);
        boolean cursed = clover.getEquippedBlessing(LeafSlot.CURSE) != null && charge.isLeafActive(clover, LeafSlot.CURSE);
        Inventory inv = create(holder, 54, cloverIcon(charge.getLeafCount(clover), cursed, false)
                .append(text(" Your Clover", cursed ? NamedTextColor.DARK_RED : NamedTextColor.DARK_GREEN)));

        int leaves = charge.getLeafCount(clover);
        inv.setItem(INFO_SLOT, item(Material.LILY_PAD, text("☘ " + player.getName() + "'s Clover", NamedTextColor.GREEN),
                line("Charge: " + clover.getCharge() + " / " + charge.getMaxCharge(), NamedTextColor.YELLOW),
                chargeBar(clover.getCharge(), charge.getMaxCharge()),
                line("Leaves open: " + leaves + " / 4", NamedTextColor.GREEN),
                line("Pollen: " + clover.getPollen(), NamedTextColor.GOLD)));
        inv.setItem(CENTER_SLOT, item(Material.MOSS_BLOCK, text("Clover heart", NamedTextColor.DARK_GREEN),
                line("Opposite leaves: 1 ↔ 3, 2 ↔ 4", NamedTextColor.GRAY),
                line("Leaf 5 holds a curse pact.", NamedTextColor.DARK_PURPLE)));

        LEAF_POSITIONS.forEach((slot, pos) -> inv.setItem(pos, leafItem(clover, slot)));
        inv.setItem(GUIDE_SLOT, item(Material.BOOK, text("Field Guide", NamedTextColor.GREEN),
                line(clover.getDiscoveredRecipes().size() + " / " + recipes.all().size() + " recipes discovered", NamedTextColor.GRAY),
                line("Press at a Pressing Table (a Fletching Table).", NamedTextColor.GRAY)));
        inv.setItem(HERBARIUM_SLOT, item(Material.BOOKSHELF, text("Herbarium", NamedTextColor.GREEN),
                line(clover.getHerbarium().size() + " stored blessings", NamedTextColor.GRAY),
                line("Click to browse and equip", NamedTextColor.YELLOW)));
        player.openInventory(inv);
    }

    private ItemStack leafItem(Clover clover, LeafSlot slot) {
        Blessing b = clover.getEquippedBlessing(slot);
        boolean open = charge.isLeafActive(clover, slot);
        String label = slot.isCurse() ? "Leaf 5 · Curse pact" : slot.label();

        if (b == null) {
            if (!open) {
                return item(Material.GRAY_STAINED_GLASS_PANE, text(label + " — closed", NamedTextColor.DARK_GRAY),
                        line("Opens at " + charge.thresholdFor(slot) + " charge.", NamedTextColor.GRAY));
            }
            return item(slot.isCurse() ? Material.PURPLE_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE,
                    text(label + " — empty", slot.isCurse() ? NamedTextColor.DARK_PURPLE : NamedTextColor.GREEN),
                    line(slot.isCurse() ? "Take a curse for its perk; breaking it costs charge." : "Ready for a blessing.",
                            NamedTextColor.GRAY),
                    line("Click to open your herbarium", NamedTextColor.YELLOW));
        }

        List<Component> extra = new ArrayList<>();
        extra.add(line(label + (open ? "" : " — wilted, paused"), open ? NamedTextColor.GRAY : NamedTextColor.RED));
        extra.add(line(slot.isCurse() ? "Shift-click to break the pact (costs "
                + (int) plugin.getConfig().getDouble("curse.break-cost-percent", 15) + "% charge)"
                : "Click to unequip", NamedTextColor.YELLOW));
        return blessingItem(clover, b, extra);
    }

    // =====================================================================
    // Herbarium
    // =====================================================================

    public void openHerbarium(Player player) {
        Clover clover = data.getClover(player.getUniqueId());
        if (clover == null) return;
        MenuHolder holder = new MenuHolder(MenuHolder.Kind.HERBARIUM, null);
        Inventory inv = create(holder, 54, "☘ Herbarium");

        int i = 0;
        // shortcut: shows the first 45 blessings only; add paging once players hold more.
        for (Blessing b : clover.getHerbarium().values()) {
            if (i >= HERBARIUM_PAGE) break;
            inv.setItem(i, blessingItem(clover, b, List.of(line("Left-click to equip", NamedTextColor.YELLOW),
                    b.isBound() ? line("Bound to you (grafted): can't be taken out", NamedTextColor.DARK_GRAY)
                            : line("Right-click to take it out as an item", NamedTextColor.YELLOW))));
            holder.blessingSlots().put(i++, b.getId());
        }
        if (i == 0) {
            inv.setItem(22, item(Material.DEAD_BUSH, text("Your herbarium is empty", NamedTextColor.GRAY),
                    line("Blessings you own but haven't equipped are kept here.", NamedTextColor.DARK_GRAY)));
        }
        inv.setItem(BACK_SLOT, item(Material.ARROW, text("Back to your clover", NamedTextColor.YELLOW)));
        player.openInventory(inv);
    }

    /** Admin: every blessing and curse; clicking one gives the admin a fresh, ungrafted copy. */
    public void openAdminAll(Player player) {
        Clover clover = data.getClover(player.getUniqueId());
        if (clover == null) return;
        Inventory inv = create(new MenuHolder(MenuHolder.Kind.ADMIN_ALL, null), 54, "☘ All blessings (admin)");
        List<BlessingCore> all = List.copyOf(registry.getAllCores());
        for (int i = 0; i < all.size() && i < HERBARIUM_PAGE; i++) {
            Blessing preview = new Blessing(UUID.randomUUID(), all.get(i).getId(), null, player.getUniqueId());
            inv.setItem(i, blessingItem(clover, preview, List.of(line("Click to give yourself one", NamedTextColor.YELLOW),
                    line("id: " + all.get(i).getId(), NamedTextColor.DARK_GRAY))));
        }
        inv.setItem(BACK_SLOT, item(Material.ARROW, text("Back to your clover", NamedTextColor.YELLOW)));
        player.openInventory(inv);
    }

    /** Every recipe: discovered ones in full, the rest as silhouettes with category and ingredient hints. */
    public void openFieldGuide(Player player) {
        Clover clover = data.getClover(player.getUniqueId());
        if (clover == null) return;
        Inventory inv = create(new MenuHolder(MenuHolder.Kind.FIELD_GUIDE, null), 54, "☘ Field Guide");
        int i = 0;
        for (RecipeRegistry.Recipe r : recipes.all()) {
            BlessingCore core = registry.getCore(r.coreId());
            if (core == null || i >= HERBARIUM_PAGE) continue;
            if (clover.hasDiscoveredRecipe(r.coreId())) {
                inv.setItem(i++, item(materialFor(core.getCategory()), text(core.getDisplayName(), categoryColor(core.getCategory())),
                        line(core.getCategory().getIcon() + " " + core.getCategory().getDisplayName() + (r.rare() ? " · rare" : ""),
                                categoryColor(core.getCategory())),
                        line(core.getRuleText(), NamedTextColor.WHITE),
                        line("Clover Seed + " + String.join(", ", r.ingredients().stream().map(Menus::pretty).toList()),
                                NamedTextColor.YELLOW)));
            } else {
                inv.setItem(i++, item(Material.GRAY_DYE, text("??? · " + core.getCategory().getDisplayName(), NamedTextColor.GRAY),
                        line(r.rare() ? "Found in the world: look for a recipe scrap." : "Hints: "
                                + String.join(", ", core.getIngredientHints()), NamedTextColor.DARK_GRAY)));
            }
        }
        inv.setItem(BACK_SLOT, item(Material.ARROW, text("Back to your clover", NamedTextColor.YELLOW)));
        player.openInventory(inv);
    }

    public static String pretty(Material m) {
        String s = m.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public void openLeafPicker(Player player, Blessing blessing) {
        Clover clover = data.getClover(player.getUniqueId());
        if (clover == null) return;
        MenuHolder holder = new MenuHolder(MenuHolder.Kind.LEAF_PICKER, blessing.getId());
        Inventory inv = create(holder, 27, "☘ Choose a leaf");
        LeafSlot[] slots = {LeafSlot.LEAF_1, LeafSlot.LEAF_2, LeafSlot.LEAF_3, LeafSlot.LEAF_4};
        for (int i = 0; i < slots.length; i++) inv.setItem(PICKER_POSITIONS[i], leafItem(clover, slots[i]));
        inv.setItem(22, item(Material.ARROW, text("Back", NamedTextColor.YELLOW)));
        player.openInventory(inv);
    }

    // =====================================================================
    // Clicks
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder) || holder.kind().isTable()) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        int raw = event.getRawSlot();
        Clover clover = data.getClover(player.getUniqueId());
        if (clover == null) return;

        switch (holder.kind()) {
            case CLOVER -> {
                if (raw == HERBARIUM_SLOT) {
                    reopen(player, () -> openHerbarium(player));
                    return;
                }
                if (raw == GUIDE_SLOT) {
                    reopen(player, () -> openFieldGuide(player));
                    return;
                }
                LeafSlot slot = slotAt(raw);
                if (slot == null) return;
                if (clover.getEquippedBlessing(slot) != null) {
                    if (slot.isCurse() && !event.isShiftClick()) {
                        player.sendMessage(Component.text("Shift-click to break the pact; it costs charge.", NamedTextColor.RED));
                        return;
                    }
                    blessings.unequip(player, slot);
                    reopen(player, () -> openClover(player));
                } else if (charge.isLeafActive(clover, slot)) {
                    reopen(player, () -> openHerbarium(player));
                }
            }
            case HERBARIUM -> {
                if (raw == BACK_SLOT) {
                    reopen(player, () -> openClover(player));
                    return;
                }
                UUID id = holder.blessingSlots().get(raw);
                Blessing b = id == null ? null : clover.getHerbarium().get(id);
                BlessingCore core = b == null ? null : registry.getCore(b.getCoreId());
                if (core == null) return;
                if (event.isRightClick()) {
                    if (claims.takeOut(player, b.getId())) reopen(player, () -> openHerbarium(player));
                    return;
                }
                if (core.isCurse()) {
                    if (clover.getEquippedBlessing(LeafSlot.CURSE) != null && !event.isShiftClick()) {
                        player.sendMessage(Component.text("Shift-click to replace your curse; breaking a pact costs charge.",
                                NamedTextColor.RED));
                        return;
                    }
                    blessings.equip(player, b.getId(), LeafSlot.CURSE);
                    reopen(player, () -> openClover(player));
                } else {
                    reopen(player, () -> openLeafPicker(player, b));
                }
            }
            case ADMIN_ALL -> {
                if (raw == BACK_SLOT) {
                    reopen(player, () -> openClover(player));
                    return;
                }
                List<BlessingCore> all = List.copyOf(registry.getAllCores());
                if (!player.hasPermission("clover.admin") || raw >= all.size()) return;
                BlessingCore core = all.get(raw);
                if (blessings.give(clover, core.getId(), null) != null) {
                    player.sendMessage(Component.text("Added " + core.getDisplayName() + " to your herbarium.", NamedTextColor.GREEN));
                }
            }
            case FIELD_GUIDE -> {
                if (raw == BACK_SLOT) reopen(player, () -> openClover(player));
            }
            case LEAF_PICKER -> {
                if (raw == 22) {
                    reopen(player, () -> openHerbarium(player));
                    return;
                }
                for (int i = 0; i < PICKER_POSITIONS.length; i++) {
                    if (PICKER_POSITIONS[i] == raw) {
                        blessings.equip(player, holder.subject(), LeafSlot.values()[i]);
                        reopen(player, () -> openClover(player));
                        return;
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder h && !h.kind().isTable()) event.setCancelled(true);
    }

    /** Opening an inventory inside a click handler is unsafe, so swap menus on the next tick. */
    private void reopen(Player player, Runnable open) {
        Bukkit.getScheduler().runTask(plugin, open);
    }

    private static LeafSlot slotAt(int raw) {
        for (var e : LEAF_POSITIONS.entrySet()) if (e.getValue() == raw) return e.getKey();
        return null;
    }

    // =====================================================================
    // Items
    // =====================================================================

    public ItemStack blessingItem(Clover clover, Blessing b, List<Component> extra) {
        BlessingCore core = registry.getCore(b.getCoreId());
        if (core == null) {
            return item(Material.BARRIER, text("Unknown blessing: " + b.getCoreId(), NamedTextColor.RED));
        }
        List<Component> lore = new ArrayList<>();
        lore.add(line(core.getCategory().getIcon() + " " + core.getCategory().getDisplayName()
                + (core.isRare() ? " · rare" : ""), categoryColor(core.getCategory())));
        lore.add(line(core.getRuleText(), NamedTextColor.WHITE));
        lore.add(line((core.isCurse() ? "☠ " : "⚠ ") + core.getWeaknessText(), NamedTextColor.RED));
        if (b.hasGraft()) {
            lore.add(line("Graft: " + b.getGraft().getDisplayName()
                    + (b.getGraft().hasCondition() ? " (" + b.getGraft().getCondition() + ")" : ""), NamedTextColor.LIGHT_PURPLE));
        }
        long remaining = cooldowns.getRemainingMs(clover, b.getId());
        if (remaining > 0) lore.add(line("Ready in " + (remaining / 1000 + 1) + "s", NamedTextColor.GOLD));
        if (!core.isEchoable() && !core.isCurse()) lore.add(line("Echo Leaf cannot copy this.", NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        lore.addAll(extra);
        return item(materialFor(core.getCategory()), text(core.getDisplayName(), categoryColor(core.getCategory())),
                lore.toArray(Component[]::new));
    }

    private static Material materialFor(BlessingCategory c) {
        return switch (c) {
            case OFFENSE -> Material.ROSE_BUSH;
            case DEFENSE -> Material.MOSS_BLOCK;
            case MOBILITY -> Material.FEATHER;
            case RECOVERY -> Material.GLISTERING_MELON_SLICE;
            case CROWD_CONTROL -> Material.VINE;
            case UTILITY -> Material.LANTERN;
            case RISK_AND_REWARD -> Material.GOLD_NUGGET;
            case EXPERIMENTAL -> Material.AMETHYST_SHARD;
            case CURSE -> Material.WITHER_ROSE;
        };
    }

    private static final Key ICON_FONT = Key.key("cloverblessing", "icons");

    /**
     * The clover image from the server resource pack: 1-4 leaves to match the open leaves, or the red/black
     * clover while a curse is active. Without the pack it shows as a missing-glyph box. White and shadowless,
     * since text colour tints font images. Glyphs are defined in resourcepack/assets/cloverblessing/font/icons.json.
     */
    public static Component cloverIcon(int leaves, boolean cursed, boolean large) {
        char glyph;
        if (cursed) glyph = large ? '\uE001' : '\uE003';
        else if (leaves >= 4) glyph = large ? '\uE000' : '\uE002';
        else glyph = (char) ((large ? '\uE004' : '\uE007') + Math.max(1, leaves) - 1);
        return Component.text(glyph).font(ICON_FONT).color(NamedTextColor.WHITE).shadowColor(ShadowColor.none())
                .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false);
    }

    public static TextColor categoryColor(BlessingCategory c) {
        return switch (c) {
            case OFFENSE -> NamedTextColor.RED;
            case DEFENSE -> NamedTextColor.BLUE;
            case MOBILITY -> NamedTextColor.AQUA;
            case RECOVERY -> NamedTextColor.GREEN;
            case CROWD_CONTROL -> NamedTextColor.DARK_GREEN;
            case UTILITY -> NamedTextColor.GRAY;
            case RISK_AND_REWARD -> NamedTextColor.GOLD;
            case EXPERIMENTAL -> NamedTextColor.LIGHT_PURPLE;
            case CURSE -> NamedTextColor.DARK_PURPLE;
        };
    }

    public static Inventory create(MenuHolder holder, int size, String title) {
        return create(holder, size, text(title, NamedTextColor.DARK_GREEN));
    }

    public static Inventory create(MenuHolder holder, int size, Component title) {
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);
        return inv;
    }

    public static ItemStack item(Material material, Component name, Component... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name);
        if (lore.length > 0) meta.lore(List.of(lore));
        stack.setItemMeta(meta);
        return stack;
    }

    public static Component text(String s, TextColor color) {
        return Component.text(s, color).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true);
    }

    public static Component line(String s, TextColor color) {
        return Component.text(s, color).decoration(TextDecoration.ITALIC, false);
    }

    private static Component chargeBar(int value, int max) {
        int filled = (int) Math.round(20.0 * value / Math.max(1, max));
        return Component.text("█".repeat(filled), NamedTextColor.GREEN)
                .append(Component.text("█".repeat(20 - filled), NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false);
    }
}
