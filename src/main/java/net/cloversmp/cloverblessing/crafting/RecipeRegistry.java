package net.cloversmp.cloverblessing.crafting;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Defines and resolves all blessing pressing recipes.
 * Each recipe maps a set of ingredients (plus a Clover Seed) to a blessing core id.
 */
public class RecipeRegistry {

    /**
     * A single pressing recipe entry.
     *
     * @param coreId            the blessing core id produced by this recipe
     * @param ingredients       required ingredient materials (order-independent)
     * @param requiresCloverSeed whether the recipe needs a Clover Seed (always true for defaults)
     */
    public record RecipeEntry(String coreId, List<Material> ingredients, boolean requiresCloverSeed) {
        public RecipeEntry {
            ingredients = List.copyOf(ingredients);
        }
    }

    private final Map<String, RecipeEntry> byCoreId = new LinkedHashMap<>();
    private final List<RecipeEntry> allRecipes = new ArrayList<>();

    /**
     * Registers a new recipe.
     */
    public void register(RecipeEntry entry) {
        byCoreId.put(entry.coreId(), entry);
        allRecipes.add(entry);
    }

    /**
     * Returns the recipe for a given core id, or null if none.
     */
    public RecipeEntry getRecipe(String coreId) {
        return byCoreId.get(coreId);
    }

    /**
     * Returns all registered recipes (unmodifiable).
     */
    public List<RecipeEntry> getAllRecipes() {
        return Collections.unmodifiableList(allRecipes);
    }

    /**
     * Attempts to match a set of ingredient items to a registered recipe.
     * The match is order-independent — it compares material type counts.
     *
     * @param ingredients the player-placed ingredient items (non-seed slots)
     * @return the matching core id, or null if no recipe matches
     */
    public String findMatch(List<ItemStack> ingredients) {
        // Build a frequency map of the provided materials (skip air / null)
        Map<Material, Integer> provided = new EnumMap<>(Material.class);
        for (ItemStack stack : ingredients) {
            if (stack == null || stack.getType() == Material.AIR) continue;
            provided.merge(stack.getType(), 1, Integer::sum);
        }

        for (RecipeEntry entry : allRecipes) {
            Map<Material, Integer> required = new EnumMap<>(Material.class);
            for (Material mat : entry.ingredients()) {
                required.merge(mat, 1, Integer::sum);
            }
            if (required.equals(provided)) {
                return entry.coreId();
            }
        }
        return null;
    }

    /**
     * Registers the default recipes for all 16 blessing cores.
     * Every recipe requires a Clover Seed plus up to 3 additional ingredients.
     */
    public void registerDefaults() {
        // Offense
        register(new RecipeEntry("thornlash",
                List.of(Material.ROSE_BUSH, Material.CACTUS, Material.IRON_INGOT), true));
        register(new RecipeEntry("stinger",
                List.of(Material.ARROW, Material.SPIDER_EYE, Material.STRING), true));

        // Defense
        register(new RecipeEntry("mossguard",
                List.of(Material.MOSS_BLOCK, Material.IRON_INGOT, Material.LEATHER), true));
        register(new RecipeEntry("last_leaf",
                List.of(Material.GOLDEN_APPLE, Material.TOTEM_OF_UNDYING), true));

        // Mobility
        register(new RecipeEntry("springleaf",
                List.of(Material.SLIME_BALL, Material.FEATHER, Material.SUGAR), true));
        register(new RecipeEntry("fieldwalker",
                List.of(Material.GRASS_BLOCK, Material.DANDELION, Material.LEATHER_BOOTS), true));

        // Recovery
        register(new RecipeEntry("quiet_dew",
                List.of(Material.FERN, Material.GLISTERING_MELON_SLICE), true));
        register(new RecipeEntry("leechvine",
                List.of(Material.VINE, Material.SPIDER_EYE, Material.GHAST_TEAR), true));

        // Crowd Control
        register(new RecipeEntry("tangle_seed",
                List.of(Material.VINE, Material.COBWEB, Material.ARROW), true));
        register(new RecipeEntry("hobble",
                List.of(Material.IRON_CHAIN, Material.ANVIL, Material.CACTUS), true));

        // Utility
        register(new RecipeEntry("waystem",
                List.of(Material.COMPASS, Material.ENDER_PEARL), true));
        register(new RecipeEntry("lanternleaf",
                List.of(Material.LANTERN, Material.GLOW_BERRIES, Material.GLOWSTONE_DUST), true));

        // Risk & Reward
        register(new RecipeEntry("glass_bloom",
                List.of(Material.GLASS, Material.BLAZE_POWDER, Material.DIAMOND), true));
        register(new RecipeEntry("wager_root",
                List.of(Material.GOLD_BLOCK, Material.EMERALD, Material.NETHER_STAR), true));

        // Experimental
        register(new RecipeEntry("echo_leaf",
                List.of(Material.ECHO_SHARD, Material.AMETHYST_SHARD, Material.SCULK), true));
        register(new RecipeEntry("turning_year",
                List.of(Material.CLOCK, Material.POPPY, Material.BLUE_ORCHID, Material.ALLIUM), true));
    }
}
