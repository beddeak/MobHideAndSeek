package dev.beddeak.mobhide;

import java.util.Objects;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

/** 실제 위치에서 회전과 도약을 결정합니다. 점프 요청과 관측한 도약은 구분합니다. */
final class MobAnimation {
    /** stepX/stepZ는 이번 틱의 실제 수평 이동입니다. 토끼 도약이 앞으로 가던 속도를 잃지 않게 씁니다. */
    record Frame(float yaw, float headYaw, float pitch, boolean grounded, boolean hop, boolean launchHop, double stepX, double stepZ) {}
    private static final float MAX_HEAD_OFFSET = 42;
    private static final float HEAD_TURN_SPEED = 6;
    private static final float PITCH_TURN_SPEED = 1.2f;
    final EntityType type;
    private final RandomGenerator random;
    private Location previous;
    private float yaw, targetYaw;
    private float headYaw, pitch, targetHeadOffset, targetPitch;
    private long gazeDeadline = Long.MIN_VALUE;
    private boolean glancing;
    private boolean armed, pendingHop, reset;
    private int groundTicks;
    private long lastHop = -MobMovement.HOP_INTERVAL;
    private long requestedAt;

    MobAnimation(EntityType type, Location position, boolean grounded) {
        this(type, position, grounded, new Random());
    }

    MobAnimation(EntityType type, Location position, boolean grounded, RandomGenerator random) {
        this.type = type;
        this.random = Objects.requireNonNull(random);
        previous = position.clone();
        yaw = targetYaw = MobMovement.normalize(position.getYaw());
        headYaw = yaw;
        armed = grounded;
        groundTicks = grounded ? 2 : 0;
    }

    void reset(Location position) {
        previous = position.clone();
        yaw = targetYaw = MobMovement.normalize(position.getYaw());
        headYaw = yaw;
        pitch = 0;
        resetGaze();
        armed = pendingHop = false;
        groundTicks = 0;
        reset = true;
    }

    Frame sample(Location at, boolean grounded, boolean swimmingOrClimbing, long tick) {
        return sample(at, grounded, swimmingOrClimbing, true, tick);
    }

    /**
     * steering은 이동 키를 누르고 있는지입니다. AI는 밀려도 몸을 돌리지 않으므로, 다른 몹에 밀리거나
     * 키를 놓고 미끄러지는 이동은 몸 방향·두리번거림 중단·토끼 자동 도약에 쓰지 않습니다.
     */
    Frame sample(Location at, boolean grounded, boolean swimmingOrClimbing, boolean steering, long tick) {
        double dx = at.getX() - previous.getX(), dy = at.getY() - previous.getY(), dz = at.getZ() - previous.getZ();
        if (at.getWorld() != previous.getWorld() || dx * dx + dy * dy + dz * dz > 16) reset(at);
        if (reset) {
            reset = false;
            previous = at.clone();
            armed = grounded;
            groundTicks = grounded ? 1 : 0;
            lastHop = tick;
            return new Frame(yaw, headYaw, pitch, grounded, false, false, 0, 0);
        }
        boolean moving = steering && dx * dx + dz * dz > 0.000001;
        if (moving) targetYaw = MobMovement.facing(dx, dz);
        // 멈춰도 남은 회전을 마무리하며 5도 이하의 회전도 버리지 않습니다.
        yaw = MobMovement.normalize(MobMovement.turn(yaw, targetYaw));
        updateGaze(!moving && grounded && !swimmingOrClimbing, tick);
        boolean hop = false, launch = false;
        if (grounded) {
            // 하강 뒤 실제 착지는 즉시 다음 수동 점프를 허용합니다. 상승 중 한 틱의
            // 잘못된 접지 표본은 여전히 두 번 연속 확인해야 도약을 재활성화합니다.
            if (++groundTicks >= 2 || dy < -0.01) armed = true;
        } else groundTicks = 0;
        if (swimmingOrClimbing) {
            pendingHop = armed = false;
        } else if (type == EntityType.RABBIT) {
            if (!grounded && armed && dy > 0.01) {
                // 속도를 설정한 때가 아니라 실제로 발이 뜬 시점에 한 번만 반응합니다.
                hop = true;
                armed = pendingHop = false;
                lastHop = tick;
            } else if (!grounded && dy < -0.01) armed = pendingHop = false;
            if (pendingHop && tick - requestedAt > 4) pendingHop = false;
            if (armed && !pendingHop && groundTicks >= 2
                    && MobMovement.shouldHop(type, moving, grounded, false, tick, lastHop)) {
                pendingHop = true;
                requestedAt = lastHop = tick;
                launch = true;
            }
        }
        previous = at.clone();
        return new Frame(yaw, headYaw, pitch, grounded, hop, launch, dx, dz);
    }

    private void updateGaze(boolean idle, long tick) {
        if (!idle) resetGaze();
        else if (gazeDeadline == Long.MIN_VALUE) gazeDeadline = tick + nextGazeWait();
        else if (tick >= gazeDeadline) {
            glancing = !glancing;
            if (glancing) {
                // 개체마다 다른 시점에 고개만 돌립니다. 플레이어 시점은 몸이나 고개에 반영하지 않습니다.
                targetHeadOffset = (random.nextBoolean() ? 1 : -1) * (18 + random.nextInt(25));
                targetPitch = random.nextInt(15) - 8;
                // 긴 고정 응시는 클라이언트의 몸통 추종을 유발하므로 짧게 둘러보고 돌아옵니다.
                gazeDeadline = tick + 10 + random.nextInt(15);
            } else {
                targetHeadOffset = targetPitch = 0;
                gazeDeadline = tick + nextGazeWait();
            }
        }
        float desiredHeadYaw = MobMovement.normalize(yaw + targetHeadOffset);
        float delta = MobMovement.normalize(desiredHeadYaw - headYaw);
        headYaw = MobMovement.normalize(headYaw + clamp(delta, HEAD_TURN_SPEED));
        // 이동 방향이 급변해도 머리가 몸의 뒤쪽을 바라보는 프레임을 내보내지 않습니다.
        headYaw = MobMovement.normalize(yaw + clamp(MobMovement.normalize(headYaw - yaw), MAX_HEAD_OFFSET));
        pitch += clamp(targetPitch - pitch, PITCH_TURN_SPEED);
    }

    private int nextGazeWait() { return 25 + random.nextInt(46); }

    private void resetGaze() {
        gazeDeadline = Long.MIN_VALUE;
        glancing = false;
        targetHeadOffset = targetPitch = 0;
    }

    private static float clamp(float value, float limit) {
        return Math.max(-limit, Math.min(limit, value));
    }
}
