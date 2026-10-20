package net.cloversmp.cloverblessing.blessing;

import java.util.Collection;
import java.util.List;

/**
 * Pure balance rules from the concept doc (section 7), kept free of Bukkit so they can be unit tested.
 */
public final class BalanceRules {

    private BalanceRules() {}

    /** Tag fatigue: the n-th source of a tag (0-based) uses the n-th multiplier; past the list it gives 0. */
    public static double fatigue(int ordinal, List<Double> multipliers) {
        if (ordinal < 0 || ordinal >= multipliers.size()) return 0.0;
        return multipliers.get(ordinal);
    }

    /** No blessing may cut a single hit by more than {@code maxReduction} (0.5 = 50 %). Increases pass through. */
    public static double clampReduction(double original, double modified, double maxReduction) {
        return Math.max(modified, original * (1.0 - maxReduction));
    }

    /**
     * Applies a cooldown modifier with total reduction capped at {@code maxCdr}. A base of 0 means no cooldown;
     * any other cooldown is at least one second.
     */
    public static long cappedCooldownMs(long baseMs, double modifier, double maxCdr) {
        if (baseMs <= 0) return 0;
        double mod = Math.max(modifier, -maxCdr);
        return Math.max(1_000L, Math.round(baseMs * (1.0 + mod)));
    }

    /** Speed sources do not stack: only the strongest counts, capped at {@code cap} (0.40 ≈ Speed II). */
    public static double speedBonus(Collection<Double> sources, double cap) {
        double best = 0.0;
        for (double s : sources) best = Math.max(best, s);
        return Math.min(best, cap);
    }

    /** Turning Year phase: 0 spring, 1 summer, 2 autumn, 3 winter, on a shared wall clock. */
    public static int turningYearPhase(long nowMs, long phaseMs) {
        return (int) ((nowMs / phaseMs) % 4);
    }
}
