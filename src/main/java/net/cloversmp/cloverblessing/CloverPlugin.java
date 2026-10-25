package net.cloversmp.cloverblessing;

import net.cloversmp.cloverblessing.stats.StatsService;
import net.cloversmp.cloverblessing.blessing.*;
import net.cloversmp.cloverblessing.charge.ChargeManager;
import net.cloversmp.cloverblessing.combat.AntiAbuseEngine;
import net.cloversmp.cloverblessing.combat.CombatTracker;
import net.cloversmp.cloverblessing.command.CloverCommand;
import net.cloversmp.cloverblessing.crafting.*;
import net.cloversmp.cloverblessing.listener.ChargeListener;
import net.cloversmp.cloverblessing.listener.CombatListener;
import net.cloversmp.cloverblessing.listener.PlayerDataListener;
import net.cloversmp.cloverblessing.model.BlessingCore;
import net.cloversmp.cloverblessing.party.PartyManager;
import net.cloversmp.cloverblessing.storage.DataManager;
import net.cloversmp.cloverblessing.ui.MenuHolder;
import net.cloversmp.cloverblessing.ui.Menus;
import net.cloversmp.cloverblessing.ui.UIManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Set;
import java.util.stream.Collectors;

public final class CloverPlugin extends JavaPlugin {

    private static CloverPlugin instance;

    private DataManager dataManager;
    private BlessingEngine blessingEngine;
    private UIManager uiManager;
    private BlessingRegistry registry;
    private RecipeRegistry recipes;
    private NamespacedKey seedRecipeKey;
    private StatsService stats;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        if (!new File(getDataFolder(), "recipes.yml").exists()) saveResource("recipes.yml", false);

        dataManager = new DataManager(this);
        registry = new BlessingRegistry();
        registry.registerDefaults(this);
        recipes = new RecipeRegistry();
        loadRecipes();

        PartyManager parties = new PartyManager();
        CombatTracker combatTracker = new CombatTracker(this);
        AntiAbuseEngine antiAbuse = new AntiAbuseEngine(this, combatTracker, parties);
        ChargeManager chargeManager = new ChargeManager(this, dataManager);
        CooldownManager cooldowns = new CooldownManager();
        SapCapEngine sapCap = new SapCapEngine(
                () -> getConfig().getDouble("sap-cap.max-hearts", 6),
                () -> getConfig().getLong("sap-cap.window-seconds", 10) * 1000L,
                System::currentTimeMillis);

        blessingEngine = new BlessingEngine(this, dataManager, registry, chargeManager, cooldowns, sapCap, parties);
        chargeManager.setModifiers(blessingEngine);
        BlessingManager blessingManager = new BlessingManager(this, dataManager, registry, chargeManager, cooldowns);

        // Crafting
        CloverItems items = new CloverItems(this);
        CraftLog craftLog = new CraftLog(this);
        BlessingLedger ledger = new BlessingLedger(getDataFolder().toPath().resolve("ledger.json"));
        ClaimService claims = new ClaimService(dataManager, registry, ledger, items, craftLog);
        seedRecipeKey = registerSeedRecipe(items);

        Menus menus = new Menus(this, dataManager, registry, chargeManager, cooldowns, blessingManager, claims, recipes);
        uiManager = new UIManager(this, dataManager, registry, chargeManager, cooldowns, blessingEngine);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerDataListener(this, dataManager), this);
        pm.registerEvents(new CombatListener(combatTracker), this);
        pm.registerEvents(new ChargeListener(this, chargeManager, combatTracker, antiAbuse), this);
        pm.registerEvents(blessingEngine, this);
        pm.registerEvents(menus, this);
        pm.registerEvents(new PressingTableMenu(this, dataManager, registry, recipes, items, blessingManager, craftLog, menus), this);
        pm.registerEvents(new ItemListener(items, claims, seedRecipeKey), this);
        pm.registerEvents(new ScrapListener(this, registry, recipes, items), this);
        for (var handler : registry.getAllHandlers().values()) {
            if (handler instanceof Listener listener) pm.registerEvents(listener, this);
        }

        stats = new StatsService(this, dataManager, chargeManager);
        pm.registerEvents(stats, this);
        stats.start();

        var cmd = getCommand("clover");
        if (cmd == null) {
            throw new IllegalStateException("clover command is missing from plugin.yml");
        }
        var handler = new CloverCommand(this, dataManager, registry, blessingManager, chargeManager, parties, menus, items, blessingEngine, stats);
        cmd.setExecutor(handler);
        cmd.setTabCompleter(handler);

        // Players already online (e.g. after a plugin manager reload) need their data loaded.
        for (Player p : getServer().getOnlinePlayers()) {
            dataManager.loadClover(p.getUniqueId());
            if (seedRecipeKey != null) p.discoverRecipe(seedRecipeKey);
        }

        blessingEngine.start();
        uiManager.startTasks();

        long saveInterval = getConfig().getLong("storage.auto-save-minutes", 5) * 60 * 20;
        getServer().getScheduler().runTaskTimer(this, () -> dataManager.saveAll(true), saveInterval, saveInterval);

        getLogger().info("Clover Blessing enabled: " + registry.getAllCores().size() + " blessings and curses, "
                + recipes.all().size() + " recipes.");
    }

    @Override
    public void onDisable() {
        // Close plugin menus first so Pressing Table inputs go back to their owners.
        for (Player p : getServer().getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) p.closeInventory();
        }
        if (seedRecipeKey != null) getServer().removeRecipe(seedRecipeKey);
        if (uiManager != null) uiManager.stopTasks();
        if (blessingEngine != null) blessingEngine.shutdown();
        getServer().getScheduler().cancelTasks(this);
        if (dataManager != null) dataManager.saveAll(false);
        if (stats != null) stats.save();
    }

    /** Reloads config.yml and recipes.yml (/clover admin reload). */
    public void reloadAll() {
        reloadConfig();
        loadRecipes();
    }

    private void loadRecipes() {
        Set<String> cores = registry.getAllCores().stream().map(BlessingCore::getId).collect(Collectors.toSet());
        var yaml = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "recipes.yml"));
        for (String error : recipes.load(yaml.getConfigurationSection("recipes"), cores, m -> m.isItem())) {
            getLogger().warning(error);
        }
    }

    /** Shapeless Clover Seed recipe from {@code crafting.seed-recipe}; null if the config is invalid. */
    private NamespacedKey registerSeedRecipe(CloverItems items) {
        NamespacedKey key = new NamespacedKey(this, "clover_seed");
        ShapelessRecipe recipe = new ShapelessRecipe(key, items.seed(1));
        for (String name : getConfig().getStringList("crafting.seed-recipe")) {
            Material m = Material.matchMaterial(name);
            if (m == null || !m.isItem()) {
                getLogger().warning("crafting.seed-recipe: unknown item '" + name + "'; Clover Seeds can't be crafted.");
                return null;
            }
            recipe.addIngredient(m);
        }
        if (recipe.getChoiceList().isEmpty()) {
            getLogger().warning("crafting.seed-recipe is empty; Clover Seeds can't be crafted.");
            return null;
        }
        getServer().removeRecipe(key);
        getServer().addRecipe(recipe);
        return key;
    }

    public static CloverPlugin getInstance() {
        return instance;
    }

    public DataManager getDataManager() {
        return dataManager;
    }
}
