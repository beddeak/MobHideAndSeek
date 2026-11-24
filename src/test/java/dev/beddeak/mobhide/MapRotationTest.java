package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

class MapRotationTest {
    private ArenaMap map(String id) { return new ArenaMap(id,id,null,null,20,64,64); }
    @Test void randomPoolExcludesLastMapWithoutLosingTheThirdMap() {
        List<ArenaMap> maps=List.of(map("village"),map("desert"),map("snow"));
        assertEquals(maps,MapRotation.candidates(maps,null));
        assertEquals(List.of(maps.get(1),maps.get(2)),MapRotation.candidates(maps,"village"));
        assertEquals(List.of(maps.get(0),maps.get(2)),MapRotation.candidates(maps,"desert"));
        assertEquals(List.of(maps.get(0),maps.get(1)),MapRotation.candidates(maps,"snow"));
        assertEquals(List.of(maps.getFirst()),MapRotation.candidates(List.of(maps.getFirst()),"village"));
    }
    @Test void legacyVillageAndEachMapsHeightAreReadIndependently() {
        try(var bukkit=mockStatic(Bukkit.class,withSettings().mockMaker(MockMakers.INLINE))) {
            World world=mock(World.class);
            when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(world);
            var config=new YamlConfiguration();
            var arena=config.createSection("arena");
            arena.set("world","world"); arena.set("name","햇살 마을");
            arena.set("x",815.5); arena.set("y",64); arena.set("z",86.5);
            arena.set("shape","cuboid"); arena.set("min",List.of(768,-64,0)); arena.set("max",List.of(864,320,96));
            arena.set("spawn-min-y",64); arena.set("spawn-max-y",64);
            ArenaMap village=ArenaMap.read("village",arena);
            assertEquals("햇살 마을",village.name()); assertEquals(64,village.minSpawnY());
            assertTrue(village.boundary().contains(815.5,64,86.5));
            arena.set("spawn-min-y",70);
            assertThrows(IllegalArgumentException.class,()->ArenaMap.read("bad",arena));
            assertEquals(64,village.minSpawnY());
        }
    }
    @Test void allFiveMapsParticipateInRotationIncludingFarmAndMine() {
        var maps=List.of(map("farm"),map("cave"),map("village"),map("desert"),map("snow"));
        for(var last:maps) {
            var next=MapRotation.candidates(maps,last.id());
            assertEquals(4,next.size()); assertFalse(next.contains(last));
            for(var other:maps) if(other!=last) assertTrue(next.contains(other));
        }
    }
    @Test void farmPathsAndShallowWaterBelowTheSpawnFloorStayInsideTheArena() {
        try(var bukkit=mockStatic(Bukkit.class,withSettings().mockMaker(MockMakers.INLINE))) {
            World world=mock(World.class);
            when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(world);
            var farm=new YamlConfiguration().createSection("farm");
            farm.set("world","world"); farm.set("x",303.5); farm.set("y",64); farm.set("z",86.5);
            farm.set("shape","cuboid"); farm.set("min",List.of(256,64,0)); farm.set("max",List.of(352,94,96));
            farm.set("spawn-min-y",64); farm.set("spawn-max-y",64);
            ArenaMap map=ArenaMap.read("farm",farm);
            assertTrue(map.boundary().contains(300.5,63.9375,40.5),"Feet on a dirt path or farmland");
            assertTrue(map.boundary().contains(300.5,62.4,40.5),"Feet in shallow water");
            assertFalse(map.boundary().contains(300.5,61.9,40.5),"Falling into a pit still leaves the arena");
            assertEquals(64,map.minSpawnY());
            farm.set("min",List.of(256,-64,0));
            assertEquals(-64,((CuboidBoundary)ArenaMap.read("farm",farm).boundary()).minY());
        }
    }
    @Test void participantsFallingInTheMapWorldAreLeftToTheArenaBoundary() throws Exception {
        var plugin=mock(MobHidePlugin.class,withSettings().mockMaker(MockMakers.INLINE));
        when(plugin.getDataFolder()).thenReturn(new java.io.File("target/map-tests"));
        MapRotation rotation=new MapRotation(plugin);
        World world=mock(World.class);
        Location lobby=new Location(world,.5,64,7.5);
        var field=MapRotation.class.getDeclaredField("lobby"); field.setAccessible(true); field.set(rotation,lobby);
        field=MapRotation.class.getDeclaredField("ready"); field.setAccessible(true); field.set(rotation,true);
        Player player=mock(Player.class);
        when(plugin.participating(player)).thenReturn(true);
        var event=new org.bukkit.event.player.PlayerMoveEvent(player,new Location(world,160.5,64,.5),new Location(world,160.5,61,.5));
        rotation.move(event);
        assertEquals(160.5,event.getTo().getX(),"The arena, not the lobby, handles a participant's fall");
        when(plugin.participating(player)).thenReturn(false);
        rotation.move(event);
        assertEquals(lobby,event.getTo());
    }
    @Test void joiningOverridesSavedArenaLocationBeforeThePlayerSpawns() throws Exception {
        var plugin=mock(MobHidePlugin.class,withSettings().mockMaker(MockMakers.INLINE));
        when(plugin.getDataFolder()).thenReturn(new java.io.File("target/map-tests"));
        MapRotation rotation=new MapRotation(plugin);
        World world=mock(World.class);
        Location lobby=new Location(world,.5,64,7.5);
        var field=MapRotation.class.getDeclaredField("lobby"); field.setAccessible(true); field.set(rotation,lobby);
        field=MapRotation.class.getDeclaredField("ready"); field.setAccessible(true); field.set(rotation,true);
        var event=new PlayerSpawnLocationEvent(mock(Player.class),new Location(world,815.5,64,86.5));
        rotation.spawn(event);
        assertEquals(lobby,event.getSpawnLocation());
        event.getSpawnLocation().setX(99);
        assertEquals(.5,rotation.lobby().getX(),"Player events must not mutate the shared lobby location");
    }
}
