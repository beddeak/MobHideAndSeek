package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;

class MobHidePluginMovementTest {
    private final UUID playerId = UUID.randomUUID();
    private final Player player = mock(Player.class);
    private final World world = mock(World.class);
    private final Map<List<Integer>, Block> blocks = new HashMap<>();
    private final Map<UUID, Location> lastInside = new HashMap<>();
    private MobHidePlugin plugin;
    private Location center;

    @BeforeEach void setUp() throws ReflectiveOperationException {
        plugin = mock(MobHidePlugin.class, withSettings().mockMaker(MockMakers.INLINE).defaultAnswer(CALLS_REAL_METHODS));
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getWorld()).thenReturn(world);
        when(player.getGameMode()).thenReturn(GameMode.ADVENTURE);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        WorldBorder border = mock(WorldBorder.class);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(any(Location.class))).thenReturn(true);
        Block air = block(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                blocks.getOrDefault(List.of(call.getArgument(0), call.getArgument(1), call.getArgument(2)), air));
        when(world.getBlockAt(any(Location.class))).thenAnswer(call -> {
            Location at = call.getArgument(0);
            return world.getBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ());
        });

        center = new Location(world, 0.5, 64, 0.5);
        setField("center", center);
        setField("radius", 20);
        setField("boundary", new ArenaBoundary(center.getX(), center.getY(), center.getZ(), 20));
        setField("round", RoundState.soloTest(playerId, 180));
        setField("startedAt", System.nanoTime());
        // Movement only tests membership; restoration snapshots are deliberately outside this fixture.
        setField("participants", new HashMap<>(Map.of(playerId, new Object())));
        setField("decoys", new ArrayList<>());
        setField("lastInside", lastInside);
        // 이동 처리는 변신 몹의 몸 크기(철골렘 등)로 안전한 복귀 위치를 고릅니다.
        setField("motions", new HashMap<>());
    }

    @Test void fallingPastTheVerticalLimitReturnsToTheLastGroundedLocation() {
        floor(5, 63, 0);
        Location safeSpawn = new Location(world, 5.5, 64, 0.5);
        lastInside.put(playerId, safeSpawn.clone());
        PlayerMoveEvent move = movement(new Location(world, 5.5, 48.2, 0.5), new Location(world, 5.5, 47.8, 0.5));

        handle(move);

        assertFalse(move.isCancelled());
        assertEquals(safeSpawn, move.getTo());
        assertEquals(safeSpawn, lastInside.get(playerId));
    }

    @Test void destroyedFallbackFloorUsesTheStillSafeArenaCenter() {
        floor(0, 63, 0);
        lastInside.put(playerId, new Location(world, 5.5, 64, 0.5));
        PlayerMoveEvent move = movement(new Location(world, 5.5, 48.2, 0.5), new Location(world, 5.5, 47.8, 0.5));

        handle(move);

        assertFalse(move.isCancelled());
        assertEquals(center, move.getTo());
    }

    @Test void groundedMovementUpdatesTheReturnPoint() {
        floor(5, 63, 0);
        lastInside.put(playerId, center.clone());
        Location from = new Location(world, 5.5, 64, 0.5);
        PlayerMoveEvent move = movement(from, new Location(world, 5.55, 64, 0.5));

        handle(move);

        assertFalse(move.isCancelled());
        assertEquals(from, lastInside.get(playerId));
    }

    @Test void countdownFreezesHidersButStillLetsThemLookAround() throws Exception {
        setField("countdownRemaining", 3);
        floor(0, 63, 0);
        Location from = center.clone();
        Location to = center.clone().add(0.2, 0.1, 0);
        to.setYaw(75); to.setPitch(20);
        PlayerMoveEvent move = movement(from, to);
        handle(move);
        assertEquals(from.toVector(), move.getTo().toVector());
        assertEquals(75, move.getTo().getYaw());
        assertEquals(20, move.getTo().getPitch());
        verify(player).setVelocity(new Vector());
    }

    @Test void fastMovementStopsAtTheNearestBodyRegardlessOfSpawnOrder() throws Exception {
        Entity far = body(new BoundingBox(2, 64, 0, 2.8, 65.4, 0.8));
        Entity near = body(new BoundingBox(0, 64, 0, 0.8, 65.4, 0.8));
        setField("decoys", new ArrayList<>(List.of(far, near)));
        when(player.getHeight()).thenReturn(1.8);
        when(player.getVelocity()).thenReturn(new Vector());
        PlayerMoveEvent move = movement(new Location(world, -0.7, 64, 0.4), new Location(world, 3.8, 64, 0.4));

        handle(move);

        assertTrue(move.getTo().getX() + 0.3 < 0, "must stop on the near body's entry side");
        verify(near).setVelocity(any(Vector.class));
        verify(far, never()).setVelocity(any(Vector.class));
    }

    private Entity body(BoundingBox box) {
        Entity body = mock(Entity.class);
        when(body.isValid()).thenReturn(true);
        when(body.getWorld()).thenReturn(world);
        when(body.getBoundingBox()).thenReturn(box);
        when(body.getVelocity()).thenReturn(new Vector());
        return body;
    }

    private void handle(PlayerMoveEvent move) {
        try (var bukkit = mockStatic(Bukkit.class, withSettings().mockMaker(MockMakers.INLINE))) {
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
            plugin.onMove(move);
        }
    }

    private PlayerMoveEvent movement(Location from, Location to) {
        when(player.getLocation()).thenReturn(from);
        when(player.getBoundingBox()).thenReturn(new BoundingBox(from.getX() - 0.3, from.getY(), from.getZ() - 0.3,
                from.getX() + 0.3, from.getY() + 1.8, from.getZ() + 0.3));
        return new PlayerMoveEvent(player, from, to);
    }

    private void floor(int x, int y, int z) {
        blocks.put(List.of(x, y, z), block(Material.STONE, new BoundingBox(0, 0, 0, 1, 1, 1)));
    }

    private Block block(Material material, BoundingBox... boxes) {
        Block block = mock(Block.class);
        VoxelShape shape = mock(VoxelShape.class);
        when(block.getType()).thenReturn(material);
        when(block.isPassable()).thenReturn(material == Material.AIR);
        when(shape.getBoundingBoxes()).thenReturn(List.of(boxes));
        when(block.getCollisionShape()).thenReturn(shape);
        return block;
    }

    private void setField(String name, Object value) throws ReflectiveOperationException {
        var field = MobHidePlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}
