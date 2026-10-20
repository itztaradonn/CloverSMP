package net.cloversmp.cloverblessing.blessing.handler;

import net.cloversmp.cloverblessing.model.BlessingCategory;
import net.cloversmp.cloverblessing.model.BlessingCore;

import java.util.List;
import java.util.Set;

/** Kills give +50 % charge; deaths cost double charge. */
public class WagerRootHandler implements BlessingHandler {

    @Override
    public BlessingCore createCore() {
        return new BlessingCore("wager_root", "Wager Root", BlessingCategory.RISK_AND_REWARD, Set.of(),
                "Kills give +50% charge; deaths cost double charge.",
                "Makes you a priority target; one bad streak wipes progress.",
                0, true, false, List.of("gold", "emerald"));
    }

    @Override
    public double chargeGainMultiplier(BlessingContext ctx) {
        return 1.0 + 0.5 * ctx.strength();
    }

    @Override
    public double deathPenaltyMultiplier(BlessingContext ctx) {
        return 1.0 + Math.min(1.0, ctx.strength());
    }
}
