package net.cloversmp.cloverblessing.crafting;

import net.cloversmp.cloverblessing.blessing.BlessingRegistry;
import net.cloversmp.cloverblessing.model.Blessing;
import net.cloversmp.cloverblessing.model.BlessingCore;
import net.cloversmp.cloverblessing.model.Clover;
import net.cloversmp.cloverblessing.storage.DataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Moves blessings between herbaria and claim items, and teaches recipes from scraps.
 * <p>
 * After every ledger change the player's Clover and inventory are saved immediately: the ledger, the Clover
 * file and the inventory are stored separately, and a crash between those saves could otherwise lose or
 * double a blessing.
 */
public class ClaimService {

    private final DataManager data;
    private final BlessingRegistry registry;
    private final BlessingLedger ledger;
    private final CloverItems items;
    private final CraftLog log;

    public ClaimService(DataManager data, BlessingRegistry registry, BlessingLedger ledger, CloverItems items, CraftLog log) {
        this.data = data;
        this.registry = registry;
        this.ledger = ledger;
        this.items = items;
        this.log = log;
    }

    /** Turns an unbound herbarium blessing into a claim item in the player's inventory. */
    public boolean takeOut(Player player, UUID blessingId) {
        Clover clover = data.getClover(player.getUniqueId());
        Blessing b = clover == null ? null : clover.getHerbarium().get(blessingId);
        BlessingCore core = b == null ? null : registry.getCore(b.getCoreId());
        if (core == null) return false;
        if (b.isBound()) return deny(player, "Grafted blessings are bound to you and can't be taken out.");
        if (player.getInventory().firstEmpty() == -1) return deny(player, "Your inventory is full.");

        ledger.takeOut(clover, blessingId);
        player.getInventory().addItem(items.claim(b, core));
        save(player);
        log.log(CraftLog.Action.TAKE_OUT, player.getName(), blessingId, b.getCoreId(), null);
        player.sendMessage(Component.text("Took out " + core.getDisplayName() + " as an item.", NamedTextColor.GREEN));
        return true;
    }

    /** Redeems the claim item in {@code hand}. A claim whose blessing is no longer out is void and removed. */
    public void redeem(Player player, ItemStack hand) {
        Clover clover = data.getClover(player.getUniqueId());
        UUID id = items.claimId(hand);
        if (clover == null) return;
        Blessing b = id == null ? null : ledger.redeem(id, clover);
        hand.setAmount(0); // a valid claim is used up; a void one (duplicate or already used) is destroyed
        if (b == null) {
            log.log(CraftLog.Action.VOID, player.getName(), id, null, "claim not in ledger");
            player.sendMessage(Component.text("This blessing claim is void: it was already used.", NamedTextColor.RED));
            return;
        }
        save(player);
        BlessingCore core = registry.getCore(b.getCoreId());
        log.log(CraftLog.Action.REDEEM, player.getName(), id, b.getCoreId(), null);
        player.playSound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        player.sendMessage(Component.text((core != null ? core.getDisplayName() : b.getCoreId())
                + " added to your herbarium.", NamedTextColor.GREEN));
    }

    /** Learns the rare recipe on a scrap; the scrap is used up either way. */
    public void learnScrap(Player player, ItemStack hand) {
        Clover clover = data.getClover(player.getUniqueId());
        BlessingCore core = registry.getCore(items.data(hand));
        if (clover == null || core == null) return;
        hand.setAmount(hand.getAmount() - 1);
        if (clover.hasDiscoveredRecipe(core.getId())) {
            player.sendMessage(Component.text("You already know " + core.getDisplayName() + ". The scrap crumbles.",
                    NamedTextColor.GRAY));
            return;
        }
        clover.discoverRecipe(core.getId());
        save(player);
        log.log(CraftLog.Action.SCRAP, player.getName(), null, core.getId(), "learned");
        player.playSound(player, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
        player.sendMessage(Component.text("You learned how to press " + core.getDisplayName() + ".", NamedTextColor.LIGHT_PURPLE));
    }

    private void save(Player player) {
        data.saveNow(player.getUniqueId());
        player.saveData();
    }

    private static boolean deny(Player player, String reason) {
        player.sendMessage(Component.text(reason, NamedTextColor.RED));
        return false;
    }
}
