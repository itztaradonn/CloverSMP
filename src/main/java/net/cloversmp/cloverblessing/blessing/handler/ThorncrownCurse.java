package net.cloversmp.cloverblessing.blessing.handler;

import net.cloversmp.cloverblessing.model.BlessingCategory;
import net.cloversmp.cloverblessing.model.BlessingCore;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.List;
import java.util.Set;

/** Curse: deal +10 % damage; take +15 % damage from players. */
public class ThorncrownCurse implements BlessingHandler {

    @Override
    public BlessingCore createCore() {
        return new BlessingCore("thorncrown", "Thorncrown", BlessingCategory.CURSE, Set.of(),
                "You deal +10% damage.",
                "You take +15% damage from players.",
                0, false, false, List.of());
    }

    @Override
    public boolean onMeleeHit(BlessingContext ctx, EntityDamageByEntityEvent event, LivingEntity target) {
        event.setDamage(event.getDamage() * (1 + 0.10 * ctx.strength()));
        return false;
    }

    @Override
    public boolean onArrowHit(BlessingContext ctx, EntityDamageByEntityEvent event, LivingEntity target) {
        return onMeleeHit(ctx, event, target);
    }

    @Override
    public boolean onDamaged(BlessingContext ctx, EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent byEntity && fromPlayer(byEntity)) {
            event.setDamage(event.getDamage() * 1.15);
        }
        return false;
    }

    private static boolean fromPlayer(EntityDamageByEntityEvent e) {
        return e.getDamager() instanceof Player
                || e.getDamager() instanceof Projectile p && p.getShooter() instanceof Player;
    }
}
