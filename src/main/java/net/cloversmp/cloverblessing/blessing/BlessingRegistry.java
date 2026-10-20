package net.cloversmp.cloverblessing.blessing;

import net.cloversmp.cloverblessing.CloverPlugin;
import net.cloversmp.cloverblessing.blessing.handler.*;
import net.cloversmp.cloverblessing.model.BlessingCategory;
import net.cloversmp.cloverblessing.model.BlessingCore;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Central registry of all blessing cores and their handlers.
 * Cores are registered by ID and can be queried by category.
 */
public class BlessingRegistry {

    private final Map<String, BlessingCore> cores = new LinkedHashMap<>();
    private final Map<String, BlessingHandler> handlers = new LinkedHashMap<>();

    /**
     * Registers a blessing core and its handler.
     */
    public void register(BlessingCore core, BlessingHandler handler) {
        cores.put(core.getId(), core);
        handlers.put(core.getId(), handler);
    }

    /**
     * Registers just a core (for cases where handler is registered separately).
     */
    public void register(BlessingCore core) {
        cores.put(core.getId(), core);
    }

    /**
     * Returns a BlessingCore by its ID, or null if not found.
     */
    public BlessingCore getCore(String id) {
        return cores.get(id);
    }

    /**
     * Returns the handler for a given core ID, or null if not found.
     */
    public BlessingHandler getHandler(String id) {
        return handlers.get(id);
    }

    /**
     * Returns an unmodifiable collection of all registered cores.
     */
    public Collection<BlessingCore> getAllCores() {
        return Collections.unmodifiableCollection(cores.values());
    }

    /**
     * Returns all registered handlers keyed by core ID.
     */
    public Map<String, BlessingHandler> getAllHandlers() {
        return Collections.unmodifiableMap(handlers);
    }

    /**
     * Returns all cores that belong to the given category.
     */
    public List<BlessingCore> getCoresByCategory(BlessingCategory category) {
        return cores.values().stream()
                .filter(c -> c.getCategory() == category)
                .collect(Collectors.toList());
    }

    /**
     * Registers the 16 default blessings and the 4 curses.
     */
    public void registerDefaults(CloverPlugin plugin) {
        registerHandler(new ThornlashHandler());
        registerHandler(new StingerHandler());
        registerHandler(new MossguardHandler());
        registerHandler(new LastLeafHandler());
        registerHandler(new SpringleafHandler());
        registerHandler(new FieldwalkerHandler());
        registerHandler(new QuietDewHandler());
        registerHandler(new LeechvineHandler());
        registerHandler(new TangleSeedHandler());
        registerHandler(new HobbleHandler());
        registerHandler(new WaystemHandler(plugin));
        registerHandler(new LanternleafHandler());
        registerHandler(new GlassBloomHandler());
        registerHandler(new WagerRootHandler());
        registerHandler(new EchoLeafHandler());
        registerHandler(new TurningYearHandler());

        registerHandler(new BloodrootCurse());
        registerHandler(new ThorncrownCurse());
        registerHandler(new HollowBellCurse());
        registerHandler(new HungeringCloverCurse());
    }

    private void registerHandler(BlessingHandler handler) {
        BlessingCore core = handler.createCore();
        register(core, handler);
    }
}
