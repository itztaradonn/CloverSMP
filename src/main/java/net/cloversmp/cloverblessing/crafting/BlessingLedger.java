package net.cloversmp.cloverblessing.crafting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.cloversmp.cloverblessing.model.Blessing;
import net.cloversmp.cloverblessing.model.Clover;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Blessings that currently exist as claim items (blessing id → core id).
 * <p>
 * A blessing is always in exactly one place: a player's herbarium/leaves, or this ledger. A claim item only
 * carries the blessing id; redeeming removes the id here, so any duplicated copy of the item finds nothing
 * and is void. Only unbound (ungrafted) blessings can be out as items, so the core id is all that's needed.
 * Every change is written to disk immediately so a crash can never lose or double a blessing.
 */
public class BlessingLedger {

    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, String> outAsItems = new LinkedHashMap<>();

    public BlessingLedger(Path file) {
        this.file = file;
        if (Files.exists(file)) {
            try {
                Map<String, String> raw = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                        new TypeToken<Map<String, String>>() {}.getType());
                if (raw != null) raw.forEach((id, core) -> outAsItems.put(UUID.fromString(id), core));
            } catch (IOException e) {
                // Refuse to start with an unreadable ledger: starting empty would void every claim item.
                throw new UncheckedIOException("Cannot read " + file, e);
            }
        }
    }

    /**
     * Takes an unbound, unequipped blessing out of the herbarium so it can become a claim item.
     *
     * @return the blessing taken out, or null if it isn't in the herbarium or is bound
     */
    public Blessing takeOut(Clover clover, UUID blessingId) {
        Blessing b = clover.getHerbarium().get(blessingId);
        if (b == null || b.isBound()) return null;
        clover.removeFromHerbarium(blessingId);
        outAsItems.put(blessingId, b.getCoreId());
        save();
        return b;
    }

    /**
     * Redeems a claim into {@code redeemer}'s herbarium; they become the owner.
     *
     * @return the redeemed blessing, or null if the id isn't out as an item (duplicate or already used)
     */
    public Blessing redeem(UUID blessingId, Clover redeemer) {
        String coreId = outAsItems.remove(blessingId);
        if (coreId == null) return null;
        save();
        Blessing b = new Blessing(blessingId, coreId, null, redeemer.getPlayerUuid());
        redeemer.addToHerbarium(b);
        return b;
    }

    public boolean isOut(UUID blessingId) {
        return outAsItems.containsKey(blessingId);
    }

    private void save() {
        Map<String, String> raw = new LinkedHashMap<>();
        outAsItems.forEach((id, core) -> raw.put(id.toString(), core));
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(tmp, gson.toJson(raw), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save " + file, e);
        }
    }
}
