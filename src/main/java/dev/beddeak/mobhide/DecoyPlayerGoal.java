package dev.beddeak.mobhide;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Creature;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** 게임 소유 AI의 플레이어 시선/먹이 추적만 교체합니다. 배회, 수영 등은 바닐라 AI가 맡습니다. */
final class DecoyPlayerGoal implements Goal<Mob> {
    enum Mode { WATCH, TEMPT }

    private final Mob mob;
    private final Predicate<Player> hidden;
    private final Mode mode;
    private final RandomGenerator random;
    private final GoalKey<Mob> key;
    private Player target;
    private int lookTicks;
    private int nextTemptTick;

    static void install(MobGoals goals, Mob mob, Predicate<Player> hidden) {
        // 토끼처럼 원래 쳐다보기 Goal이 없는 몹에는 새 행동을 추가하지 않습니다.
        if (goals.hasGoal((Mob) mob, VanillaGoal.LOOK_AT_PLAYER)) {
            goals.removeGoal((Mob) mob, VanillaGoal.LOOK_AT_PLAYER);
            goals.addGoal(mob, 6, new DecoyPlayerGoal(mob, hidden, Mode.WATCH, ThreadLocalRandom.current()));
        }
        if (mob instanceof Animals && goals.hasGoal((Creature) mob, VanillaGoal.TEMPT)) {
            goals.removeGoal((Creature) mob, VanillaGoal.TEMPT);
            goals.addGoal(mob, mob.getType() == EntityType.RABBIT ? 4 : 3,
                    new DecoyPlayerGoal(mob, hidden, Mode.TEMPT, ThreadLocalRandom.current()));
        }
    }

    DecoyPlayerGoal(Mob mob, Predicate<Player> hidden, Mode mode, RandomGenerator random) {
        this.mob = mob;
        this.hidden = hidden;
        this.mode = mode;
        this.random = random;
        key = GoalKey.of(Mob.class, new NamespacedKey("mobhideandseek",
                mode == Mode.WATCH ? "watch_visible_player" : "tempt_visible_player"));
    }

    @Override public boolean shouldActivate() {
        target = null;
        if (mode == Mode.WATCH && random.nextFloat() >= 0.02f) return false;
        if (mode == Mode.TEMPT && mob.getTicksLived() < nextTemptTick) return false;
        double nearest = rangeSquared();
        for (Player player : mob.getWorld().getPlayers()) {
            if (!eligible(player)) continue;
            double distance = mob.getLocation().distanceSquared(player.getLocation());
            if (distance < nearest) { nearest = distance; target = player; }
        }
        return target != null;
    }

    @Override public void start() { lookTicks = 40 + random.nextInt(40); }

    @Override public boolean shouldStayActive() {
        return target != null && (mode == Mode.TEMPT || lookTicks > 0) && eligible(target);
    }

    @Override public void tick() {
        // 활성화 이후 변신/발각/퇴장이 일어난 경우에도 이전 시선 대상을 계속 사용하지 않습니다.
        if (!shouldStayActive()) { stop(); return; }
        mob.lookAt(target, 10, mob.getMaxHeadPitch());
        if (mode == Mode.WATCH) {
            lookTicks--;
        } else if (mob.getLocation().distanceSquared(target.getLocation()) < 6.25) {
            mob.getPathfinder().stopPathfinding();
        } else {
            double speed = switch (mob.getType()) {
                case RABBIT -> 1.0;
                case PIG -> 1.2;
                default -> 1.25;
            };
            mob.getPathfinder().moveTo(target, speed);
        }
    }

    @Override public void stop() {
        target = null;
        lookTicks = 0;
        if (mode == Mode.TEMPT) {
            mob.getPathfinder().stopPathfinding();
            nextTemptTick = mob.getTicksLived() + 100;
        }
    }

    private boolean eligible(Player player) {
        if (hidden.test(player) || !player.isOnline() || !player.isValid() || player.isDead()
                || player.getGameMode() == GameMode.SPECTATOR || player.getWorld() != mob.getWorld()
                || mob.getLocation().distanceSquared(player.getLocation()) > rangeSquared()
                || !mob.hasLineOfSight(player)) return false;
        return mode == Mode.WATCH || (mob instanceof Animals animal
                && (animal.isBreedItem(player.getInventory().getItemInMainHand())
                || animal.isBreedItem(player.getInventory().getItemInOffHand())));
    }

    private double rangeSquared() { return mode == Mode.WATCH ? 36 : 100; }
    @Override public GoalKey<Mob> getKey() { return key; }
    @Override public EnumSet<GoalType> getTypes() {
        return mode == Mode.WATCH ? EnumSet.of(GoalType.LOOK) : EnumSet.of(GoalType.LOOK, GoalType.MOVE);
    }
}
