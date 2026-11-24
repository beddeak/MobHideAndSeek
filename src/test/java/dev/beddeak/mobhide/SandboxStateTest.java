package dev.beddeak.mobhide;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class SandboxStateTest {
    private final UUID seeker = UUID.randomUUID(), hider = UUID.randomUUID();
    private RoundState sandbox() { var round = new RoundState(seeker, List.of(hider), 15, 30, 2); round.enableSandbox(); return round; }
    @Test void sandboxHasNoPreparationOrTimeVictory() {
        var round = sandbox(); assertFalse(round.isPreparing(0)); round.advance(99999); assertEquals(RoundState.Winner.NONE, round.winner());
    }
    @Test void exhaustedSeekerDoesNotGoNegativeOrEndSandbox() {
        var round = sandbox(); assertTrue(round.wrongHit(seeker, null, 0)); assertTrue(round.wrongHit(seeker, null, 0));
        assertFalse(round.wrongHit(seeker, null, 1)); assertEquals(0, round.mistakesRemaining()); assertEquals(RoundState.Winner.NONE, round.winner());
        round.resetSeekerLives(20); assertEquals(40, round.seekerHealth());
    }
    @Test void exhaustedSeekerCannotCatchWithAnArrowStillInFlightUntilRevived() {
        var round = sandbox();
        UUID arrow = UUID.randomUUID();
        assertTrue(round.wrongHit(seeker, null, 0));
        assertTrue(round.wrongHit(seeker, null, 0));

        assertFalse(round.catchHider(seeker, hider, arrow, 1));
        assertFalse(round.catchHider(seeker, hider, 1));
        assertEquals(2, round.hiderHitsRemaining(hider));
        assertEquals(RoundState.Winner.NONE, round.winner());

        round.resetSeekerLives(2);
        assertFalse(round.catchHider(seeker, hider, arrow, 2));
        assertEquals(1, round.hiderHitsRemaining(hider));
        assertTrue(round.catchHider(seeker, hider, UUID.randomUUID(), 3));
        assertEquals(RoundState.Winner.NONE, round.winner());
    }
    @Test void eliminatedHiderCanBeRevivedWithoutEndingSandbox() {
        var round = sandbox(); round.catchHider(seeker, hider, 0); assertTrue(round.catchHider(seeker, hider, 1));
        assertEquals(RoundState.Winner.NONE, round.winner()); round.reviveHider(hider); assertEquals(2, round.hiderHitsRemaining(hider));
    }
    @Test void switchingSeekerMaintainsExclusiveRoles() {
        var round = sandbox(); round.changeSeeker(hider);
        assertEquals(hider, round.seeker()); assertEquals(java.util.Set.of(seeker), round.hiders());
        assertThrows(IllegalArgumentException.class, () -> round.reviveHider(hider));
    }
    @Test void globalAndIndividualHitSettingsAreAppliedWithoutSharingDamage() {
        var round = sandbox(); round.setHiderHitLimit(3); round.setHiderLives(hider, 5);
        assertEquals(5, round.hiderHitsRemaining(hider)); round.catchHider(seeker, hider, 1); assertEquals(4, round.hiderHitsRemaining(hider));
        round.setHiderHitLimit(2); assertEquals(2, round.hiderHitsRemaining(hider));
    }
    @Test void leavingSandboxDoesNotAccidentallyAwardAWin() {
        var round = sandbox(); round.leave(seeker, 1); assertNull(round.seeker()); round.leave(hider, 1);
        assertEquals(RoundState.Winner.NONE, round.winner()); assertTrue(round.hiders().isEmpty());
    }
}
