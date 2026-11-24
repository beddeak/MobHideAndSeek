package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class SpawnFloorTest {
    private final World world = mock(World.class);

    @Test void groundIsPreferredOverARoofInTheSameColumn() {
        var spot = SpawnFloor.find(world, 815.5, 47.5, 60, 80, 64, p -> p.getY() == 64 || p.getY() == 72);
        assertNotNull(spot); assertEquals(64, spot.getY());
    }
    @Test void anIndoorFloorIsFoundEvenWithAHighCaveCeiling() {
        var spot = SpawnFloor.find(world, 550.5, 47.5, 64, 64, 64, p -> p.getY() == 64 || p.getY() == 84);
        assertNotNull(spot); assertEquals(64, spot.getY());
    }
    @Test void blockedGroundDoesNotFallBackToTheRoofOrUnderground() {
        assertNull(SpawnFloor.find(world, 0.5, 0.5, 64, 64, 64, p -> p.getY() == 63 || p.getY() == 70));
    }
    @Test void nearbyDifferentFloorLevelsCanBeAllowedExplicitly() {
        var spot = SpawnFloor.find(world, 0.5, 0.5, 62, 66, 64, p -> p.getY() == 63 || p.getY() == 66);
        assertNotNull(spot); assertEquals(63, spot.getY());
    }
    @Test void theEntireAllowedHeightRangeIsSearchedOnce() {
        var seen = new ArrayList<Integer>();
        assertNull(SpawnFloor.find(world, 0.5, 0.5, 62, 66, 64, p -> { seen.add(p.getBlockY()); return false; }));
        assertEquals(List.of(64, 63, 65, 62, 66), seen);
    }
    @Test void referenceOutsideTheRangeDoesNotEscapeTheRange() {
        var spot = SpawnFloor.find(world, 0.5, 0.5, 64, 64, 70, p -> true);
        assertNotNull(spot); assertEquals(64, spot.getY());
    }
    @Test void invertedRangeNeverSpawns() {
        assertNull(SpawnFloor.find(world, 0, 0, 65, 64, 64, p -> true));
    }
}
