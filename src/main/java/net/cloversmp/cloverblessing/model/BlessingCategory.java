package net.cloversmp.cloverblessing.model;

/**
 * The eight blessing categories from the design document.
 */
public enum BlessingCategory {
    OFFENSE("Offense", "⚔"),
    DEFENSE("Defense", "🛡"),
    MOBILITY("Mobility", "💨"),
    RECOVERY("Recovery", "💚"),
    CROWD_CONTROL("Crowd Control", "🕸"),
    UTILITY("Utility", "🔧"),
    RISK_AND_REWARD("Risk & Reward", "🎲"),
    EXPERIMENTAL("Experimental", "✦"),
    CURSE("Curse", "☠");

    private final String displayName;
    private final String icon;

    BlessingCategory(String displayName, String icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }
}
