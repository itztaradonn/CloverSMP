package net.cloversmp.cloverblessing;

import net.cloversmp.cloverblessing.blessing.BalanceRules;
import net.cloversmp.cloverblessing.blessing.SapCapEngine;
import net.cloversmp.cloverblessing.charge.ChargeMath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RulesTest {

    // --- Charge ---

    @Test
    void mobChargeCannotOpenLeafFour() {
        var r = ChargeMath.gain(790, 20, true, true, 800, 1000, 1);
        assertEquals(799, r.charge());
        assertEquals(0, r.pollen());
    }

    @Test
    void mobChargeCountsOnceLeafFourIsOpen() {
        assertEquals(830, ChargeMath.gain(810, 20, true, true, 800, 1000, 1).charge());
    }

    @Test
    void pvpChargeOpensLeafFourAndOverflowBecomesPollen() {
        assertEquals(840, ChargeMath.gain(790, 50, false, true, 800, 1000, 1).charge());
        var r = ChargeMath.gain(980, 50, false, true, 800, 1000, 2);
        assertEquals(1000, r.charge());
        assertEquals(60, r.pollen());
    }

    @Test
    void deathCostsPercentButNeverBelowFloor() {
        assertEquals(650, ChargeMath.afterDeath(1000, 35, 1.0, 100));
        assertEquals(100, ChargeMath.afterDeath(120, 35, 1.0, 100));
        // Wager Root doubles the penalty
        assertEquals(300, ChargeMath.afterDeath(1000, 35, 2.0, 100));
        // Below the floor (no permanence yet) nothing is raised
        assertEquals(32, ChargeMath.afterDeath(50, 35, 1.0, 0)); // 17.5 rounds to 18
    }

    // --- Balance ---

    @Test
    void tagFatigueFollowsConfiguredList() {
        var list = List.of(1.0, 0.5, 0.25);
        assertEquals(1.0, BalanceRules.fatigue(0, list));
        assertEquals(0.25, BalanceRules.fatigue(2, list));
        assertEquals(0.0, BalanceRules.fatigue(3, list));
    }

    @Test
    void singleHitReductionIsClampedToHalf() {
        assertEquals(5.0, BalanceRules.clampReduction(10.0, 2.0, 0.5));
        assertEquals(8.0, BalanceRules.clampReduction(10.0, 8.0, 0.5));
        assertEquals(12.0, BalanceRules.clampReduction(10.0, 12.0, 0.5));
    }

    @Test
    void cooldownReductionIsCapped() {
        assertEquals(7_000, BalanceRules.cappedCooldownMs(10_000, -0.45, 0.30));
        assertEquals(13_500, BalanceRules.cappedCooldownMs(10_000, 0.35, 0.30));
        assertEquals(0, BalanceRules.cappedCooldownMs(0, -0.25, 0.30));
        assertEquals(1_000, BalanceRules.cappedCooldownMs(1_200, -0.30, 0.30));
    }

    @Test
    void speedSourcesDoNotStackAndAreCapped() {
        assertEquals(0.15, BalanceRules.speedBonus(List.of(0.15, 0.10), 0.40));
        assertEquals(0.40, BalanceRules.speedBonus(List.of(0.9), 0.40));
        assertEquals(0.0, BalanceRules.speedBonus(List.of(), 0.40));
    }

    @Test
    void turningYearCyclesEveryThirtySeconds() {
        assertEquals(0, BalanceRules.turningYearPhase(0, 30_000));
        assertEquals(1, BalanceRules.turningYearPhase(30_000, 30_000));
        assertEquals(3, BalanceRules.turningYearPhase(119_999, 30_000));
        assertEquals(0, BalanceRules.turningYearPhase(120_000, 30_000));
    }

    // --- Sap Cap ---

    @Test
    void sapCapLimitsHealingInsideWindow() {
        var now = new AtomicLong(0);
        var sap = new SapCapEngine(() -> 6.0, () -> 10_000L, now::get);
        UUID id = UUID.randomUUID();

        assertEquals(4.0, sap.take(id, 4.0));
        assertEquals(2.0, sap.take(id, 4.0)); // only 2 hearts left
        assertEquals(0.0, sap.take(id, 1.0));

        now.set(10_001); // window rolled over
        assertEquals(1.0, sap.take(id, 1.0));
    }
}
