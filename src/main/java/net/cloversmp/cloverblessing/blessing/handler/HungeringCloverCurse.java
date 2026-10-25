package net.cloversmp.cloverblessing.blessing.handler;

import net.cloversmp.cloverblessing.ui.Effects;
import net.cloversmp.cloverblessing.model.BlessingCategory;
import net.cloversmp.cloverblessing.model.BlessingCore;
import org.bukkit.event.entity.FoodLevelChangeEvent;

import java.util.List;
import java.util.Set;

/** Curse: all blessing strength +15 %; hunger drains twice as fast. */
public class HungeringCloverCurse implements BlessingHandler {

    @Override
    public BlessingCore createCore() {
        return new BlessingCore("hungering_clover", "Hungering Clover", BlessingCategory.CURSE, Set.of(),
                "All your blessings are 15% stronger.",
                "Hunger drains twice as fast.",
                0, false, false, List.of());
    }

    @Override
    public boolean onFoodChange(BlessingContext ctx, FoodLevelChangeEvent event) {
        int current = ctx.player().getFoodLevel();
        int next = event.getFoodLevel();
        if (next < current) {
            event.setFoodLevel(Math.max(0, current - 2 * (current - next)));
            if (Effects.every(ctx.player().getUniqueId(), "hungering", 10_000)) {
                Effects.sound(ctx.player().getLocation(), "curse.hungering_clover", 1f, 1f);
            }
        }
        return false;
    }

    @Override
    public double strengthBonus(BlessingContext ctx) {
        return 0.15 * ctx.strength();
    }
}
