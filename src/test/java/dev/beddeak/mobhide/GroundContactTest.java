package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.util.List;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.bukkit.util.VoxelShape;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GroundContactTest {
    private final Player player = mock(Player.class);
    private final World world = mock(World.class);

    @BeforeEach void setUp() {
        when(player.getWorld()).thenReturn(world);
        when(player.getVelocity()).thenReturn(new Vector(0, -0.078, 0));
        Block empty = block();
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(empty);
        feet(0.2, 64, 0.2, 0.8, 0.8);
    }

    @Test void contactWorksWithoutThePlayersGroundFlag() {
        support(0, 63, 0, new BoundingBox(0, 0, 0, 1, 1, 1));
        when(player.isOnGround()).thenReturn(false);
        assertTrue(GroundContact.grounded(player));
    }

    @Test void staleGroundFlagDoesNotKeepAPlayerGroundedOverAnEdge() {
        when(player.isOnGround()).thenReturn(true);
        assertFalse(GroundContact.grounded(player));
    }

    @Test void jumpingWinsEvenWhenFeetStillTouchTheFloor() {
        support(0, 63, 0, new BoundingBox(0, 0, 0, 1, 1, 1));
        when(player.isOnGround()).thenReturn(true);
        when(player.getVelocity()).thenReturn(new Vector(0, 0.25, 0));
        assertFalse(GroundContact.grounded(player));
    }

    @Test void airborneFallingDoesNotCountTheFloorAsContact() {
        feet(0.2, 64.1, 0.2, 0.8, 0.8);
        support(0, 63, 0, new BoundingBox(0, 0, 0, 1, 1, 1));
        when(player.getVelocity()).thenReturn(new Vector(0, -0.8, 0));
        assertFalse(GroundContact.grounded(player));
    }

    @Test void smallRoundingErrorsAtAContactSurfaceAreTolerated() {
        feet(0.2, 64.001, 0.2, 0.8, 0.8);
        support(0, 63, 0, new BoundingBox(0, 0, 0, 1, 1, 1));
        assertTrue(GroundContact.grounded(player));
        feet(0.2, 63.999, 0.2, 0.8, 0.8);
        assertTrue(GroundContact.grounded(player));
    }

    @Test void bottomSlabUsesItsHalfBlockSurface() {
        feet(0.2, 63.5, 0.2, 0.8, 0.8);
        support(0, 63, 0, new BoundingBox(0, 0, 0, 1, 0.5, 1));
        assertTrue(GroundContact.grounded(player));
    }

    @Test void stairsOnlySupportTheFeetOverTheCorrectStep() {
        support(0, 63, 0, new BoundingBox(0, 0, 0, 1, 0.5, 1),
                new BoundingBox(0.5, 0.5, 0, 1, 1, 1));
        feet(0.1, 63.5, 0.2, 0.4, 0.8);
        assertTrue(GroundContact.grounded(player));
        feet(0.1, 64, 0.2, 0.4, 0.8);
        assertFalse(GroundContact.grounded(player));
        feet(0.6, 64, 0.2, 0.9, 0.8);
        assertTrue(GroundContact.grounded(player));
    }

    @Test void fenceSupportsFeetAboveTheBlockBeingScanned() {
        feet(0.3, 64.5, 0.3, 0.7, 0.7);
        support(0, 63, 0, new BoundingBox(0.375, 0, 0.375, 0.625, 1.5, 0.625));
        assertTrue(GroundContact.grounded(player));
    }

    @Test void wallSupportWorksAtNegativeBlockCoordinates() {
        feet(-1.8, -62.5, -2.8, -1.2, -2.2);
        support(-2, -64, -3, new BoundingBox(0.25, 0, 0.25, 0.75, 1.5, 0.75));
        assertTrue(GroundContact.grounded(player));
    }

    @Test void nearbyCollisionWithoutHorizontalOverlapDoesNotSupportFeet() {
        feet(0.1, 64.5, 0.1, 0.3, 0.3);
        support(0, 63, 0, new BoundingBox(0.375, 0, 0.375, 0.625, 1.5, 0.625));
        assertFalse(GroundContact.grounded(player));
    }

    @Test void touchingTheSideOfTheBlockDoesNotCreateGroundContact() {
        feet(0.5, 64, 0.2, 0.8, 0.8);
        support(0, 63, 0, new BoundingBox(0, 0, 0, 0.5, 1, 1));
        assertFalse(GroundContact.grounded(player));
    }

    @Test void emptyWaterCollisionDoesNotCountAsGround() {
        when(player.isInWater()).thenReturn(true);
        when(player.isOnGround()).thenReturn(true);
        assertFalse(GroundContact.grounded(player));
    }

    private void feet(double minX, double y, double minZ, double maxX, double maxZ) {
        when(player.getBoundingBox()).thenReturn(new BoundingBox(minX, y, minZ, maxX, y + 0.7, maxZ));
    }

    private void support(int x, int y, int z, BoundingBox... boxes) {
        Block support = block(boxes);
        when(world.getBlockAt(x, y, z)).thenReturn(support);
    }

    private Block block(BoundingBox... boxes) {
        Block block = mock(Block.class);
        VoxelShape shape = mock(VoxelShape.class);
        when(shape.getBoundingBoxes()).thenReturn(List.of(boxes));
        when(block.getCollisionShape()).thenReturn(shape);
        return block;
    }
}
