package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent.Action;
import org.bukkit.event.entity.EntityPotionEffectEvent.Cause;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.mockito.MockedStatic;

class MobHidePluginProtectionTest {
    private MobHidePlugin plugin;
    private Player seeker, hider, outsider;
    private Map<UUID, Object> participants;
    private ArrayList<Entity> decoys;

    @BeforeAll static void initializeRegistries() { TestRegistries.initialize(); }

    @BeforeEach void setUp() throws Exception {
        plugin = mock(MobHidePlugin.class, withSettings().mockMaker(MockMakers.INLINE).defaultAnswer(CALLS_REAL_METHODS));
        seeker = player(); hider = player(); outsider = player();
        participants = new HashMap<>();
        participants.put(seeker.getUniqueId(), null);
        participants.put(hider.getUniqueId(), null);
        decoys = new ArrayList<>();
        field("participants", participants);
        field("decoys", decoys);
        field("motions", new HashMap<>());
        field("hitSpeedBoosts", new HashMap<>());
        field("hitProtection", new HashMap<>());
        field("lastInside", new HashMap<>());
        field("round", new RoundState(seeker.getUniqueId(), Set.of(hider.getUniqueId()), 15, 180));
        field("startedAt", System.nanoTime());
    }

    @Test void participantIgnitionIsCancelledWithoutBlockingUnrelatedPlayersOrMobs() {
        for (Player participant : new Player[] {seeker, hider}) {
            EntityCombustEvent event = new EntityCombustEvent(participant, 8f);
            plugin.onCombust(event);
            assertTrue(event.isCancelled());
        }
        EntityCombustEvent outside = new EntityCombustEvent(outsider, 8f);
        plugin.onCombust(outside);
        assertFalse(outside.isCancelled());
        Entity unrelatedMob = mock(Entity.class);
        when(unrelatedMob.getUniqueId()).thenReturn(UUID.randomUUID());
        EntityCombustEvent mob = new EntityCombustEvent(unrelatedMob, 8f);
        plugin.onCombust(mob);
        assertFalse(mob.isCancelled());
    }

    @Test void gameDecoysKeepTheirFireProtection() {
        Entity decoy = mock(Entity.class);
        decoys.add(decoy);
        EntityCombustEvent event = new EntityCombustEvent(decoy, 8f);
        plugin.onCombust(event);
        assertTrue(event.isCancelled());
    }

    @Test void participantsAreNoLongerProtectedAfterTheRoundEnds() throws Exception {
        field("round", null);
        EntityCombustEvent event = new EntityCombustEvent(hider, 8f);
        plugin.onCombust(event);
        assertFalse(event.isCancelled());
    }

    @Test void milkAndEarlyExpirationCannotRemoveTheSeekersPreparationBlindness() throws Exception {
        field("blindnessApplied", true);
        for (Cause cause : new Cause[] {Cause.MILK, Cause.EXPIRATION, Cause.COMMAND}) {
            EntityPotionEffectEvent event = removal(seeker, PotionEffectType.BLINDNESS, cause);
            plugin.onPotionEffect(event);
            assertTrue(event.isCancelled(), cause.name());
        }
    }

    @Test void theBlindnessGuardDoesNotBlockOtherPlayersOrOtherPotionEffects() throws Exception {
        field("blindnessApplied", true);
        for (Player other : new Player[] {hider, outsider}) {
            EntityPotionEffectEvent event = removal(other, PotionEffectType.BLINDNESS, Cause.MILK);
            plugin.onPotionEffect(event);
            assertFalse(event.isCancelled());
        }
        EntityPotionEffectEvent speed = removal(seeker, PotionEffectType.SPEED, Cause.MILK);
        plugin.onPotionEffect(speed);
        assertFalse(speed.isCancelled());
    }

    @Test void blindnessChangesAreAllowedDuringChaseAndWhenNoGuardWasApplied() throws Exception {
        EntityPotionEffectEvent beforeApplication = removal(seeker, PotionEffectType.BLINDNESS, Cause.MILK);
        plugin.onPotionEffect(beforeApplication);
        assertFalse(beforeApplication.isCancelled());
        field("blindnessApplied", true);
        field("startedAt", System.nanoTime() - 30_000_000_000L);
        EntityPotionEffectEvent duringChase = removal(seeker, PotionEffectType.BLINDNESS, Cause.MILK);
        plugin.onPotionEffect(duringChase);
        assertFalse(duringChase.isCancelled());
    }

    @Test void internalRestorationCanRemoveBlindnessEvenDuringPreparation() throws Exception {
        field("blindnessApplied", true);
        EntityPotionEffectEvent removal = removal(seeker, PotionEffectType.BLINDNESS, Cause.PLUGIN);
        doAnswer(call -> {
            plugin.onPotionEffect(removal);
            return null;
        }).when(seeker).removePotionEffect(PotionEffectType.BLINDNESS);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, withSettings().mockMaker(MockMakers.INLINE))) {
            bukkit.when(() -> Bukkit.getPlayer(seeker.getUniqueId())).thenReturn(seeker);
            invoke("restoreBlindness");
        }
        verify(seeker).removePotionEffect(PotionEffectType.BLINDNESS);
        assertFalse(removal.isCancelled());
        assertEquals(false, getField("blindnessApplied"));
    }

    @Test void stoppingRestoresTheSavedFireDurationAndClearsTheParticipant() throws Exception {
        participants.clear();
        var snapshotType = Class.forName(MobHidePlugin.class.getName() + "$Snapshot");
        var constructor = snapshotType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Location original = new Location(null, 4, 65, -2);
        participants.put(hider.getUniqueId(), constructor.newInstance(original, GameMode.SURVIVAL, 0.2f, false, true, null, 120));
        when(hider.teleport(original)).thenReturn(true);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, withSettings().mockMaker(MockMakers.INLINE))) {
            bukkit.when(() -> Bukkit.getPlayer(hider.getUniqueId())).thenReturn(hider);
            var finish = MobHidePlugin.class.getDeclaredMethod("finish", String.class);
            finish.setAccessible(true);
            finish.invoke(plugin, (Object) null);
        }
        verify(hider).setFireTicks(120);
        verify(hider).teleport(original);
        assertTrue(participants.isEmpty());
        assertNull(getField("round"));
    }

    private EntityPotionEffectEvent removal(Player player, PotionEffectType type, Cause cause) {
        return new EntityPotionEffectEvent(player, new PotionEffect(type, 300, 0), null, cause, Action.REMOVED, false);
    }

    @Test void naturalWitchCannotTargetEitherTeamButCanTargetAnOutsider() {
        var witch = mock(org.bukkit.entity.Witch.class);
        for (Player target : new Player[] {seeker, hider, outsider}) {
            var event = new org.bukkit.event.entity.EntityTargetLivingEntityEvent(witch, target,
                    org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_PLAYER);
            plugin.onTarget(event);
            assertEquals(target != outsider, event.isCancelled());
        }
        var release = new org.bukkit.event.entity.EntityTargetLivingEntityEvent(witch, null,
                org.bukkit.event.entity.EntityTargetEvent.TargetReason.FORGOT_TARGET);
        plugin.onTarget(release); assertFalse(release.isCancelled());
    }

    @Test void gameOwnedWitchCannotTargetEvenAnOutsider() {
        var witch = mock(org.bukkit.entity.Witch.class); decoys.add(witch);
        var event = new org.bukkit.event.entity.EntityTargetLivingEntityEvent(witch, outsider,
                org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_PLAYER);
        plugin.onTarget(event); assertTrue(event.isCancelled());
    }

    @Test void alreadyTargetingWitchCannotLaunchAndLosesItsTarget() {
        var witch = mock(org.bukkit.entity.Witch.class); when(witch.getTarget()).thenReturn(hider);
        var potion = mock(org.bukkit.entity.ThrownPotion.class); when(potion.getShooter()).thenReturn(witch);
        var event = new org.bukkit.event.entity.ProjectileLaunchEvent(potion);
        plugin.onMobProjectile(event);
        assertTrue(event.isCancelled()); verify(witch).setTarget(null);
    }

    @Test void airborneWitchPotionCannotApplyEffectsToEitherTeamOrDecoys() {
        var witch = mock(org.bukkit.entity.Witch.class);
        var potion = mock(org.bukkit.entity.ThrownPotion.class); when(potion.getShooter()).thenReturn(witch);
        var decoy = mock(org.bukkit.entity.Cow.class); decoys.add(decoy);
        var affected = new HashMap<org.bukkit.entity.LivingEntity, Double>();
        for (var entity : new org.bukkit.entity.LivingEntity[] {seeker, hider, outsider, decoy}) affected.put(entity, 1.0);
        var event = new org.bukkit.event.entity.PotionSplashEvent(potion, null, null, null, affected);
        plugin.onMobPotion(event);
        assertEquals(0, event.getIntensity(seeker)); assertEquals(0, event.getIntensity(hider));
        assertEquals(0, event.getIntensity(decoy)); assertEquals(1, event.getIntensity(outsider));
        assertFalse(event.isCancelled());
    }

    @Test void ownedWitchPotionIsCancelledForEveryone() {
        var witch = mock(org.bukkit.entity.Witch.class); decoys.add(witch);
        var potion = mock(org.bukkit.entity.ThrownPotion.class); when(potion.getShooter()).thenReturn(witch);
        var event = new org.bukkit.event.entity.PotionSplashEvent(potion, null, null, null, new HashMap<>(Map.of(outsider, 1.0)));
        plugin.onMobPotion(event); assertTrue(event.isCancelled());
    }

    @Test void mobCloudSkipsParticipantsWithoutChangingOtherVictims() {
        var cloud = mock(org.bukkit.entity.AreaEffectCloud.class);
        when(cloud.getSource()).thenReturn(mock(org.bukkit.entity.Witch.class));
        var event = new org.bukkit.event.entity.AreaEffectCloudApplyEvent(cloud, new ArrayList<>(java.util.List.of(seeker, hider, outsider)));
        plugin.onMobPotionCloud(event);
        assertEquals(java.util.List.of(outsider), event.getAffectedEntities());
    }

    @Test void playerPotionsAndArrowsAreNotBlockedByTheMobGuard() {
        var potion = mock(org.bukkit.entity.ThrownPotion.class); when(potion.getShooter()).thenReturn(seeker);
        var splash = new org.bukkit.event.entity.PotionSplashEvent(potion, null, null, null, new HashMap<>(Map.of(hider, 1.0)));
        plugin.onMobPotion(splash); assertEquals(1, splash.getIntensity(hider));
        var launch = new org.bukkit.event.entity.ProjectileLaunchEvent(potion);
        plugin.onMobProjectile(launch); assertFalse(launch.isCancelled());
    }

    @Test void witchProtectionEndsWithTheRound() throws Exception {
        field("round", null);
        var witch = mock(org.bukkit.entity.Witch.class); when(witch.getTarget()).thenReturn(hider);
        var target = new org.bukkit.event.entity.EntityTargetLivingEntityEvent(witch, hider,
                org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_PLAYER);
        plugin.onTarget(target); assertFalse(target.isCancelled());
        var potion = mock(org.bukkit.entity.ThrownPotion.class); when(potion.getShooter()).thenReturn(witch);
        var splash = new org.bukkit.event.entity.PotionSplashEvent(potion, null, null, null, new HashMap<>(Map.of(hider, 1.0)));
        plugin.onMobPotion(splash); assertEquals(1, splash.getIntensity(hider));
        var launch = new org.bukkit.event.entity.ProjectileLaunchEvent(potion);
        plugin.onMobProjectile(launch); assertFalse(launch.isCancelled());
    }

    @Test void hidersCannotUseOrDropItemsWhileTheSeekerAndBlockControlsStillWork() throws Exception {
        field("motions", new HashMap<>(Map.of(hider.getUniqueId(),
                new MobAnimation(org.bukkit.entity.EntityType.COW, new Location(null, 0, 64, 0), true))));
        var item = mock(org.bukkit.inventory.ItemStack.class);
        var door = mock(org.bukkit.block.Block.class);
        for (var action : new org.bukkit.event.block.Action[] {
                org.bukkit.event.block.Action.RIGHT_CLICK_AIR, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK}) {
            var block = action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK ? door : null;
            var hidden = new org.bukkit.event.player.PlayerInteractEvent(hider, action, item, block, org.bukkit.block.BlockFace.UP);
            plugin.onInteract(hidden);
            assertEquals(org.bukkit.event.Event.Result.DENY, hidden.useItemInHand());
            if (block != null) assertEquals(org.bukkit.event.Event.Result.ALLOW, hidden.useInteractedBlock());
            var seeking = new org.bukkit.event.player.PlayerInteractEvent(seeker, action, item, block, org.bukkit.block.BlockFace.UP);
            plugin.onInteract(seeking);
            assertEquals(org.bukkit.event.Event.Result.DEFAULT, seeking.useItemInHand());
        }
        var drop = new org.bukkit.event.player.PlayerDropItemEvent(hider, mock(org.bukkit.entity.Item.class));
        plugin.onDrop(drop); assertTrue(drop.isCancelled());
        var seekerDrop = new org.bukkit.event.player.PlayerDropItemEvent(seeker, mock(org.bukkit.entity.Item.class));
        plugin.onDrop(seekerDrop); assertFalse(seekerDrop.isCancelled());

        field("round", null);
        var after = new org.bukkit.event.player.PlayerInteractEvent(hider, org.bukkit.event.block.Action.RIGHT_CLICK_AIR,
                item, null, org.bukkit.block.BlockFace.SELF);
        plugin.onInteract(after);
        assertEquals(org.bukkit.event.Event.Result.DEFAULT, after.useItemInHand());
    }

    @Test void participantsCannotTrampleFarmlandButPressurePlatesStillWork() {
        var farmland = mock(org.bukkit.block.Block.class);
        when(farmland.getType()).thenReturn(org.bukkit.Material.FARMLAND);
        var plate = mock(org.bukkit.block.Block.class);
        when(plate.getType()).thenReturn(org.bukkit.Material.STONE_PRESSURE_PLATE);
        for (Player participant : new Player[] {seeker, hider}) {
            var trample = new org.bukkit.event.player.PlayerInteractEvent(participant,
                    org.bukkit.event.block.Action.PHYSICAL, null, farmland, org.bukkit.block.BlockFace.SELF);
            plugin.onInteract(trample); assertEquals(org.bukkit.event.Event.Result.DENY, trample.useInteractedBlock());
            var press = new org.bukkit.event.player.PlayerInteractEvent(participant,
                    org.bukkit.event.block.Action.PHYSICAL, null, plate, org.bukkit.block.BlockFace.SELF);
            plugin.onInteract(press); assertEquals(org.bukkit.event.Event.Result.ALLOW, press.useInteractedBlock());
        }
        var outside = new org.bukkit.event.player.PlayerInteractEvent(outsider,
                org.bukkit.event.block.Action.PHYSICAL, null, farmland, org.bukkit.block.BlockFace.SELF);
        plugin.onInteract(outside); assertEquals(org.bukkit.event.Event.Result.ALLOW, outside.useInteractedBlock());
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    private void field(String name, Object value) throws Exception {
        var field = MobHidePlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }

    private Object getField(String name) throws Exception {
        var field = MobHidePlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(plugin);
    }

    private void invoke(String name) throws Exception {
        var method = MobHidePlugin.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(plugin);
    }
}
