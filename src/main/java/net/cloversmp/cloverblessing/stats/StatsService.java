package net.cloversmp.cloverblessing.stats;

import com.google.gson.Gson;
import net.cloversmp.cloverblessing.CloverPlugin;
import net.cloversmp.cloverblessing.charge.ChargeManager;
import net.cloversmp.cloverblessing.model.Blessing;
import net.cloversmp.cloverblessing.model.Clover;
import net.cloversmp.cloverblessing.model.LeafSlot;
import net.cloversmp.cloverblessing.storage.DataManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Feeds {@link BalanceStats} from play: a loadout snapshot of every online 4-leaf player every
 * {@code stats.snapshot-minutes}, and the killer's active blessings on every PvP kill by a 4-leaf player.
 * Saved to stats.json on each snapshot, on reset and on shutdown.
 * shortcut: kills between snapshots are lost on a crash; save per kill if that ever matters.
 */
public class StatsService implements Listener {

    private final CloverPlugin plugin;
    private final DataManager data;
    private final ChargeManager charge;
    private final Path file;
    private final Gson gson = new Gson();
    private BalanceStats stats = new BalanceStats();

    public StatsService(CloverPlugin plugin, DataManager data, ChargeManager charge) {
        this.plugin = plugin;
        this.data = data;
        this.charge = charge;
        this.file = plugin.getDataFolder().toPath().resolve("stats.json");
        if (Files.exists(file)) {
            try {
                stats = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), BalanceStats.class);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Could not read stats.json; starting fresh", e);
            }
        }
    }

    public void start() {
        long ticks = Math.max(1, plugin.getConfig().getLong("stats.snapshot-minutes", 10)) * 60 * 20;
        Bukkit.getScheduler().runTaskTimer(plugin, this::snapshot, ticks, ticks);
    }

    public BalanceStats stats() {
        return stats;
    }

    public void reset() {
        stats = new BalanceStats();
        save();
    }

    private void snapshot() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            List<String> loadout = fullCloverLoadout(p);
            if (loadout != null && !loadout.isEmpty()) stats.recordLoadout(loadout);
        }
        save();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || killer.equals(event.getEntity())) return;
        List<String> loadout = fullCloverLoadout(killer);
        if (loadout != null) stats.recordKill(loadout);
    }

    /** Active blessing and curse ids, or null unless the player has all four leaves open (the doc's rule). */
    private List<String> fullCloverLoadout(Player player) {
        Clover clover = data.getClover(player.getUniqueId());
        if (clover == null || charge.getLeafCount(clover) < 4) return null;
        List<String> ids = new ArrayList<>();
        for (LeafSlot slot : LeafSlot.values()) {
            Blessing b = clover.getEquippedBlessing(slot);
            if (b != null && charge.isLeafActive(clover, slot)) ids.add(b.getCoreId());
        }
        return ids;
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(stats), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save stats.json", e);
        }
    }
}
