package net.cloversmp.cloverblessing.blessing.handler;

import net.cloversmp.cloverblessing.model.BlessingCategory;
import net.cloversmp.cloverblessing.model.BlessingCore;
import net.cloversmp.cloverblessing.model.BlessingTag;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** A critical hit stops the target sprinting for 1 s; 6 s cooldown. A raised shield blocks it. */
public class HobbleHandler implements BlessingHandler, Listener {

    /** hobbled player → millis when they may sprint again. */
    private final Map<UUID, Long> hobbled = new ConcurrentHashMap<>();

    @Override
    public BlessingCore createCore() {
        return new BlessingCore("hobble", "Hobble", BlessingCategory.CROWD_CONTROL, Set.of(BlessingTag.CONTROL),
                "A critical hit stops the target sprinting for 1s. 6s cooldown.",
                "Needs crit timing and melee range; shields block it.",
                6 * 20, true, false, List.of("chain", "cactus"));
    }

    @Override
    public boolean onMeleeHit(BlessingContext ctx, EntityDamageByEntityEvent event, LivingEntity target) {
        if (!event.isCritical() || !(target instanceof Player victim) || victim.isBlocking()) return false;
        long durationMs = Math.round(1_000 * Math.max(0.5, ctx.strength(BlessingTag.CONTROL)));
        hobbled.put(victim.getUniqueId(), System.currentTimeMillis() + durationMs);
        victim.setSprinting(false);
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent event) {
        if (!event.isSprinting()) return;
        Long until = hobbled.get(event.getPlayer().getUniqueId());
        if (until == null) return;
        if (until > System.currentTimeMillis()) event.setCancelled(true);
        else hobbled.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hobbled.remove(event.getPlayer().getUniqueId());
    }
}
