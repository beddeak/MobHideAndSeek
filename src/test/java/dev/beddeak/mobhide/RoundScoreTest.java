package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoundScoreTest {
    private final UUID seeker = UUID.randomUUID(), early = UUID.randomUUID(), late = UUID.randomUUID();

    @Test void hiderAndSeekerSplitTheChaseTimeOfEveryCatch() {
        var score = new RoundScore(10, 5, 5, 5, 5, 600);
        score.caught(early, seeker, 47, 180);
        assertEquals(4, score.points(early, false, 0));
        assertEquals(5 + 14, score.points(seeker, false, 0));
        score.caught(late, seeker, 170, 180);
        assertEquals(17, score.points(late, false, 0));
        assertEquals(19 + 5 + 1, score.points(seeker, false, 0));
    }

    @Test void aliveHiderGainsAPointEveryIntervalUntilCaught() {
        var score = new RoundScore(10, 5, 5, 5, 5, 600);
        assertEquals(0, score.points(early, true, 9));
        assertEquals(1, score.points(early, true, 10));
        assertEquals(6, score.points(early, true, 69));
        score.caught(early, seeker, 70, 180);
        assertEquals(7, score.points(early, false, 150));
    }

    @Test void survivorsGetTheWholeChaseEvenIfTheSeekerRunsOutOfHealthEarly() {
        var score = new RoundScore(10, 5, 5, 5, 5, 600);
        score.caught(early, seeker, 30, 180);
        score.survived(late, 180);
        assertEquals(18 + 5, score.points(late, false, 0));
        assertEquals(3, score.points(early, false, 0));
    }

    @Test void catchingTheLastHiderAddsTheBonusOnce() {
        var score = new RoundScore(10, 5, 5, 5, 5, 600);
        score.caught(early, seeker, 180, 180);
        score.allCaught(seeker);
        assertEquals(5 + 0 + 5, score.points(seeker, false, 0));
    }

    @Test void sandboxHasNoRemainingTimeToTakeAndNeverOverflows() {
        var score = new RoundScore(10, 5, 5, 5, 5, 600);
        score.caught(early, seeker, 1_000_000, 0);
        assertEquals(100_000, score.points(early, false, 0));
        assertEquals(5, score.points(seeker, false, 0));
        assertEquals(Integer.MAX_VALUE, score.timePoints(Long.MAX_VALUE));
        assertEquals(0, score.timePoints(-5));
    }

    @Test void zeroIntervalUsesOnlyBonusesAndNegativeSettingsAreRejected() {
        var score = new RoundScore(0, 5, 3, 2, 0, 0);
        score.caught(early, seeker, 100, 180);
        score.survived(late, 180);
        assertEquals(0, score.points(early, true, 500));
        assertEquals(3, score.points(seeker, false, 0));
        assertEquals(5, score.points(late, false, 0));
        for (var invalid : List.of(new int[] {-1, 0, 0, 0, 0}, new int[] {0, -1, 0, 0, 0}, new int[] {0, 0, -1, 0, 0}, new int[] {0, 0, 0, -1, 0}, new int[] {0, 0, 0, 0, -1}))
            assertThrows(IllegalArgumentException.class, () -> new RoundScore(invalid[0], invalid[1], invalid[2], invalid[3], invalid[4], 0));
    }

    @Test void tauntPaysOncePerCooldownPerPlayerAndIsKeptAfterBeingCaught() {
        var score = new RoundScore(10, 5, 5, 5, 5, 600);
        assertEquals(0, score.taunt(early, 100));
        assertEquals(5, score.points(early, true, 0));
        assertEquals(599, score.taunt(early, 101));
        assertEquals(1, score.taunt(early, 699));
        assertEquals(0, score.taunt(late, 101));
        assertEquals(0, score.taunt(early, 700));
        assertEquals(10, score.points(early, true, 0));
        score.caught(early, seeker, 50, 180);
        assertEquals(10 + 5, score.points(early, false, 0));
        // 도발 점수는 술래 몫의 남은 시간 계산에 영향을 주지 않습니다.
        assertEquals(5 + 13, score.points(seeker, false, 0));
    }

    @Test void shorteningTheCooldownAppliesToAWaitThatIsAlreadyRunning() {
        var score = new RoundScore(10, 5, 5, 5, 5, 600);
        assertEquals(0, score.taunt(early, 100));
        score.setTaunt(7, 200);
        assertEquals(1, score.taunt(early, 299));
        assertEquals(0, score.taunt(early, 300));
        assertEquals(5 + 7, score.points(early, true, 0));
        score.setTaunt(0, 200);
        assertEquals(0, score.tauntPoints());
        assertThrows(IllegalArgumentException.class, () -> score.setTaunt(-1, 200));
    }

    @Test void roundClockExcludesHidingTimeAndCapsAtTheChaseLength() {
        var round = new RoundState(seeker, List.of(early), 15, 180);
        assertEquals(0, round.chaseElapsed(10));
        assertEquals(5, round.chaseElapsed(20));
        assertEquals(180, round.chaseElapsed(500));
        assertEquals(180, round.chaseLimit());
        round.enableSandbox();
        assertEquals(500, round.chaseElapsed(500));
        assertEquals(0, round.chaseLimit());
    }
}
