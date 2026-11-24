package dev.beddeak.mobhide;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bukkit 없이 검증할 수 있는 라운드 판정. 시간은 단조 증가하는 경과 초입니다. */
final class RoundState {
    static final int HIDER_HIT_LIMIT = 2;
    enum Winner { NONE, SEEKER, HIDERS, TEST_FINISHED }
    private UUID seeker;
    private final Set<UUID> hiders;
    private final int preparationSeconds;
    private final int chaseSeconds;
    private final boolean test;
    private int mistakeLimit;
    private int hiderHitLimit = HIDER_HIT_LIMIT;
    private boolean sandbox;
    private int mistakesRemaining;
    private final Set<UUID> penalizedProjectiles = new LinkedHashSet<>();
    private final Map<UUID, Integer> hiderHits = new HashMap<>();
    private final Map<UUID, Integer> hiderMaximums = new HashMap<>();
    private final Map<UUID, Set<UUID>> hiderProjectiles = new HashMap<>();
    private Winner winner = Winner.NONE;

    RoundState(UUID seeker, Collection<UUID> hiders, int preparationSeconds, int chaseSeconds) {
        this(seeker, hiders, preparationSeconds, chaseSeconds, 20);
    }

    RoundState(UUID seeker, Collection<UUID> hiders, int preparationSeconds, int chaseSeconds, int mistakeLimit) {
        if (seeker == null || hiders.isEmpty() || hiders.contains(seeker)
                || preparationSeconds < 0 || chaseSeconds <= 0 || mistakeLimit <= 0) {
            throw new IllegalArgumentException("Invalid round participants or duration");
        }
        this.seeker = seeker;
        this.hiders = new LinkedHashSet<>(hiders);
        this.preparationSeconds = preparationSeconds;
        this.chaseSeconds = chaseSeconds;
        this.test = false;
        this.mistakeLimit = mistakeLimit;
        this.mistakesRemaining = mistakeLimit;
    }

    private RoundState(UUID player, int seconds) {
        this(player, seconds, 20, false);
    }

    private RoundState(UUID player, int seconds, int mistakeLimit, boolean seekerTest) {
        if (player == null || seconds <= 0 || mistakeLimit <= 0) throw new IllegalArgumentException("Invalid solo test");
        this.seeker = seekerTest ? player : null;
        this.hiders = new LinkedHashSet<>(seekerTest ? Set.of() : Set.of(player));
        this.preparationSeconds = 0;
        this.chaseSeconds = seconds;
        this.test = true;
        this.mistakeLimit = mistakeLimit;
        this.mistakesRemaining = mistakeLimit;
    }

    static RoundState soloTest(UUID player, int seconds) { return new RoundState(player, seconds); }
    static RoundState seekerTest(UUID player, int seconds, int mistakeLimit) { return new RoundState(player, seconds, mistakeLimit, true); }
    boolean isSoloTest() { return test; }
    boolean isSeekerTest() { return test && seeker != null; }
    boolean isSeeker(UUID player) { return seeker != null && seeker.equals(player); }
    UUID seeker() { return seeker; }
    Set<UUID> hiders() { return Set.copyOf(hiders); }
    Winner winner() { return winner; }
    boolean isPreparing(long elapsed) { return !sandbox && elapsed < preparationSeconds; }
    boolean sandbox() { return sandbox; }
    void enableSandbox() { sandbox = true; }
    long remaining(long elapsed) { return Math.max(0, preparationSeconds + (long) chaseSeconds - elapsed); }
    long phaseRemaining(long elapsed) { return isPreparing(elapsed) ? preparationSeconds - elapsed : remaining(elapsed); }
    int phaseDuration(long elapsed) { return isPreparing(elapsed) ? preparationSeconds : chaseSeconds; }
    /** 숨을 시간을 뺀 추격 경과 초. 샌드박스는 준비 없이 바로 추격이며 상한이 없습니다. */
    long chaseElapsed(long elapsed) {
        return sandbox ? Math.max(0, elapsed) : Math.max(0, Math.min(chaseSeconds, elapsed - preparationSeconds));
    }
    /** 점수 계산용 추격 시간. 시간 제한이 없는 샌드박스는 0입니다. */
    int chaseLimit() { return sandbox ? 0 : chaseSeconds; }
    int mistakeLimit() { return mistakeLimit; }
    int mistakesRemaining() { return mistakesRemaining; }
    double healthFraction() { return mistakesRemaining / (double) mistakeLimit; }
    double maximumSeekerHealth() { return mistakeLimit * 2.0; }
    double seekerHealth() { return mistakesRemaining * 2.0; }
    int hiderHitsRemaining(UUID player) {
        return hiders.contains(player) ? maximumHiderHits(player) - hiderHits.getOrDefault(player, 0) : 0;
    }
    int maximumHiderHits(UUID player) { return hiderMaximums.getOrDefault(player, hiderHitLimit); }
    void setHiderHitLimit(int limit) {
        if (limit < 1 || limit > 20) throw new IllegalArgumentException("Hider limit must be 1..20");
        hiderHitLimit = limit; hiderHits.clear(); hiderMaximums.clear();
    }
    void setHiderLives(UUID player, int lives) {
        if (!hiders.contains(player) || lives < 1 || lives > 20) throw new IllegalArgumentException("Invalid hider lives");
        hiderMaximums.put(player, lives); hiderHits.remove(player);
    }
    void resetSeekerLives(int lives) {
        if (lives < 1 || lives > 20) throw new IllegalArgumentException("Invalid seeker lives");
        mistakeLimit = lives; mistakesRemaining = lives;
    }
    void changeSeeker(UUID player) {
        if (player == null) throw new IllegalArgumentException("A seeker is required");
        if (seeker != null && !seeker.equals(player)) hiders.add(seeker);
        seeker = player; hiders.remove(player); hiderHits.remove(player); hiderProjectiles.remove(player);
        mistakesRemaining = mistakeLimit;
    }
    void reviveHider(UUID player) {
        if (isSeeker(player)) throw new IllegalArgumentException("Seeker cannot also hide");
        hiders.add(player); hiderHits.remove(player); hiderProjectiles.remove(player);
    }

    boolean wrongHit(UUID attacker, UUID projectile, long elapsed) {
        advance(elapsed);
        if (winner != Winner.NONE || isPreparing(elapsed) || !isSeeker(attacker) || mistakesRemaining == 0) return false;
        if (projectile != null && !penalizedProjectiles.add(projectile)) return false;
        mistakesRemaining--;
        if (mistakesRemaining == 0 && !sandbox) winner = isSoloTest() ? Winner.TEST_FINISHED : Winner.HIDERS;
        return true;
    }

    boolean catchHider(UUID attacker, UUID target, long elapsed) {
        return catchHider(attacker, target, null, elapsed);
    }

    boolean catchHider(UUID attacker, UUID target, UUID projectile, long elapsed) {
        advance(elapsed);
        if (winner != Winner.NONE || isPreparing(elapsed) || !isSeeker(attacker)
                || mistakesRemaining == 0 || !hiders.contains(target)) return false;
        if (projectile != null && !hiderProjectiles.computeIfAbsent(target, key -> new LinkedHashSet<>()).add(projectile)) return false;
        if (hiderHits.merge(target, 1, Integer::sum) < maximumHiderHits(target)) return false;
        hiders.remove(target);
        hiderHits.remove(target);
        hiderProjectiles.remove(target);
        if (hiders.isEmpty() && !sandbox) winner = Winner.SEEKER;
        return true;
    }

    void leave(UUID player, long elapsed) {
        advance(elapsed);
        if (winner != Winner.NONE) return;
        if (sandbox) { hiders.remove(player); if (isSeeker(player)) seeker = null; return; }
        if (isSoloTest()) {
            if (isSeeker(player) || hiders.remove(player)) winner = Winner.TEST_FINISHED;
        } else if (isSeeker(player)) winner = Winner.HIDERS;
        else if (hiders.remove(player) && hiders.isEmpty()) winner = Winner.SEEKER;
    }

    void advance(long elapsed) {
        if (!sandbox && winner == Winner.NONE && remaining(elapsed) == 0) {
            winner = isSoloTest() ? Winner.TEST_FINISHED : Winner.HIDERS;
        }
    }
}
