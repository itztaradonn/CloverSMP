package net.cloversmp.cloverblessing.blessing;

import org.bukkit.Location;
import org.bukkit.Color;
import net.cloversmp.cloverblessing.ui.Effects;
import net.cloversmp.cloverblessing.CloverPlugin;
import net.cloversmp.cloverblessing.blessing.handler.BlessingContext;
import net.cloversmp.cloverblessing.blessing.handler.BlessingHandler;
import net.cloversmp.cloverblessing.blessing.handler.StatSink;
import net.cloversmp.cloverblessing.charge.ChargeManager;
import net.cloversmp.cloverblessing.model.*;
import net.cloversmp.cloverblessing.party.PartyManager;
import net.cloversmp.cloverblessing.storage.DataManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleBiFunction;

/**
 * The blessing dispatcher. Turns Bukkit events into {@link BlessingHandler} hook calls and enforces the
 * shared rules in one place: wilted leaves, cooldowns, graft and tag-fatigue strength, Echo Leaf,
 * the Sap Cap, the 50 % single-hit reduction limit and the speed cap.
 */
public class BlessingEngine implements Listener, ChargeManager.Modifiers {

    public static final String ECHO_LEAF = "echo_leaf";
    private static final long FLASH_MS = 1_000;
    private static final double SPEED_CAP = 0.40; // Speed II equivalent

    private final CloverPlugin plugin;
    private final DataManager dataManager;
    private final BlessingRegistry registry;
    private final ChargeManager chargeManager;
    private final CooldownManager cooldownManager;
    private final SapCapEngine sapCap;
    private final PartyManager parties;

    private final NamespacedKey speedKey;
    private final NamespacedKey maxHealthKey;

    private final Map<UUID, Long> lastAnyDamageMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHitDealtMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDamageTakenMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> flashUntil = new ConcurrentHashMap<>();

    private BukkitTask secondTask;

    @FunctionalInterface
    private interface Hook {
        boolean call(BlessingHandler handler, BlessingContext ctx);
    }

    public BlessingEngine(CloverPlugin plugin, DataManager dataManager, BlessingRegistry registry,
                          ChargeManager chargeManager, CooldownManager cooldownManager,
                          SapCapEngine sapCap, PartyManager parties) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.registry = registry;
        this.chargeManager = chargeManager;
        this.cooldownManager = cooldownManager;
        this.sapCap = sapCap;
        this.parties = parties;
        this.speedKey = new NamespacedKey(plugin, "blessing_speed");
        this.maxHealthKey = new NamespacedKey(plugin, "blessing_max_health");
    }

    public CloverPlugin plugin() { return plugin; }
    public PartyManager parties() { return parties; }
    public ChargeManager chargeManager() { return chargeManager; }

    public void start() {
        secondTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSecond, 20L, 20L);
    }

    /** Removes all stat modifiers so nothing stays on players after the plugin stops. */
    public void shutdown() {
        if (secondTask != null) secondTask.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) clearPluginModifiers(p);
    }

    // =====================================================================
    // Context building
    // =====================================================================

    /** The core actually run by a slot: its own, or for Echo Leaf the copied opposite core (or null). */
    private BlessingCore effectiveCore(Clover clover, LeafSlot slot) {
        Blessing b = clover.getEquippedBlessing(slot);
        if (b == null || !chargeManager.isLeafActive(clover, slot)) return null;
        BlessingCore core = registry.getCore(b.getCoreId());
        if (core == null || !core.getId().equals(ECHO_LEAF)) return core;
        LeafSlot opposite = slot.getOpposite();
        if (opposite == null) return null;
        Blessing ob = clover.getEquippedBlessing(opposite);
        if (ob == null || !chargeManager.isLeafActive(clover, opposite)) return null;
        BlessingCore oc = registry.getCore(ob.getCoreId());
        return oc != null && oc.isEchoable() && !oc.getId().equals(ECHO_LEAF) ? oc : null;
    }

    /** Calls {@code visitor} for every active slot with a runnable rule, in leaf order. */
    private void forEachActive(Player player, java.util.function.Consumer<BlessingContext> visitor) {
        Clover clover = dataManager.getClover(player.getUniqueId());
        if (clover == null) return;
        double curseBonus = curseStrengthBonus(player, clover);
        for (LeafSlot slot : LeafSlot.values()) {
            BlessingCore core = effectiveCore(clover, slot);
            if (core == null) continue;
            Blessing source = clover.getEquippedBlessing(slot);
            boolean echo = !core.getId().equals(source.getCoreId());
            double strength = graftStrength(player, source) * (echo ? 0.5 : 1.0)
                    * (slot.isCurse() ? 1.0 : 1.0 + curseBonus);
            visitor.accept(new BlessingContext(this, player, clover, slot, source, core, echo, strength));
        }
    }

    private double curseStrengthBonus(Player player, Clover clover) {
        BlessingCore core = effectiveCore(clover, LeafSlot.CURSE);
        BlessingHandler h = core == null ? null : registry.getHandler(core.getId());
        if (h == null) return 0;
        Blessing b = clover.getEquippedBlessing(LeafSlot.CURSE);
        return h.strengthBonus(new BlessingContext(this, player, clover, LeafSlot.CURSE, b, core, false,
                graftStrength(player, b)));
    }

    /** Graft strength, with Hunter and Vengeful applying only when their condition holds. */
    private double graftStrength(Player player, Blessing blessing) {
        Graft graft = blessing.getGraft();
        if (graft == null) return 1.0;
        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();
        return switch (graft) {
            case HUNTER -> now - lastHitDealtMs.getOrDefault(id, 0L) <= 3_000 ? 1.0 + graft.getStrengthModifier() : 1.0;
            case VENGEFUL -> now - lastDamageTakenMs.getOrDefault(id, 0L) <= 3_000 ? 1.0 + graft.getStrengthModifier() : 1.0;
            default -> 1.0 + graft.getStrengthModifier();
        };
    }

    /** Tag fatigue for this context: its position among active slots sharing the tag, in leaf order. */
    public double fatigue(BlessingContext ctx, BlessingTag tag) {
        if (!ctx.core().getTags().contains(tag)) return 1.0;
        int ordinal = 0;
        for (LeafSlot slot : LeafSlot.values()) {
            if (slot == ctx.slot()) break;
            BlessingCore other = effectiveCore(ctx.clover(), slot);
            if (other != null && other.getTags().contains(tag)) ordinal++;
        }
        List<Double> list = plugin.getConfig().getDoubleList("tag-fatigue.multipliers");
        return BalanceRules.fatigue(ordinal, list.isEmpty() ? List.of(1.0, 0.5, 0.25) : list);
    }

    // =====================================================================
    // Dispatch and firing
    // =====================================================================

    private void dispatch(Player player, String event, Hook hook) {
        Map<LeafSlot, String> trace = debug.contains(player.getUniqueId()) ? new EnumMap<>(LeafSlot.class) : null;
        forEachActive(player, ctx -> {
            String result;
            long cooldown = cooldownManager.getRemainingMs(ctx.clover(), ctx.instanceId());
            BlessingHandler h = registry.getHandler(ctx.core().getId());
            if (cooldown > 0) result = "cooldown " + (cooldown / 1000 + 1) + "s";
            else if (h == null) result = "no handler";
            else if (hook.call(h, ctx)) {
                fire(ctx);
                result = "FIRED";
            } else result = "no trigger";
            if (trace != null) trace.put(ctx.slot(), ctx.core().getId() + (ctx.echo() ? " (echo)" : "") + "=" + result);
        });
        if (trace != null) logTrace(player, event, trace);
    }

    // =====================================================================
    // Debug mode (/clover admin debug): one console line per event per player
    // =====================================================================

    private final Set<UUID> debug = ConcurrentHashMap.newKeySet();

    /** @return true if debug is now on for this player */
    public boolean toggleDebug(UUID player) {
        if (debug.remove(player)) return false;
        debug.add(player);
        return true;
    }

    private void logTrace(Player player, String event, Map<LeafSlot, String> ran) {
        Clover clover = dataManager.getClover(player.getUniqueId());
        if (clover == null) return;
        StringJoiner line = new StringJoiner(", ");
        for (LeafSlot slot : LeafSlot.values()) {
            String r = ran.get(slot);
            if (r == null) {
                Blessing b = clover.getEquippedBlessing(slot);
                if (b == null) r = "empty";
                else if (!chargeManager.isLeafActive(clover, slot)) r = b.getCoreId() + "=wilted";
                else r = b.getCoreId() + "=nothing to copy";
            }
            line.add((slot.isCurse() ? "L5 " : "L" + (slot.getIndex() + 1) + " ") + r);
        }
        plugin.getLogger().info("[debug] " + player.getName() + " " + event + ": " + line);
    }

    /** Starts the cooldown and plays the trigger flash and sound. */
    public void fire(BlessingContext ctx) {
        long baseMs = ctx.core().getBaseCooldownTicks() * 50L;
        double modifier = ctx.source().hasGraft() ? ctx.source().getGraft().getCooldownModifier() : 0.0;
        modifier += aggregate(ctx.player(), (h, c) -> h.cooldownModifier(c), 0.0, true);
        double maxCdr = plugin.getConfig().getDouble("cooldowns.max-cdr-percent", 30) / 100.0;
        cooldownManager.startCooldown(ctx.clover(), ctx.instanceId(),
                BalanceRules.cappedCooldownMs(baseMs, modifier, maxCdr));

        flashUntil.put(ctx.instanceId(), System.currentTimeMillis() + FLASH_MS);
        signature(ctx);
    }

    /** Handlers that play their own sound where it happens (impact point, recall destination). */
    private static final Set<String> OWN_SOUND = Set.of("tangle_seed", "waystem");

    /** The trigger's sound and owner-centred particles; target-centred effects live in the handlers. */
    private void signature(BlessingContext ctx) {
        Player p = ctx.player();
        String id = ctx.core().getId();
        Location chest = Effects.body(p, 0.6);
        if (!OWN_SOUND.contains(id)) {
            // An Echo copy plays the copied blessing's sound, quieter and lower.
            Effects.sound(p.getLocation(), (ctx.core().isCurse() ? "curse." : "blessing.") + id,
                    ctx.echo() ? 0.6f : 1f, ctx.echo() ? 0.7f : 1f);
        }
        if (ctx.echo()) Effects.particles(chest, Particle.SCULK_CHARGE_POP, 6, 0.3);
        switch (id) {
            case "mossguard" -> Effects.dust(chest, Color.fromRGB(0x5A7D2A), 12, 0.5);
            case "last_leaf" -> Effects.particles(chest, Particle.TOTEM_OF_UNDYING, 15, 0.5);
            case "springleaf" -> {
                Effects.particles(p.getLocation(), Particle.CLOUD, 8, 0.3);
                Effects.particles(chest, Particle.CHERRY_LEAVES, 10, 0.4);
            }
            case "quiet_dew" -> Effects.particles(Effects.body(p, 1.1), Particle.FALLING_WATER, 10, 0.4);
            case "waystem" -> Effects.particles(chest, Particle.END_ROD, 20, 0.5);
            default -> {
            }
        }
    }

    public boolean isFlashing(UUID blessingId) {
        Long until = flashUntil.get(blessingId);
        return until != null && until > System.currentTimeMillis();
    }

    /**
     * Combines a value across active slots. {@code sum = true} adds (start + Σ); otherwise multiplies.
     */
    private double aggregate(Player player, ToDoubleBiFunction<BlessingHandler, BlessingContext> f,
                             double start, boolean sum) {
        double[] acc = {start};
        forEachActive(player, ctx -> {
            BlessingHandler h = registry.getHandler(ctx.core().getId());
            if (h == null) return;
            double v = f.applyAsDouble(h, ctx);
            acc[0] = sum ? acc[0] + v : acc[0] * v;
        });
        return acc[0];
    }

    @Override
    public double gain(Player player) {
        return aggregate(player, BlessingHandler::chargeGainMultiplier, 1.0, false);
    }

    @Override
    public double deathPenalty(Player player) {
        return aggregate(player, BlessingHandler::deathPenaltyMultiplier, 1.0, false);
    }

    // =====================================================================
    // Services for handlers
    // =====================================================================

    /**
     * Heals the holder through the Sap Cap. Wither blocks blessing healing; curse heal multipliers apply.
     * A Ward graft also sends half the heal to the nearest party member within 6 blocks.
     *
     * @return HP actually healed on the holder
     */
    public double heal(BlessingContext ctx, double hp) {
        double multiplier = aggregate(ctx.player(), BlessingHandler::healMultiplier, 1.0, false);
        double healed = healPlayer(ctx.player(), hp * multiplier);
        if (healed > 0 && multiplier > 1.0 && Effects.every(ctx.player().getUniqueId(), "bloodroot", 3_000)) {
            Effects.sound(ctx.player().getLocation(), "curse.bloodroot", 1f, 1f);
            Effects.dust(Effects.body(ctx.player(), 0.6), Color.fromRGB(0x5A0F14), 10, 0.4);
        }
        if (healed > 0 && ctx.source().getGraft() == Graft.WARD) {
            nearestPartner(ctx.player(), 6.0).ifPresent(p -> healPlayer(p, healed * 0.5));
        }
        return healed;
    }

    private double healPlayer(Player player, double hp) {
        if (hp <= 0 || player.isDead() || player.hasPotionEffect(PotionEffectType.WITHER)) return 0;
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        double missing = (max == null ? 20.0 : max.getValue()) - player.getHealth();
        if (missing <= 0) return 0;
        double allowedHearts = sapCap.take(player.getUniqueId(), Math.min(hp, missing) / 2.0);
        double amount = allowedHearts * 2.0;
        if (amount > 0) player.setHealth(player.getHealth() + amount);
        return amount;
    }

    /** Online party members of {@code player} in the same world within {@code radius} blocks. */
    public List<Player> partnersNear(Player player, double radius) {
        List<Player> result = new ArrayList<>();
        for (UUID id : parties.getPartners(player.getUniqueId())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.getWorld().equals(player.getWorld())
                    && p.getLocation().distanceSquared(player.getLocation()) <= radius * radius) {
                result.add(p);
            }
        }
        return result;
    }

    private Optional<Player> nearestPartner(Player player, double radius) {
        return partnersNear(player, radius).stream()
                .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(player.getLocation())));
    }

    public long lastAnyDamageMs(UUID player) {
        return lastAnyDamageMs.getOrDefault(player, 0L);
    }

    public long lastDamageTakenMs(UUID player) {
        return lastDamageTakenMs.getOrDefault(player, 0L);
    }

    // =====================================================================
    // Events
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent byEntity && event.getEntity() instanceof LivingEntity target) {
            Entity damager = byEntity.getDamager();
            EntityDamageEvent.DamageCause cause = event.getCause();
            if (damager instanceof Player attacker && attacker != target
                    && (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                    || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
                dispatch(attacker, "onMeleeHit -> " + target.getName(), (h, c) -> h.onMeleeHit(c, byEntity, target));
            } else if (damager instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player shooter
                    && shooter != target) {
                dispatch(shooter, "onArrowHit -> " + target.getName(), (h, c) -> h.onArrowHit(c, byEntity, target));
            }
        }

        if (!(event.getEntity() instanceof Player victim) || event.isCancelled()) return;

        double before = event.getDamage();
        dispatch(victim, "onDamaged", (h, c) -> h.onDamaged(c, event));
        double maxReduction = plugin.getConfig().getDouble("balance.max-single-source-reduction-percent", 50) / 100.0;
        event.setDamage(BalanceRules.clampReduction(before, event.getDamage(), maxReduction));

        EntityDamageEvent.DamageCause cause = event.getCause();
        boolean unstoppable = cause == EntityDamageEvent.DamageCause.VOID
                || cause == EntityDamageEvent.DamageCause.KILL
                || cause == EntityDamageEvent.DamageCause.SUICIDE;
        if (!event.isCancelled() && !unstoppable
                && event.getFinalDamage() >= victim.getHealth() + victim.getAbsorptionAmount()) {
            dispatch(victim, "onFatalDamage", (h, c) -> !event.isCancelled() && h.onFatalDamage(c, event));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void trackDamage(EntityDamageEvent event) {
        long now = System.currentTimeMillis();
        if (event.getEntity() instanceof Player victim) {
            lastAnyDamageMs.put(victim.getUniqueId(), now);
            lastDamageTakenMs.put(victim.getUniqueId(), now);
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Player attacker = byEntity.getDamager() instanceof Player p ? p
                    : byEntity.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player s ? s : null;
            if (attacker != null && attacker != event.getEntity()) {
                lastAnyDamageMs.put(attacker.getUniqueId(), now);
                lastHitDealtMs.put(attacker.getUniqueId(), now);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player shooter) {
            dispatch(shooter, "onProjectileLand", (h, c) -> h.onProjectileLand(c, event));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        dispatch(event.getPlayer(), "onSneak", (h, c) -> h.onSneak(c, event.isSneaking()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        dispatch(event.getPlayer(), "onSwapHand", (h, c) -> h.onSwapHand(c, event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        dispatch(event.getPlayer(), "onInteractBlock", (h, c) -> h.onInteractBlock(c, event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player p) dispatch(p, "onRegainHealth", (h, c) -> h.onRegainHealth(c, event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p) dispatch(p, "onFoodChange", (h, c) -> h.onFoodChange(c, event));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        clearPluginModifiers(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        clearPluginModifiers(event.getPlayer());
        registry.getAllHandlers().values().forEach(h -> h.clear(id));
        sapCap.clearPlayer(id);
        lastAnyDamageMs.remove(id);
        lastHitDealtMs.remove(id);
        lastDamageTakenMs.remove(id);
        Effects.clear(id);
    }

    // =====================================================================
    // Per-second passives and stats
    // =====================================================================

    private void tickSecond() {
        long now = System.currentTimeMillis();
        flashUntil.values().removeIf(until -> until < now);
        for (Player player : Bukkit.getOnlinePlayers()) {
            StatSink stats = new StatSink();
            forEachActive(player, ctx -> {
                BlessingHandler h = registry.getHandler(ctx.core().getId());
                if (h != null && h.onSecond(ctx, stats)) {
                    flashUntil.put(ctx.instanceId(), now + FLASH_MS);
                }
            });
            applyStats(player, stats);
            readyDing(player, now);
        }
    }

    /** A quiet private ding when an equipped blessing comes off cooldown during the last second. */
    private void readyDing(Player player, long now) {
        Clover clover = dataManager.getClover(player.getUniqueId());
        if (clover == null) return;
        for (var e : clover.getEquippedBlessings().entrySet()) {
            long expiry = clover.getCooldownExpiry(e.getValue().getId());
            if (!e.getKey().isCurse() && expiry > now - 1_000 && expiry <= now) {
                Effects.privateSound(player, "ui.blessing_ready", 0.6f);
                return;
            }
        }
    }

    private void applyStats(Player player, StatSink stats) {
        setModifier(player, Attribute.MOVEMENT_SPEED, speedKey,
                BalanceRules.speedBonus(stats.speedSources(), SPEED_CAP), AttributeModifier.Operation.ADD_SCALAR);
        setModifier(player, Attribute.MAX_HEALTH, maxHealthKey, stats.maxHealth(), AttributeModifier.Operation.ADD_NUMBER);
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        if (max != null && player.getHealth() > max.getValue()) player.setHealth(max.getValue());
    }

    /** Sets (or removes, for 0) one plugin-owned attribute modifier, touching the attribute only on change. */
    public static void setModifier(Player player, Attribute attribute, NamespacedKey key, double amount,
                                   AttributeModifier.Operation op) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) return;
        AttributeModifier current = inst.getModifier(key);
        if (current != null && current.getAmount() == amount) return;
        if (current != null) inst.removeModifier(key);
        if (amount != 0) inst.addModifier(new AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY));
    }

    /** Removes every modifier in this plugin's namespace from the attributes blessings touch. */
    private void clearPluginModifiers(Player player) {
        String ns = plugin.getName().toLowerCase(Locale.ROOT);
        for (Attribute attribute : List.of(Attribute.MOVEMENT_SPEED, Attribute.MAX_HEALTH, Attribute.JUMP_STRENGTH)) {
            AttributeInstance inst = player.getAttribute(attribute);
            if (inst == null) continue;
            for (AttributeModifier m : List.copyOf(inst.getModifiers())) {
                if (m.getKey().getNamespace().equals(ns)) inst.removeModifier(m);
            }
        }
    }
}
