package net.cloversmp.cloverblessing.storage;

import com.google.gson.*;
import net.cloversmp.cloverblessing.CloverPlugin;
import net.cloversmp.cloverblessing.model.Blessing;
import net.cloversmp.cloverblessing.model.Clover;
import net.cloversmp.cloverblessing.model.Graft;
import net.cloversmp.cloverblessing.model.LeafSlot;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * JSON flat-file storage: {@code plugins/CloverBlessing/data/{uuid}.json}, one file per player.
 * <p>
 * Clovers are mutated on the main thread only. Saving builds the JSON snapshot on the main thread and
 * can write it from another thread; each write goes to a temp file first so a crash never leaves a
 * half-written file.
 */
public class DataManager {

    private final CloverPlugin plugin;
    private final Path dataFolder;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Map<UUID, Clover> cache = new ConcurrentHashMap<>();

    public DataManager(CloverPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder().toPath().resolve("data");
        try {
            Files.createDirectories(dataFolder);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create data directory", e);
        }
    }

    // --- Lifecycle ---

    public Clover loadClover(UUID uuid) {
        Path file = file(uuid);
        Clover clover = new Clover(uuid);
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                clover = deserialize(uuid, gson.fromJson(reader, JsonObject.class));
            } catch (Exception e) {
                // Keep the unreadable file for recovery instead of overwriting it on the next save.
                Path backup = dataFolder.resolve(uuid + ".corrupt-" + System.currentTimeMillis() + ".json");
                plugin.getLogger().log(Level.SEVERE, "Failed to load clover for " + uuid + "; moved to " + backup, e);
                try {
                    Files.move(file, backup);
                } catch (IOException moveError) {
                    plugin.getLogger().log(Level.SEVERE, "Could not back up " + file, moveError);
                }
            }
        }
        cache.put(uuid, clover);
        return clover;
    }

    /** Saves and drops a player's Clover from the cache (on quit). */
    public void unloadClover(UUID uuid) {
        Clover clover = cache.remove(uuid);
        if (clover != null) write(uuid, serialize(clover));
    }

    /** Saves every cached Clover. With {@code async}, snapshots now and writes off the main thread. */
    public void saveAll(boolean async) {
        Map<UUID, JsonObject> snapshots = new HashMap<>();
        cache.forEach((uuid, clover) -> snapshots.put(uuid, serialize(clover)));
        Runnable writeAll = () -> snapshots.forEach(this::writeIfLoaded);
        if (async) Bukkit.getScheduler().runTaskAsynchronously(plugin, writeAll);
        else writeAll.run();
    }

    public Clover getClover(UUID uuid) {
        return cache.get(uuid);
    }

    /**
     * Writes a snapshot only if the player is still cached. A player who quit was already saved by
     * {@link #unloadClover} with newer data, which this older snapshot must not overwrite.
     */
    private synchronized void writeIfLoaded(UUID uuid, JsonObject root) {
        if (cache.containsKey(uuid)) write(uuid, root);
    }

    private synchronized void write(UUID uuid, JsonObject root) {
        Path target = file(uuid);
        Path tmp = dataFolder.resolve(uuid + ".json.tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save clover for " + uuid, e);
        }
    }

    private Path file(UUID uuid) {
        return dataFolder.resolve(uuid + ".json");
    }

    // --- Serialization ---

    private JsonObject serialize(Clover c) {
        JsonObject root = new JsonObject();
        root.addProperty("uuid", c.getPlayerUuid().toString());
        root.addProperty("charge", c.getCharge());
        root.addProperty("leaf2Permanent", c.isLeaf2Permanent());
        root.addProperty("pollen", c.getPollen());
        root.addProperty("lastCombatMs", c.getLastCombatMs());
        root.addProperty("dailyMobCharge", c.getDailyMobCharge());
        root.addProperty("dailyMobChargeResetDay", c.getDailyMobChargeResetDay());

        JsonObject equipped = new JsonObject();
        c.getEquippedBlessings().forEach((slot, b) -> equipped.add(slot.name(), serializeBlessing(b)));
        root.add("equipped", equipped);

        JsonArray herbarium = new JsonArray();
        c.getHerbarium().values().forEach(b -> herbarium.add(serializeBlessing(b)));
        root.add("herbarium", herbarium);

        JsonObject cooldowns = new JsonObject();
        c.getCooldownExpiries(System.currentTimeMillis()).forEach((id, until) -> cooldowns.addProperty(id.toString(), until));
        root.add("cooldowns", cooldowns);

        root.add("discoveredRecipes", toJson(c.getDiscoveredRecipes()));
        root.add("dailyBossKills", toJson(c.getAllDailyBossKills()));
        root.add("handlerData", toJson(c.getAllHandlerData()));
        return root;
    }

    private JsonObject toJson(Map<String, ?> map) {
        return gson.toJsonTree(map).getAsJsonObject();
    }

    private JsonObject serializeBlessing(Blessing b) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", b.getId().toString());
        obj.addProperty("coreId", b.getCoreId());
        obj.addProperty("ownerUuid", b.getOwnerUuid().toString());
        obj.addProperty("bound", b.isBound());
        if (b.hasGraft()) obj.addProperty("graft", b.getGraft().name());
        return obj;
    }

    private Clover deserialize(UUID uuid, JsonObject root) {
        Clover c = new Clover(uuid);
        c.setCharge(getInt(root, "charge"));
        c.setLeaf2Permanent(root.has("leaf2Permanent") && root.get("leaf2Permanent").getAsBoolean());
        c.setPollen(getInt(root, "pollen"));
        if (root.has("lastCombatMs")) c.setLastCombatMs(root.get("lastCombatMs").getAsLong());
        if (root.has("dailyMobChargeResetDay")) c.resetDailyMobCharge(root.get("dailyMobChargeResetDay").getAsLong());
        c.addDailyMobCharge(getInt(root, "dailyMobCharge"));

        if (root.has("equipped")) {
            for (var e : root.getAsJsonObject("equipped").entrySet()) {
                try {
                    Blessing b = deserializeBlessing(e.getValue().getAsJsonObject());
                    if (b != null) c.putEquipped(LeafSlot.valueOf(e.getKey()), b);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Unknown leaf slot in data for " + uuid + ": " + e.getKey());
                }
            }
        }
        if (root.has("herbarium")) {
            for (JsonElement el : root.getAsJsonArray("herbarium")) {
                Blessing b = deserializeBlessing(el.getAsJsonObject());
                if (b != null) c.addToHerbarium(b);
            }
        }
        if (root.has("cooldowns")) {
            root.getAsJsonObject("cooldowns").entrySet().forEach(e ->
                    c.setCooldownExpiry(UUID.fromString(e.getKey()), e.getValue().getAsLong()));
        }
        if (root.has("discoveredRecipes")) {
            root.getAsJsonObject("discoveredRecipes").entrySet().forEach(e -> {
                if (e.getValue().getAsBoolean()) c.discoverRecipe(e.getKey());
            });
        }
        if (root.has("dailyBossKills")) {
            root.getAsJsonObject("dailyBossKills").entrySet().forEach(e ->
                    c.setDailyBossKills(e.getKey(), e.getValue().getAsInt()));
        }
        if (root.has("handlerData")) {
            root.getAsJsonObject("handlerData").entrySet().forEach(e ->
                    c.setHandlerData(e.getKey(), e.getValue().getAsString()));
        }
        return c;
    }

    private Blessing deserializeBlessing(JsonObject obj) {
        try {
            Graft graft = obj.has("graft") ? Graft.valueOf(obj.get("graft").getAsString()) : null;
            Blessing b = new Blessing(UUID.fromString(obj.get("id").getAsString()), obj.get("coreId").getAsString(),
                    graft, UUID.fromString(obj.get("ownerUuid").getAsString()));
            if (obj.has("bound") && obj.get("bound").getAsBoolean()) b.bind();
            return b;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Skipping unreadable blessing record: " + obj, e);
            return null;
        }
    }

    private static int getInt(JsonObject root, String key) {
        return root.has(key) ? root.get(key).getAsInt() : 0;
    }
}
