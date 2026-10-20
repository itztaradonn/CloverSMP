package net.cloversmp.cloverblessing.combat;

import net.cloversmp.cloverblessing.CloverPlugin;
import org.bukkit.entity.Player;
import net.cloversmp.cloverblessing.party.PartyManager;

import java.util.List;

/**
 * Implements anti-abuse rules to prevent charge farming through
 * repeat kills, fresh-spawn kills, alt accounts, and low-contribution assists.
 *
 * <p>Each check either returns a multiplier (0.0–1.0) or a boolean gate.
 * The {@link #calculateFinalChargeMultiplier} method combines all checks into
 * a single factor applied to the base charge reward.</p>
 */
public class AntiAbuseEngine {

    private final CloverPlugin plugin;
    private final CombatTracker combatTracker;
    private final PartyManager parties;

    public AntiAbuseEngine(CloverPlugin plugin, CombatTracker combatTracker, PartyManager parties) {
        this.plugin = plugin;
        this.combatTracker = combatTracker;
        this.parties = parties;
    }

    // -------------------------------------------------------
    // Repeat-victim diminishing returns
    // -------------------------------------------------------

    /**
     * Returns a multiplier (0.0–1.0) based on how many times the killer has
     * killed the same victim within the rolling window.
     *
     * <p>Default schedule: 1st kill = 1.0, 2nd = 0.5, 3rd = 0.25, 4th+ = 0.0.
     * Configurable via {@code anti-abuse.repeat-victim.multipliers}.</p>
     */
    public double getRepeatVictimMultiplier(Player killer, Player victim) {
        int killCount = combatTracker.getRepeatKillCount(killer, victim);
        List<Double> multipliers = plugin.getConfig().getDoubleList("anti-abuse.repeat-victim.multipliers");

        if (multipliers.isEmpty()) {
            // Fallback defaults
            multipliers = List.of(1.0, 0.5, 0.25, 0.0);
        }

        // killCount is the number of kills *before* the current one is recorded,
        // so the first kill index is 0
        if (killCount >= multipliers.size()) {
            // Beyond the list — use the last entry (typically 0.0)
            return multipliers.getLast();
        }
        return multipliers.get(killCount);
    }

    // -------------------------------------------------------
    // Fresh-spawn protection
    // -------------------------------------------------------

    /**
     * Returns {@code true} if the victim respawned less than the configured
     * fresh-spawn seconds ago (default 90s).
     */
    public boolean isFreshSpawn(Player victim) {
        long freshSpawnMs = plugin.getConfig().getInt("anti-abuse.fresh-spawn-seconds", 90) * 1000L;

        long lastRespawn = combatTracker.getLastRespawnTime(victim);
        if (lastRespawn <= 0) {
            return false; // Never recorded a respawn — not a fresh spawn
        }
        return System.currentTimeMillis() - lastRespawn < freshSpawnMs;
    }

    // -------------------------------------------------------
    // Damage-share minimum
    // -------------------------------------------------------

    /**
     * Returns 0.0 if the killer dealt less than the configured minimum damage share
     * (default 25%), otherwise returns their actual damage share (0.0–1.0).
     */
    public double getDamageShareMultiplier(Player killer, Player victim) {
        double minShare = plugin.getConfig().getDouble("charge-sources.assist-min-damage-share", 0.25);
        double share = combatTracker.getDamageShare(killer, victim);

        return share < minShare ? 0.0 : share;
    }

    // -------------------------------------------------------
    // Alt / teammate detection
    // -------------------------------------------------------

    /**
     * Returns {@code true} if the victim is likely an alt or a teammate:
     * <ul>
     *   <li>Victim has less than the configured minimum playtime (default 2 hours)</li>
     *   <li>Killer and victim are in the same {@code /clover party}</li>
     * </ul>
     */
    public boolean isAltOrTeammate(Player killer, Player victim) {
        // Check minimum playtime
        int minPlaytimeHours = plugin.getConfig().getInt("anti-abuse.min-victim-playtime-hours", 2);
        long minPlaytimeTicks = minPlaytimeHours * 3600L * 20L; // hours → ticks

        if (victim.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) < minPlaytimeTicks) {
            return true;
        }

        return plugin.getConfig().getBoolean("anti-abuse.party-kills-zero", true)
                && parties.sameParty(killer.getUniqueId(), victim.getUniqueId());
    }

    // -------------------------------------------------------
    // Never engaged in combat
    // -------------------------------------------------------

    /**
     * Returns {@code true} if the victim has never dealt or taken damage since
     * their last respawn (indicated by their last combat time being 0 or before
     * their last respawn).
     */
    public boolean neverDealtOrTookDamage(Player victim) {
        var clover = plugin.getDataManager().getClover(victim.getUniqueId());
        if (clover == null) {
            return true;
        }

        long lastCombat = clover.getLastCombatMs();
        long lastRespawn = combatTracker.getLastRespawnTime(victim);

        // If last combat time is 0 or occurred before last respawn, they never engaged
        return lastCombat <= 0 || lastCombat <= lastRespawn;
    }

    // -------------------------------------------------------
    // Combined multiplier
    // -------------------------------------------------------

    /**
     * Combines all anti-abuse checks into a single charge multiplier.
     *
     * <p>Returns {@code 0.0} if any hard check fails (fresh spawn, alt/teammate,
     * never fought). Otherwise returns the product of repeat-victim and
     * damage-share multipliers.</p>
     *
     * @param killer the attacking player
     * @param victim the killed player
     * @return combined multiplier in range [0.0, 1.0]
     */
    public double calculateFinalChargeMultiplier(Player killer, Player victim) {
        // Hard gates — any one blocks all charge
        if (isFreshSpawn(victim)) {
            return 0.0;
        }
        if (isAltOrTeammate(killer, victim)) {
            return 0.0;
        }
        if (neverDealtOrTookDamage(victim)) {
            return 0.0;
        }

        // Soft multipliers
        double repeatMult = getRepeatVictimMultiplier(killer, victim);
        double damageMult = getDamageShareMultiplier(killer, victim);

        return repeatMult * damageMult;
    }
}
