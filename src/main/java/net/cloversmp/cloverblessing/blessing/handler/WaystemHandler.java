package net.cloversmp.cloverblessing.blessing.handler;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.cloversmp.cloverblessing.CloverPlugin;
import net.cloversmp.cloverblessing.model.BlessingCategory;
import net.cloversmp.cloverblessing.model.BlessingCore;
import net.cloversmp.cloverblessing.model.Clover;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plant a marker (sneak + right-click a block with an empty hand); recall to it with a 3 s channel
 * (sneak + swap-hand). The marker is visible and anyone can break it by hitting it. 5-minute cooldown.
 * <p>
 * The marker location is saved on the player's Clover. Its entities exist only while the owner is
 * online and the chunk is loaded, and are never saved with the world.
 */
public class WaystemHandler implements BlessingHandler, Listener {

    private static final String DATA_KEY = "waystem";
    private static final int CHANNEL_TICKS = 60;
    private static final double MAX_DRIFT_SQ = 1.5 * 1.5;

    private final CloverPlugin plugin;
    private final NamespacedKey ownerKey;

    private record Marker(UUID interaction, UUID display) {}

    private final Map<UUID, Marker> markers = new ConcurrentHashMap<>();
    private final Set<UUID> channeling = ConcurrentHashMap.newKeySet();

    public WaystemHandler(CloverPlugin plugin) {
        this.plugin = plugin;
        this.ownerKey = new NamespacedKey(plugin, "waystem_owner");
    }

    @Override
    public BlessingCore createCore() {
        return new BlessingCore("waystem", "Waystem", BlessingCategory.UTILITY, Set.of(),
                "Sneak + right-click a block to plant a marker; sneak + swap-hand to channel 3s and return to it. 5-minute cooldown.",
                "The channel cancels on damage or moving, and anyone can break the marker.",
                300 * 20, false, false, List.of("compass", "pearl"));
    }

    // --- Plant ---

    @Override
    public boolean onInteractBlock(BlessingContext ctx, PlayerInteractEvent event) {
        Player p = ctx.player();
        if (!p.isSneaking() || !p.getInventory().getItemInMainHand().getType().isAir() || event.getClickedBlock() == null) {
            return false;
        }
        Block target = event.getClickedBlock().getRelative(event.getBlockFace());
        if (!target.isPassable()) return false;

        event.setCancelled(true);
        Location loc = target.getLocation().add(0.5, 0, 0.5);
        despawn(p.getUniqueId());
        ctx.clover().setHandlerData(DATA_KEY, serialize(loc));
        spawn(p.getUniqueId(), loc);
        p.sendMessage(Component.text("Waystem planted.", NamedTextColor.GREEN));
        p.playSound(loc, Sound.BLOCK_AZALEA_PLACE, 1f, 1f);
        return false; // planting is free; the cooldown starts on recall
    }

    // --- Recall ---

    @Override
    public boolean onSwapHand(BlessingContext ctx, PlayerSwapHandItemsEvent event) {
        Player p = ctx.player();
        if (!p.isSneaking()) return false;
        event.setCancelled(true);
        Location marker = deserialize(ctx.clover().getHandlerData(DATA_KEY));
        if (marker == null) {
            p.sendMessage(Component.text("You have no Waystem marker.", NamedTextColor.RED));
            return false;
        }
        if (!channeling.add(p.getUniqueId())) return false;

        Location start = p.getLocation();
        long startMs = System.currentTimeMillis();
        p.sendMessage(Component.text("Returning to your Waystem...", NamedTextColor.AQUA));
        int[] ticks = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            boolean interrupted = !p.isOnline()
                    || ctx.engine().lastDamageTakenMs(p.getUniqueId()) > startMs
                    || !p.getWorld().equals(start.getWorld())
                    || p.getLocation().distanceSquared(start) > MAX_DRIFT_SQ
                    || ctx.clover().getHandlerData(DATA_KEY) == null;
            if (interrupted) {
                channeling.remove(p.getUniqueId());
                task.cancel();
                if (p.isOnline()) p.sendMessage(Component.text("Recall interrupted.", NamedTextColor.RED));
                return;
            }
            p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 10, 0.4, 0.6, 0.4, 0.2);
            ticks[0] += 5;
            if (ticks[0] >= CHANNEL_TICKS) {
                channeling.remove(p.getUniqueId());
                task.cancel();
                Location dest = marker.clone();
                dest.setYaw(p.getLocation().getYaw());
                dest.setPitch(p.getLocation().getPitch());
                p.teleport(dest);
                p.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
                ctx.fire();
            }
        }, 5L, 5L);
        return false;
    }

    // --- Marker entities ---

    /** Keeps the marker visible while the owner is online and the chunk is loaded. */
    @Override
    public boolean onSecond(BlessingContext ctx, StatSink stats) {
        UUID owner = ctx.player().getUniqueId();
        Location loc = deserialize(ctx.clover().getHandlerData(DATA_KEY));
        if (loc == null || !loc.isChunkLoaded()) return false;
        Marker m = markers.get(owner);
        if (m == null || Bukkit.getEntity(m.interaction()) == null) spawn(owner, loc);
        loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 1.2, 0), 1, 0.1, 0.2, 0.1, 0);
        return false;
    }

    private void spawn(UUID owner, Location loc) {
        despawn(owner);
        Interaction hitbox = loc.getWorld().spawn(loc, Interaction.class, e -> {
            e.setPersistent(false);
            e.setInteractionWidth(0.8f);
            e.setInteractionHeight(1.2f);
            e.setResponsive(true);
            e.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.toString());
        });
        ItemDisplay display = loc.getWorld().spawn(loc.clone().add(0, 0.5, 0), ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setItemStack(new ItemStack(Material.TORCHFLOWER));
            e.setBillboard(Display.Billboard.VERTICAL);
        });
        markers.put(owner, new Marker(hitbox.getUniqueId(), display.getUniqueId()));
    }

    private void despawn(UUID owner) {
        Marker m = markers.remove(owner);
        if (m == null) return;
        Entity a = Bukkit.getEntity(m.interaction());
        Entity b = Bukkit.getEntity(m.display());
        if (a != null) a.remove();
        if (b != null) b.remove();
    }

    /** Anyone can break a marker by hitting it. */
    @EventHandler(ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        if (!(event.getAttacked() instanceof Interaction hitbox)) return;
        String raw = hitbox.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (raw == null) return;
        event.setCancelled(true);
        UUID owner = UUID.fromString(raw);
        despawn(owner);
        hitbox.remove(); // in case it was orphaned
        Clover clover = plugin.getDataManager().getClover(owner);
        if (clover != null) clover.setHandlerData(DATA_KEY, null);
        hitbox.getWorld().playSound(hitbox.getLocation(), Sound.BLOCK_AZALEA_BREAK, 1f, 0.8f);
        Player ownerPlayer = Bukkit.getPlayer(owner);
        if (ownerPlayer != null && !ownerPlayer.equals(event.getPlayer())) {
            ownerPlayer.sendMessage(Component.text("Your Waystem marker was broken!", NamedTextColor.RED));
        }
    }

    @Override
    public void clear(UUID player) {
        despawn(player);
        channeling.remove(player);
    }

    // --- Location storage ---

    private static String serialize(Location l) {
        return l.getWorld().getName() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ();
    }

    private static Location deserialize(String s) {
        if (s == null) return null;
        String[] parts = s.split(";");
        World w = parts.length == 4 ? Bukkit.getWorld(parts[0]) : null;
        if (w == null) return null;
        try {
            return new Location(w, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
