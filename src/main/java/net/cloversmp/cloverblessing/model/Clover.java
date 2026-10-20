package net.cloversmp.cloverblessing.model;

import java.util.*;

/**
 * Represents a player's clover — their progression state, equipped blessings and herbarium.
 * This is the central player data object persisted to storage. Only touched on the main thread.
 */
public class Clover {

    private final UUID playerUuid;
    private int charge;
    private boolean leaf2Permanent;
    private int pollen;
    private final Map<LeafSlot, Blessing> equippedBlessings = new EnumMap<>(LeafSlot.class);
    /** Unequipped blessings owned by the player, keyed by blessing id. */
    private final Map<UUID, Blessing> herbarium = new LinkedHashMap<>();
    private final Map<String, Boolean> discoveredRecipes = new HashMap<>();
    /** Wall-clock millis of the last PvP hit dealt or taken (loadout combat timer). */
    private long lastCombatMs;
    /** Blessing id → wall-clock millis when its cooldown ends. */
    private final Map<UUID, Long> cooldownExpiry = new HashMap<>();
    private int dailyMobCharge;
    private long dailyMobChargeResetDay;
    private final Map<String, Integer> dailyBossKills = new HashMap<>();
    /** Small persisted per-blessing state, such as a Waystem marker location. */
    private final Map<String, String> handlerData = new HashMap<>();

    public Clover(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    // --- Charge ---

    public int getCharge() {
        return charge;
    }

    public void setCharge(int charge) {
        this.charge = Math.max(0, charge);
    }

    public void addCharge(int amount) {
        this.charge += amount;
    }

    /**
     * Returns the number of currently active leaves (1–4) based on charge and permanence.
     */
    public int getActiveLeafCount(int leaf2Threshold, int leaf3Threshold, int leaf4Threshold) {
        if (charge >= leaf4Threshold) return 4;
        if (charge >= leaf3Threshold) return 3;
        if (leaf2Permanent || charge >= leaf2Threshold) return 2;
        return 1;
    }

    /**
     * Returns true if the given leaf slot is currently active (not wilted).
     */
    public boolean isLeafActive(LeafSlot slot, int leaf2Threshold, int leaf3Threshold, int leaf4Threshold) {
        return slot.getRequiredLeafCount() <= getActiveLeafCount(leaf2Threshold, leaf3Threshold, leaf4Threshold);
    }

    public boolean isLeaf2Permanent() {
        return leaf2Permanent;
    }

    public void setLeaf2Permanent(boolean permanent) {
        this.leaf2Permanent = permanent;
    }

    // --- Pollen ---

    public int getPollen() {
        return pollen;
    }

    public void setPollen(int pollen) {
        this.pollen = Math.max(0, pollen);
    }

    public void addPollen(int amount) {
        this.pollen += amount;
    }

    // --- Equipped blessings ---

    public Blessing getEquippedBlessing(LeafSlot slot) {
        return equippedBlessings.get(slot);
    }

    public Map<LeafSlot, Blessing> getEquippedBlessings() {
        return Collections.unmodifiableMap(equippedBlessings);
    }

    /**
     * Moves a blessing from the herbarium into a slot. Whatever was in the slot goes back to the herbarium.
     *
     * @return false if the blessing is not in the herbarium
     */
    public boolean equipFromHerbarium(LeafSlot slot, UUID blessingId) {
        Blessing blessing = herbarium.remove(blessingId);
        if (blessing == null) return false;
        Blessing previous = equippedBlessings.put(slot, blessing);
        if (previous != null) herbarium.put(previous.getId(), previous);
        return true;
    }

    /**
     * Moves the blessing in a slot back to the herbarium.
     *
     * @return the unequipped blessing, or null if the slot was empty
     */
    public Blessing unequip(LeafSlot slot) {
        Blessing removed = equippedBlessings.remove(slot);
        if (removed != null) herbarium.put(removed.getId(), removed);
        return removed;
    }

    /** Used by storage only: places a blessing directly into a slot. */
    public void putEquipped(LeafSlot slot, Blessing blessing) {
        equippedBlessings.put(slot, blessing);
    }

    // --- Herbarium ---

    public Map<UUID, Blessing> getHerbarium() {
        return Collections.unmodifiableMap(herbarium);
    }

    public void addToHerbarium(Blessing blessing) {
        herbarium.put(blessing.getId(), blessing);
    }

    /** Removes every blessing (equipped and stored) and the state they kept. */
    public void clearBlessings() {
        equippedBlessings.clear();
        herbarium.clear();
        cooldownExpiry.clear();
        handlerData.clear();
    }

    // --- Discovered recipes ---

    public boolean hasDiscoveredRecipe(String coreId) {
        return discoveredRecipes.getOrDefault(coreId, false);
    }

    public void discoverRecipe(String coreId) {
        discoveredRecipes.put(coreId, true);
    }

    public Map<String, Boolean> getDiscoveredRecipes() {
        return Collections.unmodifiableMap(discoveredRecipes);
    }

    // --- Combat timing ---

    public long getLastCombatMs() {
        return lastCombatMs;
    }

    public void setLastCombatMs(long ms) {
        this.lastCombatMs = ms;
    }

    // --- Cooldowns ---

    public long getCooldownExpiry(UUID blessingId) {
        return cooldownExpiry.getOrDefault(blessingId, 0L);
    }

    public void setCooldownExpiry(UUID blessingId, long expiryMs) {
        cooldownExpiry.put(blessingId, expiryMs);
    }

    /** Snapshot of unexpired cooldowns; expired entries are dropped. */
    public Map<UUID, Long> getCooldownExpiries(long nowMs) {
        cooldownExpiry.values().removeIf(expiry -> expiry <= nowMs);
        return Collections.unmodifiableMap(cooldownExpiry);
    }

    // --- Daily mob cap ---

    public int getDailyMobCharge() {
        return dailyMobCharge;
    }

    public void addDailyMobCharge(int amount) {
        this.dailyMobCharge += amount;
    }

    public long getDailyMobChargeResetDay() {
        return dailyMobChargeResetDay;
    }

    public void resetDailyMobCharge(long currentDay) {
        this.dailyMobCharge = 0;
        this.dailyMobChargeResetDay = currentDay;
    }

    // --- Daily boss kills ---

    public int getDailyBossKills(String bossType) {
        return dailyBossKills.getOrDefault(bossType, 0);
    }

    public void addDailyBossKill(String bossType) {
        dailyBossKills.merge(bossType, 1, Integer::sum);
    }

    public void setDailyBossKills(String bossType, int count) {
        dailyBossKills.put(bossType, count);
    }

    public void resetDailyBossKills() {
        dailyBossKills.clear();
    }

    public Map<String, Integer> getAllDailyBossKills() {
        return Collections.unmodifiableMap(dailyBossKills);
    }

    // --- Handler data ---

    public String getHandlerData(String key) {
        return handlerData.get(key);
    }

    public void setHandlerData(String key, String value) {
        if (value == null) handlerData.remove(key);
        else handlerData.put(key, value);
    }

    public Map<String, String> getAllHandlerData() {
        return Collections.unmodifiableMap(handlerData);
    }
}
