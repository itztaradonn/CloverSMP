package net.cloversmp.cloverblessing.blessing.handler;

import net.cloversmp.cloverblessing.ui.Effects;
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
        // The toll carries further than normal sounds, so it gives the wearer away.
        if (Effects.every(ctx.player().getUniqueId(), "hollow_bell", 30_000)) {
            Effects.sound(ctx.player().getLocation(), "curse.hollow_bell", 1.5f, 1f);
        }
        return false;
    }

    @Override
    public double cooldownModifier(BlessingContext ctx) {
        return -0.20 * ctx.strength();
    }
}
