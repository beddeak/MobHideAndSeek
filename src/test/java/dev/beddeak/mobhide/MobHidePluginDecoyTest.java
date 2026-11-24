package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.destroystokyo.paper.entity.ai.MobGoals;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Rabbit;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;

class MobHidePluginDecoyTest {
    private MobHidePlugin plugin;
    private World world;
    private final ArrayList<Entity> decoys = new ArrayList<>();
    private final Map<UUID, Location> positions = new HashMap<>();

    @BeforeEach void setup() throws Exception {
        TestRegistries.initialize();
        plugin = mock(MobHidePlugin.class, withSettings().mockMaker(MockMakers.INLINE).defaultAnswer(CALLS_REAL_METHODS));
        doReturn(new org.bukkit.configuration.file.YamlConfiguration()).when(plugin).getConfig();
        world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        Server server = mock(Server.class);
        doReturn(server).when(plugin).getServer();
        when(server.getMobGoals()).thenReturn(mock(MobGoals.class));
        when(world.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(63);
        set("center", new Location(world, 0, 64, 0));
        set("radius", 20);
        set("boundary", new ArenaBoundary(0, 64, 0, 20));
        set("decoys", decoys);
        set("lastInside", positions);
        set("motions", new HashMap<>());
        set("hitSpeedBoosts", new HashMap<>());
        set("hitProtection", new HashMap<>());
        set("participants", new HashMap<>());
    }

    @Test void aValidMatchingDecoyIsReusedWithoutSpawningAnother() throws Exception {
        Entity existing = mock(Entity.class);
        when(existing.isValid()).thenReturn(true);
        when(existing.getType()).thenReturn(EntityType.RABBIT);
        decoys.add(existing);

        invoke("ensureMatchingDecoy", EntityType.class, EntityType.RABBIT);

        assertEquals(1, decoys.size());
        assertSame(existing, decoys.getFirst());
        verify(world, never()).spawn(any(Location.class), eq(Rabbit.class), any(SpawnReason.class), anyBoolean(), any());
    }

    @Test void aMissingMatchingDecoyIsPreparedTrackedAndRemovedAtFinish() throws Exception {
        Entity otherType = mock(Entity.class);
        when(otherType.isValid()).thenReturn(true);
        when(otherType.getType()).thenReturn(EntityType.COW);
        decoys.add(otherType);
        Rabbit spawned = mock(Rabbit.class);
        UUID id = UUID.randomUUID();
        when(spawned.getUniqueId()).thenReturn(id);
        when(spawned.isValid()).thenReturn(true);
        when(spawned.getType()).thenReturn(EntityType.RABBIT);
        when(world.spawn(any(Location.class), eq(Rabbit.class), eq(SpawnReason.CUSTOM), eq(false), any()))
                .thenAnswer(call -> {
                    Consumer<Mob> prepare = call.getArgument(4);
                    prepare.accept(spawned);
                    return spawned;
                });

        try (var safety = mockStatic(SafeLocation.class, withSettings().mockMaker(MockMakers.INLINE))) {
            safety.when(() -> SafeLocation.isSafe(any(Location.class))).thenAnswer(call -> ((Location) call.getArgument(0)).getY() == 64);
            invoke("ensureMatchingDecoy", EntityType.class, EntityType.RABBIT);
        }

        assertEquals(2, decoys.size());
        assertSame(spawned, decoys.getLast());
        assertEquals(64, positions.get(id).getY());
        verify(spawned).setPersistent(false);
        verify(spawned).setRabbitType(Rabbit.Type.BROWN);
        verify(world, times(1)).spawn(any(Location.class), eq(Rabbit.class), eq(SpawnReason.CUSTOM), eq(false), any());

        invoke("finish", String.class, null);

        verify(spawned).remove();
        assertTrue(decoys.isEmpty());
        assertTrue(positions.isEmpty());
    }

    private void set(String name, Object value) throws Exception {
        Field field = MobHidePlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }

    private void invoke(String name, Class<?> argumentType, Object argument) throws Exception {
        var method = MobHidePlugin.class.getDeclaredMethod(name, argumentType);
        method.setAccessible(true);
        method.invoke(plugin, argument);
    }
}
