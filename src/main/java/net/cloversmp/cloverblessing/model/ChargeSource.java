package net.cloversmp.cloverblessing.model;

/**
 * Sources of Clover Charge with their config keys.
 */
public enum ChargeSource {
    HOSTILE_MOB("hostile-mob"),
    ELITE_MOB("elite-mob"),
    BOSS("boss"),
    PLAYER_KILL("player-kill");

    private final String configKey;

    ChargeSource(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigKey() {
        return configKey;
    }
}
