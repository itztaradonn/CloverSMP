package net.cloversmp.cloverblessing.blessing.handler;

import net.cloversmp.cloverblessing.ui.Effects;
import net.cloversmp.cloverblessing.model.BlessingCategory;
import net.cloversmp.cloverblessing.model.BlessingCore;
import net.cloversmp.cloverblessing.model.BlessingTag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Arrow impact makes a 2-block vine zone for 3 s (Slowness II); one zone at a time per copy. */
public class TangleSeedHandler implements BlessingHandler {

    private static final long PULSE_TICKS = 10;
    private static final int ZONE_PULSES = 6; // 3 s

    private record Zone(UUID owner, BukkitTask task) {}

    /** blessing instance → its live zone (an Echo copy has its own, weaker zone). */
    private final Map<UUID, Zone> zones = new ConcurrentHashMap<>();

    @Override
    public BlessingCore createCore() {
        return new BlessingCore("tangle_seed", "Tangle Seed", BlessingCategory.CROWD_CONTROL, Set.of(BlessingTag.CONTROL),
                "Arrow impact grows a 2-block vine zone for 3s that slows (Slowness II). One zone at a time.",
                "The zone is visible and avoidable; stepping out clears the slow fast.",
                0, true, false, List.of("vine", "web"));
    }

    @Override
    public boolean onProjectileLand(BlessingContext ctx, ProjectileHitEvent event) {
        Location center = event.getHitEntity() != null ? event.getHitEntity().getLocation()
                : event.getEntity().getLocation();
        double strength = ctx.strength(BlessingTag.CONTROL);
        double radius = 2.0 * Math.max(0.5, Math.min(1.0, strength));
        int amplifier = strength >= 0.75 ? 1 : 0;
        Player owner = ctx.player();

        remove(ctx.instanceId());
        Effects.sound(center, "blessing.tangle_seed", 1f, 1f);
        Effects.particles(center, Particle.COMPOSTER, 15, 0.5);
        int[] pulses = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(ctx.engine().plugin(), () -> {
            if (pulses[0]++ >= ZONE_PULSES) {
                remove(ctx.instanceId());
                return;
            }
            center.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, center, 25, radius / 2, 0.2, radius / 2, 0);
            for (LivingEntity e : center.getNearbyLivingEntities(radius)) {
                if (e.equals(owner) || ctx.engine().parties().sameParty(owner.getUniqueId(), e.getUniqueId())) continue;
                e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 15, amplifier, false, true, true));
            }
        }, 0L, PULSE_TICKS);
        zones.put(ctx.instanceId(), new Zone(owner.getUniqueId(), task));
        return true;
    }

    private void remove(UUID instance) {
        Zone z = zones.remove(instance);
        if (z != null) z.task().cancel();
    }

    @Override
    public void clear(UUID player) {
        zones.values().removeIf(z -> {
            if (!z.owner().equals(player)) return false;
            z.task().cancel();
            return true;
        });
    }
}
