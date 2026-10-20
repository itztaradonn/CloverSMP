package net.cloversmp.cloverblessing.blessing.handler;

import net.cloversmp.cloverblessing.model.BlessingCategory;
import net.cloversmp.cloverblessing.model.BlessingCore;
import net.cloversmp.cloverblessing.model.BlessingTag;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The third hit on the same target within 4 s deals +25 % damage. */
public class ThornlashHandler implements BlessingHandler {

    private static final long CHAIN_MS = 4_000;

    private record Chain(UUID target, int hits, long lastMs) {}

    private final Map<UUID, Chain> chains = new ConcurrentHashMap<>();

    @Override
    public BlessingCore createCore() {
        return new BlessingCore("thornlash", "Thornlash", BlessingCategory.OFFENSE, Set.of(BlessingTag.DAMAGE_BOOST),
                "The third hit on the same target within 4s deals +25% damage.",
                "Switching targets breaks the chain; retreating or shielding resets it.",
                0, true, false, List.of("plant", "metal"));
    }

    @Override
    public boolean onMeleeHit(BlessingContext ctx, EntityDamageByEntityEvent event, LivingEntity target) {
        UUID key = ctx.instanceId();
        if (target instanceof Player p && p.isBlocking()) {
            chains.remove(key);
            return false;
        }
        long now = System.currentTimeMillis();
        Chain c = chains.get(key);
        int hits = c != null && c.target().equals(target.getUniqueId()) && now - c.lastMs() <= CHAIN_MS ? c.hits() + 1 : 1;
        if (hits >= 3) {
            chains.remove(key);
            event.setDamage(event.getDamage() * (1 + 0.25 * ctx.strength(BlessingTag.DAMAGE_BOOST)));
            return true;
        }
        chains.put(key, new Chain(target.getUniqueId(), hits, now));
        return false;
    }
}
