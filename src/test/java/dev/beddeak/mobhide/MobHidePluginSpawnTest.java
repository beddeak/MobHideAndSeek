package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockMakers;

class MobHidePluginSpawnTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void configuredGroundWinsOverEveryRoof(boolean cuboid) throws Exception {
        try (var fixture = new Fixture(cuboid)) {
            fixture.config.set("arena.spawn-min-y", 64); fixture.config.set("arena.spawn-max-y", 64);
            fixture.safety.when(() -> SafeLocation.isSafe(any(Location.class))).thenAnswer(call -> {
                double y = ((Location) call.getArgument(0)).getY(); return y == 64 || y == 70 || y == 84;
            });
            for (int i = 0; i < 100; i++) assertEquals(64, fixture.find().getY());
        }
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void noGroundMeansFailureInsteadOfASpawnOnTheRoof(boolean cuboid) throws Exception {
        try (var fixture = new Fixture(cuboid)) {
            fixture.config.set("arena.spawn-min-y", 64); fixture.config.set("arena.spawn-max-y", 64);
            fixture.safety.when(() -> SafeLocation.isSafe(any(Location.class)))
                    .thenAnswer(call -> ((Location) call.getArgument(0)).getY() == 70);
            assertNull(fixture.find());
        }
    }
    @Test void legacySettingsPreferTheStartFloorWithoutUsingHeightMaps() throws Exception {
        try (var fixture = new Fixture(true)) {
            fixture.safety.when(() -> SafeLocation.isSafe(any(Location.class)))
                    .thenAnswer(call -> ((Location) call.getArgument(0)).getY() == 64 || ((Location) call.getArgument(0)).getY() == 68);
            assertEquals(64, fixture.find().getY());
            verify(fixture.world, never()).getHighestBlockYAt(anyInt(), anyInt());
        }
    }
    private static final class Fixture implements AutoCloseable {
        final World world = mock(World.class);
        final YamlConfiguration config = new YamlConfiguration();
        final MobHidePlugin plugin = mock(MobHidePlugin.class, withSettings().mockMaker(MockMakers.INLINE).defaultAnswer(CALLS_REAL_METHODS));
        final org.mockito.MockedStatic<SafeLocation> safety = mockStatic(SafeLocation.class, withSettings().mockMaker(MockMakers.INLINE));
        Fixture(boolean cuboid) throws Exception {
            doReturn(config).when(plugin).getConfig();
            when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
            set("center", new Location(world, 815.5, 64, 47.5)); set("radius", 20);
            set("boundary", cuboid ? new CuboidBoundary(768, -64, 0, 864, 320, 96) : new ArenaBoundary(815.5, 64, 47.5, 20));
        }
        void set(String name, Object value) throws Exception {
            var field = MobHidePlugin.class.getDeclaredField(name); field.setAccessible(true); field.set(plugin, value);
        }
        Location find() throws Exception {
            var method = MobHidePlugin.class.getDeclaredMethod("findSpot"); method.setAccessible(true); return (Location) method.invoke(plugin);
        }
        public void close() { safety.close(); }
    }
}
