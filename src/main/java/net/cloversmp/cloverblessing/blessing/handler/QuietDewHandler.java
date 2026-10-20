package net.cloversmp.cloverblessing.blessing.handler;

import net.cloversmp.cloverblessing.model.BlessingCategory;
import net.cloversmp.cloverblessing.model.BlessingCore;
import net.cloversmp.cloverblessing.model.BlessingTag;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** After 6 s without dealing or taking damage, heal 1 heart every 2 s. */
public class QuietDewHandler implements BlessingHandler {

    private static final long QUIET_MS = 6_000;
    private static final long PULSE_MS = 2_000;

    private final Map<UUID, Long> lastPulse = new ConcurrentHashMap<>();

    @Override
    public BlessingCore createCore() {
        return new BlessingCore("quiet_dew", "Quiet Dew", BlessingCategory.RECOVERY, Set.of(BlessingTag.HEAL),
                "After 6s without dealing or taking damage, heal 1 heart every 2s.",
                "No value in combat; constant poke damage keeps it off.",
                0, true, false, List.of("fern", "melon"));
    }

    @Override
    public boolean onSecond(BlessingContext ctx, StatSink stats) {
        long now = System.currentTimeMillis();
        if (now - ctx.engine().lastAnyDamageMs(ctx.player().getUniqueId()) < QUIET_MS) return false;
        if (now - lastPulse.getOrDefault(ctx.instanceId(), 0L) < PULSE_MS) return false;
        lastPulse.put(ctx.instanceId(), now);
        return ctx.engine().heal(ctx, 2.0 * ctx.strength(BlessingTag.HEAL)) > 0;
    }
}
