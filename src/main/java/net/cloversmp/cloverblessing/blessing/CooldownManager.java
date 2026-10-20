package net.cloversmp.cloverblessing.blessing;

import net.cloversmp.cloverblessing.model.Clover;

import java.util.UUID;

/**
 * Blessing cooldowns, stored as wall-clock expiry times on the player's {@link Clover}. The Clover is
 * saved to disk, so cooldowns persist through death, logout and restarts (concept doc, section 7).
 */
public class CooldownManager {

    public void startCooldown(Clover clover, UUID blessingId, long durationMs) {
        if (durationMs <= 0) return;
        clover.setCooldownExpiry(blessingId, System.currentTimeMillis() + durationMs);
    }

    public boolean isOnCooldown(Clover clover, UUID blessingId) {
        return getRemainingMs(clover, blessingId) > 0;
    }

    public long getRemainingMs(Clover clover, UUID blessingId) {
        return Math.max(0, clover.getCooldownExpiry(blessingId) - System.currentTimeMillis());
    }
}
