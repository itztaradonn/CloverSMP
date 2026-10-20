package net.cloversmp.cloverblessing.crafting;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.function.Predicate;

/**
 * Pressing recipes, loaded from {@code recipes.yml}. Each recipe is a Clover Seed plus up to three ingredient
 * materials; matching is order-independent and exact.
 */
public class RecipeRegistry {

    public static final int MAX_INGREDIENTS = 3;
    /** Material#isAir needs the server registry, so compare directly; this keeps the class unit-testable. */
    private static final Set<Material> AIR = EnumSet.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR);

    public record Recipe(String coreId, List<Material> ingredients, boolean rare) {
        public Recipe {
            ingredients = List.copyOf(ingredients);
        }
    }

    private final Map<String, Recipe> byCore = new LinkedHashMap<>();

    /**
     * Replaces all recipes with those in {@code section} (keyed by core id). Invalid entries are skipped.
     *
     * @param isItem true for materials that exist as items ({@code Material::isItem} on a server; it needs the
     *               server registry, so tests pass their own check)
     * @return one human-readable error per skipped entry (empty when everything loaded)
     */
    public List<String> load(ConfigurationSection section, Set<String> knownCores, Predicate<Material> isItem) {
        byCore.clear();
        List<String> errors = new ArrayList<>();
        if (section == null) {
            errors.add("recipes.yml has no 'recipes' section");
            return errors;
        }
        Map<Map<Material, Integer>, String> seen = new HashMap<>();
        for (String coreId : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(coreId);
            List<String> names = entry == null ? List.of() : entry.getStringList("ingredients");
            String error = null;
            List<Material> ingredients = new ArrayList<>();
            if (!knownCores.contains(coreId)) error = "unknown blessing '" + coreId + "'";
            else if (names.isEmpty() || names.size() > MAX_INGREDIENTS) error = "needs 1-" + MAX_INGREDIENTS + " ingredients";
            else {
                for (String name : names) {
                    Material m = Material.matchMaterial(name);
                    if (m == null || AIR.contains(m) || !isItem.test(m)) {
                        error = "unknown item '" + name + "'";
                        break;
                    }
                    ingredients.add(m);
                }
            }
            if (error == null) {
                String clash = seen.putIfAbsent(counts(ingredients), coreId);
                if (clash != null) error = "same ingredients as '" + clash + "'";
            }
            if (error != null) {
                errors.add("Recipe '" + coreId + "' skipped: " + error);
                continue;
            }
            byCore.put(coreId, new Recipe(coreId, ingredients, entry.getBoolean("rare", false)));
        }
        return errors;
    }

    /** The recipe whose ingredients exactly match {@code provided} (nulls and air ignored), or null. */
    public Recipe match(List<Material> provided) {
        List<Material> items = new ArrayList<>();
        for (Material m : provided) if (m != null && !AIR.contains(m)) items.add(m);
        if (items.isEmpty()) return null;
        Map<Material, Integer> want = counts(items);
        for (Recipe r : byCore.values()) {
            if (counts(r.ingredients()).equals(want)) return r;
        }
        return null;
    }

    public Recipe getRecipe(String coreId) {
        return byCore.get(coreId);
    }

    public Collection<Recipe> all() {
        return Collections.unmodifiableCollection(byCore.values());
    }

    public List<Recipe> rare() {
        return byCore.values().stream().filter(Recipe::rare).toList();
    }

    private static Map<Material, Integer> counts(List<Material> materials) {
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        for (Material m : materials) counts.merge(m, 1, Integer::sum);
        return counts;
    }
}
