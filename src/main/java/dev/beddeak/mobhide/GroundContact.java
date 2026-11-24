package dev.beddeak.mobhide;

import org.bukkit.entity.Player;

/** Determines visual ground contact from the supporting block shapes, not a stale player flag. */
final class GroundContact {
    private static final double CONTACT_TOLERANCE = 0.025;
    private static final double EDGE_EPSILON = 0.000001;
    // Fences and walls can have collision boxes extending above their block's unit cube.
    private static final double MAX_SUPPORT_HEIGHT = 1.5;

    private GroundContact() {}

    static boolean grounded(Player player) {
        if (player.getVelocity().getY() > 0.08) return false;

        var feetBox = player.getBoundingBox();
        double feet = feetBox.getMinY();
        int minY = (int) Math.floor(feet - MAX_SUPPORT_HEIGHT - CONTACT_TOLERANCE);
        int maxY = (int) Math.floor(feet + CONTACT_TOLERANCE);
        var world = player.getWorld();
        for (int x = (int) Math.floor(feetBox.getMinX() + EDGE_EPSILON);
             x <= (int) Math.floor(feetBox.getMaxX() - EDGE_EPSILON); x++) {
            for (int z = (int) Math.floor(feetBox.getMinZ() + EDGE_EPSILON);
                 z <= (int) Math.floor(feetBox.getMaxZ() - EDGE_EPSILON); z++) {
                for (int y = minY; y <= maxY; y++) {
                    // Bukkit collision-shape boxes are local to their owning block.
                    for (var support : world.getBlockAt(x, y, z).getCollisionShape().getBoundingBoxes()) {
                        double gap = feet - (y + support.getMaxY());
                        if (Math.abs(gap) <= CONTACT_TOLERANCE
                                && x + support.getMaxX() > feetBox.getMinX() + EDGE_EPSILON
                                && x + support.getMinX() < feetBox.getMaxX() - EDGE_EPSILON
                                && z + support.getMaxZ() > feetBox.getMinZ() + EDGE_EPSILON
                                && z + support.getMinZ() < feetBox.getMaxZ() - EDGE_EPSILON) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
