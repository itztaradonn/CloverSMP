package net.cloversmp.cloverblessing;

import net.cloversmp.cloverblessing.party.PartyManager;
import net.cloversmp.cloverblessing.party.PartyManager.InviteResult;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PartyManagerTest {

    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();

    @Test
    void inviteAcceptLeave() {
        var pm = new PartyManager();
        assertEquals(InviteResult.OK, pm.invite(a, b, 4));
        assertEquals(a, pm.accept(b, 4));
        assertTrue(pm.sameParty(a, b));
        assertFalse(pm.sameParty(a, a));

        pm.leave(b); // two-person party dissolves
        assertNull(pm.getParty(a));
        assertFalse(pm.sameParty(a, b));
    }

    @Test
    void onlyLeaderInvitesAndSizeIsCapped() {
        var pm = new PartyManager();
        pm.invite(a, b, 2);
        pm.accept(b, 2);
        assertEquals(InviteResult.NOT_LEADER, pm.invite(b, c, 2));
        assertEquals(InviteResult.FULL, pm.invite(a, c, 2));
        assertNull(pm.accept(c, 2)); // no invite
    }

    @Test
    void leaderLeavingPassesLeadAndKickWorks() {
        var pm = new PartyManager();
        pm.invite(a, b, 4); pm.accept(b, 4);
        pm.invite(a, c, 4); pm.accept(c, 4);

        pm.leave(a);
        assertEquals(b, pm.getParty(b).getLeader());
        assertFalse(pm.kick(c, b)); // c is not the leader
        assertTrue(pm.kick(b, c));
        assertNull(pm.getParty(b)); // dissolved down to one
    }
}
