package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class MobAnimationTest {
    private final World world = mock(World.class);
    private Location at(double x, double y, double z) { return new Location(world, x, y, z); }
    private MobAnimation rabbit() { return new MobAnimation(EntityType.RABBIT, at(0, 64, 0), true); }

    @Test void autoHopIsEmittedOnlyWhenTheRequestedJumpActuallyTakesOff() {
        var animation = rabbit();
        var request = animation.sample(at(0.1, 64, 0), true, false, 1);
        assertTrue(request.launchHop());
        assertFalse(request.hop());
        var delayed = animation.sample(at(0.15, 64, 0), false, false, 2);
        assertFalse(delayed.launchHop());
        assertFalse(delayed.hop());
        var takeoff = animation.sample(at(0.2, 64.2, 0), false, false, 3);
        assertTrue(takeoff.hop());
        assertFalse(takeoff.launchHop());
        assertFalse(animation.sample(at(0.3, 64.35, 0), false, false, 4).hop());
        assertFalse(animation.sample(at(0.4, 64.2, 0), false, false, 5).hop());
        assertFalse(animation.sample(at(0.5, 64, 0), true, false, 6).hop());
        assertFalse(animation.sample(at(0.5, 64, 0), true, false, 20).hop());
    }

    @Test void aBlockedJumpRequestDoesNotAnimateOrRepeatedlyInjectVelocity() {
        var animation = rabbit();
        assertTrue(animation.sample(at(0.1, 64, 0), true, false, 1).launchHop());
        for (int tick = 2; tick <= 9; tick++) {
            var frame = animation.sample(at(tick / 10d, 64, 0), true, false, tick);
            assertFalse(frame.hop()); assertFalse(frame.launchHop());
        }
    }

    @Test void manualJumpAndAutoHopUseTheSameSingleTakeoffLatch() {
        var animation = rabbit();
        assertTrue(animation.sample(at(0.1, 64.2, 0), false, false, 1).hop());
        for (int tick = 2; tick < 16; tick++)
            assertFalse(animation.sample(at(tick / 10d, 64.3, 0), false, false, tick).hop());
        animation.sample(at(1.6, 64, 0), true, false, 16);
        assertTrue(animation.sample(at(1.7, 64, 0), true, false, 17).launchHop());
        assertTrue(animation.sample(at(1.8, 64.2, 0), false, false, 18).hop());
    }

    @Test void oneFlickeringGroundSampleDoesNotRearmAnAirborneRabbit() {
        var animation = rabbit();
        assertTrue(animation.sample(at(0.1, 64.2, 0), false, false, 1).hop());
        animation.sample(at(0.2, 64.3, 0), true, false, 2);
        assertFalse(animation.sample(at(0.3, 64.4, 0), false, false, 3).hop());
    }

    @Test void holdingJumpCanTakeOffAgainImmediatelyAfterARealLanding() {
        var animation = rabbit();
        assertTrue(animation.sample(at(0.1, 64.2, 0), false, false, 1).hop());
        assertFalse(animation.sample(at(0.2, 64.1, 0), false, false, 10).hop());
        assertFalse(animation.sample(at(0.3, 64, 0), true, false, 11).hop());
        var second = animation.sample(at(0.4, 64.2, 0), false, false, 12);
        assertTrue(second.hop());
        assertFalse(second.launchHop());
        assertFalse(animation.sample(at(0.5, 64.3, 0), false, false, 13).hop());
    }

    @Test void walkingOffAnEdgeAndLandingDoesNotCountAsAJump() {
        var animation = rabbit();
        var falling = animation.sample(at(0.1, 63.8, 0), false, false, 1);
        assertFalse(falling.hop()); assertFalse(falling.launchHop());
        assertFalse(animation.sample(at(0.2, 63.5, 0), false, false, 2).hop());
        assertFalse(animation.sample(at(0.3, 63, 0), true, false, 3).hop());
    }

    @Test void steppingOntoASlabWhileGroundedDoesNotSendAHop() {
        var animation = rabbit();
        assertFalse(animation.sample(at(0.1, 64.5, 0), true, false, 1).hop());
    }

    @Test void waterClimbingAndRidingDoNotLaunchOrAnimateRabbitHops() {
        var animation = rabbit();
        var walking = animation.sample(at(0.1, 64, 0), true, true, 1);
        assertFalse(walking.hop()); assertFalse(walking.launchHop());
        assertFalse(animation.sample(at(0.2, 64.3, 0), false, true, 2).hop());
    }

    @Test void otherAnimalsAndHostileMobsNeverReceiveRabbitHops() {
        for (EntityType type : MobCatalog.ALL) {
            if (type == EntityType.RABBIT) continue;
            var animation = new MobAnimation(type, at(0, 64, 0), true);
            var frame = animation.sample(at(0.1, 64.3, 0), false, false, 1);
            assertFalse(frame.hop()); assertFalse(frame.launchHop());
        }
    }

    @Test void finishesTurningAfterStoppingAndDoesNotLeaveFiveDegreeErrors() {
        var animation = new MobAnimation(EntityType.COW, at(0, 64, 0), true);
        assertEquals(-45, animation.sample(at(0.1, 64, 0), true, false, 1).yaw(), 0.001);
        MobAnimation.Frame frame = null;
        for (int tick = 2; tick <= 8; tick++) frame = animation.sample(at(0.1, 64, 0), true, false, tick);
        assertEquals(-90, frame.yaw(), 0.001);
        var smallTurn = new MobAnimation(EntityType.SHEEP, at(0, 64, 0), true);
        assertEquals(3, smallTurn.sample(at(-Math.sin(Math.toRadians(3)), 64, Math.cos(Math.toRadians(3))),
                true, false, 1).yaw(), 0.001);
    }

    @Test void beingPushedWithoutMovementKeysKeepsTheBodyAndTheIdleGlance() {
        var animation = new MobAnimation(EntityType.COW, at(0, 64, 0), true, new Random(17));
        long tick = waitForGlance(animation);
        for (int step = 1; step <= 3; step++) {
            var pushed = animation.sample(at(step * 0.08, 64, 0), true, false, false, ++tick);
            assertEquals(0, pushed.yaw(), 0.001, "AI mobs do not turn toward a push");
            // 예전에는 밀림을 걷기로 보고 고개를 틱당 6도씩 되돌렸습니다. 둘러보기는 최소 10틱 유지됩니다.
            assertTrue(Math.abs(pushed.headYaw()) >= 17.99, "A push must not cancel the idle glance");
        }
        var walking = animation.sample(at(0.4, 64, 0), true, false, true, ++tick);
        assertEquals(-45, walking.yaw(), 0.001);
    }

    @Test void aPushedRabbitDoesNotAutoHopUntilThePlayerWalks() {
        var animation = rabbit();
        for (int tick = 1; tick <= 30; tick++) {
            var frame = animation.sample(at(tick / 20d, 64, 0), true, false, false, tick);
            assertFalse(frame.launchHop()); assertFalse(frame.hop());
        }
        assertTrue(animation.sample(at(1.6, 64, 0), true, false, true, 31).launchHop());
    }

    @Test void rabbitHopsKeepTheirForwardMomentumWithABoundForBunchedPackets() {
        var frame = rabbit().sample(at(0.1, 64, 0.05), true, false, 1);
        assertTrue(frame.launchHop());
        assertEquals(0.1, frame.stepX(), 1e-9); assertEquals(0.05, frame.stepZ(), 1e-9);
        var velocity = MobHidePlugin.hopVelocity(frame);
        assertEquals(0.1, velocity.getX(), 1e-9); assertEquals(0.3, velocity.getY(), 1e-9); assertEquals(0.05, velocity.getZ(), 1e-9);
        var bunched = MobHidePlugin.hopVelocity(new MobAnimation.Frame(0, 0, 0, true, false, true, 1.2, 0));
        assertEquals(0.4, bunched.getX(), 1e-9); assertEquals(0.3, bunched.getY(), 1e-9); assertEquals(0, bunched.getZ(), 1e-9);
    }

    @Test void cameraMovementAloneDoesNotSpinTheMobBody() {
        var animation = new MobAnimation(EntityType.CHICKEN, at(0, 64, 0), true);
        var camera = at(0, 64, 0); camera.setYaw(140); camera.setPitch(89);
        assertEquals(0, animation.sample(camera, true, false, 1).yaw(), 0.001);
    }

    @Test void rotationUsesTheShortPathAcrossTheAngleBoundary() {
        var start = at(0, 64, 0); start.setYaw(179);
        var animation = new MobAnimation(EntityType.SKELETON, start, true);
        assertEquals(-179, animation.sample(at(Math.sin(Math.toRadians(179)), 64, Math.cos(Math.toRadians(179))),
                true, false, 1).yaw(), 0.001);
    }

    @Test void teleportsAndWorldChangesResetMotionInsteadOfBecomingJumps() {
        var animation = rabbit();
        var far = animation.sample(at(30, 70, 0), false, false, 1);
        assertFalse(far.hop()); assertFalse(far.launchHop());
        var otherWorld = new Location(mock(World.class), 0.1, 64.2, 0);
        var changed = animation.sample(otherWorld, false, false, 2);
        assertFalse(changed.hop()); assertFalse(changed.launchHop());
        animation.reset(at(0, 64, 0));
        var corrected = animation.sample(at(0.1, 64.2, 0), false, false, 3);
        assertFalse(corrected.hop()); assertFalse(corrected.launchHop());
    }

    @Test void idleGlancesMoveOnlyTheHeadSmoothlyAndOccasionallySettle() {
        var animation = new MobAnimation(EntityType.COW, at(0, 64, 0), true, new Random(17));
        float previousHead = 0, previousPitch = 0;
        boolean lookedLeft = false, lookedRight = false, settledAfterGlance = false, tilted = false;
        for (int tick = 1; tick <= 1200; tick++) {
            var camera = at(0, 64, 0); camera.setYaw(tick * 17); camera.setPitch(80);
            var frame = animation.sample(camera, true, false, tick);
            assertEquals(0, frame.yaw(), 0.001, "Idle gaze must not turn the body or copy the camera");
            assertTrue(Math.abs(frame.headYaw()) <= 42.001);
            assertTrue(Math.abs(frame.headYaw() - previousHead) <= 6.001);
            assertTrue(Math.abs(frame.pitch() - previousPitch) <= 1.201);
            assertTrue(frame.pitch() >= -8.001 && frame.pitch() <= 6.001);
            lookedLeft |= frame.headYaw() < -15;
            lookedRight |= frame.headYaw() > 15;
            tilted |= Math.abs(frame.pitch()) > 2;
            settledAfterGlance |= (lookedLeft || lookedRight) && Math.abs(frame.headYaw()) < 0.001;
            previousHead = frame.headYaw(); previousPitch = frame.pitch();
        }
        assertTrue(lookedLeft && lookedRight && tilted && settledAfterGlance);
    }

    @Test void seedControlsIndividualGlanceTimingAndDirection() {
        var first = new MobAnimation(EntityType.PIG, at(0, 64, 0), true, new Random(1));
        var same = new MobAnimation(EntityType.PIG, at(0, 64, 0), true, new Random(1));
        var other = new MobAnimation(EntityType.PIG, at(0, 64, 0), true, new Random(2));
        boolean differed = false;
        for (int tick = 1; tick <= 200; tick++) {
            var frame = first.sample(at(0, 64, 0), true, false, tick);
            assertEquals(frame, same.sample(at(0, 64, 0), true, false, tick));
            differed |= !frame.equals(other.sample(at(0, 64, 0), true, false, tick));
        }
        assertTrue(differed, "Different mobs must not share a synchronized glance schedule");
    }

    @Test void walkingRecentersTheHeadAndStartsAFreshIdleWait() {
        var animation = new MobAnimation(EntityType.SHEEP, at(0, 64, 0), true, new Random(17));
        long tick = waitForGlance(animation);
        for (int step = 1; step <= 10; step++) {
            var frame = animation.sample(at(0, 64, step / 10d), true, false, ++tick);
            assertEquals(0, frame.yaw(), 0.001);
            if (step == 10) {
                assertEquals(0, frame.headYaw(), 0.001);
                assertEquals(0, frame.pitch(), 0.001);
            }
        }
        for (int wait = 0; wait < 25; wait++) {
            var frame = animation.sample(at(0, 64, 1), true, false, ++tick);
            assertEquals(0, frame.headYaw(), 0.001);
            assertEquals(0, frame.pitch(), 0.001);
        }
    }

    @Test void airborneSwimmingAndClimbingMobsDoNotStartIdleGlances() {
        for (boolean grounded : new boolean[]{false, true}) {
            var animation = new MobAnimation(EntityType.CHICKEN, at(0, 64, 0), true, new Random(17));
            long tick = waitForGlance(animation);
            for (int step = 1; step <= 200; step++) {
                var frame = animation.sample(at(0, 64, 0), grounded, grounded, ++tick);
                if (step >= 10) {
                    assertEquals(0, frame.headYaw(), 0.001);
                    assertEquals(0, frame.pitch(), 0.001);
                }
                assertFalse(frame.hop()); assertFalse(frame.launchHop());
            }
        }
    }

    @Test void idleGazeStaysNearTheBodyAcrossTheYawWrapBoundary() {
        var position = at(0, 64, 0); position.setYaw(179);
        var animation = new MobAnimation(EntityType.SKELETON, position, true, new Random(17));
        float previousHead = 179;
        boolean crossed = false;
        for (int tick = 1; tick <= 500; tick++) {
            var frame = animation.sample(position, true, false, tick);
            assertEquals(179, frame.yaw(), 0.001);
            assertTrue(Math.abs(MobMovement.normalize(frame.headYaw() - frame.yaw())) <= 42.001);
            assertTrue(Math.abs(MobMovement.normalize(frame.headYaw() - previousHead)) <= 6.001);
            crossed |= frame.headYaw() < -150;
            previousHead = frame.headYaw();
        }
        assertTrue(crossed);
    }

    @Test void aSuddenMovementTurnCannotLeaveTheHeadFacingBackwards() {
        var animation = new MobAnimation(EntityType.COW, at(0, 64, 0), true, new Random(17));
        long tick = waitForGlance(animation);
        for (int step = 1; step <= 20; step++) {
            var frame = animation.sample(at(0, 64, -step / 10d), true, false, ++tick);
            assertTrue(Math.abs(MobMovement.normalize(frame.headYaw() - frame.yaw())) <= 42.001);
            if (step == 20) {
                assertEquals(0, MobMovement.normalize(frame.headYaw() - frame.yaw()), 0.001);
                assertEquals(0, frame.pitch(), 0.001);
            }
        }
    }

    @Test void explicitResetAndTeleportDiscardOldGaze() {
        for (boolean explicit : new boolean[]{false, true}) {
            var animation = new MobAnimation(EntityType.RABBIT, at(0, 64, 0), true, new Random(17));
            long tick = waitForGlance(animation);
            var destination = at(50, 70, 0); destination.setYaw(73);
            if (explicit) animation.reset(destination);
            var frame = animation.sample(destination, true, false, ++tick);
            assertEquals(73, frame.yaw(), 0.001);
            assertEquals(73, frame.headYaw(), 0.001);
            assertEquals(0, frame.pitch(), 0.001);
            assertFalse(frame.hop()); assertFalse(frame.launchHop());
            for (int wait = 0; wait < 25; wait++) {
                frame = animation.sample(destination, true, false, ++tick);
                assertEquals(73, frame.headYaw(), 0.001);
                assertEquals(0, frame.pitch(), 0.001);
            }
        }
    }

    private long waitForGlance(MobAnimation animation) {
        for (long tick = 1; tick < 200; tick++) {
            var frame = animation.sample(at(0, 64, 0), true, false, tick);
            if (Math.abs(frame.headYaw()) >= 18) return tick;
        }
        fail("Expected an idle glance within 200 ticks");
        return 0;
    }
}
