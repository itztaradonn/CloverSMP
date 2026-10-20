package net.cloversmp.cloverblessing.blessing.handler;

import net.cloversmp.cloverblessing.model.BlessingCategory;
import net.cloversmp.cloverblessing.model.BlessingCore;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Set;

/** Party members within 10 blocks get Night Vision; you glow faintly to them. */
public class LanternleafHandler implements BlessingHandler {

    @Override
    public BlessingCore createCore() {
        return new BlessingCore("lanternleaf", "Lanternleaf", BlessingCategory.UTILITY, Set.of(),
                "Party members within 10 blocks get Night Vision; you glow faintly to them.",
                "Only helps parties; the glow also marks you at night.",
                0, true, false, List.of("lantern", "glow berry"));
    }

    @Override
    public boolean onSecond(BlessingContext ctx, StatSink stats) {
        Player holder = ctx.player();
        double radius = 10.0 * Math.min(1.0, ctx.strength());
        for (Player ally : ctx.engine().partnersNear(holder, radius)) {
            // Over 200 ticks so the vanilla screen flicker never starts between refreshes.
            ally.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 260, 0, true, false, true));
            ally.spawnParticle(Particle.GLOW, holder.getLocation().add(0, 1.0, 0), 3, 0.3, 0.5, 0.3, 0);
        }
        return false;
    }
}
