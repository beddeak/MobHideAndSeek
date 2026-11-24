package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class MobMovementTest {
    @Test void allSupportedMobsMoveSlowerThanNormalPlayers() {
        for (EntityType type : MobCatalog.ALL) {
            assertTrue(MobMovement.walkSpeed(type) > 0);
            assertTrue(MobMovement.walkSpeed(type) < 0.2f);
        }
        assertThrows(IllegalArgumentException.class, () -> MobMovement.walkSpeed(EntityType.PLAYER));
    }

    @Test void onlyMovingGroundedRabbitsHop() {
        assertTrue(MobMovement.shouldHop(EntityType.RABBIT, true, true, false, 20, 0));
        assertFalse(MobMovement.shouldHop(EntityType.COW, true, true, false, 20, 0));
        assertFalse(MobMovement.shouldHop(EntityType.RABBIT, false, true, false, 20, 0));
        assertFalse(MobMovement.shouldHop(EntityType.RABBIT, true, false, false, 20, 0));
        assertFalse(MobMovement.shouldHop(EntityType.RABBIT, true, true, true, 20, 0));
    }

    @Test void hopCooldownPreventsEveryTickJumpSpam() {
        assertFalse(MobMovement.shouldHop(EntityType.RABBIT, true, true, false, 29, 20));
        assertTrue(MobMovement.shouldHop(EntityType.RABBIT, true, true, false, 30, 20));
        assertTrue(MobMovement.shouldHop(EntityType.RABBIT, true, true, false, 1, -10));
    }

    @Test void facingUsesMovementRatherThanThePlayersCamera() {
        assertEquals(0, MobMovement.facing(0, 1), 1e-5);
        assertEquals(-90, MobMovement.facing(1, 0), 1e-5);
        assertEquals(90, MobMovement.facing(-1, 0), 1e-5);
        assertEquals(180, Math.abs(MobMovement.facing(0, -1)), 1e-5);
    }
    @Test void turningCrossesTheYawWrapUsingTheShortestDirection() {
        assertEquals(181, MobMovement.turn(179, -179), 1e-5);
        assertEquals(-181, MobMovement.turn(-179, 179), 1e-5);
        assertEquals(45, MobMovement.turn(0, 170), 1e-5);
        assertEquals(30, MobMovement.turn(0, 30), 1e-5);
    }
}
