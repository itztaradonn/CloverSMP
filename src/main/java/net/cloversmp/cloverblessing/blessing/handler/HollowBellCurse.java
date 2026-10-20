package net.cloversmp.cloverblessing.blessing.handler;

import net.cloversmp.cloverblessing.model.BlessingCategory;
import net.cloversmp.cloverblessing.model.BlessingCore;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Set;

/** Curse: blessing cooldowns 20 % shorter (within the CDR cap); you glow to everyone. */
public class HollowBellCurse implements BlessingHandler {

    @Override
    public BlessingCore createCore() {
        return new BlessingCore("hollow_bell", "Hollow Bell", BlessingCategory.CURSE, Set.of(),
                "Your blessing cooldowns are 20% shorter (counts toward the 30% cap).",
                "You glow, visible to every player through walls.",
                0, false, false, List.of());
    }

    @Override
    public boolean onSecond(BlessingContext ctx, StatSink stats) {
        ctx.player().addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, true, false, false));
        return false;
    }

    @Override
    public double cooldownModifier(BlessingContext ctx) {
        return -0.20 * ctx.strength();
    }
}
