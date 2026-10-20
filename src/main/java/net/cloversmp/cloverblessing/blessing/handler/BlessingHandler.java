package net.cloversmp.cloverblessing.blessing.handler;

import net.cloversmp.cloverblessing.model.BlessingCore;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.UUID;

/**
 * One blessing or curse rule. Every hook is a no-op by default; a handler overrides only what it uses.
 * <p>
 * The {@link net.cloversmp.cloverblessing.blessing.BlessingEngine} calls hooks only for active
 * (non-wilted) slots, and — except {@link #onSecond} and the multiplier queries — only when the blessing
 * is off cooldown. A hook returns {@code true} when the blessing fired; the engine then starts its
 * cooldown and plays the trigger flash and sound. Handlers are singletons: keep per-player state in maps
 * keyed by {@link BlessingContext#instanceId()} or the player UUID, and drop it in {@link #clear}.
 */
public interface BlessingHandler {

    BlessingCore createCore();

    /** The holder hit {@code target} in melee. Change damage with {@code event.setDamage}. */
    default boolean onMeleeHit(BlessingContext ctx, EntityDamageByEntityEvent event, LivingEntity target) { return false; }

    /** An arrow shot by the holder hit {@code target}. */
    default boolean onArrowHit(BlessingContext ctx, EntityDamageByEntityEvent event, LivingEntity target) { return false; }

    /** An arrow shot by the holder landed on a block or entity. */
    default boolean onProjectileLand(BlessingContext ctx, ProjectileHitEvent event) { return false; }

    /** The holder is taking damage from any source. Reductions are clamped to 50 % afterwards. */
    default boolean onDamaged(BlessingContext ctx, EntityDamageEvent event) { return false; }

    /** The holder is about to take a hit that would kill them. */
    default boolean onFatalDamage(BlessingContext ctx, EntityDamageEvent event) { return false; }

    default boolean onSneak(BlessingContext ctx, boolean sneaking) { return false; }

    default boolean onSwapHand(BlessingContext ctx, PlayerSwapHandItemsEvent event) { return false; }

    /** Right-click on a block with the main hand. */
    default boolean onInteractBlock(BlessingContext ctx, PlayerInteractEvent event) { return false; }

    default boolean onRegainHealth(BlessingContext ctx, EntityRegainHealthEvent event) { return false; }

    default boolean onFoodChange(BlessingContext ctx, FoodLevelChangeEvent event) { return false; }

    /** Runs once a second regardless of cooldown. Request stat changes through {@code stats}. */
    default boolean onSecond(BlessingContext ctx, StatSink stats) { return false; }

    // --- Multipliers queried by other systems (no cooldown check) ---

    /** Multiplies charge the holder earns from kills. */
    default double chargeGainMultiplier(BlessingContext ctx) { return 1.0; }

    /** Multiplies the holder's death penalty. */
    default double deathPenaltyMultiplier(BlessingContext ctx) { return 1.0; }

    /** Multiplies blessing healing the holder receives. */
    default double healMultiplier(BlessingContext ctx) { return 1.0; }

    /** Added to every blessing cooldown modifier (−0.2 = 20 % shorter), within the CDR cap. */
    default double cooldownModifier(BlessingContext ctx) { return 0.0; }

    /** Added to the strength of every equipped blessing (0.15 = +15 %). Read from the curse slot only. */
    default double strengthBonus(BlessingContext ctx) { return 0.0; }

    /** Drop per-player state (logout or reset). */
    default void clear(UUID player) {}
}
