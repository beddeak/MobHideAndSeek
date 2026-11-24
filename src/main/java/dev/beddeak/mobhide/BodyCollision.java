package dev.beddeak.mobhide;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

final class BodyCollision {
    /** 이동 경로가 몸을 가로지르면 진입한 옆면까지 되돌립니다. 위로 넘는 이동은 허용합니다. */
    static Vector correction(BoundingBox from, BoundingBox attempted, BoundingBox obstacle) {
        if (!from.overlaps(obstacle)) return sweep(from, attempted, obstacle);
        // 겹쳐 소환된 경우 밖으로 나갈 수 있어야 합니다.
        if (!attempted.overlaps(obstacle) || horizontalDistance(attempted, obstacle) >= horizontalDistance(from, obstacle))
            return new Vector();
        double left = obstacle.getMinX() - attempted.getMaxX() - 0.01;
        double right = obstacle.getMaxX() - attempted.getMinX() + 0.01;
        double north = obstacle.getMinZ() - attempted.getMaxZ() - 0.01;
        double south = obstacle.getMaxZ() - attempted.getMinZ() + 0.01;
        double dx = Math.abs(left) < Math.abs(right) ? left : right;
        double dz = Math.abs(north) < Math.abs(south) ? north : south;
        return Math.abs(dx) <= Math.abs(dz) ? new Vector(dx, 0, 0) : new Vector(0, 0, dz);
    }

    private static Vector sweep(BoundingBox from, BoundingBox attempted, BoundingBox obstacle) {
        double dx = attempted.getMinX() - from.getMinX(), dy = attempted.getMinY() - from.getMinY(), dz = attempted.getMinZ() - from.getMinZ();
        var x = interval(from.getMinX(), from.getMaxX(), obstacle.getMinX(), obstacle.getMaxX(), dx);
        var y = interval(from.getMinY(), from.getMaxY(), obstacle.getMinY(), obstacle.getMaxY(), dy);
        var z = interval(from.getMinZ(), from.getMaxZ(), obstacle.getMinZ(), obstacle.getMaxZ(), dz);
        double horizontalEntry = Math.max(x.entry(), z.entry());
        double entry = Math.max(horizontalEntry, y.entry()), exit = Math.min(x.exit(), Math.min(y.exit(), z.exit()));
        if (entry < 0 || entry > 1 || entry >= exit || y.entry() > horizontalEntry) return new Vector();
        // 처음 접촉한 면에서 멈추고, 그 면을 따라 움직이는 방향은 유지합니다.
        if (x.entry() >= z.entry()) return new Vector(dx > 0
                ? obstacle.getMinX() - attempted.getMaxX() - 0.01
                : obstacle.getMaxX() - attempted.getMinX() + 0.01, 0, 0);
        return new Vector(0, 0, dz > 0
                ? obstacle.getMinZ() - attempted.getMaxZ() - 0.01
                : obstacle.getMaxZ() - attempted.getMinZ() + 0.01);
    }

    private record Interval(double entry, double exit) {}

    private static Interval interval(double min, double max, double obstacleMin, double obstacleMax, double movement) {
        if (movement == 0) return max <= obstacleMin || min >= obstacleMax
                ? new Interval(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)
                : new Interval(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        double first = (obstacleMin - max) / movement, last = (obstacleMax - min) / movement;
        return new Interval(Math.min(first, last), Math.max(first, last));
    }
    static final double SEPARATION_SPEED = 0.15;

    /**
     * 이미 겹친 두 몸을 수평으로 떼어 놓는 속도. a에 더하고 b에는 반대로 줍니다.
     * 중심이 같으면 flip으로 정한 고정 방향으로 나눠 두 사람이 서로 반대로 움직입니다.
     */
    static Vector separation(BoundingBox a, BoundingBox b, boolean flip) {
        double dx = a.getCenterX() - b.getCenterX(), dz = a.getCenterZ() - b.getCenterZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1e-4) { dx = flip ? -1 : 1; dz = 0; length = 1; }
        return new Vector(dx / length * SEPARATION_SPEED, 0, dz / length * SEPARATION_SPEED);
    }

    private static double horizontalDistance(BoundingBox a, BoundingBox b) {
        double dx = a.getCenterX() - b.getCenterX(), dz = a.getCenterZ() - b.getCenterZ();
        return dx * dx + dz * dz;
    }
}
