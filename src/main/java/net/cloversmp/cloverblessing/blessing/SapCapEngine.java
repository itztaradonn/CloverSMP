package net.cloversmp.cloverblessing.blessing;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Sap Cap: caps total blessing healing per player within a rolling window
 * ({@code sap-cap.max-hearts} per {@code sap-cap.window-seconds}).
 */
public class SapCapEngine {

    private final Supplier<Double> maxHearts;
    private final Supplier<Long> windowMs;
    private final LongSupplier clock;

    private final Map<UUID, Deque<HealRecord>> history = new ConcurrentHashMap<>();

    public SapCapEngine(Supplier<Double> maxHearts, Supplier<Long> windowMs, LongSupplier clock) {
        this.maxHearts = maxHearts;
        this.windowMs = windowMs;
        this.clock = clock;
    }

    /**
     * Takes up to {@code hearts} from the player's healing budget and records it.
     *
     * @return hearts actually allowed (0 when the cap is reached)
     */
    public double take(UUID player, double hearts) {
        if (hearts <= 0) return 0;
        long now = clock.getAsLong();
        Deque<HealRecord> records = history.computeIfAbsent(player, k -> new ArrayDeque<>());
        long cutoff = now - windowMs.get();
        while (!records.isEmpty() && records.peekFirst().timeMs() < cutoff) records.pollFirst();

        double used = 0;
        for (HealRecord r : records) used += r.hearts();
        double allowed = Math.min(hearts, Math.max(0, maxHearts.get() - used));
        if (allowed > 0) records.addLast(new HealRecord(now, allowed));
        return allowed;
    }

    public void clearPlayer(UUID uuid) {
        history.remove(uuid);
    }

    private record HealRecord(long timeMs, double hearts) {}
}
