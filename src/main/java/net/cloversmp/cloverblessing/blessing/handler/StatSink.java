package net.cloversmp.cloverblessing.blessing.handler;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects the stat changes active blessings want this second. The engine rebuilds it every second and
 * applies the result as attribute modifiers, so nothing leaks when a blessing is removed or wilts.
 */
public final class StatSink {

    private final List<Double> speedSources = new ArrayList<>();
    private double maxHealth;

    /** Request a movement speed bonus (0.15 = +15 %). Sources do not stack: the strongest wins, capped. */
    public void speed(double bonus) {
        speedSources.add(bonus);
    }

    /** Add to max health in HP (−6 = −3 hearts). */
    public void maxHealth(double hp) {
        maxHealth += hp;
    }

    public List<Double> speedSources() {
        return speedSources;
    }

    public double maxHealth() {
        return maxHealth;
    }
}
