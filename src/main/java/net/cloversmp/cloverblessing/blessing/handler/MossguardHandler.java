package net.cloversmp.cloverblessing.blessing.handler;

import net.cloversmp.cloverblessing.model.BlessingCategory;
import net.cloversmp.cloverblessing.model.BlessingCore;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.List;
import java.util.Set;

/** Hits under 4 damage are halved. Critical hits are not reduced. */
public class MossguardHandler implements BlessingHandler {

    @Override
    public BlessingCore createCore() {
        return new BlessingCore("mossguard", "Mossguard", BlessingCategory.DEFENSE, Set.of(),
                "Hits under 4 damage are halved.",
                "Does nothing against crits, strength or burst weapons.",
                0, true, false, List.of("moss", "hide"));
    }

    @Override
    public boolean onDamaged(BlessingContext ctx, EntityDamageEvent event) {
        if (event.getDamage() >= 4.0) return false;
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.isCritical()) return false;
        event.setDamage(event.getDamage() * (1 - 0.5 * Math.min(1.0, ctx.strength())));
        return true;
    }
}
