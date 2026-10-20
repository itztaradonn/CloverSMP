package net.cloversmp.cloverblessing.listener;

import net.cloversmp.cloverblessing.CloverPlugin;
import net.cloversmp.cloverblessing.charge.ChargeManager;
import net.cloversmp.cloverblessing.combat.AntiAbuseEngine;
import net.cloversmp.cloverblessing.combat.CombatTracker;
import net.cloversmp.cloverblessing.model.ChargeSource;
import net.cloversmp.cloverblessing.model.Clover;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Listens to death events and feeds the {@link ChargeManager}.
 *
 * <ul>
 *   <li><b>Hostile mob deaths</b> — grants charge to the killer with daily soft-cap and boss limits.</li>
 *   <li><b>Player deaths</b> — applies death penalty, grants PvP kill charge with anti-abuse
 *       checks, and awards assist charge to qualifying contributors.</li>
 * </ul>
 */
public class ChargeListener implements Listener {

    private static final Set<EntityType> BOSS_TYPES = Set.of(
            EntityType.ENDER_DRAGON,
            EntityType.WITHER,
            EntityType.WARDEN
    );

    private final CloverPlugin plugin;
    private final ChargeManager chargeManager;
    private final CombatTracker combatTracker;
    private final AntiAbuseEngine antiAbuseEngine;

    public ChargeListener(CloverPlugin plugin, ChargeManager chargeManager,
                          CombatTracker combatTracker, AntiAbuseEngine antiAbuseEngine) {
        this.plugin = plugin;
        this.chargeManager = chargeManager;
        this.combatTracker = combatTracker;
        this.antiAbuseEngine = antiAbuseEngine;
    }

    // -------------------------------------------------------
    // Hostile mob / boss deaths
    // -------------------------------------------------------

    /**
     * Handles charge rewards when a hostile mob or boss is killed by a player.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        // Ignore player deaths — handled separately
        if (event.getEntity() instanceof Player) return;

        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        // Only grant charge for hostile mobs
        if (!(entity instanceof Monster || entity instanceof Slime
                || entity instanceof Phantom || entity instanceof Ghast
                || entity instanceof EnderDragon || entity instanceof Wither)) {
            return;
        }

        // Determine charge amount and source
        if (isBoss(entity)) {
            handleBossKill(killer, entity);
        } else if (isElite(entity)) {
            handleMobCharge(killer, plugin.getConfig().getInt("charge-sources.elite-mob", 10), ChargeSource.ELITE_MOB);
        } else {
            handleMobCharge(killer, plugin.getConfig().getInt("charge-sources.hostile-mob", 2), ChargeSource.HOSTILE_MOB);
        }
    }

    /**
     * Handles boss kill charge with daily per-type limit.
     */
    private void handleBossKill(Player killer, LivingEntity boss) {
        String bossType = boss.getType().name();
        int dailyLimit = plugin.getConfig().getInt("boss.daily-limit-per-type", 1);

        Clover clover = plugin.getDataManager().getClover(killer.getUniqueId());
        if (clover == null) return;

        resetDailyCountersIfNeeded(clover);

        if (clover.getDailyBossKills(bossType) >= dailyLimit) {
            return; // Already hit daily limit for this boss type
        }

        clover.addDailyBossKill(bossType);
        int baseCharge = plugin.getConfig().getInt("charge-sources.boss", 60);
        chargeManager.addCharge(killer, baseCharge, ChargeSource.BOSS);
    }

    /**
     * Handles normal/elite mob charge with daily soft cap.
     */
    private void handleMobCharge(Player killer, int baseCharge, ChargeSource source) {
        Clover clover = plugin.getDataManager().getClover(killer.getUniqueId());
        if (clover == null) return;

        resetDailyCountersIfNeeded(clover);

        int dailyCap = plugin.getConfig().getInt("charge-sources.daily-mob-cap-charge", 200);
        double capMultiplier = plugin.getConfig().getDouble("charge-sources.daily-mob-cap-multiplier", 0.25);

        double multiplier = 1.0;
        if (clover.getDailyMobCharge() >= dailyCap) {
            multiplier = capMultiplier;
        }

        int finalCharge = (int) Math.round(baseCharge * multiplier);
        if (finalCharge > 0) {
            chargeManager.addCharge(killer, finalCharge, source);
            clover.addDailyMobCharge(finalCharge);
        }
    }

    // -------------------------------------------------------
    // Player deaths (PvP)
    // -------------------------------------------------------

    /**
     * Handles charge effects when a player dies:
     * <ol>
     *   <li>Applies death penalty to the dead player</li>
     *   <li>Grants kill charge to the killer (if player) with anti-abuse checks</li>
     *   <li>Grants assist charge to other players who dealt ≥25% damage</li>
     * </ol>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Read everything that depends on the victim's leaves before the penalty wilts them
        double leafMult = getVictimLeafMultiplier(victim);
        double abuseMult = killer == null || killer.equals(victim) ? 0.0
                : antiAbuseEngine.calculateFinalChargeMultiplier(killer, victim);
        Map<UUID, Double> damageShares = combatTracker.getAllDamageShares(victim);

        // 1. Apply death penalty to the dead player
        chargeManager.applyDeathPenalty(victim);

        // 2. Check for player killer
        if (killer == null || killer.equals(victim)) return;

        // Record the kill after the repeat-victim check above, so this kill counts from the next one
        combatTracker.recordKill(killer, victim);

        // Grant kill charge to the killer
        if (abuseMult > 0.0 && leafMult > 0.0) {
            int baseKillCharge = plugin.getConfig().getInt("charge-sources.player-kill", 40);
            double combinedMult = leafMult * abuseMult;
            chargeManager.addCharge(killer, baseKillCharge, ChargeSource.PLAYER_KILL, combinedMult);
        }

        // 3. Grant assist charge to other players who dealt sufficient damage
        double minShare = plugin.getConfig().getDouble("charge-sources.assist-min-damage-share", 0.25);

        for (Map.Entry<UUID, Double> entry : damageShares.entrySet()) {
            UUID assistUuid = entry.getKey();
            double share = entry.getValue();

            // Skip the actual killer (already rewarded) and the victim
            if (assistUuid.equals(killer.getUniqueId()) || assistUuid.equals(victim.getUniqueId())) {
                continue;
            }

            // Minimum damage share gate
            if (share < minShare) {
                continue;
            }

            Player assister = plugin.getServer().getPlayer(assistUuid);
            if (assister == null || !assister.isOnline()) continue;

            // Apply anti-abuse to the assister as well
            double assistAbuse = antiAbuseEngine.calculateFinalChargeMultiplier(assister, victim);
            if (assistAbuse <= 0.0) continue;

            int baseKillCharge = plugin.getConfig().getInt("charge-sources.player-kill", 40);
            double assistMult = share * leafMult * assistAbuse;
            chargeManager.addCharge(assister, baseKillCharge, ChargeSource.PLAYER_KILL, assistMult);
        }
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    /**
     * Returns the PvP charge multiplier based on the victim's active leaf count.
     */
    private double getVictimLeafMultiplier(Player victim) {
        int leafCount = chargeManager.getLeafCount(victim);
        return plugin.getConfig().getDouble("charge-sources.pvp-leaf-multiplier." + leafCount, 1.0);
    }

    /**
     * Returns {@code true} if the entity is a boss (Ender Dragon, Wither, or Warden).
     */
    private boolean isBoss(LivingEntity entity) {
        return BOSS_TYPES.contains(entity.getType());
    }

    /**
     * Returns {@code true} if the entity's type is listed in {@code charge-sources.elite-mobs}.
     */
    private boolean isElite(LivingEntity entity) {
        return plugin.getConfig().getStringList("charge-sources.elite-mobs").contains(entity.getType().name());
    }

    /**
     * Returns the current day number for daily counter resets (UTC-based).
     */
    private long getCurrentDay() {
        return System.currentTimeMillis() / 86_400_000L;
    }

    /**
     * Resets daily counters (mob charge, boss kills) if the day has changed.
     */
    private void resetDailyCountersIfNeeded(Clover clover) {
        long today = getCurrentDay();
        if (clover.getDailyMobChargeResetDay() < today) {
            clover.resetDailyMobCharge(today);
            clover.resetDailyBossKills();
        }
    }
}
