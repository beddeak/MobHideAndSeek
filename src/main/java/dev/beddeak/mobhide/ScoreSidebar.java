package dev.beddeak.mobhide;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/**
 * 참가자 화면 오른쪽의 이번 판 점수판. 경기 전용 새 점수판을 쓰므로 월드에 저장되지 않고
 * 서버 기본 점수판의 팀·목표를 바꾸지 않습니다. 종료·퇴장 때 원래 점수판으로 되돌립니다.
 */
final class ScoreSidebar {
    record Line(Component label, int points) {}
    private final Scoreboard board;
    private final Objective objective;
    private final Map<UUID, Scoreboard> previous = new HashMap<>();
    private final Map<String, Line> shown = new HashMap<>();
    private Component title;

    ScoreSidebar(Scoreboard board, Component title) {
        this.board = Objects.requireNonNull(board);
        objective = board.registerNewObjective("mobhide", Criteria.DUMMY, title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        this.title = title;
    }

    void show(Player player) {
        previous.putIfAbsent(player.getUniqueId(), player.getScoreboard());
        player.setScoreboard(board);
    }

    void hide(Player player) {
        Scoreboard original = previous.remove(player.getUniqueId());
        // 경기 중 다른 플러그인이 바꾼 점수판은 덮어쓰지 않습니다.
        if (original != null && player.getScoreboard() == board) player.setScoreboard(original);
    }

    void title(Component next) {
        if (next.equals(title)) return;
        title = next;
        objective.displayName(next);
    }

    /** 키는 플레이어 이름이며, 바뀐 줄만 갱신해 매초 깜빡이지 않게 합니다. */
    void update(Map<String, Line> lines) {
        for (String entry : List.copyOf(shown.keySet())) {
            if (!lines.containsKey(entry)) { board.resetScores(entry); shown.remove(entry); }
        }
        lines.forEach((entry, line) -> {
            if (line.equals(shown.put(entry, line))) return;
            var score = objective.getScore(entry);
            score.setScore(line.points());
            score.customName(line.label());
            score.numberFormat(NumberFormat.fixed(Component.text(line.points() + "점", NamedTextColor.YELLOW)));
        });
    }
}
