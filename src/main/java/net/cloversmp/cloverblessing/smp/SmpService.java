package net.cloversmp.cloverblessing.smp;

import com.google.gson.Gson;
import net.cloversmp.cloverblessing.CloverPlugin;
import net.cloversmp.cloverblessing.ui.Effects;
import net.cloversmp.cloverblessing.ui.MenuHolder;
import net.cloversmp.cloverblessing.ui.Menus;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.logging.Level;

/**
 * The SMP season start: a locked lobby, the /clover smp start countdown show, the world border expansion and the
 * PvP grace period. Uses the server's main world; settings live under {@code smp.*} in config.yml.
 */
public class SmpService implements Listener {

    private record Setting(String key, String label, Material icon, int def, int min, int max, int step) {}

    private static final List<Setting> SETTINGS = List.of(
            new Setting("countdown-seconds", "Countdown length (s)", Material.CLOCK, 5, 1, 30, 1),
            new Setting("grace-minutes", "Grace period (min, no PvP)", Material.SHIELD, 20, 0, 180, 5),
            new Setting("lobby-border", "Lobby border width", Material.OAK_FENCE, 50, 16, 500, 10),
            new Setting("final-border", "Final border width", Material.BARRIER, 5000, 500, 60000, 500),
            new Setting("expand-seconds", "Border expand time (s)", Material.COMPASS, 30, 0, 600, 10));
    private static final int[] SETTING_SLOTS = {10, 11, 12, 13, 14};
    private static final int FLOAT_SLOT = 15, EFFECT_SLOT = 16, START_SLOT = 31;

    private final CloverPlugin plugin;
    private final Path file;
    private final Gson gson = new Gson();
    private SmpState state = new SmpState();
    private final BossBar graceBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);

    public SmpService(CloverPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("smp.json");
        if (Files.exists(file)) {
            try {
                state = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), SmpState.class);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Could not read smp.json; staying in the lobby", e);
            }
        }
        state.afterRestart();
    }

    public SmpState state() {
        return state;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickSecond, 20L, 20L);
    }

    private World world() {
        return Bukkit.getWorlds().getFirst();
    }

    private int setting(String key) {
        Setting s = SETTINGS.stream().filter(x -> x.key().equals(key)).findFirst().orElseThrow();
        return plugin.getConfig().getInt("smp." + key, s.def());
    }

    private boolean toggle(String key) {
        return plugin.getConfig().getBoolean("smp." + key, true);
    }

    // =====================================================================
    // Commands
    // =====================================================================

    /** Back to the lobby: shrink the border around spawn. For setting up a season or testing. */
    public void reset() {
        state.reset();
        save();
        graceBar.name(Component.empty());
        Bukkit.getOnlinePlayers().forEach(p -> p.hideBossBar(graceBar));
        WorldBorder border = world().getWorldBorder();
        border.setCenter(world().getSpawnLocation());
        border.setSize(setting("lobby-border"));
    }

    /** @return false if not in the lobby */
    public boolean begin() {
        if (!state.beginCountdown()) return false;
        save();
        int seconds = setting("countdown-seconds");
        boolean floating = toggle("float"), effect = toggle("clover-effect");
        int[] tick = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int t = tick[0]++;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (t == 0 && floating) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, seconds * 20 + 10, 0, true, false, false));
                }
                if (effect && t % 2 == 0) spinClover(p, t);
                if (t % 20 == 0) countdownBeat(p, seconds - t / 20, seconds);
            }
            if (t >= seconds * 20) {
                task.cancel();
                bloom(floating);
            }
        }, 0L, 1L);
        return true;
    }

    public String status() {
        long left = state.graceRemainingMs(System.currentTimeMillis());
        return "Phase: " + state.phase() + (left > 0 ? " · grace ends in " + format(left) : "")
                + " · border " + (int) world().getWorldBorder().getSize();
    }

    // =====================================================================
    // The show
    // =====================================================================

    /** A four-leaf clover of green dust spinning around the player at chest height. */
    private void spinClover(Player p, int t) {
        Location c = Effects.body(p, 0.6);
        double turn = t * Math.PI / 40;
        for (int leaf = 0; leaf < 4; leaf++) {
            double a = turn + leaf * Math.PI / 2;
            for (double r = 0.9; r <= 1.8; r += 0.3) {
                for (double side = -0.25; side <= 0.25; side += 0.25) {
                    double w = side * (1 - Math.abs(r - 1.35) / 0.6); // leaf is widest in the middle
                    Location at = c.clone().add(r * Math.cos(a + w), 0, r * Math.sin(a + w));
                    Effects.dust(at, r > 1.6 ? Color.fromRGB(0x3B6D11) : Color.fromRGB(0x5FA829), 1, 0);
                }
            }
        }
    }

    private void countdownBeat(Player p, int left, int total) {
        if (left <= 0) return;
        p.showTitle(Title.title(Component.text(left, NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                Component.text("The clover is about to bloom...", NamedTextColor.GRAY),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)));
        p.playSound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.6f + 1.2f * (total - left) / Math.max(1, total));
    }

    /** Zero: the burst, the fall, the border, and grace begins. */
    private void bloom(boolean floating) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (floating) {
                p.removePotionEffect(PotionEffectType.LEVITATION);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 0, true, false, false));
            }
            Location c = Effects.body(p, 0.8);
            Effects.particles(c, Particle.FIREWORK, 60, 0.8);
            Effects.particles(c, Particle.CHERRY_LEAVES, 40, 1.2);
            p.playSound(p, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1f, 1f);
            p.playSound(p, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1f);
            p.showTitle(Title.title(Component.text("☘ The SMP has begun!", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                    Component.text(setting("grace-minutes") > 0 ? "Grace: no PvP for " + setting("grace-minutes") + " minutes"
                            : "PvP is on", NamedTextColor.YELLOW)));
        }
        WorldBorder border = world().getWorldBorder();
        border.setCenter(world().getSpawnLocation());
        border.changeSize(setting("final-border"), Math.max(0, setting("expand-seconds")) * 20L);
        state.beginGrace(System.currentTimeMillis(), setting("grace-minutes") * 60_000L);
        save();
        if (state.phase() == SmpState.Phase.GRACE) Bukkit.getOnlinePlayers().forEach(p -> p.showBossBar(graceBar));
    }

    private void tickSecond() {
        long now = System.currentTimeMillis();
        if (state.tick(now)) {
            save();
            Bukkit.getOnlinePlayers().forEach(p -> {
                p.hideBossBar(graceBar);
                p.sendMessage(Component.text("⚔ Grace is over: PvP is now enabled.", NamedTextColor.RED));
                p.playSound(p, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.2f);
            });
            return;
        }
        long left = state.graceRemainingMs(now);
        if (left > 0) {
            graceBar.name(Component.text("☘ Grace period · PvP in " + format(left), NamedTextColor.GREEN))
                    .progress(Math.max(0f, Math.min(1f, (float) left / Math.max(1, state.graceTotalMs()))));
        }
    }

    private static String format(long ms) {
        long s = ms / 1000;
        return s / 60 + ":" + String.format("%02d", s % 60);
    }

    // =====================================================================
    // Lobby and grace rules
    // =====================================================================

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!state.progressionAllowed() && !e.getPlayer().hasPermission("clover.smp.bypass")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!state.progressionAllowed() && !e.getPlayer().hasPermission("clover.smp.bypass")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (state.pvpAllowed() || !(e.getEntity() instanceof Player victim)) return;
        Player attacker = e.getDamager() instanceof Player p ? p
                : e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player s ? s : null;
        if (attacker != null && attacker != victim) e.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (state.phase() == SmpState.Phase.GRACE) e.getPlayer().showBossBar(graceBar);
    }

    // =====================================================================
    // Settings menu
    // =====================================================================

    public void openSettings(Player player) {
        Inventory inv = Menus.create(new MenuHolder(MenuHolder.Kind.SMP_SETTINGS, null), 36, "☘ SMP settings");
        for (int i = 0; i < SETTINGS.size(); i++) {
            Setting s = SETTINGS.get(i);
            inv.setItem(SETTING_SLOTS[i], Menus.item(s.icon(), Menus.text(s.label() + ": " + setting(s.key()), NamedTextColor.GREEN),
                    Menus.line("Left-click +" + s.step() + ", right-click -" + s.step(), NamedTextColor.YELLOW),
                    Menus.line("Shift-click for ×10 steps", NamedTextColor.GRAY)));
        }
        inv.setItem(FLOAT_SLOT, toggleItem("Float during countdown", "float", Material.FEATHER));
        inv.setItem(EFFECT_SLOT, toggleItem("Spinning clover effect", "clover-effect", Material.LILY_PAD));
        inv.setItem(START_SLOT, Menus.item(Material.EMERALD_BLOCK, Menus.text("Status: " + state.phase(), NamedTextColor.AQUA),
                Menus.line("/cl smp start begins the countdown", NamedTextColor.GRAY),
                Menus.line("/cl smp reset returns to the lobby", NamedTextColor.GRAY)));
        player.openInventory(inv);
    }

    private org.bukkit.inventory.ItemStack toggleItem(String label, String key, Material icon) {
        boolean on = toggle(key);
        return Menus.item(icon, Menus.text(label + ": " + (on ? "ON" : "OFF"), on ? NamedTextColor.GREEN : NamedTextColor.RED),
                Menus.line("Click to toggle", NamedTextColor.YELLOW));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSettingsClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof MenuHolder h) || h.kind() != MenuHolder.Kind.SMP_SETTINGS) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || !p.hasPermission("clover.admin")) return;
        int raw = e.getRawSlot();
        for (int i = 0; i < SETTINGS.size(); i++) {
            if (SETTING_SLOTS[i] != raw) continue;
            Setting s = SETTINGS.get(i);
            int step = s.step() * (e.isShiftClick() ? 10 : 1) * (e.isRightClick() ? -1 : 1);
            plugin.getConfig().set("smp." + s.key(), Math.max(s.min(), Math.min(s.max(), setting(s.key()) + step)));
        }
        if (raw == FLOAT_SLOT) plugin.getConfig().set("smp.float", !toggle("float"));
        if (raw == EFFECT_SLOT) plugin.getConfig().set("smp.clover-effect", !toggle("clover-effect"));
        plugin.saveConfig();
        Bukkit.getScheduler().runTask(plugin, () -> openSettings(p));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSettingsDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof MenuHolder h && h.kind() == MenuHolder.Kind.SMP_SETTINGS) {
            e.setCancelled(true);
        }
    }

    // =====================================================================

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(state), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save smp.json", e);
        }
    }
}
