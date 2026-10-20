package net.cloversmp.cloverblessing.crafting;

import net.cloversmp.cloverblessing.model.Graft;
import org.bukkit.Material;

/**
 * Graft costs and prune refunds (concept doc section 5).
 */
public final class GraftRules {

    private GraftRules() {}

    /** The contested vanilla material each graft costs (on top of Pollen). */
    public static Material material(Graft graft) {
        return switch (graft) {
            case QUICK -> Material.PHANTOM_MEMBRANE;
            case DEEP -> Material.DIAMOND;
            case HUNTER -> Material.BREEZE_ROD;
            case VENGEFUL -> Material.WITHER_SKELETON_SKULL;
            case WARD -> Material.HEART_OF_THE_SEA;
        };
    }

    /** Prune refund: {@code percent} of {@code amount}, rounded down. */
    public static int refund(int amount, double percent) {
        return (int) Math.floor(amount * percent / 100.0);
    }
}
