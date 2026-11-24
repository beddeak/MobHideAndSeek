package dev.beddeak.mobhide;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.mockito.MockedStatic;

class ScoreSidebarTest {
    private MockedStatic<Bukkit> bukkit;
    private final Scoreboard board = mock(Scoreboard.class);
    private final Objective objective = mock(Objective.class);
    private ScoreSidebar sidebar;

    @BeforeEach void setup() {
        // Criteria의 상수는 클래스 초기화 때 서버에서 조회합니다.
        bukkit = mockStatic(Bukkit.class, withSettings().mockMaker(MockMakers.INLINE));
        bukkit.when(() -> Bukkit.getScoreboardCriteria(anyString())).thenReturn(mock(Criteria.class));
        when(board.registerNewObjective(eq("mobhide"), any(Criteria.class), any(Component.class))).thenReturn(objective);
        sidebar = new ScoreSidebar(board, Component.text("점수"));
    }

    @AfterEach void cleanup() { bukkit.close(); }

    @Test void showsOnTheSidebarAndRestoresEachPlayersOwnBoard() {
        verify(objective).setDisplaySlot(DisplaySlot.SIDEBAR);
        Scoreboard own = mock(Scoreboard.class);
        Player player = player(own);
        sidebar.show(player);
        verify(player).setScoreboard(board);
        when(player.getScoreboard()).thenReturn(board);
        sidebar.hide(player);
        verify(player).setScoreboard(own);
        sidebar.hide(player);
        verify(player, times(1)).setScoreboard(own);
    }

    @Test void aBoardSetByAnotherPluginDuringTheRoundIsNotOverwritten() {
        Scoreboard own = mock(Scoreboard.class), other = mock(Scoreboard.class);
        Player player = player(own);
        sidebar.show(player);
        when(player.getScoreboard()).thenReturn(other);
        sidebar.hide(player);
        verify(player, never()).setScoreboard(own);
    }

    @Test void onlyChangedLinesAreSentAndLeaversAreRemoved() {
        Score first = mock(Score.class), second = mock(Score.class);
        when(objective.getScore("A")).thenReturn(first);
        when(objective.getScore("B")).thenReturn(second);
        sidebar.update(Map.of("A", line("A", 1), "B", line("B", 2)));
        sidebar.update(Map.of("A", line("A", 1), "B", line("B", 3)));
        verify(first, times(1)).setScore(1);
        verify(first, times(1)).customName(Component.text("A"));
        verify(second).setScore(2);
        verify(second).setScore(3);
        verify(second, times(2)).numberFormat(any());
        sidebar.update(Map.of("A", line("A", 1)));
        verify(board).resetScores("B");
        verify(board, never()).resetScores("A");
    }

    @Test void titleIsOnlyResentWhenItChanges() {
        sidebar.title(Component.text("점수"));
        verify(objective, never()).displayName(any());
        sidebar.title(Component.text("연습 점수"));
        verify(objective).displayName(Component.text("연습 점수"));
    }

    private static ScoreSidebar.Line line(String name, int points) { return new ScoreSidebar.Line(Component.text(name), points); }

    private static Player player(Scoreboard current) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getScoreboard()).thenReturn(current);
        return player;
    }
}
