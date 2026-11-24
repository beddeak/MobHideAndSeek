package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 재해는 관리자가 일으키고, 경고 3초 뒤 시작하며, 경기가 끝나면 즉시 멈춰야 합니다. */
class DisastersTest {
    private final World world = mock(World.class);
    private final Location spot = new Location(world, 10.5, 64, 10.5);
    private final Player seeker = mock(Player.class);
    private final Entity decoy = mock(Entity.class);
    private final List<Entity> bodies = new ArrayList<>(List.of(seeker, decoy));
    private final Disasters.Stage stage = new Disasters.Stage() {
        public World world() { return world; }
        public Location randomSpot() { return spot.clone(); }
        public boolean inside(Location location) { return location.distanceSquared(spot) < 30 * 30; }
        public List<Entity> bodies() { return bodies; }
        public List<Player> audience() { return List.of(seeker); }
    };
    private final Disasters disasters = new Disasters(stage, new Random(7));

    private void tick(int times) { for (int i = 0; i < times; i++) disasters.tick(); }

    @Test void warningShowsBeforeAnythingHappens() {
        when(seeker.getLocation()).thenReturn(spot.clone());
        when(decoy.getLocation()).thenReturn(spot.clone().add(1, 0, 0));
        assertTrue(disasters.trigger(Disasters.Kind.TORNADO));
        assertFalse(disasters.trigger(Disasters.Kind.QUAKE), "한 번에 하나만");
        assertEquals(Disasters.Kind.TORNADO, disasters.current());
        var title = ArgumentCaptor.forClass(Title.class);
        verify(seeker).showTitle(title.capture());
        assertTrue(title.getValue().title().toString().contains("무언가가 다가옵니다..."));
        verify(seeker).sendMessage(contains("무언가가 다가옵니다"));
        tick(Disasters.WARN_TICKS - 1);
        verify(seeker, never()).setVelocity(any());
        verify(decoy, never()).setVelocity(any());
        verify(seeker, atLeast(3)).playSound(any(Location.class), eq(Sound.ENTITY_WARDEN_HEARTBEAT), anyFloat(), anyFloat());
    }

    @Test void tornadoLiftsEveryBodyInsideAndNothingOutside() {
        when(seeker.getLocation()).thenReturn(spot.clone().add(1.5, 0, 0));
        when(decoy.getLocation()).thenReturn(spot.clone().add(20, 0, 0));
        disasters.trigger(Disasters.Kind.TORNADO);
        tick(Disasters.WARN_TICKS + 1);
        var push = ArgumentCaptor.forClass(Vector.class);
        verify(seeker, atLeastOnce()).setVelocity(push.capture());
        assertTrue(push.getValue().getY() > 0, "안쪽 몸은 떠올라야 합니다");
        verify(decoy, never()).setVelocity(any());
        verify(seeker, times(2)).showTitle(any(Title.class));
        assertTrue(disasters.busy());
        tick(Disasters.TORNADO_TICKS + 5);
        assertFalse(disasters.busy());
        assertNull(disasters.current());
    }

    @Test void meteorsExplodeOnTheGroundAndFlingWhoeverStandsThere() {
        when(seeker.getLocation()).thenReturn(spot.clone());
        when(decoy.getLocation()).thenReturn(spot.clone().add(0.5, 0, 0.5));
        disasters.trigger(Disasters.Kind.METEOR);
        tick(Disasters.WARN_TICKS + Disasters.METEOR_TICKS + 40);
        verify(seeker, atLeastOnce()).setVelocity(argThat(v -> v.getY() > 0.5));
        verify(decoy, atLeastOnce()).setVelocity(argThat(v -> v.getY() > 0.5));
        verify(world, atLeastOnce()).playSound(any(Location.class), eq(Sound.ENTITY_GENERIC_EXPLODE), anyFloat(), anyFloat());
        assertFalse(disasters.busy(), "마지막 불덩이까지 떨어진 뒤 끝나야 합니다");
    }

    @Test void lightningUsesVisualOnlyStrikes() {
        when(seeker.getLocation()).thenReturn(spot.clone());
        when(decoy.getLocation()).thenReturn(spot.clone());
        disasters.trigger(Disasters.Kind.LIGHTNING);
        tick(Disasters.WARN_TICKS + Disasters.LIGHTNING_TICKS + 1);
        verify(world, atLeast(5)).strikeLightningEffect(any(Location.class));
        verify(world, never()).strikeLightning(any(Location.class));
        assertFalse(disasters.busy());
    }

    @Test void quakeJoltsEveryoneEqually() {
        when(seeker.getLocation()).thenReturn(spot.clone());
        when(decoy.getLocation()).thenReturn(spot.clone().add(8, 0, 8));
        disasters.trigger(Disasters.Kind.QUAKE);
        tick(Disasters.WARN_TICKS + 30);
        verify(seeker, atLeast(4)).setVelocity(argThat(v -> v.getY() > 0));
        verify(decoy, atLeast(4)).setVelocity(argThat(v -> v.getY() > 0));
    }

    @Test void clearStopsImmediately() {
        when(seeker.getLocation()).thenReturn(spot.clone());
        disasters.trigger(Disasters.Kind.QUAKE);
        tick(Disasters.WARN_TICKS + 1);
        disasters.clear();
        clearInvocations(seeker, world);
        tick(100);
        verify(seeker, never()).setVelocity(any());
        verify(world, never()).spawnParticle(any(), anyDouble(), anyDouble(), anyDouble(), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        assertFalse(disasters.busy());
        assertTrue(disasters.trigger(Disasters.Kind.TORNADO), "정리 뒤에는 다시 일으킬 수 있습니다");
    }

    @Test void kindsParseEnglishAndKorean() {
        assertEquals(Disasters.Kind.TORNADO, Disasters.Kind.parse("Tornado"));
        assertEquals(Disasters.Kind.QUAKE, Disasters.Kind.parse("earthquake"));
        assertEquals(Disasters.Kind.METEOR, Disasters.Kind.parse("유성우"));
        assertEquals(Disasters.Kind.LIGHTNING, Disasters.Kind.parse("낙뢰"));
        assertNull(Disasters.Kind.parse("volcano"));
        assertNull(Disasters.Kind.parse(null));
        assertNotNull(Disasters.Kind.random(new Random(1)));
    }
}
