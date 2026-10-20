package net.cloversmp.cloverblessing.charge;

/**
 * Pure charge arithmetic, kept free of Bukkit so it can be unit tested.
 */
public final class ChargeMath {

    private ChargeMath() {}

    public record Result(int charge, int pollen) {}

    /**
     * Adds charge, applying the leaf-4 gate for mob sources and converting overflow above max to Pollen.
     *
     * @param mobSource       hostile or elite mob kill (bosses and players are not mob sources)
     * @param leaf4Gate       whether mob charge is barred from opening leaf 4
     * @param pollenPerCharge Pollen gained per point of overflow
     */
    public static Result gain(int charge, int amount, boolean mobSource, boolean leaf4Gate,
                              int leaf4Threshold, int max, int pollenPerCharge) {
        if (amount <= 0) return new Result(charge, 0);
        int next = charge + amount;
        if (mobSource && leaf4Gate && charge < leaf4Threshold) {
            next = Math.min(next, leaf4Threshold - 1);
        }
        if (next > max) {
            return new Result(max, (next - max) * pollenPerCharge);
        }
        return new Result(Math.max(charge, next), 0);
    }

    /**
     * Charge after a death: loses {@code percent × multiplier} of the meter, but never drops below
     * {@code floor}. A meter already under the floor is left as the penalty makes it, never raised.
     */
    public static int afterDeath(int charge, double percent, double multiplier, int floor) {
        int penalty = (int) Math.round(charge * (percent / 100.0) * multiplier);
        return Math.max(Math.min(charge, floor), charge - penalty);
    }
}
