package dev.beddeak.mobhide;
import static org.junit.jupiter.api.Assertions.*;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
class BodyCollisionTest {
    private final BoundingBox mob = new BoundingBox(0, 64, 0, 0.8, 65.4, 0.8);
    @Test void walkingIntoAMobStopsAtItsBody() {
        var from = new BoundingBox(-1, 64, 0.1, -0.4, 65.8, 0.7);
        var attempted = from.clone().shift(0.6, 0, 0);
        var correction = BodyCollision.correction(from, attempted, mob);
        assertTrue(correction.getX() < 0);
        assertFalse(attempted.shift(correction).overlaps(mob));
    }
    @Test void walkingAlongsideDoesNotCausePushback() {
        var from = new BoundingBox(-1, 64, 2, -0.4, 65.8, 2.6);
        assertEquals(new Vector(), BodyCollision.correction(from, from.clone().shift(1, 0, 0), mob));
    }
    @Test void jumpingAboveASmallMobIsAllowed() {
        var from = new BoundingBox(-1, 66, 0, -0.4, 67.8, 0.6);
        assertEquals(new Vector(), BodyCollision.correction(from, from.clone().shift(1, 0, 0), mob));
    }
    @Test void OverlappingSpawnCanMoveOut() {
        var from = new BoundingBox(0.1, 64, 0.1, 0.7, 65.8, 0.7);
        assertEquals(new Vector(), BodyCollision.correction(from, from.clone().shift(-0.2, 0, 0), mob));
    }

    @Test void boostedMovementCannotSkipThroughTheWholeBody() {
        var from = new BoundingBox(-1, 64, 0.1, -0.4, 65.8, 0.7);
        var attempted = from.clone().shift(2.5, 0, 0);
        assertFalse(attempted.overlaps(mob));
        var stopped = attempted.shift(BodyCollision.correction(from, attempted, mob));
        assertTrue(stopped.getMaxX() < mob.getMinX());
    }

    @Test void deepOverlapStopsAtTheEntrySideInsteadOfPushingThrough() {
        var from = new BoundingBox(-1, 64, 0.1, -0.4, 65.8, 0.7);
        var attempted = from.clone().shift(1.5, 0, 0);
        var stopped = attempted.shift(BodyCollision.correction(from, attempted, mob));
        assertTrue(stopped.getMaxX() < mob.getMinX());
    }

    @Test void fastMovementIsBlockedFromEveryHorizontalDirection() {
        for (Vector direction : new Vector[] {new Vector(1, 0, 0), new Vector(-1, 0, 0),
                new Vector(0, 0, 1), new Vector(0, 0, -1)}) {
            var from = new BoundingBox(0.1, 64, 0.1, 0.7, 65.8, 0.7).shift(direction.clone().multiply(-2));
            var attempted = from.clone().shift(direction.clone().multiply(4));
            var correction = BodyCollision.correction(from, attempted, mob);
            assertTrue(correction.dot(direction) < 0, direction.toString());
            assertFalse(attempted.shift(correction).overlaps(mob));
        }
    }

    @Test void jumpingOverTheBodyAndDiagonalNearMissRemainAllowed() {
        var from = new BoundingBox(-1, 64, 0.1, -0.4, 65.8, 0.7);
        assertEquals(new Vector(), BodyCollision.correction(from, from.clone().shift(0.5, 2, 0), mob));
        var beside = new BoundingBox(-2, 64, 1, -1.4, 65.8, 1.6);
        assertEquals(new Vector(), BodyCollision.correction(beside, beside.clone().shift(3, 0, 2), mob));
    }

    @Test void overlappingBodiesAreSeparatedAwayFromEachOther() {
        var inside = new BoundingBox(0.3, 64, 0.2, 0.9, 65.8, 0.8);
        var away = BodyCollision.separation(inside, mob, false);
        assertTrue(away.getX() > 0, "a는 b 중심에서 멀어지는 쪽(+x)으로 밀려야 합니다");
        assertEquals(0, away.getY());
        assertEquals(BodyCollision.SEPARATION_SPEED, away.length(), 1e-9);
    }

    @Test void coincidentCentersStillSeparateDeterministically() {
        var same = mob.clone();
        var first = BodyCollision.separation(same, mob, false);
        var second = BodyCollision.separation(same, mob, true);
        assertEquals(BodyCollision.SEPARATION_SPEED, first.getX(), 1e-9);
        assertEquals(-BodyCollision.SEPARATION_SPEED, second.getX(), 1e-9);
        assertEquals(0, first.getZ());
    }
}
