package net.cloversmp.cloverblessing.blessing.handler;

import net.cloversmp.cloverblessing.model.BlessingCategory;
import net.cloversmp.cloverblessing.model.BlessingCore;
import org.bukkit.event.entity.EntityRegainHealthEvent;

import java.util.List;
import java.util.Set;

/** Curse: no natural regeneration; blessing healing +50 % (still under the Sap Cap). */
public class BloodrootCurse implements BlessingHandler {

    @Override
    public BlessingCore createCore() {
        return new BlessingCore("bloodroot", "Bloodroot", BlessingCategory.CURSE, Set.of(),
                "Blessing healing +50% (still under the Sap Cap).",
                "You never regenerate health from food or natural regeneration.",
                0, false, false, List.of());
    }

    @Override
    public boolean onRegainHealth(BlessingContext ctx, EntityRegainHealthEvent event) {
        var reason = event.getRegainReason();
        if (reason == EntityRegainHealthEvent.RegainReason.SATIATED || reason == EntityRegainHealthEvent.RegainReason.REGEN) {
            event.setCancelled(true);
        }
        return false;
    }

    @Override
    public double healMultiplier(BlessingContext ctx) {
        return 1.0 + 0.5 * ctx.strength();
    }
}
