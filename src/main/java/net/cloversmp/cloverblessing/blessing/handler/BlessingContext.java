package net.cloversmp.cloverblessing.blessing.handler;

import net.cloversmp.cloverblessing.blessing.BlessingEngine;
import net.cloversmp.cloverblessing.model.*;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Everything a handler needs for one call: who holds the blessing, which slot, and how strong it is.
 *
 * @param source the blessing equipped in {@code slot} (for an Echo copy, the Echo Leaf itself)
 * @param core   the rule being run (for an Echo copy, the opposite leaf's core)
 * @param echo   true when this call is Echo Leaf copying the opposite leaf
 * @param baseStrength graft × echo × curse bonus, before tag fatigue
 */
public record BlessingContext(BlessingEngine engine, Player player, Clover clover, LeafSlot slot,
                              Blessing source, BlessingCore core, boolean echo, double baseStrength) {

    /** Strength for effects that carry no balance tag. */
    public double strength() {
        return baseStrength;
    }

    /** Strength for an effect of the given tag, after tag fatigue. */
    public double strength(BlessingTag tag) {
        return baseStrength * engine.fatigue(this, tag);
    }

    /** Unique per equipped copy; use it to key cooldowns and per-copy state (an Echo copy has its own). */
    public UUID instanceId() {
        return source.getId();
    }

    /** Marks the blessing as fired outside a hook return (e.g. after a channel completes). */
    public void fire() {
        engine.fire(this);
    }
}
