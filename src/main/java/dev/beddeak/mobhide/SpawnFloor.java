package dev.beddeak.mobhide;

import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.World;

/** 경기장 경계와 별개인 소환 높이 안에서 기준층에 가까운 안전한 바닥을 찾습니다. */
final class SpawnFloor {
    private SpawnFloor() {}

    static Location find(World world, double x, double z, int minY, int maxY, int preferredY,
                         Predicate<Location> allowed) {
        if (minY > maxY) return null;
        int reference = Math.max(minY, Math.min(maxY, preferredY));
        int distance = Math.max(reference - minY, maxY - reference);
        for (int offset = 0; offset <= distance; offset++) {
            int below = reference - offset, above = reference + offset;
            if (below >= minY) {
                Location point = new Location(world, x, below, z);
                if (allowed.test(point)) return point;
            }
            if (offset > 0 && above <= maxY) {
                Location point = new Location(world, x, above, z);
                if (allowed.test(point)) return point;
            }
        }
        return null;
    }
}
