package net.cloversmp.cloverblessing.blessing.handler;

import net.cloversmp.cloverblessing.ui.Effects;
import net.cloversmp.cloverblessing.blessing.BalanceRules;
import net.cloversmp.cloverblessing.model.BlessingCategory;
import net.cloversmp.cloverblessing.model.BlessingCore;
import net.cloversmp.cloverblessing.model.BlessingTag;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.List;
import java.util.Set;

/**
 * Every 30 s cycles a small buff: spring heal, summer +10 % damage, autumn +10 % speed,
 * winter −10 % damage taken. Phases run on a shared clock and the particle colour gives them away.
 */
public class TurningYearHandler implements BlessingHandler {

    private static final long PHASE_MS = 30_000;
    private static final int SPRING = 0, SUMMER = 1, AUTUMN = 2, WINTER = 3;
    private static final Color[] COLORS = {
            Color.fromRGB(0xF4A7C0), Color.fromRGB(0xF7C948), Color.fromRGB(0xD9622B), Color.fromRGB(0xA8D8F0)};

    private final java.util.Map<java.util.UUID, Integer> lastPhase = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public BlessingCore createCore() {
        return new BlessingCore("turning_year", "Turning Year", BlessingCategory.EXPERIMENTAL,
                Set.of(BlessingTag.HEAL, BlessingTag.DAMAGE_BOOST, BlessingTag.SPEED),
                "Every 30s cycles a buff: spring heals, summer +10% damage, autumn +10% speed, winter -10% damage taken.",
                "Predictable rhythm; particle colour gives the phase away, so attack in the weak phase.",
                0, true, true, List.of("clock", "flowers"));
    }

    private static int phase() {
        return BalanceRules.turningYearPhase(System.currentTimeMillis(), PHASE_MS);
    }

    @Override
    public boolean onMeleeHit(BlessingContext ctx, EntityDamageByEntityEvent event, LivingEntity target) {
        return summer(ctx, event);
    }

    @Override
    public boolean onArrowHit(BlessingContext ctx, EntityDamageByEntityEvent event, LivingEntity target) {
        return summer(ctx, event);
    }

    private boolean summer(BlessingContext ctx, EntityDamageByEntityEvent event) {
        if (phase() != SUMMER) return false;
        event.setDamage(event.getDamage() * (1 + 0.10 * ctx.strength(BlessingTag.DAMAGE_BOOST)));
        return false;
    }

    @Override
    public boolean onDamaged(BlessingContext ctx, EntityDamageEvent event) {
        if (phase() != WINTER) return false;
        event.setDamage(event.getDamage() * (1 - 0.10 * Math.min(1.0, ctx.strength())));
        return false;
    }

    @Override
    public boolean onSecond(BlessingContext ctx, StatSink stats) {
        int phase = phase();
        Integer before = lastPhase.put(ctx.instanceId(), phase);
        if (before != null && before != phase) {
            Effects.sound(ctx.player().getLocation(), "blessing.turning_year", 1f, 0.8f + 0.15f * phase);
            Effects.dust(Effects.body(ctx.player(), 0.6), COLORS[phase], 20, 0.6);
        }
        ctx.player().getWorld().spawnParticle(Particle.DUST, ctx.player().getLocation().add(0, 1.0, 0), 6,
                0.4, 0.5, 0.4, 0, new Particle.DustOptions(COLORS[phase], 1.0f));
        if (phase == AUTUMN) stats.speed(0.10 * ctx.strength(BlessingTag.SPEED));
        if (phase == SPRING && System.currentTimeMillis() / 1000 % 2 == 0) {
            ctx.engine().heal(ctx, 1.0 * ctx.strength(BlessingTag.HEAL));
        }
        return false;
    }
}
