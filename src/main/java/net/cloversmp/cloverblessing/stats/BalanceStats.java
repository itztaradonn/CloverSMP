package net.cloversmp.cloverblessing.stats;

import java.util.*;

/**
 * The concept doc's "dominance watch": how often each blessing is picked by 4-leaf players, and how often it is
 * equipped on the killer in PvP. Plain counters so it can be unit tested and saved as JSON.
 */
public class BalanceStats {

    private int loadouts;
    private int kills;
    private final Map<String, Integer> picks = new HashMap<>();
    private final Map<String, Integer> killsWith = new HashMap<>();

    /** One sampled loadout (blessing/curse ids on open leaves). Duplicates in a loadout count once. */
    public void recordLoadout(Collection<String> coreIds) {
        loadouts++;
        new HashSet<>(coreIds).forEach(id -> picks.merge(id, 1, Integer::sum));
    }

    /** One PvP kill with the killer's active blessing/curse ids. */
    public void recordKill(Collection<String> killerCoreIds) {
        kills++;
        new HashSet<>(killerCoreIds).forEach(id -> killsWith.merge(id, 1, Integer::sum));
    }

    public double pickRate(String coreId) {
        return loadouts == 0 ? 0 : picks.getOrDefault(coreId, 0) / (double) loadouts;
    }

    public double killShare(String coreId) {
        return kills == 0 ? 0 : killsWith.getOrDefault(coreId, 0) / (double) kills;
    }

    public boolean overPicked(String coreId, double limit, int minSamples) {
        return loadouts >= minSamples && pickRate(coreId) > limit;
    }

    public boolean overKilling(String coreId, double limit, int minSamples) {
        return kills >= minSamples && killShare(coreId) > limit;
    }

    public int loadouts() { return loadouts; }
    public int kills() { return kills; }
    public int picks(String coreId) { return picks.getOrDefault(coreId, 0); }
    public int killsWith(String coreId) { return killsWith.getOrDefault(coreId, 0); }
}
