package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ArenaBoundaryTest {
    private final ArenaBoundary arena = new ArenaBoundary(100, 64, -200, 20);

    @Test void exactCircleAndHeightEdgesAreInside() {
        assertTrue(arena.contains(120, 80, -200));
        assertTrue(arena.contains(80, 48, -200));
        assertFalse(arena.contains(120.01, 64, -200));
        assertFalse(arena.contains(100, 80.01, -200));
        assertFalse(arena.contains(100, 47.99, -200));
    }

    @Test void crossingTheEdgeReturnsOneBlockInsideInsteadOfTheCenter() {
        ArenaBoundary.Point point = arena.inset(120.1, 64, -200);
        assertEquals(119, point.x(), 1e-9);
        assertEquals(-200, point.z(), 1e-9);
        assertEquals(64, point.y(), 1e-9);
        assertTrue(arena.contains(point.x(), point.y(), point.z()));
    }

    @Test void diagonalCrossingKeepsTheDirectionFromTheCenter() {
        ArenaBoundary.Point point = arena.inset(130, 64, -170);
        assertEquals(19, Math.hypot(point.x() - 100, point.z() + 200), 1e-9);
        assertEquals(point.x() - 100, point.z() + 200, 1e-9);
    }

    @Test void farOutsidePositionsAlsoReturnToTheNearestEdge() {
        ArenaBoundary.Point point = arena.inset(-1000, 64, -200);
        assertEquals(81, point.x(), 1e-9);
        assertEquals(-200, point.z(), 1e-9);
    }

    @Test void verticalCrossingReturnsOneBlockBelowOrAboveTheHeightEdge() {
        assertEquals(79, arena.inset(100, 90, -200).y(), 1e-9);
        assertEquals(49, arena.inset(100, 30, -200).y(), 1e-9);
        assertEquals(100, arena.inset(100, 90, -200).x(), 1e-9);
    }

    @Test void anInsidePositionDoesNotMove() {
        assertEquals(new ArenaBoundary.Point(103, 65, -204), arena.inset(103, 65, -204));
    }

    @Test void everyCrossingAngleStaysOneBlockInside() {
        Random random = new Random(42);
        for (int i = 0; i < 200; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 20.01 + random.nextDouble() * 100;
            ArenaBoundary.Point point = arena.inset(100 + Math.cos(angle) * distance, 64, -200 + Math.sin(angle) * distance);
            assertTrue(arena.contains(point.x(), point.y(), point.z()));
            assertEquals(19, Math.hypot(point.x() - 100, point.z() + 200), 1e-8);
        }
    }

    @Test void changingTheRadiusChangesTheBoundaryAndReturnDistance() {
        ArenaBoundary wider = new ArenaBoundary(100, 64, -200, 30);
        assertTrue(wider.contains(125, 64, -200));
        assertFalse(arena.contains(125, 64, -200));
        assertEquals(129, wider.inset(131, 64, -200).x(), 1e-9);
    }
}
