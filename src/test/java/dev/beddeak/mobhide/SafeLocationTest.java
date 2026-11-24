package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SafeLocationTest {
    private final World world = mock(World.class);
    private final WorldBorder border = mock(WorldBorder.class);
    private final Map<List<Integer>, Block> blocks = new HashMap<>();

    @BeforeEach void setUp() {
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(any(Location.class))).thenReturn(true);
        Block air = block(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                blocks.getOrDefault(List.of(call.getArgument(0), call.getArgument(1), call.getArgument(2)), air));
    }

    @Test void ordinarySolidFloorSupportsAllCurrentMobSizes() {
        place(0, 63, 0, Material.STONE, cube());
        assertTrue(SafeLocation.isSafe(at(0.5, 64, 0.5)));
    }

    @Test void ironGolemNeedsItsOwnWiderAndTallerSpace() {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) place(x, 63, z, Material.STONE, cube());
        place(0, 66, 0, Material.STONE, cube());
        assertTrue(SafeLocation.isSafe(at(0.5, 64, 0.5)), "Cows, players and skeletons fit under a two-block ceiling");
        assertFalse(SafeLocation.isSafe(at(0.5, 64, 0.5), 0.7, 2.7), "A 2.7-block iron golem would clip into it");
        blocks.remove(List.of(0, 66, 0));
        place(1, 64, 0, Material.STONE, cube());
        assertTrue(SafeLocation.isSafe(at(0.5, 64, 0.5)));
        assertFalse(SafeLocation.isSafe(at(0.5, 64, 0.5), 0.7, 2.7), "A 1.4-block wide golem would clip into the wall");
        blocks.remove(List.of(1, 64, 0));
        assertTrue(SafeLocation.isSafe(at(0.5, 64, 0.5), 0.7, 2.7));
    }

    @Test void unsupportedAirIsNotASafeReturnPoint() {
        assertFalse(SafeLocation.isSafe(at(0.5, 64, 0.5)));
        place(0, 62, 0, Material.STONE, cube());
        assertFalse(SafeLocation.isSafe(at(0.5, 64, 0.5)));
    }

    @Test void fractionalPositionBesideWallMustFitTheCowBody() {
        place(1, 63, 0, Material.STONE, cube());
        place(0, 64, 0, Material.STONE, cube());
        assertFalse(SafeLocation.isSafe(at(1.05, 64, 0.5))); // Even a player clips into the wall.
        assertFalse(SafeLocation.isSafe(at(1.35, 64, 0.5))); // A player fits, but a cow/sheep does not.
        assertTrue(SafeLocation.isSafe(at(1.45, 64, 0.5)));  // Touching the wall is allowed.
    }

    @Test void neighboringBlockCanObstructTheHeadEvenWhenTheFootColumnIsEmpty() {
        place(1, 63, 0, Material.STONE, cube());
        place(0, 65, 0, Material.STONE, cube());
        assertFalse(SafeLocation.isSafe(at(1.35, 64, 0.5)));
        assertTrue(SafeLocation.isSafe(at(1.45, 64, 0.5)));
    }

    @Test void diagonalWallIsCheckedAcrossBothHorizontalAxes() {
        place(1, 63, 1, Material.STONE, cube());
        place(0, 64, 0, Material.STONE, cube());
        assertFalse(SafeLocation.isSafe(at(1.35, 64, 1.35)));
    }

    @Test void twoBlocksOfHeadroomFitButBottomCeilingSlabDoesNot() {
        place(0, 63, 0, Material.STONE, cube());
        place(0, 66, 0, Material.STONE, cube());
        assertTrue(SafeLocation.isSafe(at(0.5, 64, 0.5)));
        place(0, 65, 0, Material.STONE_SLAB, new BoundingBox(0, 0, 0, 1, 0.5, 1));
        assertFalse(SafeLocation.isSafe(at(0.5, 64, 0.5)));
    }

    @Test void bottomSlabUsesActualHalfHeightInsteadOfTreatingItAsAFullBlock() {
        place(0, 63, 0, Material.STONE_SLAB, new BoundingBox(0, 0, 0, 1, 0.5, 1));
        assertTrue(SafeLocation.isSafe(at(0.5, 63.5, 0.5)));
        assertFalse(SafeLocation.isSafe(at(0.5, 64, 0.5))); // Floating half a block above it.
        assertFalse(SafeLocation.isSafe(at(0.5, 63.4, 0.5))); // Embedded in the slab.
    }

    @Test void upperStepBesideBottomSlabStillObstructsTheBody() {
        place(1, 63, 0, Material.STONE_SLAB, new BoundingBox(0, 0, 0, 1, 0.5, 1));
        place(0, 63, 0, Material.STONE, cube());
        assertFalse(SafeLocation.isSafe(at(1.35, 63.5, 0.5)));
        assertTrue(SafeLocation.isSafe(at(1.45, 63.5, 0.5)));
    }

    @Test void contactRoundingToleranceDoesNotTurnAJumpIntoASafeFallback() {
        place(0, 63, 0, Material.STONE, cube());
        assertTrue(SafeLocation.isSafe(at(0.5, 64.001, 0.5)));
        assertTrue(SafeLocation.isSafe(at(0.5, 63.999, 0.5)));
        assertFalse(SafeLocation.isSafe(at(0.5, 64.1, 0.5)));
    }

    @Test void fenceShapeCanSupportFeetAboveItsOwningBlock() {
        place(0, 63, 0, Material.OAK_FENCE, new BoundingBox(0.375, 0, 0.375, 0.625, 1.5, 0.625));
        assertTrue(SafeLocation.isSafe(at(0.5, 64.5, 0.5)));
        assertFalse(SafeLocation.isSafe(at(0.5, 64, 0.5)));
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {"MAGMA_BLOCK", "CACTUS", "CAMPFIRE", "SOUL_CAMPFIRE"})
    void hazardousSupportIsRejected(Material hazard) {
        place(0, 63, 0, hazard, cube());
        assertFalse(SafeLocation.isSafe(at(0.5, 64, 0.5)));
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {"FIRE", "SOUL_FIRE", "LAVA", "WATER", "BUBBLE_COLUMN",
            "POWDER_SNOW", "SWEET_BERRY_BUSH", "WITHER_ROSE", "NETHER_PORTAL", "END_PORTAL", "END_GATEWAY"})
    void EmptyCollisionShapeDoesNotMakeAHazardSafe(Material hazard) {
        place(0, 63, 0, Material.STONE, cube());
        place(0, 64, 0, hazard);
        assertFalse(SafeLocation.isSafe(at(0.5, 64, 0.5)));
    }

    @Test void hazardBesideTheFootColumnIsCheckedForTheWholeBody() {
        place(1, 63, 0, Material.STONE, cube());
        place(0, 64, 0, Material.LAVA);
        assertFalse(SafeLocation.isSafe(at(1.35, 64, 0.5)));
        assertTrue(SafeLocation.isSafe(at(1.45, 64, 0.5)));
    }

    @Test void nonCollidingVegetationDoesNotBlockAnOtherwiseSafeSpot() {
        place(0, 63, 0, Material.STONE, cube());
        place(0, 64, 0, Material.SHORT_GRASS);
        assertTrue(SafeLocation.isSafe(at(0.5, 64, 0.5)));
    }

    @Test void entireBodyMustRemainInsideWorldBorder() {
        place(0, 63, 0, Material.STONE, cube());
        when(border.isInside(any(Location.class))).thenAnswer(call -> ((Location) call.getArgument(0)).getX() < 0.8);
        assertFalse(SafeLocation.isSafe(at(0.5, 64, 0.5)));
    }

    @Test void negativeCoordinatesUseTheirOwningBlockOffsets() {
        place(-2, -64, -3, Material.STONE, cube());
        assertTrue(SafeLocation.isSafe(at(-1.5, -63, -2.5)));
    }

    @Test void invalidWorldCoordinatesFailWithoutReadingBlocks() {
        assertFalse(SafeLocation.isSafe(new Location(null, 0, 64, 0)));
        assertFalse(SafeLocation.isSafe(at(Double.NaN, 64, 0)));
        assertFalse(SafeLocation.isSafe(at(0, Double.POSITIVE_INFINITY, 0)));
        assertFalse(SafeLocation.isSafe(at(0, 64, Double.NEGATIVE_INFINITY)));
        assertFalse(SafeLocation.isSafe(at(0, -65, 0)));
        assertFalse(SafeLocation.isSafe(at(0, 319, 0)));
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }

    private Location at(double x, double y, double z) { return new Location(world, x, y, z); }

    private BoundingBox cube() { return new BoundingBox(0, 0, 0, 1, 1, 1); }

    private void place(int x, int y, int z, Material material, BoundingBox... shapes) {
        blocks.put(List.of(x, y, z), block(material, shapes));
    }

    private Block block(Material material, BoundingBox... shapes) {
        Block block = mock(Block.class);
        VoxelShape shape = mock(VoxelShape.class);
        when(block.getType()).thenReturn(material);
        when(shape.getBoundingBoxes()).thenReturn(List.of(shapes));
        when(block.getCollisionShape()).thenReturn(shape);
        return block;
    }
}
