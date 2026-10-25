package net.cloversmp.cloverblessing.charge;

import org.bukkit.Particle;
import org.bukkit.Location;
import org.bukkit.Color;
import org.bukkit.Bukkit;
import net.cloversmp.cloverblessing.ui.Effects;
import net.cloversmp.cloverblessing.CloverPlugin;
import net.cloversmp.cloverblessing.model.ChargeSource;
import net.cloversmp.cloverblessing.model.Clover;
import net.cloversmp.cloverblessing.model.LeafSlot;
import net.cloversmp.cloverblessing.storage.DataManager;
import org.bukkit.entity.Player;

/**
 * The Clover Charge meter: gains (with the leaf-4 gate and Pollen overflow), death penalties,
 * leaf counts and leaf-2 permanence. All tunables come from {@code config.yml}.
 */
public class ChargeManager {

    /** Charge multipliers contributed by equipped blessings (Wager Root). */
    public interface Modifiers {
        double gain(Player player);
        double deathPenalty(Player player);
    }

    private final CloverPlugin plugin;
    private final DataManager dataManager;
    private Modifiers modifiers = new Modifiers() {
        public double gain(Player p) { return 1.0; }
        public double deathPenalty(Player p) { return 1.0; }
    };

    public ChargeManager(CloverPlugin plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    public void setModifiers(Modifiers modifiers) {
        this.modifiers = modifiers;
    }

    // --- Config ---

    public int getLeaf2Threshold() { return plugin.getConfig().getInt("charge.thresholds.leaf2", 100); }
    public int getLeaf3Threshold() { return plugin.getConfig().getInt("charge.thresholds.leaf3", 350); }
    public int getLeaf4Threshold() { return plugin.getConfig().getInt("charge.thresholds.leaf4", 800); }
    public int getMaxCharge() { return plugin.getConfig().getInt("charge.max", 1000); }

    // --- Gains and losses ---

    /**
     * Adds charge from a kill. {@code multiplier} carries anti-abuse, leaf and damage-share factors;
     * blessing modifiers (Wager Root) are applied here.
     */
    public void addCharge(Player player, int baseAmount, ChargeSource source, double multiplier) {
        Clover clover = dataManager.getClover(player.getUniqueId());
        if (clover == null || !plugin.progressionAllowed()) return;

        int amount = (int) Math.round(baseAmount * multiplier * modifiers.gain(player));
        boolean mob = source == ChargeSource.HOSTILE_MOB || source == ChargeSource.ELITE_MOB;
        boolean gate = plugin.getConfig().getBoolean("charge.leaf4-requires-pvp-or-boss", true);
        int rate = plugin.getConfig().getInt("pollen.conversion-rate", 1);

        int leavesBefore = getLeafCount(clover);
        ChargeMath.Result r = ChargeMath.gain(clover.getCharge(), amount, mob, gate,
                getLeaf4Threshold(), getMaxCharge(), rate);
        clover.setCharge(r.charge());
        clover.addPollen(r.pollen());
        checkLeaf2Permanence(clover);

        if (amount > 0) {
            Effects.sound(player.getLocation(), "charge.gain", 0.6f, 1f);
            Effects.particles(Effects.body(player, 1.1), Particle.ELECTRIC_SPARK, 4, 0.2);
            if (modifiers.gain(player) > 1.0) {
                Effects.sound(player.getLocation(), "blessing.wager_root_win", 1f, 1f);
                Effects.dust(Effects.body(player, 0.6), Color.fromRGB(0xF7C948), 12, 0.4);
            }
        }
        leafEffects(player, leavesBefore, getLeafCount(clover));
    }

    /** A leaf opening or wilting is a moment everyone nearby should notice. */
    private void leafEffects(Player player, int before, int after) {
        if (player == null || before == after) return;
        Location chest = Effects.body(player, 0.6);
        if (after > before) {
            Effects.sound(chest, "clover.leaf_open", 1f, 1f);
            Effects.particles(chest, Particle.CHERRY_LEAVES, 30, 0.8);
            Effects.particles(chest, Particle.HAPPY_VILLAGER, 15, 0.6);
        } else {
            Effects.sound(chest, "clover.leaf_wilt", 1f, 1f);
            Effects.dust(chest, Color.fromRGB(0x7A5230), 20, 0.6);
        }
    }

    public void addCharge(Player player, int baseAmount, ChargeSource source) {
        addCharge(player, baseAmount, source, 1.0);
    }

    /** Death penalty: a percentage of the meter, never below the floor once leaf 2 is permanent. */
    public void applyDeathPenalty(Player player) {
        Clover clover = dataManager.getClover(player.getUniqueId());
        if (clover == null || !plugin.progressionAllowed()) return;
        double percent = plugin.getConfig().getDouble("charge.death-penalty-percent", 35);
        int leavesBefore = getLeafCount(clover);
        double penaltyMultiplier = modifiers.deathPenalty(player);
        clover.setCharge(ChargeMath.afterDeath(clover.getCharge(), percent, penaltyMultiplier, floor(clover)));
        if (penaltyMultiplier > 1.0) {
            Effects.sound(player.getLocation(), "blessing.wager_root_loss", 1f, 1f);
            Effects.particles(Effects.body(player, 0.6), Particle.ASH, 25, 0.5);
        }
        leafEffects(player, leavesBefore, getLeafCount(clover));
    }

    /** Removes a percentage of current charge (breaking a curse), respecting the floor. */
    public void removePercent(Clover clover, double percent) {
        clover.setCharge(ChargeMath.afterDeath(clover.getCharge(), percent, 1.0, floor(clover)));
    }

    /** Admin: sets charge directly, clamped to 0..max. */
    public void setCharge(Clover clover, int amount) {
        int leavesBefore = getLeafCount(clover);
        clover.setCharge(Math.min(Math.max(0, amount), getMaxCharge()));
        checkLeaf2Permanence(clover);
        leafEffects(Bukkit.getPlayer(clover.getPlayerUuid()), leavesBefore, getLeafCount(clover));
    }

    private int floor(Clover clover) {
        return clover.isLeaf2Permanent() ? plugin.getConfig().getInt("charge.floor", 100) : 0;
    }

    private void checkLeaf2Permanence(Clover clover) {
        if (!clover.isLeaf2Permanent() && clover.getCharge() >= getLeaf2Threshold()) {
            clover.setLeaf2Permanent(true);
        }
    }

    // --- Leaves ---

    public int getLeafCount(Clover clover) {
        return clover.getActiveLeafCount(getLeaf2Threshold(), getLeaf3Threshold(), getLeaf4Threshold());
    }

    public int getLeafCount(Player player) {
        Clover clover = dataManager.getClover(player.getUniqueId());
        return clover == null ? 1 : getLeafCount(clover);
    }

    public boolean isLeafActive(Clover clover, LeafSlot slot) {
        return slot.getRequiredLeafCount() <= getLeafCount(clover);
    }

    /** Charge needed for a slot to open. */
    public int thresholdFor(LeafSlot slot) {
        return switch (slot.getRequiredLeafCount()) {
            case 1 -> 0;
            case 2 -> getLeaf2Threshold();
            case 3 -> getLeaf3Threshold();
            default -> getLeaf4Threshold();
        };
    }
}
