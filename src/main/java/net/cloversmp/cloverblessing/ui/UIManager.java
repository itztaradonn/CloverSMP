package net.cloversmp.cloverblessing.ui;

import java.util.*;
import net.kyori.adventure.bossbar.BossBar;
import net.cloversmp.cloverblessing.CloverPlugin;
import net.cloversmp.cloverblessing.blessing.BlessingEngine;
import net.cloversmp.cloverblessing.blessing.BlessingRegistry;
import net.cloversmp.cloverblessing.blessing.CooldownManager;
import net.cloversmp.cloverblessing.charge.ChargeManager;
import net.cloversmp.cloverblessing.model.*;
import net.cloversmp.cloverblessing.storage.DataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Always-on feedback (concept doc, "Feedback and UI"): one action bar line showing each leaf's state,
 * and a clover of leaf particles that shows opponents your leaf count, plus a dark aura for an active curse.
 */
public class UIManager {

    private final CloverPlugin plugin;
    private final DataManager data;
    private final BlessingRegistry registry;
    private final ChargeManager charge;
    private final CooldownManager cooldowns;
    private final BlessingEngine engine;

    private BukkitTask actionBarTask;
    private BukkitTask particleTask;

    public UIManager(CloverPlugin plugin, DataManager data, BlessingRegistry registry, ChargeManager charge,
                     CooldownManager cooldowns, BlessingEngine engine) {
        this.plugin = plugin;
        this.data = data;
        this.registry = registry;
        this.charge = charge;
        this.cooldowns = cooldowns;
        this.engine = engine;
    }

    public void startTasks() {
        long bar = plugin.getConfig().getLong("ui.actionbar-refresh-ticks", 10);
        long ring = plugin.getConfig().getLong("ui.particle-refresh-ticks", 10);
        actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickActionBar, 20L, bar);
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickParticles, 20L, ring);
    }

    public void stopTasks() {
        if (actionBarTask != null) actionBarTask.cancel();
        if (particleTask != null) particleTask.cancel();
    }

    private void tickActionBar() {
        bars.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        for (Player player : Bukkit.getOnlinePlayers()) {
            Clover clover = data.getClover(player.getUniqueId());
            if (clover == null) continue;
            player.sendActionBar(actionBar(clover));
            updateCooldownBar(player, clover);
        }
    }

    private final Map<UUID, BossBar> bars = new HashMap<>();
    /** Blessing id → the longest remaining time seen for its current cooldown, so the bar can show progress. */
    private final Map<UUID, Long> totals = new HashMap<>();

    /**
     * A boss bar listing every open-leaf blessing that is cooling down or waking ("Thornlash 12s · Stinger 3s").
     * The bar fill follows the longest one; yellow while that one is still waking, green otherwise. Hidden when
     * everything is ready.
     */
    private void updateCooldownBar(Player player, Clover clover) {
        List<String> parts = new ArrayList<>();
        long longest = 0;
        float progress = 0;
        boolean waking = false;
        for (var e : clover.getEquippedBlessings().entrySet()) {
            if (e.getKey().isCurse() || !charge.isLeafActive(clover, e.getKey())) continue;
            Blessing b = e.getValue();
            long remaining = cooldowns.getRemainingMs(clover, b.getId());
            if (remaining <= 0) {
                totals.remove(b.getId());
                continue;
            }
            long total = totals.merge(b.getId(), remaining, Math::max);
            boolean wake = cooldowns.getWakeMs(clover, b.getId()) >= remaining;
            BlessingCore core = registry.getCore(b.getCoreId());
            parts.add((core != null ? core.getDisplayName() : b.getCoreId()) + " " + (remaining / 1000 + 1) + "s"
                    + (wake ? " (waking)" : ""));
            if (remaining > longest) {
                longest = remaining;
                progress = (float) remaining / total;
                waking = wake;
            }
        }
        BossBar bar = bars.get(player.getUniqueId());
        if (parts.isEmpty()) {
            if (bar != null) {
                player.hideBossBar(bar);
                bars.remove(player.getUniqueId());
            }
            return;
        }
        Component title = Component.text(String.join("  ·  ", parts), NamedTextColor.WHITE);
        BossBar.Color color = waking ? BossBar.Color.YELLOW : BossBar.Color.GREEN;
        float fill = Math.max(0f, Math.min(1f, progress));
        if (bar == null) {
            bar = BossBar.bossBar(title, fill, color, BossBar.Overlay.PROGRESS);
            bars.put(player.getUniqueId(), bar);
            player.showBossBar(bar);
        } else {
            bar.name(title).progress(fill).color(color);
        }
    }

    /** e.g. "🍀⚔ 🍀🛡 🍀💨 3s 🍂 ☠ ⚡650/1000" */
    Component actionBar(Clover clover) {
        boolean cursed = clover.getEquippedBlessing(LeafSlot.CURSE) != null && charge.isLeafActive(clover, LeafSlot.CURSE);
        TextComponent.Builder bar = Component.text()
                .append(Menus.cloverIcon(charge.getLeafCount(clover), cursed, true), Component.space());
        bar.append(Component.text("⚡" + clover.getCharge() + "/" + charge.getMaxCharge(), NamedTextColor.YELLOW));
        return bar.build();
    }

    private static final Color LEAF = Color.fromRGB(0x5F, 0xA8, 0x29);
    private static final Color LEAF_DARK = Color.fromRGB(0x3B, 0x6D, 0x11);
    private static final Color WILTED = Color.fromRGB(0x6B, 0x5B, 0x4B);
    private static final Color BLOOD = Color.fromRGB(0x5A, 0x0F, 0x14);
    private static final Color BLACK = Color.fromRGB(0x10, 0x10, 0x10);
    /** Cross offsets (x, z in the rotating frame) for leaf 1 left, 2 top, 3 right, 4 bottom. */
    private static final double[][] CROSS = {{-1, 0}, {0, -1}, {1, 0}, {0, 1}};

    /**
     * Clover ring at the feet (one 3-dot cluster per open leaf, one grey-brown dot for a wilted one) plus, when
     * cursed, a rising dark swirl and the curse's own occasional tell. About 12 + 8 particles per player per run.
     */
    private void tickParticles() {
        double t = (System.currentTimeMillis() % 20_000) / 20_000.0 * 2 * Math.PI;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Clover clover = data.getClover(player.getUniqueId());
            if (clover == null || player.isInvisible()) continue;
            Blessing curse = clover.getEquippedBlessing(LeafSlot.CURSE);
            boolean cursed = curse != null && charge.isLeafActive(clover, LeafSlot.CURSE);
            Location base = player.getLocation().add(0, 0.1, 0);
            double cos = Math.cos(t), sin = Math.sin(t);
            for (LeafSlot slot : LeafSlot.values()) {
                if (slot.isCurse()) continue;
                boolean open = charge.isLeafActive(clover, slot);
                if (!open && clover.getEquippedBlessing(slot) == null) continue;
                double[] c = CROSS[slot.getIndex()];
                Location at = base.clone().add(1.1 * (c[0] * cos - c[1] * sin), 0, 1.1 * (c[0] * sin + c[1] * cos));
                if (!open) {
                    Effects.dust(at, WILTED, 1, 0.1);
                } else if (cursed) {
                    Effects.dust(at, LEAF_DARK, 2, 0.12);
                    Effects.dust(at, BLOOD, 1, 0.15);
                } else {
                    Effects.dust(at, LEAF, 2, 0.12);
                    Effects.dust(at, LEAF_DARK, 1, 0.12);
                }
            }
            if (cursed) curseAura(player, curse.getCoreId(), base, t);
        }
    }

    /** Blood-red and black dust spiralling up the body, a rare wisp, and a cheap per-curse tell. */
    private void curseAura(Player player, String curseId, Location base, double t) {
        double h = player.getHeight();
        for (int i = 0; i < 6; i++) {
            double a = t * 8 + i * Math.PI / 3;
            double y = ((t * 4 / Math.PI + i / 6.0) % 1.0) * h;
            Effects.dust(base.clone().add(0.55 * Math.cos(a), y, 0.55 * Math.sin(a)), i % 2 == 0 ? BLOOD : BLACK, 1, 0.02);
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextInt(8) == 0) Effects.particles(Effects.body(player, 0.5), r.nextBoolean() ? Particle.SOUL : Particle.SMOKE, 1, 0.3);
        if (r.nextInt(8) != 0) return;
        switch (curseId) {
            case "hungering_clover" -> Effects.particles(Effects.body(player, 0.5), Particle.ITEM_SLIME, 2, 0.3);
            case "thorncrown" -> Effects.particles(Effects.body(player, 1.0), Particle.CRIT, 2, 0.2);
            case "bloodroot" -> player.getWorld().spawnParticle(Particle.FALLING_DUST, Effects.body(player, 0.9), 1, 0.2, 0, 0.2, 0,
                    Material.NETHER_WART_BLOCK.createBlockData());
            case "hollow_bell" -> {
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    Effects.dust(base.clone().add(1.6 * Math.cos(a), 0, 1.6 * Math.sin(a)), BLACK, 1, 0.02);
                }
            }
            default -> { }
        }
    }
}
