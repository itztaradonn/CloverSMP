package net.cloversmp.cloverblessing.model;

import java.util.List;
import java.util.Set;

/**
 * Defines a blessing core — one standalone rule with a category, tags,
 * cooldown, and weakness text. This is the template; {@link Blessing}
 * is a pressed instance of a core.
 */
public class BlessingCore {

    private final String id;
    private final String displayName;
    private final BlessingCategory category;
    private final Set<BlessingTag> tags;
    private final String ruleText;
    private final String weaknessText;
    private final long baseCooldownTicks;
    private final boolean echoable;
    private final boolean rare;
    private final List<String> ingredientHints;

    public BlessingCore(String id, String displayName, BlessingCategory category,
                        Set<BlessingTag> tags, String ruleText, String weaknessText,
                        long baseCooldownTicks, boolean echoable, boolean rare,
                        List<String> ingredientHints) {
        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.tags = Set.copyOf(tags);
        this.ruleText = ruleText;
        this.weaknessText = weaknessText;
        this.baseCooldownTicks = baseCooldownTicks;
        this.echoable = echoable;
        this.rare = rare;
        this.ingredientHints = List.copyOf(ingredientHints);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public BlessingCategory getCategory() {
        return category;
    }

    /** Curses go only in the curse slot (leaf 5); blessings only in leaves 1–4. */
    public boolean isCurse() {
        return category == BlessingCategory.CURSE;
    }

    public Set<BlessingTag> getTags() {
        return tags;
    }

    /**
     * The rule text — about 25 words describing trigger, effect, cooldown.
     */
    public String getRuleText() {
        return ruleText;
    }

    /**
     * The stated weakness and counterplay text.
     */
    public String getWeaknessText() {
        return weaknessText;
    }

    /**
     * Base cooldown in ticks (20 ticks = 1 second) before graft modifiers.
     */
    public long getBaseCooldownTicks() {
        return baseCooldownTicks;
    }

    /**
     * Whether Echo Leaf can copy this blessing.
     */
    public boolean isEchoable() {
        return echoable;
    }

    /**
     * Whether this is a rare core found in structures/boss rewards.
     */
    public boolean isRare() {
        return rare;
    }

    /**
     * Ingredient family hints for the Field Guide silhouettes.
     */
    public List<String> getIngredientHints() {
        return ingredientHints;
    }
}
