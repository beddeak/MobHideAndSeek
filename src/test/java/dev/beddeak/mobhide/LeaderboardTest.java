package dev.beddeak.mobhide;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class LeaderboardTest {
    @TempDir Path temp;
    @Test void scoresSurviveRestartAndRenamingDoesNotCreateAnotherPlayer() throws Exception {
        UUID player = UUID.randomUUID();
        var scores = new Leaderboard(temp.resolve("leaderboard.yml"));
        scores.record(player, "OldName", 12, true, true); scores.save();
        scores = new Leaderboard(temp.resolve("leaderboard.yml"));
        scores.record(player, "NewName", 7, true, false); scores.record(player, "NewName", 3, false, false); scores.save();
        var top = new Leaderboard(temp.resolve("leaderboard.yml")).top();
        assertEquals(1, top.size());
        assertEquals(new Leaderboard.Entry(player, "NewName", 22, 2, 1, 1), top.getFirst());
    }
    @Test void leaderboardSortsByPointsThenWinsAndLimitsToTen() {
        var scores = new Leaderboard(temp.resolve("scores.yml"));
        for (int i = 1; i <= 15; i++) scores.record(UUID.randomUUID(), "P" + i, i * 10, false, false);
        UUID winner = UUID.randomUUID();
        scores.record(winner, "Tied", 150, true, false);
        assertEquals(10, scores.top().size());
        assertEquals(winner, scores.top().getFirst().id());
        assertEquals("P15", scores.top().get(1).name());
        assertEquals(70, scores.top().getLast().points());
    }
    @Test void legacyWinsAreKeptAndNewPointsStartFromZero() throws Exception {
        UUID player = UUID.randomUUID();
        Path file = temp.resolve("leaderboard.yml");
        Files.writeString(file, player + ":\n  name: Old\n  wins: 4\n");
        var scores = new Leaderboard(file);
        assertEquals(new Leaderboard.Entry(player, "Old", 0, 4, 0, 0), scores.top().getFirst());
        scores.record(player, "Old", 9, false, true);
        assertEquals(9, scores.points(player));
        assertEquals(4, scores.top().getFirst().wins());
        assertEquals(0, scores.points(UUID.randomUUID()));
    }
}
