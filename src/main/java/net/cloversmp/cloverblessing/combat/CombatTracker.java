package net.cloversmp.cloverblessing.combat;

import net.cloversmp.cloverblessing.CloverPlugin;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks damage dealt between players and kill events within rolling windows.
 * Used by the anti-abuse engine and charge system to calculate damage shares,
 * repeat-kill counts, and respawn timing.
 */
public class CombatTracker {

    private final CloverPlugin plugin;

    /**
     * Damage events keyed by victim UUID → list of DamageEntry records.
     */
    private final Map<UUID, List<DamageEntry>> damageLog = new ConcurrentHashMap<>();

    /**
     * Kill events keyed by "killer:victim" compound key → list of kill timestamps (millis).
     */
    private final Map<String, List<Long>> killLog = new ConcurrentHashMap<>();

    /**
     * Last respawn time (wall-clock millis) per player UUID.
     */
    private final Map<UUID, Long> respawnTimes = new ConcurrentHashMap<>();

    public CombatTracker(CloverPlugin plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    // -------------------------------------------------------
    // Damage tracking
    // -------------------------------------------------------

    /**
     * Records a damage event where {@code attacker} hit {@code victim} for {@code damage}.
     */
    public void recordDamage(Player attacker, Player victim, double damage) {
        damageLog.computeIfAbsent(victim.getUniqueId(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new DamageEntry(attacker.getUniqueId(), damage, System.currentTimeMillis()));
    }

    /**
     * Returns the attacker's share (0.0–1.0) of total damage dealt to the victim
     * within the assist window (default 20 seconds, configurable).
     */
    public double getDamageShare(Player attacker, Player victim) {
        int windowSeconds = plugin.getConfig().getInt("charge-sources.assist-window-seconds", 20);
        long cutoff = System.currentTimeMillis() - (windowSeconds * 1000L);

        List<DamageEntry> entries = damageLog.get(victim.getUniqueId());
        if (entries == null || entries.isEmpty()) {
            return 0.0;
        }

        double totalDamage = 0.0;
        double attackerDamage = 0.0;

        synchronized (entries) {
            for (DamageEntry entry : entries) {
                if (entry.timestamp() >= cutoff) {
                    totalDamage += entry.damage();
                    if (entry.attackerUuid().equals(attacker.getUniqueId())) {
                        attackerDamage += entry.damage();
                    }
                }
            }
        }

        return totalDamage > 0.0 ? attackerDamage / totalDamage : 0.0;
    }

    /**
     * Returns all players who dealt damage to the given victim within the assist window,
     * mapped to their damage share (0.0–1.0).
     */
    public Map<UUID, Double> getAllDamageShares(Player victim) {
        int windowSeconds = plugin.getConfig().getInt("charge-sources.assist-window-seconds", 20);
        long cutoff = System.currentTimeMillis() - (windowSeconds * 1000L);

        List<DamageEntry> entries = damageLog.get(victim.getUniqueId());
        if (entries == null || entries.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Double> totals = new HashMap<>();
        double totalDamage = 0.0;

        synchronized (entries) {
            for (DamageEntry entry : entries) {
                if (entry.timestamp() >= cutoff) {
                    totalDamage += entry.damage();
                    totals.merge(entry.attackerUuid(), entry.damage(), Double::sum);
                }
            }
        }

        if (totalDamage <= 0.0) {
            return Map.of();
        }

        Map<UUID, Double> shares = new HashMap<>();
        double finalTotal = totalDamage;
        totals.forEach((uuid, dmg) -> shares.put(uuid, dmg / finalTotal));
        return shares;
    }

    // -------------------------------------------------------
    // Kill tracking
    // -------------------------------------------------------

    /**
     * Records that {@code killer} killed {@code victim} at the current time.
     */
    public void recordKill(Player killer, Player victim) {
        String key = compoundKey(killer.getUniqueId(), victim.getUniqueId());
        killLog.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(System.currentTimeMillis());
    }

    /**
     * Returns how many times the killer has killed the victim within the rolling window
     * (default 60 minutes, configurable via {@code anti-abuse.repeat-victim.window-minutes}).
     */
    public int getRepeatKillCount(Player killer, Player victim) {
        int windowMinutes = plugin.getConfig().getInt("anti-abuse.repeat-victim.window-minutes", 60);
        long cutoff = System.currentTimeMillis() - (windowMinutes * 60_000L);

        String key = compoundKey(killer.getUniqueId(), victim.getUniqueId());
        List<Long> timestamps = killLog.get(key);
        if (timestamps == null) {
            return 0;
        }

        int count = 0;
        synchronized (timestamps) {
            for (long ts : timestamps) {
                if (ts >= cutoff) {
                    count++;
                }
            }
        }
        return count;
    }

    // -------------------------------------------------------
    // Respawn tracking
    // -------------------------------------------------------

    /**
     * Records the player's respawn time.
     */
    public void recordRespawn(Player player) {
        respawnTimes.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Returns when (wall-clock millis) the player last respawned, or {@code 0} if never recorded.
     */
    public long getLastRespawnTime(Player player) {
        return respawnTimes.getOrDefault(player.getUniqueId(), 0L);
    }

    // -------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------

    /**
     * Schedules a repeating task every 5 minutes to purge stale entries.
     */
    private void startCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::purgeStaleEntries,
                6000L, 6000L); // 5 minutes = 6000 ticks
    }

    /**
     * Removes damage and kill entries older than their respective windows.
     */
    private void purgeStaleEntries() {
        // Purge damage log (use assist window)
        int assistWindowSeconds = plugin.getConfig().getInt("charge-sources.assist-window-seconds", 20);
        long damageCutoff = System.currentTimeMillis() - (assistWindowSeconds * 1000L);

        damageLog.forEach((uuid, entries) -> {
            synchronized (entries) {
                entries.removeIf(e -> e.timestamp() < damageCutoff);
            }
        });
        damageLog.entrySet().removeIf(e -> {
            List<DamageEntry> list = e.getValue();
            synchronized (list) {
                return list.isEmpty();
            }
        });

        // Purge kill log (use repeat-victim window)
        int killWindowMinutes = plugin.getConfig().getInt("anti-abuse.repeat-victim.window-minutes", 60);
        long killCutoff = System.currentTimeMillis() - (killWindowMinutes * 60_000L);

        killLog.forEach((key, timestamps) -> {
            synchronized (timestamps) {
                timestamps.removeIf(ts -> ts < killCutoff);
            }
        });
        killLog.entrySet().removeIf(e -> {
            List<Long> list = e.getValue();
            synchronized (list) {
                return list.isEmpty();
            }
        });
    }

    /**
     * Clears all tracked data for a player. Called on quit if desired.
     */
    public void clearPlayer(UUID uuid) {
        damageLog.remove(uuid);
        respawnTimes.remove(uuid);
        // Kill log entries involving this player are kept for the rolling window
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private String compoundKey(UUID killer, UUID victim) {
        return killer.toString() + ":" + victim.toString();
    }

    /**
     * Immutable record for a single damage event.
     */
    private record DamageEntry(UUID attackerUuid, double damage, long timestamp) {}
}
