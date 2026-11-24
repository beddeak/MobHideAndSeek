package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.MobGoals;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

class DecoyPlayerGoalTest {
    private final Animals mob = mock(Animals.class);
    private final World world = mock(World.class);
    private final Pathfinder pathfinder = mock(Pathfinder.class);
    private final Set<UUID> hiders = new HashSet<>();
    private final RandomGenerator random = mock(RandomGenerator.class);

    DecoyPlayerGoalTest() {
        when(mob.getWorld()).thenReturn(world);
        when(mob.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(mob.getType()).thenReturn(EntityType.COW);
        when(mob.getPathfinder()).thenReturn(pathfinder);
        when(mob.hasLineOfSight(any(Player.class))).thenReturn(true);
        when(mob.getMaxHeadPitch()).thenReturn(40);
        when(mob.getTicksLived()).thenReturn(1);
    }

    private Player player(double distance) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, distance, 64, 0));
        when(player.isValid()).thenReturn(true);
        when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.ADVENTURE);
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        return player;
    }

    private DecoyPlayerGoal goal(DecoyPlayerGoal.Mode mode) {
        return new DecoyPlayerGoal(mob, p -> hiders.contains(p.getUniqueId()), mode, random);
    }

    @Test void nearerDisguisedPlayerDoesNotHideTheSeekerFromSelection() {
        Player hidden = player(1), seeker = player(4);
        hiders.add(hidden.getUniqueId());
        when(world.getPlayers()).thenReturn(List.of(hidden, seeker));
        var goal = goal(DecoyPlayerGoal.Mode.WATCH);
        assertTrue(goal.shouldActivate());
        goal.start();
        goal.tick();
        verify(mob).lookAt(seeker, 10, 40);
        verify(mob, never()).lookAt(eq(hidden), anyFloat(), anyFloat());
    }

    @Test void soloHiderDoesNotBecomeALookOrFoodTarget() {
        Player hidden = player(1);
        hiders.add(hidden.getUniqueId());
        when(world.getPlayers()).thenReturn(List.of(hidden));
        assertFalse(goal(DecoyPlayerGoal.Mode.WATCH).shouldActivate());
        assertFalse(goal(DecoyPlayerGoal.Mode.TEMPT).shouldActivate());
        verify(hidden, never()).getInventory();
    }

    @Test void becomingHiddenStopsAnExistingLookImmediately() {
        Player target = player(3);
        when(world.getPlayers()).thenReturn(List.of(target));
        var goal = goal(DecoyPlayerGoal.Mode.WATCH);
        assertTrue(goal.shouldActivate());
        goal.start();
        hiders.add(target.getUniqueId());
        assertFalse(goal.shouldStayActive());
        goal.tick();
        verify(mob, never()).lookAt(any(Player.class), anyFloat(), anyFloat());
    }

    @Test void spectatorDeadOfflineOccludedAndOutOfRangePlayersAreIgnored() {
        Player spectator = player(1), dead = player(2), offline = player(3), occluded = player(4), distant = player(7);
        when(spectator.getGameMode()).thenReturn(GameMode.SPECTATOR);
        when(dead.isDead()).thenReturn(true);
        when(offline.isOnline()).thenReturn(false);
        when(mob.hasLineOfSight(occluded)).thenReturn(false);
        when(world.getPlayers()).thenReturn(List.of(spectator, dead, offline, occluded, distant));
        assertFalse(goal(DecoyPlayerGoal.Mode.WATCH).shouldActivate());
    }

    @Test void worldChangeDoesNotKeepAnExistingTargetOrThrowDistanceError() {
        Player target = player(3);
        when(world.getPlayers()).thenReturn(List.of(target));
        var goal = goal(DecoyPlayerGoal.Mode.WATCH);
        assertTrue(goal.shouldActivate());
        goal.start();
        when(target.getWorld()).thenReturn(mock(World.class));
        assertFalse(goal.shouldStayActive());
        goal.tick();
        verify(mob, never()).lookAt(any(Player.class), anyFloat(), anyFloat());
    }

    @Test void lookingEndsSoRandomLookingAndWalkingCanResume() {
        Player target = player(3);
        when(world.getPlayers()).thenReturn(List.of(target));
        var goal = goal(DecoyPlayerGoal.Mode.WATCH);
        assertTrue(goal.shouldActivate());
        goal.start();
        for (int i = 0; i < 40; i++) goal.tick();
        assertFalse(goal.shouldStayActive());
        verifyNoInteractions(pathfinder);
    }

    @Test void offhandFoodAttractsAVisiblePlayerButNeverTheNearerHider() {
        Player hidden = player(1), seeker = player(5);
        hiders.add(hidden.getUniqueId());
        ItemStack food = mock(ItemStack.class);
        when(seeker.getInventory().getItemInOffHand()).thenReturn(food);
        when(mob.isBreedItem(food)).thenReturn(true);
        when(world.getPlayers()).thenReturn(List.of(hidden, seeker));
        var goal = goal(DecoyPlayerGoal.Mode.TEMPT);
        assertTrue(goal.shouldActivate());
        goal.start();
        goal.tick();
        verify(mob).lookAt(seeker, 10, 40);
        verify(pathfinder).moveTo(seeker, 1.25);
        verify(hidden, never()).getInventory();
    }

    @Test void hidingDuringFoodFollowingStopsTheOldPath() {
        Player target = player(5);
        ItemStack food = mock(ItemStack.class);
        when(target.getInventory().getItemInMainHand()).thenReturn(food);
        when(mob.isBreedItem(food)).thenReturn(true);
        when(world.getPlayers()).thenReturn(List.of(target));
        var goal = goal(DecoyPlayerGoal.Mode.TEMPT);
        assertTrue(goal.shouldActivate());
        goal.start();
        hiders.add(target.getUniqueId());
        goal.tick();
        verify(pathfinder).stopPathfinding();
        verify(pathfinder, never()).moveTo(any(Player.class), anyDouble());
        verify(mob, never()).lookAt(any(Player.class), anyFloat(), anyFloat());
        hiders.clear();
        assertFalse(goal.shouldActivate()); // Cooldown after releasing the previous target.
        when(mob.getTicksLived()).thenReturn(101);
        assertTrue(goal.shouldActivate());
    }

    @Test void puttingFoodAwayEndsFollowing() {
        Player target = player(5);
        ItemStack food = mock(ItemStack.class);
        when(target.getInventory().getItemInMainHand()).thenReturn(food);
        when(mob.isBreedItem(food)).thenReturn(true);
        when(world.getPlayers()).thenReturn(List.of(target));
        var goal = goal(DecoyPlayerGoal.Mode.TEMPT);
        assertTrue(goal.shouldActivate());
        goal.start();
        when(target.getInventory().getItemInMainHand()).thenReturn(null);
        assertFalse(goal.shouldStayActive());
        goal.tick();
        verify(pathfinder).stopPathfinding();
    }

    @Test void installationOnlyReplacesLookAndTemptLeavingOtherGoalsAlone() {
        MobGoals goals = mock(MobGoals.class);
        when(goals.hasGoal(mob, VanillaGoal.LOOK_AT_PLAYER)).thenReturn(true);
        when(goals.hasGoal(mob, VanillaGoal.TEMPT)).thenReturn(true);
        DecoyPlayerGoal.install(goals, mob, p -> false);
        verify(goals).removeGoal(mob, VanillaGoal.LOOK_AT_PLAYER);
        verify(goals).removeGoal(mob, VanillaGoal.TEMPT);
        verify(goals).addGoal(eq(mob), eq(6), any(Goal.class));
        verify(goals).addGoal(eq(mob), eq(3), any(Goal.class));
        verify(goals).hasGoal(mob, VanillaGoal.LOOK_AT_PLAYER);
        verify(goals).hasGoal(mob, VanillaGoal.TEMPT);
        verifyNoMoreInteractions(goals);
    }

    @Test void rabbitDoesNotGainANewWatchGoal() {
        MobGoals goals = mock(MobGoals.class);
        when(mob.getType()).thenReturn(EntityType.RABBIT);
        when(goals.hasGoal(mob, VanillaGoal.TEMPT)).thenReturn(true);
        DecoyPlayerGoal.install(goals, mob, p -> false);
        verify(goals, never()).removeGoal(mob, VanillaGoal.LOOK_AT_PLAYER);
        verify(goals, never()).addGoal(eq(mob), eq(6), any(Goal.class));
        verify(goals).addGoal(eq(mob), eq(4), any(Goal.class));
    }
}
