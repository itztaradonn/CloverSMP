package net.cloversmp.cloverblessing.crafting;

import net.cloversmp.cloverblessing.blessing.BlessingRegistry;
import net.cloversmp.cloverblessing.model.BlessingCore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Recipe scraps for rare cores: a chance in configured structure chests ({@code scraps.loot-tables}) and from
 * bosses killed by a player ({@code scraps.bosses}).
 */
public class ScrapListener implements Listener {

    private final Plugin plugin;
    private final BlessingRegistry registry;
    private final RecipeRegistry recipes;
    private final CloverItems items;

    public ScrapListener(Plugin plugin, BlessingRegistry registry, RecipeRegistry recipes, CloverItems items) {
        this.plugin = plugin;
        this.registry = registry;
        this.recipes = recipes;
        this.items = items;
    }

    @EventHandler(ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        double chance = chance("scraps.loot-tables", event.getLootTable().getKey().getKey());
        if (roll(chance)) randomScrap().ifPresent(event.getLoot()::add);
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        if (roll(chance("scraps.bosses", event.getEntityType().name()))) randomScrap().ifPresent(event.getDrops()::add);
    }

    private double chance(String section, String key) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection(section);
        return s == null ? 0 : s.getDouble(key, 0);
    }

    private static boolean roll(double chance) {
        return chance > 0 && ThreadLocalRandom.current().nextDouble() < chance;
    }

    private java.util.Optional<ItemStack> randomScrap() {
        List<RecipeRegistry.Recipe> rare = recipes.rare();
        if (rare.isEmpty()) return java.util.Optional.empty();
        BlessingCore core = registry.getCore(rare.get(ThreadLocalRandom.current().nextInt(rare.size())).coreId());
        return core == null ? java.util.Optional.empty() : java.util.Optional.of(items.scrap(core));
    }
}
