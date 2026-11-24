package dev.beddeak.mobhide;

import org.bukkit.entity.EntityType;

final class MobMovement {
    static final int HOP_INTERVAL = 10;

    static float walkSpeed(EntityType type) {
        return switch (type) {
            case COW, PIG, MOOSHROOM -> 0.10f;
            case SHEEP -> 0.09f;
            case CHICKEN -> 0.075f;
            case RABBIT -> 0.095f;
            case ZOMBIE, HUSK, VILLAGER -> 0.10f;
            case SKELETON, STRAY, CREEPER, CAT -> 0.11f;
            // 철골렘은 마을을 천천히 순찰하고, 늑대는 다른 동물보다 빠르게 걷습니다.
            case IRON_GOLEM -> 0.08f;
            case WOLF -> 0.12f;
            default -> throw new IllegalArgumentException("Unsupported disguise movement: " + type);
        };
    }

    static boolean shouldHop(EntityType type, boolean moving, boolean grounded, boolean inWater,
                             long tick, long lastHop) {
        return type == EntityType.RABBIT && moving && grounded && !inWater && tick - lastHop >= HOP_INTERVAL;
    }

    static float facing(double dx, double dz) { return (float) Math.toDegrees(Math.atan2(-dx, dz)); }
    static float normalize(float yaw) { return (float) Math.IEEEremainder(yaw, 360); }
    // 바닐라 AI는 이동 방향으로 틱당 최대 90도까지 돕니다. 15도는 방향을 바꿀 때마다 0.5초 넘게
    // 옆걸음질처럼 보였습니다. 클라이언트가 3틱에 걸쳐 회전을 보간하므로 45도도 부드럽게 보입니다.
    static final float MAX_TURN = 45;

    static float turn(float current, float desired) {
        float delta = (float) Math.IEEEremainder(desired - current, 360);
        return current + Math.max(-MAX_TURN, Math.min(MAX_TURN, delta));
    }
}
