package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoundStateTest {
    private final UUID seeker = UUID.randomUUID();
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private RoundState round() { return new RoundState(seeker, List.of(first, second), 15, 180, 5); }

    @Test void cannotCatchDuringPreparation() {
        RoundState round = round();
        assertFalse(round.catchHider(seeker, first, 14));
        assertEquals(2, round.hiders().size());
        assertEquals(2, round.hiderHitsRemaining(first));
        assertFalse(round.catchHider(seeker, first, 15));
        assertTrue(round.catchHider(seeker, first, 15));
    }
    @Test void onlySeekerCanCatch() {
        RoundState round = round();
        assertFalse(round.catchHider(second, first, 20));
        assertFalse(round.catchHider(UUID.randomUUID(), first, 20));
        assertEquals(2, round.hiders().size());
    }
    @Test void attackingAnAiMobDoesNotCatchAnyone() {
        RoundState round = round();
        assertFalse(round.catchHider(seeker, UUID.randomUUID(), 20));
        assertEquals(RoundState.Winner.NONE, round.winner());
    }
    @Test void catchesAreCountedOnceAndLastCatchWins() {
        RoundState round = round();
        assertFalse(round.catchHider(seeker, first, 19));
        assertTrue(round.catchHider(seeker, first, 20));
        assertFalse(round.catchHider(seeker, first, 21));
        assertEquals(RoundState.Winner.NONE, round.winner());
        assertFalse(round.catchHider(seeker, second, 22));
        assertTrue(round.catchHider(seeker, second, 23));
        assertEquals(RoundState.Winner.SEEKER, round.winner());
    }
    @Test void preparationDoesNotReduceChaseDuration() {
        RoundState round = round();
        assertEquals(180, round.remaining(15));
        round.advance(194);
        assertEquals(RoundState.Winner.NONE, round.winner());
        round.advance(195);
        assertEquals(RoundState.Winner.HIDERS, round.winner());
        assertEquals(0, round.remaining(500));
    }
    @Test void deadlineWinsBeforeALateHitEvenIfTickerHasNotRun() {
        RoundState round = round();
        assertFalse(round.catchHider(seeker, first, 195));
        assertEquals(RoundState.Winner.HIDERS, round.winner());
    }
    @Test void seekerDisconnectGivesHidersTheWin() {
        RoundState round = round();
        round.leave(seeker, 3);
        assertEquals(RoundState.Winner.HIDERS, round.winner());
    }
    @Test void lastHiderDisconnectGivesSeekerTheWin() {
        RoundState round = round();
        round.leave(first, 3);
        assertEquals(RoundState.Winner.NONE, round.winner());
        round.leave(second, 4);
        assertEquals(RoundState.Winner.SEEKER, round.winner());
    }
    @Test void leavingAfterTheDeadlineCannotReverseTheWin() {
        RoundState round = new RoundState(seeker, List.of(first), 0, 30);
        round.leave(first, 30);
        assertEquals(RoundState.Winner.HIDERS, round.winner());
    }
    @Test void finishedOutcomeIsStable() {
        RoundState round = new RoundState(seeker, List.of(first), 0, 30);
        assertFalse(round.catchHider(seeker, first, 0));
        assertTrue(round.catchHider(seeker, first, 1));
        round.advance(100);
        round.leave(seeker, 100);
        assertEquals(RoundState.Winner.SEEKER, round.winner());
    }
    @Test void hiderCollectionCannotBeMutatedExternally() {
        assertThrows(UnsupportedOperationException.class, () -> round().hiders().clear());
    }
    @Test void invalidRoundsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RoundState(seeker, List.of(), 0, 30));
        assertThrows(IllegalArgumentException.class, () -> new RoundState(seeker, List.of(seeker), 0, 30));
        assertThrows(IllegalArgumentException.class, () -> new RoundState(seeker, List.of(first), -1, 30));
        assertThrows(IllegalArgumentException.class, () -> new RoundState(seeker, List.of(first), 0, 0));
    }

    @Test void soloTestStartsWithOneHiderAndNoSeekerOrPreparation() {
        RoundState round = RoundState.soloTest(first, 180);
        assertTrue(round.isSoloTest());
        assertNull(round.seeker());
        assertFalse(round.isSeeker(first));
        assertFalse(round.isSeeker(null));
        assertEquals(java.util.Set.of(first), round.hiders());
        assertFalse(round.isPreparing(0));
        assertEquals(180, round.remaining(0));
        round.advance(1);
        assertEquals(RoundState.Winner.NONE, round.winner());
    }

    @Test void soloTestCannotCatchPlayersOrDecoys() {
        RoundState round = RoundState.soloTest(first, 180);
        assertFalse(round.catchHider(first, first, 1));
        assertFalse(round.catchHider(seeker, first, 1));
        assertFalse(round.catchHider(first, UUID.randomUUID(), 1));
        assertEquals(java.util.Set.of(first), round.hiders());
        assertEquals(RoundState.Winner.NONE, round.winner());
    }

    @Test void soloTestExpiresWithoutDeclaringATeamWinner() {
        RoundState round = RoundState.soloTest(first, 30);
        round.advance(29);
        assertEquals(RoundState.Winner.NONE, round.winner());
        round.advance(30);
        assertEquals(RoundState.Winner.TEST_FINISHED, round.winner());
        assertEquals(0, round.remaining(30));
        assertFalse(round.catchHider(first, first, 30));
    }

    @Test void leavingSoloTestEndsItAndIgnoresOtherPlayers() {
        RoundState round = RoundState.soloTest(first, 30);
        round.leave(second, 1);
        assertEquals(RoundState.Winner.NONE, round.winner());
        round.leave(first, 2);
        assertTrue(round.hiders().isEmpty());
        assertEquals(RoundState.Winner.TEST_FINISHED, round.winner());
        round.advance(100);
        assertEquals(RoundState.Winner.TEST_FINISHED, round.winner());
    }

    @Test void invalidSoloTestsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> RoundState.soloTest(null, 30));
        assertThrows(IllegalArgumentException.class, () -> RoundState.soloTest(first, 0));
        assertThrows(IllegalArgumentException.class, () -> RoundState.soloTest(first, -1));
    }

    @Test void visibleTimerChangesFromPreparationToTheFullChaseDuration() {
        RoundState round = round();
        assertEquals(15, round.phaseRemaining(0));
        assertEquals(15, round.phaseDuration(0));
        assertEquals(1, round.phaseRemaining(14));
        assertEquals(180, round.phaseRemaining(15));
        assertEquals(180, round.phaseDuration(15));
        assertEquals(1, round.phaseRemaining(194));
        assertEquals(0, round.phaseRemaining(195));
    }

    @Test void soloVisibleTimerHasNoPreparationPhase() {
        RoundState round = RoundState.soloTest(first, 180);
        assertEquals(180, round.phaseRemaining(0));
        assertEquals(180, round.phaseDuration(0));
        assertEquals(179, round.phaseRemaining(1));
        assertEquals(0, round.phaseRemaining(180));
    }

    @Test void wrongHitsUseTheConfiguredLimitAndHealthFraction() {
        RoundState round = new RoundState(seeker, List.of(first), 0, 30, 5);
        assertEquals(5, round.mistakeLimit());
        assertEquals(5, round.mistakesRemaining());
        assertEquals(1, round.healthFraction(), 1e-9);
        assertTrue(round.wrongHit(seeker, null, 1));
        assertEquals(4, round.mistakesRemaining());
        assertEquals(0.8, round.healthFraction(), 1e-9);
        assertEquals(16, 20 * round.healthFraction(), 1e-9);
        assertEquals(RoundState.Winner.NONE, round.winner());
    }

    @Test void onlySeekerWrongHitsConsumeHealth() {
        RoundState round = round();
        assertFalse(round.wrongHit(first, null, 20));
        assertFalse(round.wrongHit(UUID.randomUUID(), null, 20));
        assertFalse(round.wrongHit(null, null, 20));
        assertEquals(5, round.mistakesRemaining());
    }

    @Test void preparationWrongHitsDoNotConsumeHealthOrProjectileIdentity() {
        RoundState round = round();
        UUID arrow = UUID.randomUUID();
        assertFalse(round.wrongHit(seeker, arrow, 14));
        assertEquals(5, round.mistakesRemaining());
        assertTrue(round.wrongHit(seeker, arrow, 15));
        assertEquals(4, round.mistakesRemaining());
    }

    @Test void hittingAPlayerCorrectlyDoesNotConsumeHealth() {
        RoundState round = round();
        assertFalse(round.catchHider(seeker, first, 19));
        assertTrue(round.catchHider(seeker, first, 20));
        assertEquals(5, round.mistakesRemaining());
        assertEquals(1, round.healthFraction(), 1e-9);
    }

    @Test void theLastWrongHitGivesHidersTheWinAndTheOutcomeIsStable() {
        RoundState round = new RoundState(seeker, List.of(first), 0, 30, 2);
        assertTrue(round.wrongHit(seeker, null, 1));
        assertTrue(round.wrongHit(seeker, null, 2));
        assertEquals(0, round.mistakesRemaining());
        assertEquals(0, round.healthFraction(), 1e-9);
        assertEquals(RoundState.Winner.HIDERS, round.winner());
        assertFalse(round.wrongHit(seeker, null, 3));
        assertFalse(round.catchHider(seeker, first, 3));
        round.leave(first, 3);
        assertEquals(0, round.mistakesRemaining());
        assertEquals(RoundState.Winner.HIDERS, round.winner());
    }

    @Test void aOneHitLimitEndsTheRoundOnTheFirstMistake() {
        RoundState round = new RoundState(seeker, List.of(first), 0, 30, 1);
        assertTrue(round.wrongHit(seeker, null, 1));
        assertEquals(RoundState.Winner.HIDERS, round.winner());
    }

    @Test void oneProjectileCanOnlyConsumeOneChanceAcrossMultipleAiTargets() {
        RoundState round = round();
        UUID arrow = UUID.randomUUID();
        assertTrue(round.wrongHit(seeker, arrow, 20));
        assertFalse(round.wrongHit(seeker, arrow, 21));
        assertFalse(round.wrongHit(seeker, arrow, 22));
        assertEquals(4, round.mistakesRemaining());
        assertTrue(round.wrongHit(seeker, UUID.randomUUID(), 23));
        assertEquals(3, round.mistakesRemaining());
    }

    @Test void aWrongHitAtTheDeadlineDoesNotSpendAnotherChance() {
        RoundState round = round();
        assertFalse(round.wrongHit(seeker, null, 195));
        assertEquals(5, round.mistakesRemaining());
        assertEquals(RoundState.Winner.HIDERS, round.winner());
    }

    @Test void seekerVictoryCannotBeReversedByLaterAiHits() {
        RoundState round = new RoundState(seeker, List.of(first), 0, 30, 5);
        assertFalse(round.catchHider(seeker, first, 0));
        assertTrue(round.catchHider(seeker, first, 1));
        assertFalse(round.wrongHit(seeker, null, 2));
        assertEquals(5, round.mistakesRemaining());
        assertEquals(RoundState.Winner.SEEKER, round.winner());
    }

    @Test void disguiseOnlySoloTestDoesNotHaveAnAiHitPenalty() {
        RoundState round = RoundState.soloTest(first, 30);
        assertFalse(round.wrongHit(first, null, 1));
        assertEquals(20, round.mistakesRemaining());
        assertEquals(RoundState.Winner.NONE, round.winner());
    }

    @Test void soloSeekerTestHasNoHidersButSupportsMistakesAndFinishesAtZero() {
        RoundState round = RoundState.seekerTest(seeker, 30, 2);
        assertTrue(round.isSoloTest());
        assertTrue(round.isSeekerTest());
        assertTrue(round.isSeeker(seeker));
        assertFalse(round.isPreparing(0));
        assertTrue(round.hiders().isEmpty());
        round.advance(1);
        assertEquals(RoundState.Winner.NONE, round.winner());
        assertTrue(round.wrongHit(seeker, null, 1));
        assertEquals(0.5, round.healthFraction(), 1e-9);
        assertTrue(round.wrongHit(seeker, null, 2));
        assertEquals(RoundState.Winner.TEST_FINISHED, round.winner());
    }

    @Test void soloSeekerTestFinishesOnLeaveOrTimeout() {
        RoundState leaving = RoundState.seekerTest(seeker, 30, 5);
        leaving.leave(first, 1);
        assertEquals(RoundState.Winner.NONE, leaving.winner());
        leaving.leave(seeker, 2);
        assertEquals(RoundState.Winner.TEST_FINISHED, leaving.winner());
        RoundState timed = RoundState.seekerTest(seeker, 30, 5);
        timed.advance(30);
        assertEquals(RoundState.Winner.TEST_FINISHED, timed.winner());
    }

    @Test void invalidMistakeLimitsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RoundState(seeker, List.of(first), 0, 30, 0));
        assertThrows(IllegalArgumentException.class, () -> new RoundState(seeker, List.of(first), 0, 30, -1));
        assertThrows(IllegalArgumentException.class, () -> RoundState.seekerTest(seeker, 30, 0));
        assertThrows(IllegalArgumentException.class, () -> RoundState.seekerTest(null, 30, 5));
    }

    @Test void defaultTwentyChancesStartWithTwentyFullHearts() {
        RoundState round = new RoundState(seeker, List.of(first), 0, 180);
        assertEquals(20, round.mistakesRemaining());
        assertEquals(40, round.maximumSeekerHealth(), 1e-9);
        assertEquals(40, round.seekerHealth(), 1e-9);
    }

    @Test void everyWrongHitRemovesExactlyOneHeartUntilTheTwentiethHit() {
        RoundState round = new RoundState(seeker, List.of(first), 0, 180, 20);
        for (int hit = 1; hit <= 20; hit++) {
            double before = round.seekerHealth();
            assertTrue(round.wrongHit(seeker, null, hit));
            assertEquals(2, before - round.seekerHealth(), 1e-9);
            assertEquals((20 - hit) * 2, round.seekerHealth(), 1e-9);
            assertEquals(hit == 20 ? RoundState.Winner.HIDERS : RoundState.Winner.NONE, round.winner());
        }
    }

    @Test void customChanceLimitsAlsoUseOneFullHeartPerWrongHit() {
        for (int limit : List.of(1, 5, 10, 20)) {
            RoundState round = RoundState.seekerTest(seeker, 180, limit);
            assertEquals(limit * 2, round.maximumSeekerHealth(), 1e-9);
            assertTrue(round.wrongHit(seeker, null, 1));
            assertEquals((limit - 1) * 2, round.seekerHealth(), 1e-9);
        }
    }

    @Test void firstHitKeepsTheHiderAliveAndTheSecondEliminatesThem() {
        RoundState round = new RoundState(seeker, List.of(first), 0, 30);
        assertEquals(2, round.hiderHitsRemaining(first));
        assertFalse(round.catchHider(seeker, first, 1));
        assertEquals(1, round.hiderHitsRemaining(first));
        assertTrue(round.hiders().contains(first));
        assertEquals(RoundState.Winner.NONE, round.winner());
        assertTrue(round.catchHider(seeker, first, 2));
        assertEquals(0, round.hiderHitsRemaining(first));
        assertFalse(round.hiders().contains(first));
        assertEquals(RoundState.Winner.SEEKER, round.winner());
    }

    @Test void eachHiderHasAnIndependentTwoHitBudget() {
        RoundState round = round();
        assertFalse(round.catchHider(seeker, first, 20));
        assertEquals(1, round.hiderHitsRemaining(first));
        assertEquals(2, round.hiderHitsRemaining(second));
        assertFalse(round.catchHider(seeker, second, 21));
        assertTrue(round.catchHider(seeker, first, 22));
        assertEquals(1, round.hiderHitsRemaining(second));
        assertEquals(RoundState.Winner.NONE, round.winner());
        assertEquals(5, round.mistakesRemaining());
    }

    @Test void aRepeatedProjectileCannotEliminateTheSameHiderInOneShot() {
        RoundState round = round();
        UUID arrow = UUID.randomUUID();
        assertFalse(round.catchHider(seeker, first, arrow, 20));
        assertFalse(round.catchHider(seeker, first, arrow, 21));
        assertEquals(1, round.hiderHitsRemaining(first));
        assertTrue(round.catchHider(seeker, first, UUID.randomUUID(), 22));
        assertEquals(0, round.hiderHitsRemaining(first));
    }

    @Test void aPiercingProjectileCanHitEachHiderOnce() {
        RoundState round = round();
        UUID arrow = UUID.randomUUID();
        assertFalse(round.catchHider(seeker, first, arrow, 20));
        assertFalse(round.catchHider(seeker, second, arrow, 20));
        assertEquals(1, round.hiderHitsRemaining(first));
        assertEquals(1, round.hiderHitsRemaining(second));
    }

    @Test void rejectedHitsDoNotSpendHiderLivesOrRememberProjectiles() {
        RoundState round = round();
        UUID arrow = UUID.randomUUID();
        assertFalse(round.catchHider(seeker, first, arrow, 14));
        assertFalse(round.catchHider(second, first, arrow, 15));
        assertEquals(2, round.hiderHitsRemaining(first));
        assertFalse(round.catchHider(seeker, first, arrow, 16));
        assertEquals(1, round.hiderHitsRemaining(first));
    }

    @Test void secondHitAtTheDeadlineDoesNotEliminateTheHider() {
        RoundState round = new RoundState(seeker, List.of(first), 0, 30);
        assertFalse(round.catchHider(seeker, first, 29));
        assertFalse(round.catchHider(seeker, first, 30));
        assertEquals(1, round.hiderHitsRemaining(first));
        assertEquals(RoundState.Winner.HIDERS, round.winner());
    }

    @Test void leavingAndStartingAnotherRoundDoesNotCarryOverHiderDamage() {
        RoundState round = round();
        assertFalse(round.catchHider(seeker, first, 20));
        round.leave(first, 21);
        assertEquals(0, round.hiderHitsRemaining(first));
        assertFalse(round.catchHider(seeker, first, 22));
        assertEquals(2, round().hiderHitsRemaining(first));
    }
}
