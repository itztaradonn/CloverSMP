package net.cloversmp.cloverblessing.listener;

import net.cloversmp.cloverblessing.CloverPlugin;
import net.cloversmp.cloverblessing.combat.CombatTracker;
import net.cloversmp.cloverblessing.model.Clover;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Listens to damage and respawn events and feeds the {@link CombatTracker}.
 *
 * <ul>
 *   <li>Records all player-vs-player damage (resolving projectile owners).</li>
 *   <li>Updates the player's last combat time on their {@link Clover} when they
 *       deal or receive damage.</li>
 *   <li>Records respawn events for fresh-spawn protection tracking.</li>
 * </ul>
 */
public class CombatListener implements Listener {

    private final CombatTracker combatTracker;

    public CombatListener(CombatTracker combatTracker) {
        this.combatTracker = combatTracker;
    }

    // -------------------------------------------------------
    // Player-vs-Player damage
    // -------------------------------------------------------

    /**
     * Records PvP damage events in the combat tracker and updates both players'
     * last combat times.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Victim must be a player
        if (!(event.getEntity() instanceof Player victim)) return;

        // Resolve the attacker (may be a projectile's owner)
        Player attacker = resolvePlayerAttacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;

        double damage = event.getFinalDamage();
        if (damage <= 0.0) return;

        // Record in combat tracker
        combatTracker.recordDamage(attacker, victim, damage);

        // Update combat timer on both players' Clovers
        long now = System.currentTimeMillis();
        updateCombatTime(attacker, now);
        updateCombatTime(victim, now);
    }

    // -------------------------------------------------------
    // Respawn tracking
    // -------------------------------------------------------

    /**
     * Records the player's respawn time for fresh-spawn protection.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        combatTracker.recordRespawn(event.getPlayer());
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    /**
     * Resolves the actual player behind a damage source.
     * Handles direct player attacks and projectiles (arrows, tridents, etc.).
     *
     * @param damager the entity that caused the damage
     * @return the player responsible, or {@code null} if not a player source
     */
    private Player resolvePlayerAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player shooter) {
                return shooter;
            }
        }
        // TNT and other indirect sources are intentionally excluded
        return null;
    }

    /**
     * Updates the last combat time on a player's Clover data.
     */
    private void updateCombatTime(Player player, long nowMs) {
        Clover clover = CloverPlugin.getInstance().getDataManager().getClover(player.getUniqueId());
        if (clover != null) {
            clover.setLastCombatMs(nowMs);
        }
    }
}
