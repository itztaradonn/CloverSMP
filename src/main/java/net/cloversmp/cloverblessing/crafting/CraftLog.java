package net.cloversmp.cloverblessing.crafting;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Append-only audit trail of every blessing that is pressed, grafted, pruned, taken out, redeemed or voided,
 * and every recipe scrap used (concept doc section 7: "every press, trade and prune is logged").
 */
public class CraftLog {

    public enum Action { PRESS, GRAFT, PRUNE, TAKE_OUT, REDEEM, VOID, SCRAP }

    private final Plugin plugin;
    private final Path file;

    public CraftLog(Plugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("ledger.log");
    }

    public void log(Action action, String player, UUID blessingId, String coreId, String details) {
        String line = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS) + " | " + action + " | " + player
                + " | " + (blessingId == null ? "-" : blessingId) + " | " + (coreId == null ? "-" : coreId)
                + " | " + (details == null ? "" : details) + System.lineSeparator();
        if (Bukkit.isPrimaryThread() && plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> append(line));
        } else {
            append(line);
        }
    }

    private synchronized void append(String line) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not write ledger.log", e);
        }
    }
}
