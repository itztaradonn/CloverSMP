package net.cloversmp.cloverblessing.model;

/**
 * Hidden balance tags used by the Tag Fatigue system.
 * Each blessing declares one or more tags; when multiple blessings
 * in a loadout share a tag, diminishing returns apply.
 */
public enum BlessingTag {
    HEAL,
    SPEED,
    DAMAGE_BOOST,
    CONTROL
}
