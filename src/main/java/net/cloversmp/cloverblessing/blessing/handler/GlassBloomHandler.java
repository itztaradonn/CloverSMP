package net.cloversmp.cloverblessing.blessing.handler;

import net.cloversmp.cloverblessing.model.BlessingCategory;
import net.cloversmp.cloverblessing.model.BlessingCore;
import net.cloversmp.cloverblessing.model.BlessingTag;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.List;
import java.util.Set;

/** +25 % damage dealt; max health reduced by 3 hearts. */
public class GlassBloomHandler implements BlessingHandler {

    @Override
    public BlessingCore createCore() {
        return new BlessingCore("glass_bloom", "Glass Bloom", BlessingCategory.RISK_AND_REWARD, Set.of(BlessingTag.DAMAGE_BOOST),
                "+25% damage dealt; max health reduced by 3 hearts.",
                "Dies fast to burst and ambush.",
                0, true, false, List.of("glass", "blaze"));
    }

    @Override
    public boolean onMeleeHit(BlessingContext ctx, EntityDamageByEntityEvent event, LivingEntity target) {
        return boost(ctx, event);
    }

    @Override
    public boolean onArrowHit(BlessingContext ctx, EntityDamageByEntityEvent event, LivingEntity target) {
        return boost(ctx, event);
    }

    private boolean boost(BlessingContext ctx, EntityDamageByEntityEvent event) {
        event.setDamage(event.getDamage() * (1 + 0.25 * ctx.strength(BlessingTag.DAMAGE_BOOST)));
        return false; // always on; no trigger flash
    }

    /** The health cost scales with strength but not with tag fatigue: a downside is not a bonus. */
    @Override
    public boolean onSecond(BlessingContext ctx, StatSink stats) {
        stats.maxHealth(-6.0 * Math.min(1.0, ctx.strength()));
        return false;
    }
}
