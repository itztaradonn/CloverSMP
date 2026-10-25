package net.cloversmp.cloverblessing.blessing.handler;

import net.cloversmp.cloverblessing.ui.Effects;
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
        Effects.particles(Effects.body(target, 1.15), Particle.ELECTRIC_SPARK, 10, 0.3);
        return true;
    }

    @Override
    public boolean onMeleeHit(BlessingContext ctx, EntityDamageByEntityEvent event, LivingEntity target) {
        Map<UUID, Long> m = marks.get(ctx.instanceId());
        Long until = m == null ? null : m.get(target.getUniqueId());
        if (until == null || until < System.currentTimeMillis()) return false;
        event.setDamage(event.getDamage() * (1 + 0.15 * ctx.strength(BlessingTag.DAMAGE_BOOST)));
        Effects.particles(Effects.body(target, 0.6), Particle.ENCHANTED_HIT, 12, 0.3);
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
