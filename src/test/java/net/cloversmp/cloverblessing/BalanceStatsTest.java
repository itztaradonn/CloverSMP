package net.cloversmp.cloverblessing;

import net.cloversmp.cloverblessing.stats.BalanceStats;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BalanceStatsTest {

    @Test
    void pickRateAndKillShare() {
        var s = new BalanceStats();
        s.recordLoadout(List.of("thornlash", "leechvine"));
        s.recordLoadout(List.of("thornlash"));
        s.recordLoadout(List.of("mossguard"));
        s.recordLoadout(List.of("thornlash", "mossguard"));
        assertEquals(0.75, s.pickRate("thornlash"));
        assertEquals(0.0, s.pickRate("waystem"));

        s.recordKill(List.of("thornlash"));
        s.recordKill(List.of("mossguard"));
        assertEquals(0.5, s.killShare("thornlash"));
        assertEquals(2, s.kills());
    }

    @Test
    void emptyStatsAreZeroNotNaN() {
        var s = new BalanceStats();
        assertEquals(0.0, s.pickRate("thornlash"));
        assertEquals(0.0, s.killShare("thornlash"));
    }

    @Test
    void flagsNeedEnoughSamples() {
        var s = new BalanceStats();
        for (int i = 0; i < 5; i++) s.recordLoadout(List.of("thornlash"));
        assertFalse(s.overPicked("thornlash", 0.40, 20)); // 100% but only 5 samples
        for (int i = 0; i < 15; i++) s.recordLoadout(List.of("mossguard"));
        assertFalse(s.overPicked("thornlash", 0.40, 20)); // 25%
        assertTrue(s.overPicked("mossguard", 0.40, 20));  // 75% of 20

        for (int i = 0; i < 20; i++) s.recordKill(List.of(i < 12 ? "stinger" : "hobble"));
        assertTrue(s.overKilling("stinger", 0.55, 20));   // 60%
        assertFalse(s.overKilling("hobble", 0.55, 20));   // 40%
    }

    @Test
    void sameBlessingTwiceInOneLoadoutCountsOnce() {
        var s = new BalanceStats();
        s.recordLoadout(List.of("thornlash", "thornlash"));
        assertEquals(1.0, s.pickRate("thornlash"));
    }
}
