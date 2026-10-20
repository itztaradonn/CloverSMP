package net.cloversmp.cloverblessing.model;

/**
 * Graft types — optional sidegrades applied to a blessing core.
 * Each graft modifies a blessing's strength and/or cooldown with a condition.
 */
public enum Graft {
    /**
     * -25% cooldown, -15% strength.
     */
    QUICK("Quick", -0.25, -0.15, null),

    /**
     * +25% strength, +35% cooldown.
     */
    DEEP("Deep", 0.35, 0.25, null),

    /**
     * +15% strength, only after landing a hit in the last 3s.
     */
    HUNTER("Hunter", 0.0, 0.15, "Landed a hit in the last 3 seconds"),

    /**
     * +15% strength, only after taking damage in the last 3s.
     */
    VENGEFUL("Vengeful", 0.0, 0.15, "Took damage in the last 3 seconds"),

    /**
     * A party member within 6 blocks gets half the effect.
     */
    WARD("Ward", 0.0, 0.0, "Party member within 6 blocks gets 50% effect");

    private final String displayName;
    private final double cooldownModifier;
    private final double strengthModifier;
    private final String condition;

    Graft(String displayName, double cooldownModifier, double strengthModifier, String condition) {
        this.displayName = displayName;
        this.cooldownModifier = cooldownModifier;
        this.strengthModifier = strengthModifier;
        this.condition = condition;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Cooldown modifier as a fraction (e.g. -0.25 means 25% less cooldown).
     */
    public double getCooldownModifier() {
        return cooldownModifier;
    }

    /**
     * Strength modifier as a fraction (e.g. 0.25 means 25% more strength).
     */
    public double getStrengthModifier() {
        return strengthModifier;
    }

    /**
     * Human-readable condition, or null if unconditional.
     */
    public String getCondition() {
        return condition;
    }

    public boolean hasCondition() {
        return condition != null;
    }
}
