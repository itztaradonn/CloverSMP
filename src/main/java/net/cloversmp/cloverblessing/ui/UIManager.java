package net.cloversmp.cloverblessing.ui;

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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Always-on feedback (concept doc, "Feedback and UI"): one action bar line showing each leaf's state,
 * and a ring of leaf particles that shows opponents your leaf count, plus a dark mote for a curse.
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
        long ring = plugin.getConfig().getLong("ui.particle-refresh-ticks", 20);
        actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickActionBar, 20L, bar);
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickParticles, 20L, ring);
    }

    public void stopTasks() {
        if (actionBarTask != null) actionBarTask.cancel();
        if (particleTask != null) particleTask.cancel();
    }

    private void tickActionBar() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Clover clover = data.getClover(player.getUniqueId());
            if (clover != null) player.sendActionBar(actionBar(clover));
        }
    }

    /** e.g. "🍀⚔ 🍀🛡 🍀💨 3s 🍂 ☠ ⚡650/1000" */
    Component actionBar(Clover clover) {
        TextComponent.Builder bar = Component.text();
        for (LeafSlot slot : LeafSlot.values()) {
            Blessing b = clover.getEquippedBlessing(slot);
            boolean open = charge.isLeafActive(clover, slot);
            if (slot.isCurse() && b == null) continue;
            if (!open) {
                bar.append(Component.text(slot.isCurse() ? "☠" : "🍂", NamedTextColor.DARK_GRAY));
            } else if (b == null) {
                bar.append(Component.text("🍀", NamedTextColor.GREEN));
            } else {
                BlessingCore core = registry.getCore(b.getCoreId());
                long remaining = cooldowns.getRemainingMs(clover, b.getId());
                NamedTextColor color = engine.isFlashing(b.getId()) ? NamedTextColor.GOLD
                        : remaining > 0 ? NamedTextColor.GRAY : slot.isCurse() ? NamedTextColor.DARK_PURPLE : NamedTextColor.GREEN;
                bar.append(Component.text(slot.isCurse() ? "☠" : "🍀", color));
                if (core != null && !slot.isCurse()) bar.append(Component.text(core.getCategory().getIcon(), color));
                if (remaining > 0) bar.append(Component.text((remaining / 1000 + 1) + "s", NamedTextColor.GRAY));
            }
            bar.append(Component.space());
        }
        bar.append(Component.text("⚡" + clover.getCharge() + "/" + charge.getMaxCharge(), NamedTextColor.YELLOW));
        return bar.build();
    }

    private void tickParticles() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Clover clover = data.getClover(player.getUniqueId());
            if (clover == null || player.isInvisible()) continue;
            int leaves = charge.getLeafCount(clover);
            Location base = player.getLocation().add(0, 0.1, 0);
            for (int i = 0; i < leaves; i++) {
                double angle = (2 * Math.PI / leaves) * i + (System.currentTimeMillis() % 4000) / 4000.0 * 2 * Math.PI;
                player.getWorld().spawnParticle(Particle.COMPOSTER,
                        base.clone().add(1.2 * Math.cos(angle), 0, 1.2 * Math.sin(angle)), 1, 0, 0.05, 0, 0);
            }
            if (clover.getEquippedBlessing(LeafSlot.CURSE) != null && charge.isLeafActive(clover, LeafSlot.CURSE)) {
                player.getWorld().spawnParticle(Particle.SQUID_INK, base.clone().add(0, 0.2, 0), 2, 0.3, 0.05, 0.3, 0);
            }
        }
    }
}
