package dev.beddeak.mobhide;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

/** Shared spawn/return clearance for every currently supported disguise, including the seeker. */
final class SafeLocation {
    // Cow/sheep are the widest supported mobs; the skeleton is the tallest.
    private static final double HALF_WIDTH = 0.45;
    private static final double HEIGHT = 1.99;
    private static final double CONTACT_TOLERANCE = 0.025;
    private static final double EPSILON = 0.000001;

    private SafeLocation() {}

    static boolean isSafe(Location location) {
        return isSafe(location, HALF_WIDTH, HEIGHT);
    }

    /** 공통 기준보다 큰 몹(철골렘)의 몸 크기로 같은 검사를 합니다. */
    static boolean isSafe(Location location, double halfWidth, double height) {
        World world = location.getWorld();
        double x = location.getX(), feet = location.getY(), z = location.getZ();
        if (world == null || !Double.isFinite(x) || !Double.isFinite(feet) || !Double.isFinite(z)
                || feet < world.getMinHeight() || feet + height > world.getMaxHeight()) return false;

        double minX = x - halfWidth, maxX = x + halfWidth;
        double minZ = z - halfWidth, maxZ = z + halfWidth;
        for (double edgeX : new double[] {minX, maxX}) {
            for (double edgeZ : new double[] {minZ, maxZ}) {
                if (!world.getWorldBorder().isInside(new Location(world, edgeX, feet, edgeZ))) return false;
            }
        }

        BoundingBox body = new BoundingBox(minX, feet + CONTACT_TOLERANCE, minZ, maxX, feet + height, maxZ);
        boolean supported = false;
        // Fences and walls extend 1.5 blocks above their owning block.
        int minY = Math.max(world.getMinHeight(), (int) Math.floor(feet - 1.5 - CONTACT_TOLERANCE));
        int maxY = Math.min(world.getMaxHeight() - 1, (int) Math.floor(feet + height - EPSILON));
        for (int bx = (int) Math.floor(minX + EPSILON); bx <= (int) Math.floor(maxX - EPSILON); bx++) {
            for (int bz = (int) Math.floor(minZ + EPSILON); bz <= (int) Math.floor(maxZ - EPSILON); bz++) {
                for (int by = minY; by <= maxY; by++) {
                    var block = world.getBlockAt(bx, by, bz);
                    boolean hazardous = hazardous(block.getType());
                    // Fire, liquids, plants and portals may have no collision shape at all.
                    if (hazardous && by + 1 > feet + EPSILON && by < feet + height - EPSILON) return false;
                    for (var local : block.getCollisionShape().getBoundingBoxes()) {
                        BoundingBox shape = local.clone().shift(bx, by, bz);
                        boolean beneathFeet = Math.abs(feet - shape.getMaxY()) <= CONTACT_TOLERANCE
                                && shape.getMaxX() > minX + EPSILON && shape.getMinX() < maxX - EPSILON
                                && shape.getMaxZ() > minZ + EPSILON && shape.getMinZ() < maxZ - EPSILON;
                        if (beneathFeet) {
                            if (hazardous) return false;
                            supported = true;
                        }
                        if (body.overlaps(shape)) return false;
                    }
                }
            }
        }
        return supported;
    }

    private static boolean hazardous(Material material) {
        return switch (material) {
            case MAGMA_BLOCK, CACTUS, CAMPFIRE, SOUL_CAMPFIRE, FIRE, SOUL_FIRE,
                    LAVA, WATER, BUBBLE_COLUMN, POWDER_SNOW, SWEET_BERRY_BUSH, WITHER_ROSE,
                    NETHER_PORTAL, END_PORTAL, END_GATEWAY -> true;
            default -> false;
        };
    }
}
