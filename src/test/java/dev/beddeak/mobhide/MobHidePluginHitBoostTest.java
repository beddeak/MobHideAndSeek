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
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockMakers;
import org.mockito.MockedStatic;

class MobHidePluginHitBoostTest {
    private MobHidePlugin plugin;
    private MockedStatic<Bukkit> bukkit;
    private final World world = mock(World.class);
    private final YamlConfiguration config = new YamlConfiguration();
    private final Map<UUID, Object> participants = new HashMap<>(), boosts = new HashMap<>();
    private final Map<UUID, MobAnimation> motions = new HashMap<>();
    private final Map<UUID, Long> protection = new HashMap<>();
    private final List<Entity> decoys = new ArrayList<>();
    private Player seeker, hider;
    private RoundState round;

    @BeforeEach void setup() throws Exception {
        TestRegistries.initialize();
        plugin = mock(MobHidePlugin.class, withSettings().mockMaker(MockMakers.INLINE).defaultAnswer(CALLS_REAL_METHODS));
        doReturn(config).when(plugin).getConfig();
        doNothing().when(plugin).saveConfig();
        bukkit = mockStatic(Bukkit.class, withSettings().mockMaker(MockMakers.INLINE));
        seeker = player(); hider = player();
        round = new RoundState(seeker.getUniqueId(), List.of(hider.getUniqueId()), 0, 180);
        round.enableSandbox();
        set("round", round);
        set("participants", participants);
        set("motions", motions);
        set("hitSpeedBoosts", boosts);
        set("hitProtection", protection);
        set("decoys", decoys);
        set("lastInside", new HashMap<>());
        set("startedAt", System.nanoTime());
        participants.put(seeker.getUniqueId(), snapshot());
        participants.put(hider.getUniqueId(), snapshot());
        morph(EntityType.COW);
    }

    @AfterEach void cleanup() { if (bukkit != null) bukkit.close(); }

    @ParameterizedTest
    @EnumSource(value = EntityType.class, names = {"COW", "PIG", "SHEEP", "CHICKEN", "RABBIT", "ZOMBIE", "SKELETON", "CREEPER"})
    void survivingHitGivesEveryMobAnEscapeSpeedAndKeepsTheSecondHit(EntityType type) {
        morph(type);
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);

        assertEquals(1, round.hiderHitsRemaining(hider.getUniqueId()));
        verify(hider).setWalkSpeed(0.30f);
        verify(seeker, never()).setWalkSpeed(anyFloat());
        verify(hider, never()).addPotionEffect(any());
        verify(hider, never()).removePotionEffect(any());
    }

    @Test void boostExpiresAfterSixtyTicksAndRestoresTheMobSpeed() throws Exception {
        morph(EntityType.CHICKEN);
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        clearInvocations(hider);
        expireAt(59);
        verify(hider, never()).setWalkSpeed(anyFloat());
        expireAt(60);
        verify(hider).setWalkSpeed(MobMovement.walkSpeed(EntityType.CHICKEN));
        assertTrue(boosts.isEmpty());
        expireAt(61);
        verify(hider, times(1)).setWalkSpeed(anyFloat());
    }

    @Test void countdownCannotConsumeHiderLivesOrSeekerMistakesEvenInSandbox() throws Exception {
        set("countdownRemaining", 3);
        Entity ai = mock(Entity.class);
        when(ai.getUniqueId()).thenReturn(UUID.randomUUID());
        decoys.add(ai);
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        hit(seeker, ai, DamageCause.ENTITY_ATTACK);
        assertEquals(2, round.hiderHitsRemaining(hider.getUniqueId()));
        assertEquals(20, round.mistakesRemaining());
        assertTrue(boosts.isEmpty());
        verify(hider, never()).setHealth(anyDouble());
        verify(seeker, never()).setHealth(anyDouble());
    }

    @Test void anotherSurvivingHitRefreshesDurationWithoutStackingSpeed() throws Exception {
        round.setHiderLives(hider.getUniqueId(), 5);
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        set("motionTick", 30L);
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        verify(hider, times(2)).setWalkSpeed(0.30f);
        clearInvocations(hider);
        expireAt(60);
        expireAt(89);
        verify(hider, never()).setWalkSpeed(anyFloat());
        expireAt(90);
        verify(hider).setWalkSpeed(MobMovement.walkSpeed(EntityType.COW));
    }

    @Test void theSameProjectileCannotRefreshTheBoost() throws Exception {
        Projectile arrow = mock(Projectile.class);
        when(arrow.getUniqueId()).thenReturn(UUID.randomUUID());
        when(arrow.getShooter()).thenReturn(seeker);
        hit(arrow, hider, DamageCause.PROJECTILE);
        set("motionTick", 30L);
        hit(arrow, hider, DamageCause.PROJECTILE);
        assertEquals(1, round.hiderHitsRemaining(hider.getUniqueId()));
        verify(hider, times(1)).setWalkSpeed(0.30f);
        expireAt(60);
        verify(hider).setWalkSpeed(MobMovement.walkSpeed(EntityType.COW));
    }

    @Test void movementTickerActuallyExpiresTheBoost() throws Exception {
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        set("motionTick", 59L);
        set("animations", mock(AnimationBridge.class, withSettings().mockMaker(MockMakers.INLINE)));
        set("disguises", mock(Disguises.class, withSettings().mockMaker(MockMakers.INLINE)));
        try (var bridge = mockStatic(AnimationBridge.class, withSettings().mockMaker(MockMakers.INLINE))) {
            bridge.when(() -> AnimationBridge.grounded(hider)).thenReturn(true);
            var tick = MobHidePlugin.class.getDeclaredMethod("moveMobs");
            tick.setAccessible(true);
            tick.invoke(plugin);
        }
        assertTrue(boosts.isEmpty());
        verify(hider).setWalkSpeed(MobMovement.walkSpeed(EntityType.COW));
    }

    @Test void outsiderHiderSweepAndAiHitsDoNotGrantABoost() {
        hit(player(), hider, DamageCause.ENTITY_ATTACK);
        hit(hider, seeker, DamageCause.ENTITY_ATTACK);
        hit(seeker, hider, DamageCause.ENTITY_SWEEP_ATTACK);
        Entity decoy = mock(Entity.class);
        when(decoy.getUniqueId()).thenReturn(UUID.randomUUID());
        decoys.add(decoy);
        hit(seeker, decoy, DamageCause.ENTITY_ATTACK);
        assertEquals(19, round.mistakesRemaining());
        assertEquals(2, round.hiderHitsRemaining(hider.getUniqueId()));
        assertTrue(boosts.isEmpty());
        verify(hider, never()).setWalkSpeed(anyFloat());
        verify(seeker, never()).setWalkSpeed(anyFloat());
    }

    @Test void rapidHitsOnTheSameAiCostOneMistakeButAnotherAiCountsSeparately() throws Exception {
        Entity first = mock(Entity.class), second = mock(Entity.class);
        when(first.getUniqueId()).thenReturn(UUID.randomUUID());
        when(second.getUniqueId()).thenReturn(UUID.randomUUID());
        decoys.add(first); decoys.add(second);
        hit(seeker, first, DamageCause.ENTITY_ATTACK);
        hit(seeker, first, DamageCause.ENTITY_ATTACK);
        set("motionTick", 9L);
        Projectile arrow = mock(Projectile.class);
        when(arrow.getUniqueId()).thenReturn(UUID.randomUUID());
        when(arrow.getShooter()).thenReturn(seeker);
        hit(arrow, first, DamageCause.PROJECTILE);
        assertEquals(19, round.mistakesRemaining());
        hit(seeker, second, DamageCause.ENTITY_ATTACK);
        assertEquals(18, round.mistakesRemaining());
        set("motionTick", 10L);
        hit(seeker, first, DamageCause.ENTITY_ATTACK);
        assertEquals(17, round.mistakesRemaining());
        invoke("finish", String.class, null);
        assertTrue(protection.isEmpty());
    }

    @Test void preparationHitsDoNotGrantABoost() throws Exception {
        set("round", new RoundState(seeker.getUniqueId(), List.of(hider.getUniqueId()), 15, 180));
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        assertTrue(boosts.isEmpty());
        verify(hider, never()).setWalkSpeed(anyFloat());
    }

    @Test void fatalHitImmediatelyClearsBoostAndSpectatesTheHider() throws Exception {
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        set("motionTick", 10L);
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        assertFalse(round.hiders().contains(hider.getUniqueId()));
        assertTrue(boosts.isEmpty());
        assertTrue(protection.isEmpty());
        verify(hider).setGameMode(GameMode.SPECTATOR);
        verify(hider).setWalkSpeed(MobMovement.walkSpeed(EntityType.COW));
        verify(hider, times(1)).setWalkSpeed(0.30f);
        clearInvocations(hider);
        expireAt(60);
        verify(hider, never()).setWalkSpeed(anyFloat());
    }

    @Test void aOneHitHiderNeverReceivesTheBoost() {
        round.setHiderLives(hider.getUniqueId(), 1);
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        assertTrue(boosts.isEmpty());
        verify(hider, never()).setWalkSpeed(anyFloat());
    }

    @Test void adminSpeedChangesDuringBoostArePreservedAfterExpiry() throws Exception {
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        plugin.adminSpeed(hider, 3);
        verify(hider).setWalkSpeed(0.5f);
        clearInvocations(hider);
        expireAt(60);
        verify(hider).setWalkSpeed(0.30f);
    }

    @Test void globalSpeedChangesDuringBoostArePreservedAfterExpiry() throws Exception {
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        plugin.adminNumber(seeker, "speed-multiplier", 4);
        verify(hider).setWalkSpeed(0.5f);
        clearInvocations(hider);
        expireAt(60);
        verify(hider).setWalkSpeed(0.4f);
    }

    @Test void leavingRestoresOriginalSpeedAndCancelsExpiry() throws Exception {
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        invoke("leave", Player.class, hider);
        assertTrue(boosts.isEmpty());
        assertTrue(protection.isEmpty());
        assertFalse(participants.containsKey(hider.getUniqueId()));
        verify(hider).setWalkSpeed(0.2f);
        clearInvocations(hider);
        expireAt(60);
        verify(hider, never()).setWalkSpeed(anyFloat());
    }

    @Test void stoppingClearsBoostBeforeAnotherRoundCanStart() throws Exception {
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        invoke("finish", String.class, null);
        assertTrue(boosts.isEmpty());
        assertTrue(protection.isEmpty());
        verify(hider).setWalkSpeed(0.2f);
        set("round", new RoundState(seeker.getUniqueId(), List.of(hider.getUniqueId()), 0, 180));
        morph(EntityType.RABBIT);
        clearInvocations(hider);
        expireAt(60);
        verify(hider, never()).setWalkSpeed(anyFloat());
    }

    @Test void removingDisguiseForRoleOrMobChangeCancelsBoost() throws Exception {
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        invoke("removeDisguise", Player.class, hider);
        assertTrue(boosts.isEmpty());
        assertTrue(protection.isEmpty());
        verify(hider).setWalkSpeed(MobMovement.walkSpeed(EntityType.COW));
        clearInvocations(hider);
        expireAt(60);
        verify(hider, never()).setWalkSpeed(anyFloat());
    }

    @Test void zeroDurationDisablesBoostWithoutChangingHitRules() {
        config.set("hit-speed-boost.seconds", 0);
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        assertEquals(1, round.hiderHitsRemaining(hider.getUniqueId()));
        assertTrue(boosts.isEmpty());
        verify(hider, never()).setWalkSpeed(anyFloat());
    }

    @Test void customDurationAndMultiplierApplyWithoutMinimumFloor() throws Exception {
        config.set("hit-speed-boost.seconds", 2);
        config.set("hit-speed-boost.multiplier", 2.5);
        config.set("hit-speed-boost.minimum-walk-speed", 0);
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        verify(hider).setWalkSpeed(0.25f);
        clearInvocations(hider);
        expireAt(39);
        verify(hider, never()).setWalkSpeed(anyFloat());
        expireAt(40);
        verify(hider).setWalkSpeed(MobMovement.walkSpeed(EntityType.COW));
    }

    @Test void invalidConfigCannotProduceAnInvalidWalkSpeed() {
        config.set("hit-speed-boost.multiplier", Double.POSITIVE_INFINITY);
        config.set("hit-speed-boost.minimum-walk-speed", Double.NaN);
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        verify(hider).setWalkSpeed(0.30f);
    }

    @Test void rapidHitsCannotEliminateHiderBeforeEscapeBoostCanBeUsed() throws Exception {
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        set("motionTick", 9L);
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        assertEquals(1, round.hiderHitsRemaining(hider.getUniqueId()));
        verify(hider, times(1)).setWalkSpeed(0.30f);
        set("motionTick", 10L);
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        assertFalse(round.hiders().contains(hider.getUniqueId()));
    }

    @Test void ignoredRapidHitDoesNotExtendBoost() throws Exception {
        round.setHiderLives(hider.getUniqueId(), 5);
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        set("motionTick", 9L);
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        expireAt(60);
        assertEquals(4, round.hiderHitsRemaining(hider.getUniqueId()));
        assertTrue(boosts.isEmpty());
        verify(hider).setWalkSpeed(MobMovement.walkSpeed(EntityType.COW));
    }

    @Test void protectionStillWorksWhenSpeedBoostIsDisabled() {
        config.set("hit-speed-boost.seconds", 0);
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        assertEquals(1, round.hiderHitsRemaining(hider.getUniqueId()));
        verify(hider, never()).setWalkSpeed(anyFloat());
    }

    @Test void meleeAndNewProjectilesShareTheHidersProtection() {
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        Projectile arrow = mock(Projectile.class);
        when(arrow.getUniqueId()).thenReturn(UUID.randomUUID());
        when(arrow.getShooter()).thenReturn(seeker);
        hit(arrow, hider, DamageCause.PROJECTILE);
        assertEquals(1, round.hiderHitsRemaining(hider.getUniqueId()));
    }

    @Test void invalidBaseSpeedFallsBackAndRecoversAfterBoost() throws Exception {
        config.set("speed-multiplier", Double.NaN);
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        verify(hider).setWalkSpeed(0.30f);
        expireAt(60);
        verify(hider).setWalkSpeed(MobMovement.walkSpeed(EntityType.COW));
    }

    @Test void invalidPersonalSpeedUsesTheValidGlobalSpeed() throws Exception {
        config.set("speed-multiplier", 2);
        config.set("players." + hider.getUniqueId() + ".speed", Double.NEGATIVE_INFINITY);
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        verify(hider).setWalkSpeed(0.4f);
        expireAt(60);
        verify(hider).setWalkSpeed(0.2f);
    }

    @Test void oneHidersProtectionDoesNotProtectAnotherHider() throws Exception {
        Player other = player();
        participants.put(other.getUniqueId(), snapshot());
        round.reviveHider(other.getUniqueId());
        motions.put(other.getUniqueId(), new MobAnimation(EntityType.CHICKEN, other.getLocation(), true));
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        hit(seeker, other, DamageCause.ENTITY_ATTACK);
        assertEquals(1, round.hiderHitsRemaining(hider.getUniqueId()));
        assertEquals(1, round.hiderHitsRemaining(other.getUniqueId()));
        verify(other).setWalkSpeed(0.30f);
    }

    @Test void invalidAttackCannotProtectAHiderFromTheSeeker() {
        hit(player(), hider, DamageCause.ENTITY_ATTACK);
        hit(seeker, hider, DamageCause.ENTITY_SWEEP_ATTACK);
        hit(seeker, hider, DamageCause.ENTITY_ATTACK);
        assertEquals(1, round.hiderHitsRemaining(hider.getUniqueId()));
        verify(hider).setWalkSpeed(0.30f);
    }

    private void morph(EntityType type) {
        motions.put(hider.getUniqueId(), new MobAnimation(type, hider.getLocation(), true));
    }

    private Player player() {
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(player.getName()).thenReturn(id.toString());
        when(player.getLocation()).thenAnswer(call -> new Location(world, 0, 64, 0));
        when(player.getWorld()).thenReturn(world);
        when(player.teleport(any(Location.class))).thenReturn(true);
        AttributeInstance health = mock(AttributeInstance.class);
        when(health.getValue()).thenReturn(20.0);
        when(player.getAttribute(Attribute.MAX_HEALTH)).thenReturn(health);
        bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
        return player;
    }

    private void hit(Entity attacker, Entity target, DamageCause cause) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(attacker);
        when(event.getEntity()).thenReturn(target);
        when(event.getCause()).thenReturn(cause);
        plugin.onHit(event);
        verify(event).setCancelled(true);
    }

    private Object snapshot() throws Exception {
        var type = Class.forName(MobHidePlugin.class.getName() + "$Snapshot");
        var constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(new Location(world, 0, 64, 0), GameMode.SURVIVAL, 0.2f, false, true, null, 0);
    }

    private void expireAt(long tick) throws Exception {
        set("motionTick", tick);
        var method = MobHidePlugin.class.getDeclaredMethod("expireHitSpeedBoosts");
        method.setAccessible(true);
        method.invoke(plugin);
    }

    private void invoke(String name, Class<?> type, Object argument) throws Exception {
        var method = MobHidePlugin.class.getDeclaredMethod(name, type);
        method.setAccessible(true);
        method.invoke(plugin, argument);
    }

    private void set(String name, Object value) throws Exception {
        var field = MobHidePlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}
