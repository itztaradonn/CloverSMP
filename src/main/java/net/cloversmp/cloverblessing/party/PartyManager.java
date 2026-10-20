package net.cloversmp.cloverblessing.party;

import java.util.*;

/**
 * Small built-in party system used by Lanternleaf, the Ward graft, Tangle Seed and the
 * "party kills give 0 charge" rule.
 * <p>
 * shortcut: parties live in memory and reset on restart; persist them if players ask for it.
 */
public class PartyManager {

    private static final long INVITE_TTL_MS = 60_000;

    public static final class Party {
        private UUID leader;
        private final Set<UUID> members = new LinkedHashSet<>();

        Party(UUID leader) {
            this.leader = leader;
            members.add(leader);
        }

        public UUID getLeader() { return leader; }
        public Set<UUID> getMembers() { return Collections.unmodifiableSet(members); }
    }

    private record Invite(UUID leader, long expiresMs) {}

    private final Map<UUID, Party> byMember = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();

    public Party getParty(UUID player) {
        return byMember.get(player);
    }

    public boolean sameParty(UUID a, UUID b) {
        if (a.equals(b)) return false;
        Party p = byMember.get(a);
        return p != null && p.members.contains(b);
    }

    /** Members other than {@code player}; empty when not in a party. */
    public Set<UUID> getPartners(UUID player) {
        Party p = byMember.get(player);
        if (p == null) return Set.of();
        Set<UUID> others = new LinkedHashSet<>(p.members);
        others.remove(player);
        return others;
    }

    public enum InviteResult { OK, NOT_LEADER, TARGET_IN_PARTY, FULL, SELF }

    public InviteResult invite(UUID inviter, UUID target, int maxSize) {
        if (inviter.equals(target)) return InviteResult.SELF;
        if (byMember.containsKey(target)) return InviteResult.TARGET_IN_PARTY;
        Party p = byMember.get(inviter);
        if (p != null && !p.leader.equals(inviter)) return InviteResult.NOT_LEADER;
        if (p != null && p.members.size() >= maxSize) return InviteResult.FULL;
        invites.put(target, new Invite(inviter, System.currentTimeMillis() + INVITE_TTL_MS));
        return InviteResult.OK;
    }

    /**
     * Accepts a pending invite.
     *
     * @return the leader who invited, or null when there is no valid invite or the party is full
     */
    public UUID accept(UUID target, int maxSize) {
        Invite inv = invites.remove(target);
        if (inv == null || inv.expiresMs() < System.currentTimeMillis() || byMember.containsKey(target)) return null;
        Party p = byMember.computeIfAbsent(inv.leader(), Party::new);
        if (!p.leader.equals(inv.leader()) || p.members.size() >= maxSize) return null;
        p.members.add(target);
        byMember.put(target, p);
        return inv.leader();
    }

    /** Removes the player from their party. The party dissolves when one member is left. */
    public void leave(UUID player) {
        Party p = byMember.remove(player);
        if (p == null) return;
        p.members.remove(player);
        if (p.members.size() <= 1) {
            p.members.forEach(byMember::remove);
            p.members.clear();
        } else if (p.leader.equals(player)) {
            p.leader = p.members.iterator().next();
        }
    }

    /** @return false when {@code leader} does not lead a party containing {@code target} */
    public boolean kick(UUID leader, UUID target) {
        Party p = byMember.get(leader);
        if (p == null || !p.leader.equals(leader) || leader.equals(target) || !p.members.contains(target)) return false;
        leave(target);
        return true;
    }
}
