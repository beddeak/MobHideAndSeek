package dev.beddeak.mobhide;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;

/** 누적 점수와 역할별 승리 수. 이전 버전의 wins만 있는 기록은 점수 0으로 읽습니다. */
final class Leaderboard {
    record Entry(UUID id, String name, int points, int wins, int seekerWins, int hiderWins) {}
    private final Path file;
    private final Map<UUID, Entry> scores = new HashMap<>();
    Leaderboard(Path file) {
        this.file = file;
        var data = YamlConfiguration.loadConfiguration(file.toFile());
        for (String key : data.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                scores.put(id, new Entry(id, data.getString(key + ".name", key), Math.max(0, data.getInt(key + ".points")),
                        Math.max(0, data.getInt(key + ".wins")), Math.max(0, data.getInt(key + ".seeker-wins")),
                        Math.max(0, data.getInt(key + ".hider-wins"))));
            } catch (IllegalArgumentException ignored) { }
        }
    }
    void record(UUID id, String name, int points, boolean won, boolean asSeeker) {
        Entry old = scores.getOrDefault(id, new Entry(id, name, 0, 0, 0, 0));
        scores.put(id, new Entry(id, name, old.points() + Math.max(0, points), old.wins() + (won ? 1 : 0),
                old.seekerWins() + (won && asSeeker ? 1 : 0), old.hiderWins() + (won && !asSeeker ? 1 : 0)));
    }
    int points(UUID id) { Entry entry = scores.get(id); return entry == null ? 0 : entry.points(); }
    List<Entry> top() { return scores.values().stream().sorted(Comparator.comparingInt(Entry::points).reversed()
            .thenComparing(Comparator.comparingInt(Entry::wins).reversed())
            .thenComparing(Entry::name).thenComparing(entry -> entry.id().toString())).limit(10).toList(); }
    void save() throws IOException {
        var data = new YamlConfiguration();
        for (Entry entry : scores.values()) {
            data.set(entry.id() + ".name", entry.name());
            data.set(entry.id() + ".points", entry.points());
            data.set(entry.id() + ".wins", entry.wins());
            data.set(entry.id() + ".seeker-wins", entry.seekerWins());
            data.set(entry.id() + ".hider-wins", entry.hiderWins());
        }
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        data.save(temp.toFile());
        try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
    }
}
