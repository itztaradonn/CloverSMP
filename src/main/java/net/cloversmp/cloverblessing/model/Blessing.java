package net.cloversmp.cloverblessing.model;

import java.util.UUID;

/**
 * A pressed blessing instance — a core plus an optional graft.
 * Each blessing is a unique server-side record identified by a UUID.
 * The in-game item is only a claim on this record.
 */
public class Blessing {

    private final UUID id;
    private final String coreId;
    private final Graft graft;
    private final UUID ownerUuid;
    private boolean bound;

    public Blessing(UUID id, String coreId, Graft graft, UUID ownerUuid) {
        this.id = id;
        this.coreId = coreId;
        this.graft = graft;
        this.ownerUuid = ownerUuid;
        // Grafted blessings are bound on creation; ungrafted cores are tradable
        this.bound = graft != null;
    }

    public UUID getId() {
        return id;
    }

    public String getCoreId() {
        return coreId;
    }

    public Graft getGraft() {
        return graft;
    }

    public boolean hasGraft() {
        return graft != null;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public boolean isBound() {
        return bound;
    }

    public void bind() {
        this.bound = true;
    }

    /**
     * Calculates the effective cooldown in ticks, applying graft modifier.
     */
    public long getEffectiveCooldownTicks(long baseCooldownTicks) {
        if (graft == null) {
            return baseCooldownTicks;
        }
        return Math.max(20L, (long) (baseCooldownTicks * (1.0 + graft.getCooldownModifier())));
    }

    /**
     * Calculates the effective strength multiplier, applying graft modifier.
     */
    public double getEffectiveStrengthMultiplier() {
        if (graft == null) {
            return 1.0;
        }
        return 1.0 + graft.getStrengthModifier();
    }
}
