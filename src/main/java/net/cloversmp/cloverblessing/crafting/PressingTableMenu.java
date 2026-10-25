package net.cloversmp.cloverblessing.crafting;

import net.cloversmp.cloverblessing.ui.Effects;
import net.cloversmp.cloverblessing.CloverPlugin;
import net.cloversmp.cloverblessing.blessing.BlessingManager;
import net.cloversmp.cloverblessing.blessing.BlessingRegistry;
import net.cloversmp.cloverblessing.model.*;
import net.cloversmp.cloverblessing.storage.DataManager;
import net.cloversmp.cloverblessing.ui.MenuHolder;
import net.cloversmp.cloverblessing.ui.Menus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * The Pressing Table: any Fletching Table. Two tabs — Press (seed + up to three ingredients → a blessing)
 * and Graft (add or prune a graft on a herbarium blessing).
 * <p>
 * Only the four input slots of the Press tab accept items; every other click is cancelled. Items left in the
 * inputs are always returned when the menu closes (including quit, death and plugin shutdown).
 */
public class PressingTableMenu implements Listener {

    private static final int TAB_PRESS = 3, TAB_GRAFT = 5;
    private static final int SEED_SLOT = 19, PREVIEW_SLOT = 25, PRESS_BUTTON = 40;
    private static final int[] INGREDIENT_SLOTS = {21, 22, 23};
    private static final Set<Integer> INPUTS = Set.of(SEED_SLOT, 21, 22, 23);
    private static final int GRAFT_LIST_START = 9, GRAFT_LIST_END = 45, POLLEN_SLOT = 49;
    private static final int SUBJECT_SLOT = 13, PRUNE_SLOT = 31, BACK_SLOT = 49;
    private static final int[] GRAFT_OPTION_SLOTS = {29, 30, 31, 32, 33};

    private final CloverPlugin plugin;
    private final DataManager data;
    private final BlessingRegistry registry;
    private final RecipeRegistry recipes;
    private final CloverItems items;
    private final BlessingManager blessings;
    private final CraftLog log;
    private final Menus menus;

    public PressingTableMenu(CloverPlugin plugin, DataManager data, BlessingRegistry registry, RecipeRegistry recipes,
                             CloverItems items, BlessingManager blessings, CraftLog log, Menus menus) {
        this.plugin = plugin;
        this.data = data;
        this.registry = registry;
        this.recipes = recipes;
        this.items = items;
        this.blessings = blessings;
        this.log = log;
        this.menus = menus;
    }

    /** Right-click a Fletching Table (not sneaking) to open it. Runs last so protection plugins can deny it. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onOpenTable(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.FLETCHING_TABLE
                || event.getPlayer().isSneaking() || event.useInteractedBlock() == Event.Result.DENY) return;
        event.setCancelled(true);
        openPress(event.getPlayer());
    }

    // =====================================================================
    // Press tab
    // =====================================================================

    public void openPress(Player player) {
        MenuHolder holder = new MenuHolder(MenuHolder.Kind.PRESS, null);
        Inventory inv = Menus.create(holder, 54, "☘ Pressing Table");
        fill(inv);
        tabs(inv, true);
        for (int slot : INPUTS) inv.setItem(slot, null);
        inv.setItem(10, Menus.item(Material.LIME_STAINED_GLASS_PANE, Menus.text("Clover Seed ↓", NamedTextColor.GREEN)));
        for (int s : new int[]{12, 13, 14}) {
            inv.setItem(s, Menus.item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, Menus.text("Ingredient ↓", NamedTextColor.AQUA),
                    Menus.line("Up to three, in any order.", NamedTextColor.GRAY)));
        }
        inv.setItem(24, Menus.item(Material.ARROW, Menus.text("→", NamedTextColor.GRAY)));
        player.openInventory(inv);
        refreshPreview(player, inv);
    }

    private void refreshPreview(Player player, Inventory inv) {
        Clover clover = data.getClover(player.getUniqueId());
        if (clover == null) return;
        Outcome o = evaluate(clover, inv);
        if (o.core == null) {
            inv.setItem(PREVIEW_SLOT, Menus.item(Material.BARRIER, Menus.text(o.problem, NamedTextColor.RED)));
            inv.setItem(PRESS_BUTTON, Menus.item(Material.GRAY_DYE, Menus.text("Press", NamedTextColor.DARK_GRAY),
                    Menus.line(o.problem, NamedTextColor.GRAY)));
            return;
        }
        Blessing preview = new Blessing(UUID.randomUUID(), o.core.getId(), null, player.getUniqueId());
        inv.setItem(PREVIEW_SLOT, menus.blessingItem(clover, preview, List.of(
                Menus.line(o.problem == null ? "Click Press to make it." : o.problem,
                        o.problem == null ? NamedTextColor.YELLOW : NamedTextColor.RED))));
        inv.setItem(PRESS_BUTTON, o.problem == null
                ? Menus.item(Material.LIME_DYE, Menus.text("Press", NamedTextColor.GREEN),
                Menus.line("Uses 1 seed and 1 of each ingredient.", NamedTextColor.GRAY))
                : Menus.item(Material.GRAY_DYE, Menus.text("Press", NamedTextColor.DARK_GRAY), Menus.line(o.problem, NamedTextColor.GRAY)));
    }

    /** What the current inputs would press. {@code core} null = nothing; {@code problem} non-null = can't press yet. */
    private record Outcome(BlessingCore core, String problem) {}

    private Outcome evaluate(Clover clover, Inventory inv) {
        List<Material> ingredients = new ArrayList<>();
        for (int slot : INGREDIENT_SLOTS) {
            ItemStack i = inv.getItem(slot);
            if (i == null) continue;
            if (items.kind(i) != null) return new Outcome(null, "Plugin items aren't ingredients");
            ingredients.add(i.getType());
        }
        RecipeRegistry.Recipe recipe = recipes.match(ingredients);
        BlessingCore core = recipe == null ? null : registry.getCore(recipe.coreId());
        if (core == null) return new Outcome(null, ingredients.isEmpty() ? "Add ingredients" : "No recipe matches");
        if (recipe.rare() && !clover.hasDiscoveredRecipe(core.getId())) {
            return new Outcome(null, "You haven't learned this rare recipe");
        }
        if (items.kind(inv.getItem(SEED_SLOT)) != CloverItems.Kind.SEED) return new Outcome(core, "Put a Clover Seed in the seed slot");
        return new Outcome(core, null);
    }

    private void press(Player player, Inventory inv) {
        Clover clover = data.getClover(player.getUniqueId());
        if (clover == null) return;
        Outcome o = evaluate(clover, inv);
        if (o.core == null || o.problem != null) {
            player.sendMessage(Component.text(o.problem, NamedTextColor.RED));
            return;
        }
        take(inv, SEED_SLOT);
        for (int slot : INGREDIENT_SLOTS) if (inv.getItem(slot) != null) take(inv, slot);
        Blessing b = blessings.give(clover, o.core.getId(), null);
        log.log(CraftLog.Action.PRESS, player.getName(), b.getId(), b.getCoreId(), null);
        Effects.sound(player.getLocation(), "craft.press", 1f, 1f);
        Effects.particles(Effects.body(player, 0.6), org.bukkit.Particle.COMPOSTER, 12, 0.4);
        player.sendMessage(Component.text("Pressed " + o.core.getDisplayName() + ". It's in your herbarium (/clover).",
                NamedTextColor.GREEN));
        refreshPreview(player, inv);
    }

    private static void take(Inventory inv, int slot) {
        ItemStack i = inv.getItem(slot);
        if (i == null) return;
        i.setAmount(i.getAmount() - 1);
        inv.setItem(slot, i.getAmount() > 0 ? i : null);
    }

    // =====================================================================
    // Graft tab
    // =====================================================================

    public void openGraftList(Player player) {
        Clover clover = data.getClover(player.getUniqueId());
        if (clover == null) return;
        MenuHolder holder = new MenuHolder(MenuHolder.Kind.GRAFT_LIST, null);
        Inventory inv = Menus.create(holder, 54, "☘ Graft and prune");
        fill(inv);
        tabs(inv, false);
        int slot = GRAFT_LIST_START;
        // shortcut: lists the first 36 herbarium blessings; page it if players hoard more.
        for (Blessing b : clover.getHerbarium().values()) {
            BlessingCore core = registry.getCore(b.getCoreId());
            if (core == null || core.isCurse()) continue;
            if (slot >= GRAFT_LIST_END) break;
            inv.setItem(slot, menus.blessingItem(clover, b, List.of(Menus.line(
                    b.hasGraft() ? "Click to prune" : "Click to graft", NamedTextColor.YELLOW))));
            holder.blessingSlots().put(slot++, b.getId());
        }
        if (slot == GRAFT_LIST_START) {
            inv.setItem(22, Menus.item(Material.DEAD_BUSH, Menus.text("Nothing to graft", NamedTextColor.GRAY),
                    Menus.line("Grafts go on blessings in your herbarium (not equipped, not curses).", NamedTextColor.DARK_GRAY)));
        }
        inv.setItem(POLLEN_SLOT, pollenItem(clover));
        player.openInventory(inv);
    }

    public void openGraftOne(Player player, UUID blessingId) {
        Clover clover = data.getClover(player.getUniqueId());
        Blessing b = clover == null ? null : clover.getHerbarium().get(blessingId);
        if (b == null) {
            openGraftList(player);
            return;
        }
        MenuHolder holder = new MenuHolder(MenuHolder.Kind.GRAFT_ONE, blessingId);
        Inventory inv = Menus.create(holder, 54, "☘ Graft and prune");
        fill(inv);
        inv.setItem(SUBJECT_SLOT, menus.blessingItem(clover, b, List.of()));
        int pollenCost = pollenCost(), count = materialCount();
        double refundPct = plugin.getConfig().getDouble("crafting.prune-refund-percent", 50);
        if (b.hasGraft()) {
            inv.setItem(PRUNE_SLOT, Menus.item(Material.SHEARS, Menus.text("Prune " + b.getGraft().getDisplayName(), NamedTextColor.YELLOW),
                    Menus.line("Removes the graft; the blessing is tradable again.", NamedTextColor.GRAY),
                    Menus.line("Refund: " + GraftRules.refund(pollenCost, refundPct) + " Pollen and "
                            + GraftRules.refund(count, refundPct) + "× " + Menus.pretty(GraftRules.material(b.getGraft())), NamedTextColor.GREEN)));
        } else {
            Graft[] grafts = Graft.values();
            for (int i = 0; i < grafts.length; i++) {
                Graft g = grafts[i];
                Material mat = GraftRules.material(g);
                int have = countIn(player, mat);
                inv.setItem(GRAFT_OPTION_SLOTS[i], Menus.item(mat, Menus.text("Graft: " + g.getDisplayName(), NamedTextColor.LIGHT_PURPLE),
                        Menus.line(describe(g), NamedTextColor.WHITE),
                        Menus.line("Cost: " + pollenCost + " Pollen (you have " + clover.getPollen() + ")",
                                clover.getPollen() >= pollenCost ? NamedTextColor.GREEN : NamedTextColor.RED),
                        Menus.line("Cost: " + count + "× " + Menus.pretty(mat) + " (you have " + have + ")",
                                have >= count ? NamedTextColor.GREEN : NamedTextColor.RED),
                        Menus.line("Grafting binds the blessing to you.", NamedTextColor.GRAY)));
            }
        }
        inv.setItem(BACK_SLOT, Menus.item(Material.ARROW, Menus.text("Back", NamedTextColor.YELLOW)));
        player.openInventory(inv);
    }

    private void graft(Player player, UUID blessingId, Graft graft) {
        Clover clover = data.getClover(player.getUniqueId());
        Blessing b = clover == null ? null : clover.getHerbarium().get(blessingId);
        BlessingCore core = b == null ? null : registry.getCore(b.getCoreId());
        if (core == null || b.hasGraft() || core.isCurse()) return;
        int cost = pollenCost(), count = materialCount();
        Material mat = GraftRules.material(graft);
        if (clover.getPollen() < cost) {
            player.sendMessage(Component.text("You need " + cost + " Pollen (overflow charge above the max).", NamedTextColor.RED));
            return;
        }
        if (countIn(player, mat) < count) {
            player.sendMessage(Component.text("You need " + count + "× " + Menus.pretty(mat) + ".", NamedTextColor.RED));
            return;
        }
        player.getInventory().removeItem(new ItemStack(mat, count));
        clover.setPollen(clover.getPollen() - cost);
        clover.removeFromHerbarium(blessingId);
        clover.addToHerbarium(new Blessing(blessingId, b.getCoreId(), graft, b.getOwnerUuid()));
        data.saveNow(player.getUniqueId());
        player.saveData();
        log.log(CraftLog.Action.GRAFT, player.getName(), blessingId, b.getCoreId(), graft.name());
        Effects.sound(player.getLocation(), "craft.graft", 1f, 1f);
        Effects.particles(Effects.body(player, 0.6), org.bukkit.Particle.WAX_ON, 12, 0.4);
        player.sendMessage(Component.text(core.getDisplayName() + " grafted with " + graft.getDisplayName() + ".", NamedTextColor.LIGHT_PURPLE));
    }

    private void prune(Player player, UUID blessingId) {
        Clover clover = data.getClover(player.getUniqueId());
        Blessing b = clover == null ? null : clover.getHerbarium().get(blessingId);
        if (b == null || !b.hasGraft()) return;
        double pct = plugin.getConfig().getDouble("crafting.prune-refund-percent", 50);
        int pollen = GraftRules.refund(pollenCost(), pct);
        int mats = GraftRules.refund(materialCount(), pct);
        Material mat = GraftRules.material(b.getGraft());
        clover.setPollen(clover.getPollen() + pollen);
        if (mats > 0) giveOrDrop(player, new ItemStack(mat, mats));
        clover.removeFromHerbarium(blessingId);
        clover.addToHerbarium(new Blessing(blessingId, b.getCoreId(), null, b.getOwnerUuid()));
        data.saveNow(player.getUniqueId());
        player.saveData();
        log.log(CraftLog.Action.PRUNE, player.getName(), blessingId, b.getCoreId(), b.getGraft().name());
        Effects.sound(player.getLocation(), "craft.prune", 1f, 1f);
        player.sendMessage(Component.text("Pruned. Refunded " + pollen + " Pollen and " + mats + "× " + Menus.pretty(mat) + ".",
                NamedTextColor.YELLOW));
    }

    // =====================================================================
    // Events
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder holder) || !holder.kind().isTable()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int raw = event.getRawSlot();
        boolean inTop = raw >= 0 && raw < top.getSize();

        if (holder.kind() == MenuHolder.Kind.PRESS && event.getAction() != InventoryAction.COLLECT_TO_CURSOR
                && (!inTop || INPUTS.contains(raw))) {
            // Moving items between the player's inventory and the four inputs is allowed.
            Bukkit.getScheduler().runTask(plugin, () -> refreshPreview(player, top));
            return;
        }
        event.setCancelled(true);
        if (!inTop) return;

        switch (holder.kind()) {
            case PRESS -> {
                if (raw == PRESS_BUTTON) press(player, top);
                else if (raw == TAB_GRAFT) later(() -> openGraftList(player));
            }
            case GRAFT_LIST -> {
                if (raw == TAB_PRESS) later(() -> openPress(player));
                UUID id = holder.blessingSlots().get(raw);
                if (id != null) later(() -> openGraftOne(player, id));
            }
            case GRAFT_ONE -> {
                UUID id = holder.subject();
                if (raw == BACK_SLOT) {
                    later(() -> openGraftList(player));
                    return;
                }
                Clover clover = data.getClover(player.getUniqueId());
                Blessing b = clover == null ? null : clover.getHerbarium().get(id);
                if (b == null) return;
                if (b.hasGraft() && raw == PRUNE_SLOT) {
                    prune(player, id);
                    later(() -> openGraftOne(player, id));
                } else if (!b.hasGraft()) {
                    for (int i = 0; i < GRAFT_OPTION_SLOTS.length; i++) {
                        if (GRAFT_OPTION_SLOTS[i] == raw) {
                            graft(player, id, Graft.values()[i]);
                            later(() -> openGraftOne(player, id));
                        }
                    }
                }
            }
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder holder) || !holder.kind().isTable()) return;
        for (int raw : event.getRawSlots()) {
            if (raw < top.getSize() && (holder.kind() != MenuHolder.Kind.PRESS || !INPUTS.contains(raw))) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getWhoClicked() instanceof Player p) Bukkit.getScheduler().runTask(plugin, () -> refreshPreview(p, top));
    }

    /** Inputs always go back to the player (or at their feet) when the Press tab closes for any reason. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder holder) || holder.kind() != MenuHolder.Kind.PRESS) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        for (int slot : INPUTS) {
            ItemStack i = top.getItem(slot);
            if (i == null) continue;
            top.setItem(slot, null);
            giveOrDrop(player, i);
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private void later(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    private static void fill(Inventory inv) {
        ItemStack pane = Menus.item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    private static void tabs(Inventory inv, boolean pressActive) {
        inv.setItem(TAB_PRESS, Menus.item(Material.FLETCHING_TABLE,
                Menus.text("Press", pressActive ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                Menus.line("Seed + ingredients → a blessing", NamedTextColor.GRAY)));
        inv.setItem(TAB_GRAFT, Menus.item(Material.SHEARS,
                Menus.text("Graft and prune", pressActive ? NamedTextColor.GRAY : NamedTextColor.LIGHT_PURPLE),
                Menus.line("Tune a blessing with Pollen and a rare material", NamedTextColor.GRAY)));
    }

    private ItemStack pollenItem(Clover clover) {
        return Menus.item(Material.SUNFLOWER, Menus.text("Pollen: " + clover.getPollen(), NamedTextColor.GOLD),
                Menus.line("Earned from charge above the max.", NamedTextColor.GRAY),
                Menus.line("Each graft costs " + pollenCost() + ".", NamedTextColor.GRAY));
    }

    private int pollenCost() {
        return plugin.getConfig().getInt("graft.pollen-cost", 100);
    }

    private int materialCount() {
        return plugin.getConfig().getInt("graft.material-count", 2);
    }

    private static int countIn(Player player, Material mat) {
        int n = 0;
        for (ItemStack i : player.getInventory().getStorageContents()) {
            if (i != null && i.isSimilar(new ItemStack(mat))) n += i.getAmount();
        }
        return n;
    }

    private static void giveOrDrop(Player player, ItemStack item) {
        for (ItemStack left : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    private static String describe(Graft g) {
        return switch (g) {
            case QUICK -> "-25% cooldown, -15% strength";
            case DEEP -> "+25% strength, +35% cooldown";
            case HUNTER -> "+15% strength after you land a hit (3s)";
            case VENGEFUL -> "+15% strength after you take damage (3s)";
            case WARD -> "A party member within 6 blocks gets half of its healing";
        };
    }
}
