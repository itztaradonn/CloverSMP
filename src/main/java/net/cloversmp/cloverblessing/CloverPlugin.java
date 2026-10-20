package net.cloversmp.cloverblessing;

import net.cloversmp.cloverblessing.blessing.*;
import net.cloversmp.cloverblessing.charge.ChargeManager;
import net.cloversmp.cloverblessing.combat.AntiAbuseEngine;
import net.cloversmp.cloverblessing.combat.CombatTracker;
import net.cloversmp.cloverblessing.command.CloverCommand;
import net.cloversmp.cloverblessing.listener.ChargeListener;
import net.cloversmp.cloverblessing.listener.CombatListener;
import net.cloversmp.cloverblessing.listener.PlayerDataListener;
import net.cloversmp.cloverblessing.party.PartyManager;
import net.cloversmp.cloverblessing.storage.DataManager;
import net.cloversmp.cloverblessing.ui.Menus;
import net.cloversmp.cloverblessing.ui.UIManager;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class CloverPlugin extends JavaPlugin {

    private static CloverPlugin instance;

    private DataManager dataManager;
    private BlessingEngine blessingEngine;
    private UIManager uiManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        dataManager = new DataManager(this);
        BlessingRegistry registry = new BlessingRegistry();
        registry.registerDefaults(this);

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
        Menus menus = new Menus(this, dataManager, registry, chargeManager, cooldowns, blessingManager);
        uiManager = new UIManager(this, dataManager, registry, chargeManager, cooldowns, blessingEngine);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerDataListener(this, dataManager), this);
        pm.registerEvents(new CombatListener(combatTracker), this);
        pm.registerEvents(new ChargeListener(this, chargeManager, combatTracker, antiAbuse), this);
        pm.registerEvents(blessingEngine, this);
        pm.registerEvents(menus, this);
        for (var handler : registry.getAllHandlers().values()) {
            if (handler instanceof Listener listener) pm.registerEvents(listener, this);
        }

        var cmd = getCommand("clover");
        if (cmd == null) {
            throw new IllegalStateException("clover command is missing from plugin.yml");
        }
        var handler = new CloverCommand(this, dataManager, registry, blessingManager, chargeManager, parties, menus);
        cmd.setExecutor(handler);
        cmd.setTabCompleter(handler);

        // Players already online (e.g. after a plugin manager reload) need their data loaded.
        for (Player p : getServer().getOnlinePlayers()) dataManager.loadClover(p.getUniqueId());

        blessingEngine.start();
        uiManager.startTasks();

        long saveInterval = getConfig().getLong("storage.auto-save-minutes", 5) * 60 * 20;
        getServer().getScheduler().runTaskTimer(this, () -> dataManager.saveAll(true), saveInterval, saveInterval);

        getLogger().info("Clover Blessing enabled: " + registry.getAllCores().size() + " blessings and curses.");
    }

    @Override
    public void onDisable() {
        if (uiManager != null) uiManager.stopTasks();
        if (blessingEngine != null) blessingEngine.shutdown();
        getServer().getScheduler().cancelTasks(this);
        if (dataManager != null) dataManager.saveAll(false);
    }

    public static CloverPlugin getInstance() {
        return instance;
    }

    public DataManager getDataManager() {
        return dataManager;
    }
}
