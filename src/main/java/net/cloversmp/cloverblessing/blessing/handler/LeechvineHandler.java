package net.cloversmp.cloverblessing.blessing.handler;

import net.cloversmp.cloverblessing.model.BlessingCategory;
import net.cloversmp.cloverblessing.model.BlessingCore;
import net.cloversmp.cloverblessing.model.BlessingTag;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Heal 10 % of melee damage dealt, capped at 3 hearts per 10 s (and the Sap Cap). */
public class LeechvineHandler implements BlessingHandler {

    private static final double CAP_HP = 6.0;
    private static final long WINDOW_MS = 10_000;

    private record Heal(long timeMs, double hp) {}

    private final Map<UUID, Deque<Heal>> history = new ConcurrentHashMap<>();

    @Override
    public BlessingCore createCore() {
        return new BlessingCore("leechvine", "Leechvine", BlessingCategory.RECOVERY, Set.of(BlessingTag.HEAL),
                "Heal 10% of melee damage dealt, capped at 3 hearts per 10s.",
                "Ranged kiting and burst beat it; anti-heal (Wither) shuts it down.",
                0, true, false, List.of("vine", "tear"));
    }

    @Override
    public boolean onMeleeHit(BlessingContext ctx, EntityDamageByEntityEvent event, LivingEntity target) {
        long now = System.currentTimeMillis();
        Deque<Heal> h = history.computeIfAbsent(ctx.instanceId(), k -> new ArrayDeque<>());
        while (!h.isEmpty() && h.peekFirst().timeMs() < now - WINDOW_MS) h.pollFirst();
        double used = h.stream().mapToDouble(Heal::hp).sum();

        double want = Math.min(event.getFinalDamage() * 0.10 * ctx.strength(BlessingTag.HEAL), CAP_HP - used);
        double healed = ctx.engine().heal(ctx, want);
        if (healed <= 0) return false;
        h.addLast(new Heal(now, healed));
        return true;
    }
}
