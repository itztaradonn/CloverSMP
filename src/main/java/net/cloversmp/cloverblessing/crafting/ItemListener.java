package net.cloversmp.cloverblessing.crafting;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Right-click use of plugin items, and a guard so tagged items can't be spent as the vanilla paper they're made of.
 */
public class ItemListener implements Listener {

    private final CloverItems items;
    private final ClaimService claims;
    private final NamespacedKey seedRecipe;

    public ItemListener(CloverItems items, ClaimService claims, NamespacedKey seedRecipe) {
        this.items = items;
        this.claims = claims;
        this.seedRecipe = seedRecipe;
    }

    /** Shows the Clover Seed recipe in the recipe book. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (seedRecipe != null) event.getPlayer().discoverRecipe(seedRecipe);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack hand = event.getItem();
        CloverItems.Kind kind = items.kind(hand);
        if (kind == null) return;
        // A seed on a Fletching Table is left alone so the Pressing Table opens as usual.
        if (kind == CloverItems.Kind.SEED && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.FLETCHING_TABLE) return;
        event.setCancelled(true);
        switch (kind) {
            case CLAIM -> claims.redeem(event.getPlayer(), hand);
            case SCRAP -> claims.learnScrap(event.getPlayer(), hand);
            case SEED -> event.getPlayer().sendMessage(Component.text(
                    "Use Clover Seeds at a Pressing Table (a Fletching Table).", NamedTextColor.YELLOW));
        }
        // Write the used-up stack back explicitly rather than relying on getItem() being a live mirror.
        if (event.getHand() != null && kind != CloverItems.Kind.SEED) {
            event.getPlayer().getInventory().setItem(event.getHand(), hand.getAmount() > 0 ? hand : null);
        }
    }

    /** No vanilla crafting may consume a tagged item. */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack i : event.getInventory().getMatrix()) {
            if (items.kind(i) != null) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    /** Cartography tables also take paper. */
    @EventHandler
    public void onPrepareResult(PrepareResultEvent event) {
        if (event.getInventory().getType() != InventoryType.CARTOGRAPHY) return;
        for (ItemStack i : event.getInventory().getContents()) {
            if (items.kind(i) != null) {
                event.setResult(null);
                return;
            }
        }
    }
}
