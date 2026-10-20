package net.cloversmp.cloverblessing.blessing.handler;

import net.cloversmp.cloverblessing.model.BlessingCategory;
import net.cloversmp.cloverblessing.model.BlessingCore;
import net.cloversmp.cloverblessing.model.BlessingTag;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Arrow hits mark a target for 5 s; your melee hits on a marked target deal +15 %. */
public class StingerHandler implements BlessingHandler {

    private static final long MARK_MS = 5_000;

    /** blessing instance → (target → mark expiry). */
    private final Map<UUID, Map<UUID, Long>> marks = new ConcurrentHashMap<>();

    @Override
    public BlessingCore createCore() {
        return new BlessingCore("stinger", "Stinger", BlessingCategory.OFFENSE, Set.of(BlessingTag.DAMAGE_BOOST),
                "Arrow hits mark a target for 5s; your melee hits on a marked target deal +15% damage.",
                "Needs a bow and a melee weapon; breaking line of sight loses the mark.",
                0, true, false, List.of("arrow", "insect"));
    }

    @Override
    public boolean onArrowHit(BlessingContext ctx, EntityDamageByEntityEvent event, LivingEntity target) {
        marks.computeIfAbsent(ctx.instanceId(), k -> new ConcurrentHashMap<>())
                .put(target.getUniqueId(), System.currentTimeMillis() + MARK_MS);
        target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, target.getHeight() + 0.3, 0), 8, 0.2, 0.1, 0.2, 0);
        return true;
    }

    @Override
    public boolean onMeleeHit(BlessingContext ctx, EntityDamageByEntityEvent event, LivingEntity target) {
        Map<UUID, Long> m = marks.get(ctx.instanceId());
        Long until = m == null ? null : m.get(target.getUniqueId());
        if (until == null || until < System.currentTimeMillis()) return false;
        event.setDamage(event.getDamage() * (1 + 0.15 * ctx.strength(BlessingTag.DAMAGE_BOOST)));
        return true;
    }

    /** Drops expired marks and marks on targets the holder can no longer see. */
    @Override
    public boolean onSecond(BlessingContext ctx, StatSink stats) {
        Map<UUID, Long> m = marks.get(ctx.instanceId());
        if (m == null) return false;
        long now = System.currentTimeMillis();
        m.entrySet().removeIf(e -> {
            Entity target = Bukkit.getEntity(e.getKey());
            return e.getValue() < now || target == null || !target.getWorld().equals(ctx.player().getWorld())
                    || !ctx.player().hasLineOfSight(target);
        });
        if (m.isEmpty()) marks.remove(ctx.instanceId());
        return false;
    }
}
