package net.cloversmp.cloverblessing.listener;

import net.cloversmp.cloverblessing.CloverPlugin;
import net.cloversmp.cloverblessing.storage.DataManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player data lifecycle — loading Clover data on join and saving on quit.
 * The actual I/O is delegated to {@link DataManager}.
 */
public class PlayerDataListener implements Listener {

    private final CloverPlugin plugin;
    private final DataManager dataManager;

    public PlayerDataListener(CloverPlugin plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    /**
     * Loads the player's Clover data from storage when they join the server.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        dataManager.loadClover(event.getPlayer().getUniqueId());
    }

    /**
     * Saves the player's Clover and drops it from the cache when they leave the server.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        dataManager.unloadClover(event.getPlayer().getUniqueId());
    }
}
