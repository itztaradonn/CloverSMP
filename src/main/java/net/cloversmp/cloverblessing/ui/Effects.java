package net.cloversmp.cloverblessing.ui;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sound and particle feedback. Sounds use the plugin's own names ({@code cloverblessing:<name>}); the resource pack's
 * sounds.json maps each to a custom .ogg or a vanilla placeholder (tools/build_sounds.py). Everything plays in the
 * world, so nearby players see and hear it: readable counterplay, as the concept doc asks.
 */
public final class Effects {

    private static final Map<String, Long> LAST = new ConcurrentHashMap<>();

    private Effects() {}

    /** Plays {@code cloverblessing:<name>} at a location for everyone nearby. */
    public static void sound(Location at, String name, float volume, float pitch) {
        at.getWorld().playSound(at, "cloverblessing:" + name, SoundCategory.PLAYERS, volume, pitch);
    }

    public static void sound(Entity at, String name) {
        sound(at.getLocation(), name, 1f, 1f);
    }

    /** Plays a sound only this player hears (UI feedback). */
    public static void privateSound(Player player, String name, float volume) {
        player.playSound(player, "cloverblessing:" + name, SoundCategory.PLAYERS, volume, 1f);
    }

    public static void particles(Location at, Particle particle, int count, double spread) {
        at.getWorld().spawnParticle(particle, at, count, spread, spread, spread, 0);
    }

    public static void dust(Location at, Color color, int count, double spread) {
        at.getWorld().spawnParticle(Particle.DUST, at, count, spread, spread, spread, 0, new Particle.DustOptions(color, 1.2f));
    }

    /** Point on the entity's body, {@code fraction} of its height up. */
    public static Location body(Entity e, double fraction) {
        return e.getLocation().add(0, e.getHeight() * fraction, 0);
    }

    /** A particle line from one point to another (e.g. Leechvine draining a target). */
    public static void stream(Location from, Location to, Color color) {
        var step = to.toVector().subtract(from.toVector());
        for (int i = 0; i <= 8; i++) {
            dust(from.clone().add(step.clone().multiply(i / 8.0)), color, 1, 0.02);
        }
    }

    /** True at most once per {@code ms} for this player and key; for effects tied to frequent events. */
    public static boolean every(UUID player, String key, long ms) {
        long now = System.currentTimeMillis();
        String k = player + key;
        Long last = LAST.get(k);
        if (last != null && now - last < ms) return false;
        LAST.put(k, now);
        return true;
    }

    public static void clear(UUID player) {
        LAST.keySet().removeIf(k -> k.startsWith(player.toString()));
    }
}
