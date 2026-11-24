package dev.beddeak.mobhide;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class CuboidBoundaryTest {
    @Test void eitherCornerOrderIncludesBothSelectedBlocks() {
        var box = CuboidBoundary.between(10, 60, -20, 0, 70, -10);
        assertEquals(new CuboidBoundary(0, 60, -20, 11, 71, -9), box);
        assertTrue(box.contains(10.99, 70.99, -9.01));
        assertFalse(box.contains(11, 64, -15));
        assertFalse(box.contains(1, 71, -15));
    }
    @Test void eachFaceReturnsOneBlockInWithoutRecenteringOtherAxes() {
        var box = new CuboidBoundary(-10, -64, -20, 10, 320, 20);
        assertEquals(new ArenaBoundary.Point(-9, 64, 5), box.inset(-30, 64, 5));
        assertEquals(new ArenaBoundary.Point(9, 64, 5), box.inset(30, 64, 5));
        assertEquals(new ArenaBoundary.Point(4, -63, 5), box.inset(4, -100, 5));
        assertEquals(new ArenaBoundary.Point(4, 319, 5), box.inset(4, 400, 5));
        assertEquals(new ArenaBoundary.Point(4, 64, -19), box.inset(4, 64, -30));
        assertEquals(new ArenaBoundary.Point(4, 64, 19), box.inset(4, 64, 30));
    }
    @Test void cornersAndLongTeleportsStillResolveInside() {
        var box = new CuboidBoundary(100, 50, 200, 120, 80, 250);
        var point = box.inset(-10000, 900, 99999);
        assertEquals(new ArenaBoundary.Point(101, 79, 249), point);
        assertTrue(box.contains(point.x(), point.y(), point.z()));
    }
    @Test void interiorPointsRemainUnchanged() {
        var box = new CuboidBoundary(0, 0, 0, 8, 8, 8);
        assertEquals(new ArenaBoundary.Point(3.2, 4.5, 2.1), box.inset(3.2, 4.5, 2.1));
    }
    @Test void flatOrOversizedSelectionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> CuboidBoundary.between(0, 64, 0, 10, 64, 10));
        assertThrows(IllegalArgumentException.class, () -> new CuboidBoundary(0, 0, 0, 257, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> new CuboidBoundary(Integer.MIN_VALUE, 0, 0, Integer.MAX_VALUE, 10, 10));
    }
}
