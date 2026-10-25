package net.cloversmp.cloverblessing.blessing;

import net.cloversmp.cloverblessing.ui.Effects;
import net.cloversmp.cloverblessing.CloverPlugin;
import net.cloversmp.cloverblessing.charge.ChargeManager;
import net.cloversmp.cloverblessing.model.*;
import net.cloversmp.cloverblessing.storage.DataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Loadout operations: giving, equipping and unequipping blessings and curses, with the loadout rules
 * (out of combat, open leaf, slot kind, one Echo Leaf, curse break cost, 30 s lock on new blessings).
 */
public class BlessingManager {

    private final CloverPlugin plugin;
    private final DataManager dataManager;
    private final BlessingRegistry registry;
    private final ChargeManager chargeManager;
    private final CooldownManager cooldownManager;

    public BlessingManager(CloverPlugin plugin, DataManager dataManager, BlessingRegistry registry,
                           ChargeManager chargeManager, CooldownManager cooldownManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.registry = registry;
        this.chargeManager = chargeManager;
        this.cooldownManager = cooldownManager;
    }

    /**
     * Creates a new blessing record in the player's herbarium (admin give, later the Pressing Table).
     *
     * @return the new blessing, or null if the core is unknown or a curse was given a graft
     */
    public Blessing give(Clover clover, String coreId, Graft graft) {
        BlessingCore core = registry.getCore(coreId);
        if (core == null || (core.isCurse() && graft != null)) return null;
        Blessing blessing = new Blessing(UUID.randomUUID(), coreId, graft, clover.getPlayerUuid());
        clover.addToHerbarium(blessing);
        clover.discoverRecipe(coreId);
        plugin.getLogger().info("Blessing created: " + blessing.getId() + " " + coreId
                + (graft != null ? "+" + graft : "") + " for " + clover.getPlayerUuid());
        return blessing;
    }

    /** Equips a herbarium blessing into a slot. Sends the player a reason on failure. */
    public boolean equip(Player player, UUID blessingId, LeafSlot slot) {
        Clover clover = dataManager.getClover(player.getUniqueId());
        if (clover == null) return false;
        Blessing blessing = clover.getHerbarium().get(blessingId);
        BlessingCore core = blessing == null ? null : registry.getCore(blessing.getCoreId());
        if (core == null) return deny(player, "That blessing is not in your herbarium.");
        if (core.isCurse() != slot.isCurse()) {
            return deny(player, core.isCurse() ? "Curses go in leaf 5." : "Leaf 5 only holds curses.");
        }
        if (!canChangeLoadout(player, clover, slot, true)) return false;
        if (core.getId().equals(BlessingEngine.ECHO_LEAF) && hasEchoElsewhere(clover, slot)) {
            return deny(player, "Only one Echo Leaf can be equipped.");
        }
        Blessing current = clover.getEquippedBlessing(slot);
        if (current != null && slot.isCurse()) payCurseBreak(player, clover);

        clover.equipFromHerbarium(slot, blessingId);
        if (slot.isCurse()) {
            Effects.sound(player.getLocation(), "clover.pact_take", 1f, 1f);
            Effects.particles(Effects.body(player, 0.6), org.bukkit.Particle.SOUL, 20, 0.6);
            Effects.particles(Effects.body(player, 0.3), org.bukkit.Particle.SQUID_INK, 10, 0.4);
            Effects.dust(Effects.body(player, 0.5), org.bukkit.Color.fromRGB(0x5A, 0x0F, 0x14), 25, 0.5);
            Effects.particles(Effects.body(player, 1.0), org.bukkit.Particle.SOUL_FIRE_FLAME, 8, 0.2);
        }
        long lockMs = plugin.getConfig().getLong("loadout.swap-lock-seconds", 30) * 1000L;
        if (cooldownManager.getRemainingMs(clover, blessingId) < lockMs) {
            cooldownManager.startCooldown(clover, blessingId, lockMs);
        }
        player.sendMessage(Component.text("Equipped ", NamedTextColor.GREEN)
                .append(Component.text(core.getDisplayName(), NamedTextColor.GOLD))
                .append(Component.text(" to " + slot.label() + ". It wakes in "
                        + lockMs / 1000 + "s.", NamedTextColor.GRAY)));
        return true;
    }

    /** Moves a slot's blessing back to the herbarium. Removing a curse costs charge. */
    public boolean unequip(Player player, LeafSlot slot) {
        Clover clover = dataManager.getClover(player.getUniqueId());
        if (clover == null) return false;
        Blessing current = clover.getEquippedBlessing(slot);
        if (current == null) return false;
        if (!canChangeLoadout(player, clover, slot, false)) return false;
        if (slot.isCurse()) payCurseBreak(player, clover);

        clover.unequip(slot);
        BlessingCore core = registry.getCore(current.getCoreId());
        player.sendMessage(Component.text("Unequipped " + (core != null ? core.getDisplayName() : current.getCoreId())
                + " from " + slot.label() + ".", NamedTextColor.YELLOW));
        return true;
    }

    private boolean canChangeLoadout(Player player, Clover clover, LeafSlot slot, boolean needsOpenLeaf) {
        if (!isOutOfCombat(clover)) {
            long secs = (outOfCombatMs() - (System.currentTimeMillis() - clover.getLastCombatMs())) / 1000 + 1;
            return deny(player, "You can't change your clover in combat (" + secs + "s).");
        }
        if (needsOpenLeaf && !chargeManager.isLeafActive(clover, slot)) {
            return deny(player, slot.label() + " is not open yet (needs " + chargeManager.thresholdFor(slot) + " charge).");
        }
        return true;
    }

    private void payCurseBreak(Player player, Clover clover) {
        double percent = plugin.getConfig().getDouble("curse.break-cost-percent", 15);
        int before = clover.getCharge();
        chargeManager.removePercent(clover, percent);
        player.sendMessage(Component.text("Breaking the pact cost " + (before - clover.getCharge()) + " charge.",
                NamedTextColor.DARK_PURPLE));
        Effects.sound(player.getLocation(), "clover.pact_break", 1f, 1f);
        Effects.particles(Effects.body(player, 0.6), org.bukkit.Particle.LARGE_SMOKE, 15, 0.4);
        Effects.dust(Effects.body(player, 0.5), org.bukkit.Color.fromRGB(0x10, 0x10, 0x10), 20, 0.5);
        Effects.particles(Effects.body(player, 0.6), org.bukkit.Particle.CRIT, 12, 0.5);
    }

    private boolean hasEchoElsewhere(Clover clover, LeafSlot target) {
        for (var e : clover.getEquippedBlessings().entrySet()) {
            if (e.getKey() != target && e.getValue().getCoreId().equals(BlessingEngine.ECHO_LEAF)) return true;
        }
        return false;
    }

    private long outOfCombatMs() {
        return plugin.getConfig().getLong("loadout.out-of-combat-seconds", 30) * 1000L;
    }

    /** True once the player has gone the configured time without PvP damage dealt or taken. */
    public boolean isOutOfCombat(Clover clover) {
        return System.currentTimeMillis() - clover.getLastCombatMs() >= outOfCombatMs();
    }

    private static boolean deny(Player player, String reason) {
        player.sendMessage(Component.text(reason, NamedTextColor.RED));
        return false;
    }
}
