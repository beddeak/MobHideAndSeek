package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.EntityPositionData;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnimationBridgeTest {
    private static final int SOURCE_ID = 100, SELF_ID = 999;
    private final Player player = mock(Player.class), viewer = mock(Player.class);
    private final PlayerManager manager = mock(PlayerManager.class);
    private final World world = mock(World.class);
    private final AnimationBridge bridge = new AnimationBridge(SELF_ID);
    private final List<Sent> sent = new ArrayList<>();
    private record Sent(Player viewer, PacketWrapper<?> packet) {}

    @BeforeEach void setup() {
        PacketEventsAPI<?> api = mock(PacketEventsAPI.class);
        ServerManager server = mock(ServerManager.class);
        when(api.getServerManager()).thenReturn(server);
        when(server.getVersion()).thenReturn(ServerVersion.V_1_21_4);
        when(api.getPlayerManager()).thenReturn(manager);
        PacketEvents.setAPI(api);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        when(player.getEntityId()).thenReturn(SOURCE_ID);
        when(player.getVelocity()).thenReturn(new Vector());
        when(player.getTrackedBy()).thenReturn(Set.of(viewer, player));
        at(1, 64, 2);
        doAnswer(call -> {
            sent.add(new Sent(call.getArgument(0), call.getArgument(1)));
            return null;
        }).when(manager).sendPacketSilently(any(), any(PacketWrapper.class));
    }

    @AfterEach void resetApi() { PacketEvents.setAPI(null); }

    @Test void everySupportedMobUsesTheSameAnimationPathIncludingHostileMobs() {
        for (EntityType type : MobCatalog.ALL) {
            assertTrue(AnimationBridge.controls(type), type.name());
            bridge.track(player, type);
            bridge.render(player, true, 0, true, false);
            assertEquals(2, packets(WrapperPlayServerEntityPositionSync.class).size(), type.name());
            assertEquals(Set.of(SOURCE_ID, SELF_ID), packets(WrapperPlayServerEntityPositionSync.class).stream()
                    .map(WrapperPlayServerEntityPositionSync::getId).collect(java.util.stream.Collectors.toSet()));
            sent.clear();
        }
        assertFalse(AnimationBridge.controls(EntityType.PLAYER));
        assertFalse(AnimationBridge.controls(EntityType.ARMOR_STAND));
    }

    @Test void initialAbsoluteSyncUsesTheVelocityAndDoesNotTeleportEveryTick() {
        bridge.track(player, EntityType.RABBIT);
        when(player.getVelocity()).thenReturn(new Vector(0.1, 0.25, -0.05));
        bridge.render(player, false, 90, false, false);
        var sync = packets(WrapperPlayServerEntityPositionSync.class).getFirst();
        assertEquals(0.25, sync.getValues().getDeltaMovement().getY());
        assertEquals(0.1, sync.getValues().getDeltaMovement().getX());
        assertFalse(sync.isOnGround());
        sent.clear();
        at(1.1, 64.2, 2);
        bridge.render(player, false, 90, false, false);
        assertEquals(1, packets(WrapperPlayServerEntityRelativeMoveAndRotation.class).size());
        assertTrue(packets(WrapperPlayServerEntityTeleport.class).isEmpty());
        assertTrue(packets(WrapperPlayServerEntityPositionSync.class).isEmpty());
        assertTrue(packets(WrapperPlayServerEntityHeadLook.class).isEmpty());
    }

    @Test void groundedSyncDoesNotCopyThePlayersResidualFallingVelocity() {
        bridge.track(player, EntityType.CHICKEN);
        when(player.getVelocity()).thenReturn(new Vector(0.1, -0.078, 0.2));
        bridge.render(player, true, 0, true, false);
        for (var packet : packets(WrapperPlayServerEntityPositionSync.class)) {
            assertTrue(packet.isOnGround());
            assertEquals(0, packet.getValues().getDeltaMovement().getY());
            assertEquals(0.2, packet.getValues().getDeltaMovement().getZ());
        }
    }

    @Test void idleMobsSendNothingEvenAfterThePeriodicSyncInterval() {
        bridge.track(player, EntityType.CHICKEN);
        bridge.render(player, true, 0, true, false);
        sent.clear();
        for (int tick = 0; tick < 450; tick++) bridge.render(player, true, 0, true, false);
        assertTrue(sent.isEmpty());
    }

    @Test void tinyMovementsReconstructWithoutAccumulatedDriftAcrossNegativeCoordinatesAndResyncs() {
        bridge.track(player, EntityType.RABBIT);
        Map<Player, Vector3d> clientPositions = new HashMap<>();
        int syncs = 0;
        for (int tick = 0; tick < 450; tick++) {
            double x = -0.3333 - tick * 0.00007, y = -63.4, z = -2.6666 + tick * 0.00011;
            at(x, y, z);
            bridge.render(player, true, 0, true, false);
            for (Sent delivered : sent) {
                if (delivered.packet() instanceof WrapperPlayServerEntityPositionSync sync) {
                    clientPositions.put(delivered.viewer(), sync.getValues().getPosition());
                    syncs++;
                } else if (delivered.packet() instanceof WrapperPlayServerEntityRelativeMoveAndRotation move) {
                    // Reconstruct the actual signed-short wire deltas, not merely the wrapper's doubles.
                    Vector3d old = clientPositions.get(delivered.viewer());
                    double dx = wireDelta(move.getDeltaX()), dy = wireDelta(move.getDeltaY()), dz = wireDelta(move.getDeltaZ());
                    clientPositions.put(delivered.viewer(), old.add(dx, dy, dz));
                }
            }
            for (Player target : List.of(viewer, player)) {
                var client = clientPositions.get(target);
                assertNotNull(client);
                assertEquals(x, client.getX(), 1 / 8192d + 1e-10);
                assertEquals(y, client.getY(), 1 / 8192d + 1e-10);
                assertEquals(z, client.getZ(), 1 / 8192d + 1e-10);
            }
            assertTrue(packets(WrapperPlayServerEntityTeleport.class).isEmpty());
            sent.clear();
        }
        assertTrue(syncs >= 6, "Both viewers need initial and periodic absolute baselines");
        assertTrue(syncs < 12, "Tiny movement must not force continuous absolute synchronization");
    }

    @Test void relativeMovementFallsBackToAbsoluteSyncBeforeSignedShortOverflow() {
        bridge.track(player, EntityType.CHICKEN);
        at(0, 64, 0);
        bridge.render(player, false, 0, true, false);
        sent.clear();
        at(8, 64, 0);
        bridge.render(player, false, 0, true, false);
        assertEquals(1, packets(WrapperPlayServerEntityPositionSync.class).size());
        assertTrue(packets(WrapperPlayServerEntityRelativeMoveAndRotation.class).isEmpty());
        sent.clear();
        at(0, 64, 0);
        bridge.render(player, false, 0, true, false);
        assertEquals(-8, packets(WrapperPlayServerEntityRelativeMoveAndRotation.class).getFirst().getDeltaX());
        sent.clear();
        at(-8.01, 64, 0);
        bridge.render(player, false, 0, true, false);
        assertEquals(1, packets(WrapperPlayServerEntityPositionSync.class).size());
    }

    @Test void groundChangesAtRestAreDeliveredWithoutResettingThePosition() {
        bridge.track(player, EntityType.CHICKEN);
        bridge.render(player, true, 0, true, false);
        sent.clear();
        bridge.render(player, true, 0, false, false);
        assertEquals(2, sent.size());
        assertTrue(packets(WrapperPlayServerEntityRotation.class).stream().noneMatch(WrapperPlayServerEntityRotation::isOnGround));
        sent.clear();
        bridge.render(player, true, 0, true, false);
        assertEquals(2, packets(WrapperPlayServerEntityRotation.class).size());
        assertTrue(packets(WrapperPlayServerEntityRotation.class).stream().allMatch(WrapperPlayServerEntityRotation::isOnGround));
        assertEquals(2, sent.size());
    }

    @Test void headRotationUpdatesOnlyWhenTheWireAngleChanges() {
        bridge.track(player, EntityType.COW);
        bridge.render(player, true, 0, true, false);
        sent.clear();
        bridge.render(player, true, 0.1f, true, false);
        assertTrue(sent.isEmpty());
        bridge.render(player, true, -90, true, false);
        assertEquals(2, packets(WrapperPlayServerEntityHeadLook.class).size());
        assertEquals(2, packets(WrapperPlayServerEntityRotation.class).size());
        assertTrue(packets(WrapperPlayServerEntityHeadLook.class).stream().allMatch(p -> p.getHeadYaw() == 270));
        sent.clear();
        bridge.render(player, true, 270, true, false);
        assertTrue(sent.isEmpty());
    }

    @Test void rabbitTakeoffSendsOneHopPerViewerIncludingSelfAndNoneOnFollowingTicks() {
        bridge.track(player, EntityType.RABBIT);
        bridge.render(player, true, 90, false, true);
        var hops = packets(WrapperPlayServerEntityStatus.class);
        assertEquals(2, hops.size());
        assertEquals(Set.of(SOURCE_ID, SELF_ID), hops.stream().map(WrapperPlayServerEntityStatus::getEntityId)
                .collect(java.util.stream.Collectors.toSet()));
        assertTrue(hops.stream().allMatch(p -> p.getStatus() == 1));
        sent.clear();
        for (int tick = 0; tick < 5; tick++) bridge.render(player, true, 90, false, false);
        assertTrue(packets(WrapperPlayServerEntityStatus.class).isEmpty());
    }

    @Test void idleHeadGlanceReachesBothViewersWithoutRotatingOrMovingTheBody() {
        bridge.track(player, EntityType.ZOMBIE);
        bridge.render(player, true, 90, 90, 0, true, false);
        sent.clear();
        bridge.render(player, true, 90, 123.75f, 0, true, false);
        assertEquals(2, sent.size());
        var heads = packets(WrapperPlayServerEntityHeadLook.class);
        assertEquals(2, heads.size());
        assertEquals(Set.of(SOURCE_ID, SELF_ID), heads.stream().map(WrapperPlayServerEntityHeadLook::getEntityId)
                .collect(java.util.stream.Collectors.toSet()));
        assertTrue(heads.stream().allMatch(p -> p.getHeadYaw() == 123.75f));
        assertTrue(packets(WrapperPlayServerEntityRotation.class).isEmpty());
        assertTrue(packets(WrapperPlayServerEntityPositionSync.class).isEmpty());
        sent.clear();
        bridge.render(player, true, 90, 123.75f, 0, true, false);
        assertTrue(sent.isEmpty(), "Holding a gaze should not resend unchanged packets");
    }

    @Test void idlePitchChangesKeepBodyHeadingAndGroundContact() {
        bridge.track(player, EntityType.COW);
        bridge.render(player, true, 90, 112.5f, 0, true, false);
        sent.clear();
        bridge.render(player, true, 90, 112.5f, -11.25f, true, false);
        var rotations = packets(WrapperPlayServerEntityRotation.class);
        assertEquals(2, sent.size()); assertEquals(2, rotations.size());
        assertTrue(rotations.stream().allMatch(p -> p.getYaw() == 90 && p.getPitch() == -11.25f && p.isOnGround()));
        assertTrue(packets(WrapperPlayServerEntityHeadLook.class).isEmpty());
        assertTrue(packets(WrapperPlayServerEntityStatus.class).isEmpty());
    }

    @Test void independentBodyHeadAndPitchSurviveSyncMovementAndViewerReset() {
        bridge.track(player, EntityType.SKELETON);
        bridge.render(player, true, 90, 135, -11.25f, true, false);
        for (var packet : packets(WrapperPlayServerEntityPositionSync.class)) {
            assertEquals(90, packet.getValues().getYaw()); assertEquals(-11.25f, packet.getValues().getPitch());
        }
        assertTrue(packets(WrapperPlayServerEntityHeadLook.class).stream().allMatch(p -> p.getHeadYaw() == 135));
        sent.clear(); at(1.1, 64, 2);
        bridge.render(player, true, 101.25f, 135, -5.625f, true, false);
        var moves = packets(WrapperPlayServerEntityRelativeMoveAndRotation.class);
        assertEquals(2, moves.size());
        assertTrue(moves.stream().allMatch(p -> p.getYaw() == 101.25f && p.getPitch() == -5.625f));
        assertTrue(packets(WrapperPlayServerEntityHeadLook.class).isEmpty());
        sent.clear(); bridge.resetViewer(player, viewer);
        bridge.render(player, true, 101.25f, 135, -5.625f, true, false);
        assertEquals(1, packets(WrapperPlayServerEntityPositionSync.class).size());
        assertEquals(135, packets(WrapperPlayServerEntityHeadLook.class).getFirst().getHeadYaw());
        assertTrue(sent.stream().allMatch(p -> p.viewer() == viewer));
    }

    @Test void hidingAndShowingSelfViewCreatesAFreshSelfBaselineOnly() {
        bridge.track(player, EntityType.CHICKEN);
        bridge.render(player, true, 0, true, false);
        sent.clear();
        bridge.render(player, false, 0, true, false);
        at(1.2, 64, 2);
        bridge.render(player, false, 0, true, false);
        assertTrue(sent.stream().noneMatch(p -> p.viewer() == player));
        sent.clear();
        bridge.render(player, true, 0, true, false);
        assertEquals(1, packets(WrapperPlayServerEntityPositionSync.class).size());
        assertEquals(SELF_ID, packets(WrapperPlayServerEntityPositionSync.class).getFirst().getId());
        assertTrue(sent.stream().allMatch(p -> p.viewer() == player));
    }

    @Test void aViewerWhoLeavesAndReturnsReceivesAFreshBaseline() {
        bridge.track(player, EntityType.COW);
        bridge.render(player, true, 0, true, false);
        when(player.getTrackedBy()).thenReturn(Set.of(player));
        bridge.render(player, true, 0, true, false);
        at(1.1, 64, 2);
        bridge.render(player, true, 0, true, false);
        sent.clear();
        when(player.getTrackedBy()).thenReturn(Set.of(player, viewer));
        bridge.render(player, true, 0, true, false);
        assertEquals(1, packets(WrapperPlayServerEntityPositionSync.class).size());
        assertTrue(sent.stream().allMatch(p -> p.viewer() == viewer));
    }

    @Test void explicitResetAndChangingMobInvalidateEveryViewerBaseline() {
        bridge.track(player, EntityType.RABBIT);
        bridge.render(player, true, 0, true, false);
        sent.clear();
        bridge.reset(player);
        bridge.render(player, true, 0, true, false);
        assertEquals(2, packets(WrapperPlayServerEntityPositionSync.class).size());
        sent.clear();
        bridge.track(player, EntityType.SKELETON);
        bridge.render(player, true, 0, true, false);
        assertEquals(2, packets(WrapperPlayServerEntityPositionSync.class).size());
    }

    @Test void worldChangesCannotReuseAnOldRelativeCoordinateBaseline() {
        bridge.track(player, EntityType.RABBIT);
        bridge.render(player, true, 0, true, false);
        World otherWorld = mock(World.class);
        when(otherWorld.getUID()).thenReturn(UUID.randomUUID());
        when(player.getLocation()).thenReturn(new Location(otherWorld, 1.01, 64, 2));
        sent.clear();
        bridge.render(player, true, 0, true, false);
        assertEquals(2, packets(WrapperPlayServerEntityPositionSync.class).size());
        assertTrue(packets(WrapperPlayServerEntityRelativeMoveAndRotation.class).isEmpty());
    }

    @Test void cancellingOwnedMovementIncludesEveryPositionAndRotationPacket() {
        bridge.track(player, EntityType.RABBIT);
        for (PacketWrapper<?> packet : List.of(
                new WrapperPlayServerEntityRelativeMove(SOURCE_ID, 0.1, 0, 0, true),
                new WrapperPlayServerEntityRelativeMoveAndRotation(SOURCE_ID, 0.1, 0, 0, 0, 0, true),
                new WrapperPlayServerEntityRotation(SOURCE_ID, 0, 0, true),
                new WrapperPlayServerEntityTeleport(SOURCE_ID, new Vector3d(1, 64, 2), 0, 0, true),
                new WrapperPlayServerEntityPositionSync(SOURCE_ID, new EntityPositionData(new Vector3d(1, 64, 2), Vector3d.zero(), 0, 0), true),
                new WrapperPlayServerEntityHeadLook(SOURCE_ID, 0))) {
            var event = event(viewer, packet);
            bridge.onPacketSend(event);
            verify(event).setCancelled(true);
        }
    }

    @Test void theSharedSelfEntityIdIsBlockedOnlyForAnOwnedRecipient() {
        bridge.track(player, EntityType.RABBIT);
        var ownerEvent = event(player, new WrapperPlayServerEntityRelativeMove(SELF_ID, 0.1, 0, 0, true));
        bridge.onPacketSend(ownerEvent);
        verify(ownerEvent).setCancelled(true);
        var unrelatedEvent = event(viewer, new WrapperPlayServerEntityRelativeMove(SELF_ID, 0.1, 0, 0, true));
        bridge.onPacketSend(unrelatedEvent);
        verify(unrelatedEvent, never()).setCancelled(anyBoolean());
        bridge.remove(player);
        var removedEvent = event(player, new WrapperPlayServerEntityRelativeMove(SELF_ID, 0.1, 0, 0, true));
        bridge.onPacketSend(removedEvent);
        verify(removedEvent, never()).setCancelled(anyBoolean());
    }

    @Test void hiderArmSwingsAreHiddenButCriticalHitsAndAiSwingsPassThrough() {
        bridge.track(player, EntityType.ZOMBIE);
        for (var kind : List.of(WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM,
                WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_OFF_HAND)) {
            var others = event(viewer, new WrapperPlayServerEntityAnimation(SOURCE_ID, kind));
            bridge.onPacketSend(others);
            verify(others).setCancelled(true);
            var self = event(player, new WrapperPlayServerEntityAnimation(SELF_ID, kind));
            bridge.onPacketSend(self);
            verify(self).setCancelled(true);
        }
        var critical = event(viewer, new WrapperPlayServerEntityAnimation(SOURCE_ID,
                WrapperPlayServerEntityAnimation.EntityAnimationType.CRITICAL_HIT));
        var ai = event(viewer, new WrapperPlayServerEntityAnimation(101,
                WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM));
        bridge.onPacketSend(critical);
        bridge.onPacketSend(ai);
        verify(critical, never()).setCancelled(anyBoolean());
        verify(ai, never()).setCancelled(anyBoolean());
        bridge.remove(player);
        var removed = event(viewer, new WrapperPlayServerEntityAnimation(SOURCE_ID,
                WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM));
        bridge.onPacketSend(removed);
        verify(removed, never()).setCancelled(anyBoolean());
    }

    @Test void ordinaryAiAndNonMovementPacketsAreUntouched() {
        bridge.track(player, EntityType.RABBIT);
        var aiMove = event(viewer, new WrapperPlayServerEntityRelativeMove(101, 0.1, 0, 0, true));
        var status = event(viewer, new WrapperPlayServerEntityStatus(SOURCE_ID, 2));
        bridge.onPacketSend(aiMove);
        bridge.onPacketSend(status);
        verify(aiMove, never()).setCancelled(anyBoolean());
        verify(status, never()).setCancelled(anyBoolean());
    }

    @Test void removalClearAndUnsupportedMorphStopBothRenderingAndCancellation() {
        for (int mode = 0; mode < 3; mode++) {
            bridge.track(player, EntityType.CREEPER);
            bridge.render(player, true, 0, true, false);
            if (mode == 0) bridge.remove(player);
            else if (mode == 1) bridge.clear();
            else bridge.track(player, EntityType.ARMOR_STAND);
            sent.clear();
            bridge.render(player, true, 0, true, true);
            assertTrue(sent.isEmpty());
            var event = event(viewer, new WrapperPlayServerEntityRotation(SOURCE_ID, 0, 0, true));
            bridge.onPacketSend(event);
            verify(event, never()).setCancelled(anyBoolean());
        }
    }

    @Test void destroyAndSpawnInvalidateOnlyTheRecipientsBaseline() {
        bridge.track(player, EntityType.RABBIT);
        bridge.render(player, true, 0, true, false);
        for (PacketWrapper<?> lifecycle : List.of(new WrapperPlayServerDestroyEntities(SOURCE_ID), spawn(SOURCE_ID))) {
            sent.clear();
            var event = event(viewer, lifecycle);
            bridge.onPacketSend(event);
            verify(event, never()).setCancelled(anyBoolean());
            bridge.render(player, true, 0, true, false);
            assertEquals(1, packets(WrapperPlayServerEntityPositionSync.class).size());
            assertTrue(sent.stream().allMatch(p -> p.viewer() == viewer));
        }
    }

    @Test void selfLifecycleInvalidationUsesTheOwnersRealSourceId() {
        bridge.track(player, EntityType.RABBIT);
        bridge.render(player, true, 0, true, false);
        for (PacketWrapper<?> lifecycle : List.of(new WrapperPlayServerDestroyEntities(SELF_ID), spawn(SELF_ID))) {
            sent.clear();
            bridge.onPacketSend(event(player, lifecycle));
            bridge.render(player, true, 0, true, false);
            assertEquals(1, packets(WrapperPlayServerEntityPositionSync.class).size());
            assertEquals(SELF_ID, packets(WrapperPlayServerEntityPositionSync.class).getFirst().getId());
            assertTrue(sent.stream().allMatch(p -> p.viewer() == player));
        }
    }

    @Test void unrelatedSpawnAndDestroyDoNotResetTheControlledMob() {
        bridge.track(player, EntityType.RABBIT);
        bridge.render(player, true, 0, true, false);
        sent.clear();
        bridge.onPacketSend(event(viewer, new WrapperPlayServerDestroyEntities(101, SELF_ID)));
        bridge.onPacketSend(event(viewer, spawn(101)));
        bridge.render(player, true, 0, true, false);
        assertTrue(sent.isEmpty());
    }

    private void at(double x, double y, double z) {
        when(player.getLocation()).thenReturn(new Location(world, x, y, z));
    }

    private static double wireDelta(double delta) { return (short) (delta * 4096) / 4096d; }

    private <T> List<T> packets(Class<T> type) {
        return sent.stream().map(Sent::packet).filter(type::isInstance).map(type::cast).toList();
    }

    private PacketSendEvent event(Player target, PacketWrapper<?> packet) {
        PacketSendEvent event = mock(PacketSendEvent.class);
        when(event.getPacketType()).thenReturn(packet.getPacketTypeData().getPacketType());
        when(event.getServerVersion()).thenReturn(ServerVersion.V_1_21_4);
        User user = mock(User.class);
        UUID viewerId = target.getUniqueId();
        when(user.getUUID()).thenReturn(viewerId);
        when(user.getClientVersion()).thenReturn(ClientVersion.V_1_21_4);
        when(event.getUser()).thenReturn(user);
        doReturn(packet).when(event).getLastUsedWrapper();
        return event;
    }

    private WrapperPlayServerSpawnEntity spawn(int entityId) {
        var type = mock(com.github.retrooper.packetevents.protocol.entity.type.EntityType.class);
        return new WrapperPlayServerSpawnEntity(entityId, Optional.of(player.getUniqueId()), type,
                new Vector3d(1, 64, 2), 0, 0, 0, 0, Optional.of(Vector3d.zero()));
    }
}
