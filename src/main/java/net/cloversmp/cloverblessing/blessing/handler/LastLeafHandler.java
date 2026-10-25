package net.cloversmp.cloverblessing.blessing.handler;

import net.cloversmp.cloverblessing.blessing.BlessingEngine;
import net.cloversmp.cloverblessing.model.BlessingCategory;
import net.cloversmp.cloverblessing.model.BlessingCore;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.List;
import java.util.Set;

/** Once per 180 s a fatal hit leaves you at 2 hearts, rooted for 1.5 s. Not echoable. */
public class LastLeafHandler implements BlessingHandler {

    private static final double SAVE_HP = 4.0;
    private static final long ROOT_TICKS = 30;

    @Override
    public BlessingCore createCore() {
        return new BlessingCore("last_leaf", "Last Leaf", BlessingCategory.DEFENSE, Set.of(),
                "Once per 180s, a fatal hit leaves you at 2 hearts, rooted for 1.5s.",
                "The root is a free finishing window; opponents who saw it fire know the cooldown.",
                180 * 20, false, false, List.of("golden fruit", "totem"));
    }

    @Override
    public boolean onFatalDamage(BlessingContext ctx, EntityDamageEvent event) {
        Player p = ctx.player();
        event.setCancelled(true);
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(Math.min(SAVE_HP, max == null ? SAVE_HP : max.getValue()));
        root(ctx.engine(), p);
        p.getWorld().spawnParticle(Particle.FALLING_SPORE_BLOSSOM, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0);
        return true;
    }

    private void root(BlessingEngine engine, Player p) {
        NamespacedKey key = new NamespacedKey(engine.plugin(), "last_leaf_root");
        BlessingEngine.setModifier(p, Attribute.MOVEMENT_SPEED, key, -1.0, AttributeModifier.Operation.ADD_SCALAR);
        BlessingEngine.setModifier(p, Attribute.JUMP_STRENGTH, key, -1.0, AttributeModifier.Operation.ADD_SCALAR);
        Bukkit.getScheduler().runTaskLater(engine.plugin(), () -> {
            BlessingEngine.setModifier(p, Attribute.MOVEMENT_SPEED, key, 0, AttributeModifier.Operation.ADD_SCALAR);
            BlessingEngine.setModifier(p, Attribute.JUMP_STRENGTH, key, 0, AttributeModifier.Operation.ADD_SCALAR);
        }, ROOT_TICKS);
    }
}
