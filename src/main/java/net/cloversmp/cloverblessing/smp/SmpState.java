package net.cloversmp.cloverblessing.smp;

/**
 * The server's season phase: LOBBY (state 0, everything locked) → COUNTDOWN → GRACE (no PvP) → RUNNING.
 * Plain fields so it is unit-testable and saved as JSON.
 */
public class SmpState {

    public enum Phase { LOBBY, COUNTDOWN, GRACE, RUNNING }

    private Phase phase = Phase.LOBBY;
    private long graceEndMs;
    private long graceTotalMs;

    public Phase phase() {
        return phase;
    }

    public boolean pvpAllowed() {
        return phase == Phase.RUNNING;
    }

    /** Building, charge and blessings work once the SMP has started. */
    public boolean progressionAllowed() {
        return phase == Phase.GRACE || phase == Phase.RUNNING;
    }

    /** @return false unless in the lobby */
    public boolean beginCountdown() {
        if (phase != Phase.LOBBY) return false;
        phase = Phase.COUNTDOWN;
        return true;
    }

    public void beginGrace(long nowMs, long graceMs) {
        graceTotalMs = Math.max(0, graceMs);
        graceEndMs = nowMs + graceTotalMs;
        phase = graceTotalMs > 0 ? Phase.GRACE : Phase.RUNNING;
    }

    /** @return true at the moment grace ends and PvP turns on */
    public boolean tick(long nowMs) {
        if (phase != Phase.GRACE || nowMs < graceEndMs) return false;
        phase = Phase.RUNNING;
        return true;
    }

    public long graceRemainingMs(long nowMs) {
        return phase == Phase.GRACE ? Math.max(0, graceEndMs - nowMs) : 0;
    }

    public long graceTotalMs() {
        return graceTotalMs;
    }

    /** A countdown can't survive a restart (its effects are gone), so it falls back to the lobby. */
    public void afterRestart() {
        if (phase == Phase.COUNTDOWN) phase = Phase.LOBBY;
    }

    public void reset() {
        phase = Phase.LOBBY;
        graceEndMs = 0;
        graceTotalMs = 0;
    }
}
