package net.cloversmp.cloverblessing;

import net.cloversmp.cloverblessing.crafting.BlessingLedger;
import net.cloversmp.cloverblessing.crafting.GraftRules;
import net.cloversmp.cloverblessing.crafting.RecipeRegistry;
import net.cloversmp.cloverblessing.model.Blessing;
import net.cloversmp.cloverblessing.model.Clover;
import net.cloversmp.cloverblessing.model.Graft;
import net.cloversmp.cloverblessing.model.LeafSlot;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CraftingTest {

    private static final Set<String> CORES = Set.of("thornlash", "stinger", "mossguard", "last_leaf", "springleaf",
            "fieldwalker", "quiet_dew", "leechvine", "tangle_seed", "hobble", "waystem", "lanternleaf", "glass_bloom",
            "wager_root", "echo_leaf", "turning_year", "bloodroot", "thorncrown", "hollow_bell", "hungering_clover");

    // --- Recipes ---

    @Test
    void bundledRecipesAreValidUniqueAndCoverEveryCore() {
        var yaml = YamlConfiguration.loadConfiguration(new File("src/main/resources/recipes.yml"));
        var registry = new RecipeRegistry();
        List<String> errors = registry.load(yaml.getConfigurationSection("recipes"), CORES, m -> true);
        assertEquals(List.of(), errors);
        assertEquals(CORES.size(), registry.all().size());
        assertEquals(Set.of("echo_leaf", "turning_year"),
                registry.rare().stream().map(RecipeRegistry.Recipe::coreId).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void matchingIsOrderIndependentAndExact() {
        var registry = registryOf("""
                recipes:
                  thornlash: { ingredients: [ROSE_BUSH, CACTUS, IRON_INGOT] }
                  stinger:   { ingredients: [ARROW, ARROW] }
                """);
        assertEquals("thornlash", registry.match(Arrays.asList(Material.IRON_INGOT, null, Material.ROSE_BUSH, Material.CACTUS)).coreId());
        assertEquals("stinger", registry.match(List.of(Material.ARROW, Material.ARROW)).coreId());
        assertNull(registry.match(List.of(Material.ARROW)));                       // too few
        assertNull(registry.match(List.of(Material.ROSE_BUSH, Material.CACTUS))); // missing one
        assertNull(registry.match(List.of()));
    }

    @Test
    void invalidEntriesAreReportedAndSkipped() {
        var registry = new RecipeRegistry();
        var yaml = new YamlConfiguration();
        try {
            yaml.loadFromString("""
                    recipes:
                      thornlash: { ingredients: [ROSE_BUSH, CACTUS] }
                      stinger:   { ingredients: [CACTUS, ROSE_BUSH] }
                      nope:      { ingredients: [DIRT] }
                      hobble:    { ingredients: [NOT_A_BLOCK] }
                      waystem:   { ingredients: [DIRT, DIRT, DIRT, DIRT] }
                      mossguard: { ingredients: [] }
                    """);
        } catch (Exception e) {
            fail(e);
        }
        List<String> errors = registry.load(yaml.getConfigurationSection("recipes"), CORES, m -> true);
        assertEquals(5, errors.size(), errors.toString());
        assertEquals(1, registry.all().size()); // only thornlash survives
    }

    private static RecipeRegistry registryOf(String text) {
        var yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (Exception e) {
            fail(e);
        }
        var registry = new RecipeRegistry();
        assertEquals(List.of(), registry.load(yaml.getConfigurationSection("recipes"), CORES, m -> true));
        return registry;
    }

    // --- Ledger ---

    @Test
    void claimRedeemsOnceThenVoids(@TempDir Path dir) {
        var ledger = new BlessingLedger(dir.resolve("ledger.json"));
        var alice = new Clover(UUID.randomUUID());
        var bob = new Clover(UUID.randomUUID());
        var core = new Blessing(UUID.randomUUID(), "thornlash", null, alice.getPlayerUuid());
        alice.addToHerbarium(core);

        assertNotNull(ledger.takeOut(alice, core.getId()));
        assertFalse(alice.getHerbarium().containsKey(core.getId()));

        Blessing redeemed = ledger.redeem(core.getId(), bob);
        assertNotNull(redeemed);
        assertEquals(bob.getPlayerUuid(), redeemed.getOwnerUuid());
        assertTrue(bob.getHerbarium().containsKey(core.getId()));

        assertNull(ledger.redeem(core.getId(), alice)); // a duplicated claim item is void
        assertFalse(alice.getHerbarium().containsKey(core.getId()));
    }

    @Test
    void ledgerSurvivesRestart(@TempDir Path dir) {
        var alice = new Clover(UUID.randomUUID());
        var core = new Blessing(UUID.randomUUID(), "stinger", null, alice.getPlayerUuid());
        alice.addToHerbarium(core);
        new BlessingLedger(dir.resolve("ledger.json")).takeOut(alice, core.getId());

        var reloaded = new BlessingLedger(dir.resolve("ledger.json"));
        assertEquals("stinger", reloaded.redeem(core.getId(), alice).getCoreId());
    }

    @Test
    void boundAndEquippedBlessingsCannotLeave(@TempDir Path dir) {
        var ledger = new BlessingLedger(dir.resolve("ledger.json"));
        var alice = new Clover(UUID.randomUUID());
        var grafted = new Blessing(UUID.randomUUID(), "thornlash", Graft.QUICK, alice.getPlayerUuid());
        var equipped = new Blessing(UUID.randomUUID(), "stinger", null, alice.getPlayerUuid());
        alice.addToHerbarium(grafted);
        alice.addToHerbarium(equipped);
        alice.equipFromHerbarium(LeafSlot.LEAF_1, equipped.getId());

        assertNull(ledger.takeOut(alice, grafted.getId()));
        assertNull(ledger.takeOut(alice, equipped.getId()));
        assertTrue(alice.getHerbarium().containsKey(grafted.getId()));
    }

    // --- Graft maths ---

    @Test
    void pruneRefundsHalfRoundedDown() {
        assertEquals(50, GraftRules.refund(100, 50));
        assertEquals(1, GraftRules.refund(2, 50));
        assertEquals(0, GraftRules.refund(1, 50));
        assertEquals(33, GraftRules.refund(67, 50));
    }
}
