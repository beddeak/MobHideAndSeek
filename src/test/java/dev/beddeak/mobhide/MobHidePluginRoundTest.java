package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.boss.BossBar;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockMakers;
import org.mockito.MockedStatic;

class MobHidePluginRoundTest {
    private MobHidePlugin plugin;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<SafeLocation> safety;
    private final World world = mock(World.class);
    private final Player seeker = mock(Player.class), hider = mock(Player.class);
    private final YamlConfiguration config = new YamlConfiguration();
    private RoundState round;

    @BeforeEach void setup() throws Exception {
        TestRegistries.initialize();
        plugin = mock(MobHidePlugin.class, withSettings().mockMaker(MockMakers.INLINE).defaultAnswer(CALLS_REAL_METHODS));
        doReturn(config).when(plugin).getConfig();
        doNothing().when(plugin).saveConfig();
        bukkit = mockStatic(Bukkit.class, withSettings().mockMaker(MockMakers.INLINE));
        safety = mockStatic(SafeLocation.class, withSettings().mockMaker(MockMakers.INLINE));
        safety.when(() -> SafeLocation.isSafe(any(Location.class)))
                .thenAnswer(call -> ((Location) call.getArgument(0)).getY() == 64);
        when(world.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(63);
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        preparePlayer(seeker);
        preparePlayer(hider);
        round = new RoundState(seeker.getUniqueId(), List.of(hider.getUniqueId()), 15, 180, 2);
        set("round", round);
        set("center", new Location(world, 0, 64, 0));
        set("radius", 20);
        set("boundary", new ArenaBoundary(0, 64, 0, 20));
        set("roundHealthKey", new NamespacedKey("mobhide", "round_health"));
        set("lastInside", new HashMap<>());
        set("motions", new HashMap<>());
        set("hitSpeedBoosts", new HashMap<>());
        set("hitProtection", new HashMap<>());
        set("decoys", new ArrayList<>());
        set("participants", new HashMap<>(Map.of(seeker.getUniqueId(), snapshot(), hider.getUniqueId(), snapshot())));
        set("startedAt", System.nanoTime());
        set("scoringEligible", true);
    }

    @AfterEach void cleanup() {
        if (safety != null) safety.close();
        if (bukkit != null) bukkit.close();
    }

    @Test void globalSeekerHealthRespectsThePlayersOverride() {
        config.set("players." + seeker.getUniqueId() + ".hits", 5);
        plugin.adminNumber(seeker, "seeker-mistake-limit", 19);

        assertEquals(19, config.getInt("seeker-mistake-limit"));
        assertEquals(5, round.mistakeLimit());
        assertEquals(5, round.mistakesRemaining());
        verify(seeker).setHealth(10);
        verify(seeker, never()).teleport(any(Location.class));
    }

    @Test void globalHealthRestoresAnExhaustedSpectatorOnSafeGroundBeforeAdventureMode() {
        round.enableSandbox();
        round.wrongHit(seeker.getUniqueId(), null, 0);
        round.wrongHit(seeker.getUniqueId(), null, 0);
        when(seeker.getLocation()).thenReturn(new Location(world, 0, 75, 0));
        when(seeker.getGameMode()).thenReturn(GameMode.SPECTATOR);

        plugin.adminNumber(seeker, "seeker-mistake-limit", 7);

        var position = ArgumentCaptor.forClass(Location.class);
        var order = inOrder(seeker);
        order.verify(seeker).teleport(position.capture());
        order.verify(seeker).setGameMode(GameMode.ADVENTURE);
        assertEquals(world, position.getValue().getWorld());
        assertEquals(64, position.getValue().getY());
        assertTrue(new ArenaBoundary(0, 64, 0, 20).contains(
                position.getValue().getX(), position.getValue().getY(), position.getValue().getZ()));
        assertEquals(7, round.mistakesRemaining());
    }

    @Test void changingTheSeekerDuringPreparationTransfersOnlyTheRemainingBlindness() throws Exception {
        Disguises disguises = mock(Disguises.class, withSettings().mockMaker(MockMakers.INLINE));
        AnimationBridge animations = mock(AnimationBridge.class, withSettings().mockMaker(MockMakers.INLINE));
        set("disguises", disguises);
        set("animations", animations);
        Entity cow = mock(Entity.class);
        when(cow.getType()).thenReturn(EntityType.COW);
        when(cow.isValid()).thenReturn(true);
        set("decoys", new ArrayList<>(List.of(cow)));
        config.set("mob-types", List.of("COW"));
        set("blindnessApplied", true);

        try (var bridge = mockStatic(AnimationBridge.class, withSettings().mockMaker(MockMakers.INLINE))) {
            bridge.when(() -> AnimationBridge.grounded(any(Player.class))).thenReturn(true);
            set("startedAt", System.nanoTime() - 2_000_000_000L);
            plugin.adminSeeker(seeker, hider);
        }

        assertEquals(hider.getUniqueId(), round.seeker());
        assertTrue(round.hiders().contains(seeker.getUniqueId()));
        verify(seeker).removePotionEffect(PotionEffectType.BLINDNESS);
        var effect = ArgumentCaptor.forClass(PotionEffect.class);
        verify(hider).addPotionEffect(effect.capture());
        assertEquals(PotionEffectType.BLINDNESS, effect.getValue().getType());
        assertTrue(effect.getValue().getDuration() > 0);
        assertTrue(effect.getValue().getDuration() <= 13 * 20);
        verify(disguises).disguise(seeker, EntityType.COW, true);
    }

    @Test void revivingAnEliminatedHiderWithLivesKeepsTheirRandomMobSetting() throws Exception {
        Disguises disguises = mock(Disguises.class, withSettings().mockMaker(MockMakers.INLINE));
        set("disguises", disguises);
        set("animations", mock(AnimationBridge.class, withSettings().mockMaker(MockMakers.INLINE)));
        Entity cow = mock(Entity.class);
        when(cow.getType()).thenReturn(EntityType.COW);
        when(cow.isValid()).thenReturn(true);
        set("decoys", new ArrayList<>(List.of(cow)));
        config.set("mob-types", List.of("COW"));
        round.enableSandbox();
        round.setHiderLives(hider.getUniqueId(), 1);
        assertTrue(round.catchHider(seeker.getUniqueId(), hider.getUniqueId(), 0));

        try (var bridge = mockStatic(AnimationBridge.class, withSettings().mockMaker(MockMakers.INLINE))) {
            bridge.when(() -> AnimationBridge.grounded(any(Player.class))).thenReturn(true);
            plugin.adminLives(seeker, hider, 3);
        }

        assertFalse(config.isSet("players." + hider.getUniqueId() + ".mob"));
        assertEquals(3, config.getInt("players." + hider.getUniqueId() + ".hits"));
        assertTrue(round.hiders().contains(hider.getUniqueId()));
        assertEquals(3, round.hiderHitsRemaining(hider.getUniqueId()));
        verify(disguises).disguise(hider, EntityType.COW, true);
        verify(hider).setGameMode(GameMode.ADVENTURE);
    }

    @Test void catchingTheLastHiderRecordsTheSplitTimeAndTheSeekerWin() throws Exception {
        Leaderboard scores = mock(Leaderboard.class, withSettings().mockMaker(MockMakers.INLINE));
        set("leaderboard", scores);
        set("score", new RoundScore(10, 5, 5, 5, 5, 600));
        round.setHiderLives(hider.getUniqueId(), 1);
        // 숨을 시간 15초 + 추격 30.5초에 잡습니다.
        set("startedAt", System.nanoTime() - 45_500_000_000L);
        var event = mock(org.bukkit.event.entity.EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(seeker);
        when(event.getEntity()).thenReturn(hider);
        when(event.getCause()).thenReturn(org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK);

        plugin.onHit(event);

        // verify 안에서 mock을 호출하면 그 호출이 검증 대상이 되므로 이름을 먼저 읽습니다.
        String seekerName = seeker.getName(), hiderName = hider.getName();
        assertFalse(plugin.running());
        verify(scores).record(seeker.getUniqueId(), seekerName, 5 + 15 + 5, true, true);
        verify(scores).record(hider.getUniqueId(), hiderName, 3, false, false);
        verify(scores).save();
        verify(seeker).sendMessage(contains("술래 +25점"));
        verify(hider).sendMessage(contains("이번 판 점수 §f1위 " + seekerName + " 25점 · 2위 " + hiderName + " 3점"));
    }

    @Test void timeoutGivesSurvivorsTheWholeChaseAndAnAdminChangedRoundIsNotRecorded() throws Exception {
        Leaderboard scores = mock(Leaderboard.class, withSettings().mockMaker(MockMakers.INLINE));
        set("leaderboard", scores);
        set("score", new RoundScore(10, 5, 5, 5, 5, 600));
        set("originalHiders", java.util.Set.of(hider.getUniqueId()));
        set("startedAt", System.nanoTime() - 200_000_000_000L);
        invoke("tick");
        String seekerName = seeker.getName(), hiderName = hider.getName();
        verify(scores).record(hider.getUniqueId(), hiderName, 18 + 5, true, false);
        verify(scores).record(seeker.getUniqueId(), seekerName, 0, false, true);

        clearInvocations(scores, hider);
        round = new RoundState(seeker.getUniqueId(), List.of(hider.getUniqueId()), 15, 180, 2);
        set("round", round);
        set("participants", new HashMap<>(Map.of(seeker.getUniqueId(), snapshot(), hider.getUniqueId(), snapshot())));
        set("score", new RoundScore(10, 5, 5, 5, 5, 600));
        set("scoringEligible", false);
        set("startedAt", System.nanoTime() - 200_000_000_000L);
        invoke("tick");
        verifyNoInteractions(scores);
        verify(hider).sendMessage(contains("이번 판 23점"));
    }

    @Test void tauntLeavesAGlowingPillarAtTheSpotThatExpiresAndIsClearedOnFinish() throws Exception {
        set("motions", new HashMap<>(Map.of(hider.getUniqueId(), new MobAnimation(EntityType.COW, new Location(world, 0, 64, 0), true))));
        set("score", new RoundScore(10, 5, 5, 5, 5, 200));
        TauntMarkers markers = new TauntMarkers();
        set("tauntMarkers", markers);
        set("startedAt", System.nanoTime() - 20_000_000_000L);
        var first = mock(org.bukkit.entity.BlockDisplay.class);
        var second = mock(org.bukkit.entity.BlockDisplay.class);
        when(world.spawn(any(Location.class), eq(org.bukkit.entity.BlockDisplay.class), any(java.util.function.Consumer.class)))
                .thenReturn(first, second);

        plugin.onSwapHands(swap(hider));
        var spot = ArgumentCaptor.forClass(Location.class);
        verify(world).spawn(spot.capture(), eq(org.bukkit.entity.BlockDisplay.class), any(java.util.function.Consumer.class));
        assertEquals(new Location(world, 0, 64, 0), spot.getValue());
        verify(seeker).sendMessage(contains("빛나는 기둥이 5초 동안 남습니다"));
        markers.expire(99);
        verify(first, never()).remove();
        markers.expire(100);
        verify(first).remove();

        set("motionTick", 200L);
        plugin.onSwapHands(swap(hider));
        var finish = MobHidePlugin.class.getDeclaredMethod("finish", String.class);
        finish.setAccessible(true); finish.invoke(plugin, (Object) null);
        verify(second).remove();
        assertNull(get("tauntMarkers"));
    }

    @Test void zeroGlowSecondsKeepsTheFireworkButLeavesNoPillar() throws Exception {
        set("motions", new HashMap<>(Map.of(hider.getUniqueId(), new MobAnimation(EntityType.COW, new Location(world, 0, 64, 0), true))));
        set("score", new RoundScore(10, 5, 5, 5, 5, 200));
        set("tauntMarkers", new TauntMarkers());
        set("startedAt", System.nanoTime() - 20_000_000_000L);
        config.set("taunt.glow-seconds", 0);
        plugin.onSwapHands(swap(hider));
        verify(world, never()).spawn(any(Location.class), eq(org.bukkit.entity.BlockDisplay.class), any(java.util.function.Consumer.class));
        verify(world, times(2)).playSound(any(Location.class), any(org.bukkit.Sound.class), eq(6f), eq(1f));
    }

    @Test void hiderTauntRevealsTheSpotForPointsOnlyDuringTheChaseAndThenWaits() throws Exception {
        set("motions", new HashMap<>(Map.of(hider.getUniqueId(), new MobAnimation(EntityType.COW, new Location(world, 0, 64, 0), true))));
        RoundScore score = new RoundScore(10, 5, 5, 5, 5, 600);
        set("score", score);
        var preparing = swap(hider);
        plugin.onSwapHands(preparing);
        assertTrue(preparing.isCancelled());
        assertEquals(0, score.points(hider.getUniqueId(), true, 0));
        verify(world, never()).playSound(any(Location.class), any(org.bukkit.Sound.class), anyFloat(), anyFloat());

        set("startedAt", System.nanoTime() - 20_000_000_000L);
        var chasing = swap(hider);
        plugin.onSwapHands(chasing);
        assertTrue(chasing.isCancelled());
        assertEquals(5, score.points(hider.getUniqueId(), true, 0));
        verify(world, times(2)).playSound(any(Location.class), any(org.bukkit.Sound.class), eq(6f), eq(1f));
        verify(world, atLeastOnce()).spawnParticle(eq(org.bukkit.Particle.FIREWORK), any(Location.class), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), eq(true));
        verify(seeker).sendMessage(contains("폭죽을 터뜨렸습니다"));

        plugin.onCommand(hider, null, "mh", new String[] {"taunt"});
        assertEquals(5, score.points(hider.getUniqueId(), true, 0));
        verify(hider).sendMessage(contains("도발 대기 중: 30초"));

        var seekerSwap = swap(seeker);
        plugin.onSwapHands(seekerSwap);
        assertFalse(seekerSwap.isCancelled());
        plugin.onCommand(seeker, null, "mh", new String[] {"taunt"});
        verify(seeker).sendMessage(contains("변신한 숨는 플레이어만"));
    }

    @Test void panelTauntChangesApplyToTheRunningRoundAndZeroPointsRestoresHandSwap() throws Exception {
        set("motions", new HashMap<>(Map.of(hider.getUniqueId(), new MobAnimation(EntityType.COW, new Location(world, 0, 64, 0), true))));
        RoundScore score = new RoundScore(10, 5, 5, 5, 5, 600);
        set("score", score);
        set("startedAt", System.nanoTime() - 20_000_000_000L);
        plugin.onSwapHands(swap(hider));
        assertEquals(5, score.points(hider.getUniqueId(), true, 0));

        plugin.adminNumber(seeker, "taunt.cooldown-seconds", 10);
        assertEquals(10, config.get("taunt.cooldown-seconds"));
        assertFalse((boolean) get("scoringEligible"));
        set("motionTick", 199L);
        plugin.onSwapHands(swap(hider));
        assertEquals(5, score.points(hider.getUniqueId(), true, 0));
        set("motionTick", 200L);
        plugin.onSwapHands(swap(hider));
        assertEquals(10, score.points(hider.getUniqueId(), true, 0));

        plugin.adminNumber(seeker, "taunt.points", 0);
        set("motionTick", 400L);
        var handSwap = swap(hider);
        plugin.onSwapHands(handSwap);
        assertFalse(handSwap.isCancelled());
        assertEquals(10, score.points(hider.getUniqueId(), true, 0));
    }

    @Test void settingsResetRestoresPanelDefaultsButKeepsTheArenaPlayersAndBoard() throws Exception {
        config.set("hider-hit-limit", 20); config.set("round-seconds", 2220); config.set("hide-seconds", 0);
        config.set("decoy-count", 95); config.set("speed-multiplier", 2.5); config.set("taunt.cooldown-seconds", 30);
        config.set("mob-types", List.of("CHICKEN")); config.set("fixed-seeker", seeker.getUniqueId().toString());
        config.set("arena.world", "world"); config.set("arena.spawn-min-y", 64);
        config.set("players." + hider.getUniqueId() + ".mob", "PIG");
        config.set("score.seconds-per-point", 20);

        plugin.adminResetSettings(seeker);
        verify(seeker).sendMessage(contains("게임을 종료한 뒤"));
        assertEquals(20, config.getInt("hider-hit-limit"));

        set("round", null);
        plugin.adminResetSettings(seeker);
        assertEquals(2, config.get("hider-hit-limit"));
        assertEquals(180, config.get("round-seconds"));
        assertEquals(15, config.get("hide-seconds"));
        assertEquals(25, config.get("decoy-count"));
        assertEquals(1.0, config.get("speed-multiplier"));
        assertEquals(10, config.get("taunt.cooldown-seconds"));
        assertEquals(5, config.get("taunt.points"));
        assertEquals(20, config.get("seeker-mistake-limit"));
        assertEquals(MobCatalog.ALL.stream().map(Enum::name).toList(), config.getStringList("mob-types"));
        assertFalse(config.isSet("fixed-seeker"));
        assertEquals("world", config.getString("arena.world"));
        assertEquals(64, config.getInt("arena.spawn-min-y"));
        assertEquals("PIG", config.getString("players." + hider.getUniqueId() + ".mob"));
        assertEquals(20, config.getInt("score.seconds-per-point"));
        verify(plugin).saveConfig();
    }

    @Test void adminCanPreviewTheTauntOnAFarBlockWithoutPoints() throws Exception {
        RoundScore score = new RoundScore(10, 5, 5, 5, 5, 600);
        set("score", score);
        when(seeker.hasPermission("mobhide.admin")).thenReturn(true);
        var block = mock(org.bukkit.block.Block.class);
        when(block.getLocation()).thenReturn(new Location(world, 59, 63, 0));
        when(seeker.getTargetBlockExact(160)).thenReturn(block);

        plugin.onCommand(seeker, null, "mh", new String[] {"tauntfx"});

        verify(world, times(2)).playSound(any(Location.class), any(org.bukkit.Sound.class), eq(6f), eq(1f));
        verify(seeker).sendMessage(contains("60블록 거리"));
        assertEquals(0, score.points(seeker.getUniqueId(), false, 0));
        plugin.onCommand(hider, null, "mh", new String[] {"tauntfx"});
        verify(hider).sendMessage(contains("관리자 권한이 필요합니다"));
        verify(world, times(2)).playSound(any(Location.class), any(org.bukkit.Sound.class), anyFloat(), anyFloat());
    }

    private static org.bukkit.event.player.PlayerSwapHandItemsEvent swap(Player player) {
        return new org.bukkit.event.player.PlayerSwapHandItemsEvent(player,
                mock(org.bukkit.inventory.ItemStack.class), mock(org.bukkit.inventory.ItemStack.class));
    }

    @Test void panelNumbersAreSavedAsIntegersExceptTheSpeedMultiplier() {
        plugin.adminNumber(seeker, "round-seconds", 240);
        plugin.adminNumber(seeker, "decoy-count", 30);
        plugin.adminNumber(seeker, "speed-multiplier", 1.25);
        assertInstanceOf(Integer.class, config.get("round-seconds"));
        assertInstanceOf(Integer.class, config.get("decoy-count"));
        assertEquals(1.25, config.get("speed-multiplier"));
    }

    @Test void countdownKeepsTheRoundClockStoppedThenStartsTheFullHidingTime() throws Exception {
        set("countdownRemaining", 3);
        set("startedAt", System.nanoTime() - 100_000_000_000L);
        BossBar bar = mock(BossBar.class);
        set("timerBar", bar);
        invoke("tick");
        assertEquals(0L, invoke("elapsed"));
        verify(bar).setTitle("§6몹 숨바꼭질 §f| 시작까지 §e2초");
        invoke("tick");
        assertEquals(0L, invoke("elapsed"));
        verify(bar).setTitle("§6몹 숨바꼭질 §f| 시작까지 §e1초");
        long beforeStart = System.nanoTime();
        invoke("tick");
        assertEquals(0, get("countdownRemaining"));
        assertTrue((long) get("startedAt") >= beforeStart);
        assertEquals(15, round.phaseRemaining((long) invoke("elapsed")));
        var titles = ArgumentCaptor.forClass(Title.class);
        verify(hider, times(3)).showTitle(titles.capture());
        assertEquals(List.of("2", "1", "시작!"), titles.getAllValues().stream()
                .map(title -> ((TextComponent) title.title()).content()).toList());
        assertTrue(titles.getAllValues().stream().allMatch(title ->
                ((TextComponent) title.subtitle()).content().equals("플러그인 by beddeak(침댁)")));
        verify(seeker, times(3)).showTitle(any(Title.class));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void soloAndSandboxWaitForTheSameCountdown(boolean sandbox) throws Exception {
        round = RoundState.soloTest(hider.getUniqueId(), 180);
        if (sandbox) round.enableSandbox();
        set("round", round);
        set("countdownRemaining", 3);
        set("startedAt", System.nanoTime() - 200_000_000_000L);
        set("chaseAnnounced", true);
        invoke("tick"); invoke("tick");
        assertEquals(RoundState.Winner.NONE, round.winner());
        invoke("tick");
        assertEquals(RoundState.Winner.NONE, round.winner());
        assertEquals(180, round.remaining((long) invoke("elapsed")));
    }

    @ParameterizedTest @ValueSource(ints = {0, 15})
    void seekerBlindnessIncludesCountdownEvenWithNoHidingTime(int hiding) throws Exception {
        round = new RoundState(seeker.getUniqueId(), List.of(hider.getUniqueId()), hiding, 180);
        set("round", round); set("countdownRemaining", 3);
        var method = MobHidePlugin.class.getDeclaredMethod("applyPreparationBlindness", Player.class);
        method.setAccessible(true); method.invoke(plugin, seeker);
        var effect = ArgumentCaptor.forClass(PotionEffect.class);
        verify(seeker).addPotionEffect(effect.capture());
        assertEquals((hiding + 3) * 20, effect.getValue().getDuration());
        invoke("tick"); invoke("tick"); invoke("tick");
        if (hiding == 0) verify(seeker).removePotionEffect(PotionEffectType.BLINDNESS);
        else verify(seeker, never()).removePotionEffect(PotionEffectType.BLINDNESS);
    }

    @Test void cancellingCountdownClearsTitlesAndCannotStartLater() throws Exception {
        set("countdownRemaining", 2);
        BukkitTask task = mock(BukkitTask.class);
        set("ticker", task);
        var finish = MobHidePlugin.class.getDeclaredMethod("finish", String.class);
        finish.setAccessible(true); finish.invoke(plugin, (Object) null);
        verify(task).cancel();
        verify(hider).clearTitle(); verify(seeker).clearTitle();
        assertEquals(0, get("countdownRemaining"));
        assertFalse(plugin.running());
        invoke("tick");
        verify(hider, never()).showTitle(any(Title.class));
    }

    @Test void leavingDuringCountdownDoesNotAwardAPrematureWin() throws Exception {
        set("countdownRemaining", 2);
        Leaderboard scores = mock(Leaderboard.class, withSettings().mockMaker(MockMakers.INLINE));
        set("leaderboard", scores);
        plugin.onQuit(new org.bukkit.event.player.PlayerQuitEvent(seeker, "quit"));
        assertFalse(plugin.running());
        verifyNoInteractions(scores);
        verify(hider).sendMessage(contains("카운트다운 중 참가자가 부족해 시작을 취소했습니다."));
    }

    @Test void stopRestoresPlayerStateButReturnsToLobbyInsteadOfOldMap() throws Exception {
        MapRotation rotation = mock(MapRotation.class, withSettings().mockMaker(MockMakers.INLINE));
        World lobbyWorld = mock(World.class);
        Location lobby = new Location(lobbyWorld, .5, 64, 7.5);
        when(rotation.ready()).thenReturn(true); when(rotation.lobby()).thenReturn(lobby);
        set("mapRotation", rotation);
        var finish = MobHidePlugin.class.getDeclaredMethod("finish", String.class);
        finish.setAccessible(true); finish.invoke(plugin, (Object)null);
        verify(seeker).teleport(lobby); verify(hider).teleport(lobby);
        verify(hider).setGameMode(GameMode.SURVIVAL);
        verify(hider).setWalkSpeed(.2f);
        assertFalse(plugin.running());
    }

    @Test void countdownIncludesSelectedMapName() throws Exception {
        set("activeMap", new ArenaMap("snow", "눈 덮인 벌목장", new Location(world,320.5,64,32.5),
                new CuboidBoundary(281,64,-39,360,94,40),20,64,64));
        set("countdownRemaining",3);
        invoke("tick");
        var title = ArgumentCaptor.forClass(Title.class);
        verify(hider).showTitle(title.capture());
        assertTrue(((TextComponent)title.getValue().subtitle()).content().contains("눈 덮인 벌목장"));
        assertTrue(plugin.arenaDescription().startsWith("눈 덮인 벌목장"));
    }

    private Object invoke(String name) throws Exception {
        var method = MobHidePlugin.class.getDeclaredMethod(name); method.setAccessible(true); return method.invoke(plugin);
    }

    private Object get(String name) throws Exception {
        Field field = MobHidePlugin.class.getDeclaredField(name); field.setAccessible(true); return field.get(plugin);
    }

    private void preparePlayer(Player player) {
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(player.getName()).thenReturn(id.toString());
        when(player.getLocation()).thenAnswer(call -> new Location(world, 0, 64, 0));
        when(player.teleport(any(Location.class))).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        AttributeInstance health = mock(AttributeInstance.class);
        when(health.getModifiers()).thenReturn(List.of());
        when(health.getValue()).thenReturn(20.0);
        when(player.getAttribute(Attribute.MAX_HEALTH)).thenReturn(health);
        bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
    }

    private Object snapshot() throws Exception {
        Class<?> healthType = Class.forName(MobHidePlugin.class.getName() + "$HealthSnapshot");
        Constructor<?> health = healthType.getDeclaredConstructor(double.class, boolean.class, double.class);
        health.setAccessible(true);
        Class<?> snapshotType = Class.forName(MobHidePlugin.class.getName() + "$Snapshot");
        Constructor<?> snapshot = snapshotType.getDeclaredConstructor(Location.class, GameMode.class,
                float.class, boolean.class, boolean.class, healthType, int.class);
        snapshot.setAccessible(true);
        return snapshot.newInstance(new Location(world, 0, 64, 0), GameMode.SURVIVAL,
                0.2f, false, true, health.newInstance(20.0, false, 20.0), 0);
    }

    private void set(String name, Object value) throws Exception {
        Field field = MobHidePlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}
