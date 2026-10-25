package net.cloversmp.cloverblessing;

import net.cloversmp.cloverblessing.smp.SmpState;
import net.cloversmp.cloverblessing.smp.SmpState.Phase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SmpStateTest {

    @Test
    void lobbyLocksEverything() {
        var s = new SmpState();
        assertEquals(Phase.LOBBY, s.phase());
        assertFalse(s.pvpAllowed());
        assertFalse(s.progressionAllowed());
        assertTrue(s.beginCountdown());
        assertFalse(s.progressionAllowed());
        assertFalse(s.beginCountdown()); // only from the lobby
    }

    @Test
    void graceThenRunning() {
        var s = new SmpState();
        s.beginCountdown();
        s.beginGrace(1_000, 20 * 60_000);
        assertEquals(Phase.GRACE, s.phase());
        assertTrue(s.progressionAllowed());
        assertFalse(s.pvpAllowed());
        assertEquals(20 * 60_000 - 500, s.graceRemainingMs(1_500));

        assertFalse(s.tick(20 * 60_000));            // 1 s before the end
        assertTrue(s.tick(20 * 60_000 + 1_000));     // grace over
        assertEquals(Phase.RUNNING, s.phase());
        assertTrue(s.pvpAllowed());
        assertFalse(s.tick(30 * 60_000));            // already running
    }

    @Test
    void zeroGraceGoesStraightToRunning() {
        var s = new SmpState();
        s.beginCountdown();
        s.beginGrace(0, 0);
        assertEquals(Phase.RUNNING, s.phase());
    }

    @Test
    void interruptedCountdownFallsBackToLobby() {
        var s = new SmpState();
        s.beginCountdown();
        s.afterRestart();
        assertEquals(Phase.LOBBY, s.phase());
    }
}
