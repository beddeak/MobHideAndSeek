package dev.beddeak.mobhide;

import static org.mockito.Mockito.*;

import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;

class TauntMarkersTest {
    @Test void eachPillarDisappearsAtItsOwnTimeAndClearRemovesTheRest() {
        var markers = new TauntMarkers();
        Entity early = mock(Entity.class), late = mock(Entity.class);
        markers.add(early, 100);
        markers.add(late, 200);
        markers.expire(99);
        verify(early, never()).remove();
        markers.expire(100);
        verify(early).remove();
        verify(late, never()).remove();
        markers.expire(150);
        verify(early, times(1)).remove();
        markers.clear();
        verify(late).remove();
        markers.clear();
        verify(late, times(1)).remove();
    }
}
