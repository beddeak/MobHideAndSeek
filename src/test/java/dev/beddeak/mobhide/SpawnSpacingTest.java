package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

/** 같은 블록 중앙에 두 참가자가 소환되면 중심이 완전히 겹쳐 서로 밀어내지도 못했습니다. */
class SpawnSpacingTest {
    private final World world = mock(World.class);

    @Test void sameBlockCenterIsRejected() {
        Location center = new Location(world, 10.5, 64, 10.5);
        assertFalse(MobHidePlugin.spaced(new Location(world, 10.5, 64, 10.5), List.of(center)));
        assertFalse(MobHidePlugin.spaced(new Location(world, 11.5, 64, 10.5), List.of(center)), "한 블록 옆도 몸이 겹칩니다");
    }

    @Test void twoBlocksApartOrAnotherWorldIsFine() {
        Location center = new Location(world, 10.5, 64, 10.5);
        assertTrue(MobHidePlugin.spaced(new Location(world, 12.5, 64, 10.5), List.of(center)));
        assertTrue(MobHidePlugin.spaced(new Location(world, 10.5, 70, 10.5), List.of(new Location(mock(World.class), 10.5, 64, 10.5))));
        assertTrue(MobHidePlugin.spaced(new Location(world, 10.5, 64, 10.5), List.of()));
    }
}
