package net.cloversmp.cloverblessing.blessing.handler;

import net.cloversmp.cloverblessing.model.BlessingCategory;
import net.cloversmp.cloverblessing.model.BlessingCore;
import net.cloversmp.cloverblessing.model.BlessingTag;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Hold sneak 1 s on the ground, release to leap about 6 blocks forward; 12 s cooldown. */
public class SpringleafHandler implements BlessingHandler {

    private static final long CHARGE_MS = 1_000;

    private record Charging(UUID player, long startMs) {}

    /** blessing instance → charge in progress. */
    private final Map<UUID, Charging> charging = new ConcurrentHashMap<>();

    @Override
    public BlessingCore createCore() {
        return new BlessingCore("springleaf", "Springleaf", BlessingCategory.MOBILITY, Set.of(BlessingTag.SPEED),
                "Hold sneak for 1s, release to leap about 6 blocks. 12s cooldown.",
                "The charge cue is visible and cancels on damage; misplaced leaps cause fall damage.",
                12 * 20, true, false, List.of("slime", "feather"));
    }

    @Override
    public boolean onSneak(BlessingContext ctx, boolean sneaking) {
        Player p = ctx.player();
        UUID key = ctx.instanceId();
        if (sneaking) {
            if (!onGround(p)) return false;
            Charging c = new Charging(p.getUniqueId(), System.currentTimeMillis());
            charging.put(key, c);
            // Visible charge cue; stops itself once the charge ends for any reason.
            Bukkit.getScheduler().runTaskTimer(ctx.engine().plugin(), task -> {
                if (charging.get(key) != c || !p.isOnline() || !p.isSneaking()) {
                    task.cancel();
                    return;
                }
                p.getWorld().spawnParticle(Particle.COMPOSTER, p.getLocation().add(0, 0.1, 0), 3, 0.3, 0, 0.3, 0);
            }, 0L, 4L);
            return false;
        }

        Charging c = charging.remove(key);
        if (c == null || System.currentTimeMillis() - c.startMs() < CHARGE_MS || !onGround(p)) return false;

        double power = Math.max(0.5, Math.min(1.3, ctx.strength(BlessingTag.SPEED)));
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() > 1e-6) dir.normalize().multiply(1.25 * power);
        p.setVelocity(dir.setY(0.6 * Math.max(0.75, power)));
        p.getWorld().playSound(p, Sound.ENTITY_SLIME_JUMP, 0.8f, 1.2f);
        return true;
    }

    /** Server-side ground check; Player#isOnGround is client-reported and can be spoofed. */
    private static boolean onGround(Player p) {
        return !p.getLocation().subtract(0, 0.05, 0).getBlock().isPassable();
    }

    /** Taking damage cancels the charge. */
    @Override
    public boolean onDamaged(BlessingContext ctx, EntityDamageEvent event) {
        charging.remove(ctx.instanceId());
        return false;
    }

    @Override
    public void clear(UUID player) {
        charging.values().removeIf(c -> c.player().equals(player));
    }
}
