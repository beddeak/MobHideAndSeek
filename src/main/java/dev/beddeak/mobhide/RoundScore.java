package dev.beddeak.mobhide;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 이번 판 점수. 숨는 사람마다 추격 시간을 술래와 나눠 갖습니다.
 * 버틴 시간은 숨는 사람 몫, 잡힌 뒤 남은 시간은 술래 몫입니다. Bukkit 없이 검증합니다.
 */
final class RoundScore {
    private final int secondsPerPoint;
    private final int surviveBonus;
    private final int catchBonus;
    private final int allCaughtBonus;
    // 관리자 패널에서 경기 중에도 바꿀 수 있습니다.
    private int tauntPoints;
    private long tauntCooldownTicks;
    // 잡힌 숨는 사람의 확정 시간 점수. 살아 있는 동안은 현재 시간으로 계산합니다.
    private final Map<UUID, Integer> time = new HashMap<>();
    private final Map<UUID, Integer> bonus = new HashMap<>();
    // 다음 가능 시각이 아니라 마지막 도발 시각을 저장해야 쿨타임을 줄였을 때 바로 적용됩니다.
    private final Map<UUID, Long> tauntedAt = new HashMap<>();

    RoundScore(int secondsPerPoint, int surviveBonus, int catchBonus, int allCaughtBonus, int tauntPoints, long tauntCooldownTicks) {
        if (secondsPerPoint < 0 || surviveBonus < 0 || catchBonus < 0 || allCaughtBonus < 0 || tauntPoints < 0 || tauntCooldownTicks < 0)
            throw new IllegalArgumentException("Score settings cannot be negative");
        this.tauntPoints = tauntPoints;
        this.tauntCooldownTicks = tauntCooldownTicks;
        this.secondsPerPoint = secondsPerPoint;
        this.surviveBonus = surviveBonus;
        this.catchBonus = catchBonus;
        this.allCaughtBonus = allCaughtBonus;
    }

    int secondsPerPoint() { return secondsPerPoint; }
    int tauntPoints() { return tauntPoints; }

    void setTaunt(int points, long cooldownTicks) {
        if (points < 0 || cooldownTicks < 0) throw new IllegalArgumentException("Taunt settings cannot be negative");
        tauntPoints = points;
        tauntCooldownTicks = cooldownTicks;
    }

    /**
     * 위치를 드러내는 도발. 쿨타임이 지났으면 점수를 주고 0을, 아니면 남은 틱을 돌려줍니다.
     * 도발 점수는 잡혀도 유지합니다. 위험은 들켜서 남은 시간 점수를 술래에게 넘기는 것입니다.
     */
    long taunt(UUID hider, long tick) {
        Long last = tauntedAt.get(hider);
        if (last != null && last + tauntCooldownTicks > tick) return last + tauntCooldownTicks - tick;
        tauntedAt.put(hider, tick);
        add(hider, tauntPoints);
        return 0;
    }

    int timePoints(long chaseSeconds) {
        return secondsPerPoint == 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, Math.max(0, chaseSeconds) / secondsPerPoint);
    }

    /** chaseLimit이 0이면 시간 제한이 없어 술래가 가져갈 남은 시간도 없습니다. */
    void caught(UUID hider, UUID seeker, long chaseSeconds, int chaseLimit) {
        int kept = timePoints(chaseLimit > 0 ? Math.min(chaseSeconds, chaseLimit) : chaseSeconds);
        time.put(hider, kept);
        if (seeker != null) add(seeker, catchBonus + (chaseLimit > 0 ? Math.max(0, timePoints(chaseLimit) - kept) : 0));
    }

    /** 숨는 팀이 이기면 경기가 일찍 끝나도 살아남은 사람은 추격 시간 점수를 전부 받습니다. */
    void survived(UUID hider, int chaseLimit) {
        time.put(hider, timePoints(chaseLimit));
        add(hider, surviveBonus);
    }

    void allCaught(UUID seeker) { add(seeker, allCaughtBonus); }

    /** 살아 있는 숨는 사람은 현재 추격 시간으로, 그 외에는 확정 점수로 계산합니다. */
    int points(UUID player, boolean aliveHider, long chaseSeconds) {
        return bonus.getOrDefault(player, 0) + (aliveHider ? timePoints(chaseSeconds) : time.getOrDefault(player, 0));
    }

    private void add(UUID player, int points) { bonus.merge(player, points, Integer::sum); }
}
