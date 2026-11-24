package dev.beddeak.mobhide;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.Difficulty;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Mob;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import com.github.retrooper.packetevents.PacketEvents;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class MobHidePlugin extends JavaPlugin implements Listener {
    private static final String PREFIX = "§6[몹 숨바꼭질] §f";
    private static final List<EntityType> SAFE_MOBS = MobCatalog.ALL;
    private static final long HIT_PROTECTION_TICKS = 10;
    private static final Color[] TAUNT_COLORS = {Color.RED, Color.YELLOW, Color.AQUA, Color.LIME, Color.FUCHSIA, Color.ORANGE};
    private final Map<UUID, Snapshot> participants = new HashMap<>();
    private final List<Entity> decoys = new ArrayList<>();
    private final Map<UUID, MobAnimation> motions = new HashMap<>();
    private final Map<UUID, HitSpeedBoost> hitSpeedBoosts = new HashMap<>();
    // 숨는 플레이어와 AI 몹 모두 대상 UUID별로 연타 보호 만료 틱을 저장합니다.
    private final Map<UUID, Long> hitProtection = new HashMap<>();
    private final Map<UUID, Location> lastInside = new HashMap<>();
    private Disguises disguises;
    private RoundState round;
    private BukkitTask ticker;
    private BukkitTask movementTicker;
    private BossBar timerBar;
    private ScoreSidebar sidebar;
    private TauntMarkers tauntMarkers;
    private Disasters disasters;
    private RoundScore score;
    private ArenaRegion boundary;
    private ArenaSetup arenaSetup;
    private MapRotation mapRotation;
    private ArenaMap activeMap;
    private AdminPanel adminPanel;
    private AnimationBridge animations;
    private VillagerCalm villagerCalm;
    private Leaderboard leaderboard;
    private Set<UUID> originalHiders = Set.of();
    private boolean scoringEligible;
    private long motionTick;
    private Location center;
    private int radius;
    private long startedAt;
    private int countdownRemaining;
    private NamespacedKey roundHealthKey;
    private boolean restoring;
    private boolean chaseAnnounced;
    private boolean blindnessApplied;
    private PotionEffect previousBlindness;
    private long blindnessStartedAt;

    private record HealthSnapshot(double health, boolean scaled, double scale) {}
    private record HitSpeedBoost(long expiresAt, double multiplier, double minimumSpeed) {}
    private record Snapshot(Location location, GameMode gameMode, float walkSpeed, boolean sprinting, boolean collidable, HealthSnapshot health, int fireTicks) {}

    @Override public void onEnable() {
        saveDefaultConfig();
        // 이전 기본 동물 5종 설정만 새 기본 8종으로 갱신하고 사용자가 고른 별도 풀은 유지합니다.
        if (!getConfig().getBoolean("catalog-v2", false)) {
            if (Set.copyOf(getConfig().getStringList("mob-types")).equals(Set.of("COW", "PIG", "SHEEP", "CHICKEN", "RABBIT")))
                getConfig().set("mob-types", SAFE_MOBS.stream().map(Enum::name).toList());
            getConfig().set("catalog-v2", true); saveConfig();
        }
        // 기존 기본 8종을 그대로 쓰던 목록은 새 전체 목록으로 넓히고, 직접 고른 목록은 유지합니다.
        if (!getConfig().getBoolean("catalog-v3", false)) {
            if (Set.copyOf(getConfig().getStringList("mob-types")).equals(Set.copyOf(MobCatalog.LEGACY_DEFAULT.stream().map(Enum::name).toList())))
                getConfig().set("mob-types", SAFE_MOBS.stream().map(Enum::name).toList());
            getConfig().set("catalog-v3", true); saveConfig();
        }
        roundHealthKey = new NamespacedKey(this, "round_health");
        try {
            var dependency = Objects.requireNonNull(getServer().getPluginManager().getPlugin("LibsDisguises"));
            disguises = new Disguises(dependency.getClass().getClassLoader(), getLogger());
            animations = new AnimationBridge(disguises.selfId());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            getLogger().log(Level.SEVERE, "호환되는 LibsDisguises가 필요합니다. 플러그인을 비활성화합니다.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        villagerCalm = VillagerCalm.create(getLogger());
        Objects.requireNonNull(getCommand("mobhide")).setExecutor(this);
        Objects.requireNonNull(getCommand("mobhide")).setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        arenaSetup = new ArenaSetup(this);
        adminPanel = new AdminPanel(this);
        leaderboard = new Leaderboard(getDataFolder().toPath().resolve("leaderboard.yml"));
        PacketEvents.getAPI().getEventManager().registerListener(animations);
        getServer().getPluginManager().registerEvents(arenaSetup, this);
        getServer().getPluginManager().registerEvents(adminPanel, this);
        getServer().getPluginManager().registerEvents(new DecoyProtection(decoys::contains), this);
        mapRotation = new MapRotation(this);
        getServer().getPluginManager().registerEvents(mapRotation, this);
        // Bukkit world creation is deferred until all ordinary worlds/plugins have loaded.
        Bukkit.getScheduler().runTask(this, mapRotation::initialize);
    }

    @Override public void onDisable() {
        finish(null);
        if (animations != null) PacketEvents.getAPI().getEventManager().unregisterListener(animations);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "help" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (action) {
            case "admin", "panel" -> { if (admin(sender) && sender instanceof Player player) adminPanel.open(player); }
            case "top", "leaderboard" -> showLeaderboard(sender);
            case "maps" -> {
                if (mapRotation == null || !mapRotation.ready()) { tell(sender, "로비/맵 준비 중입니다. 서버 로그를 확인하세요."); break; }
                tell(sender, "게임 시작 시 랜덤 선정 (직전 맵 연속 제외): " + String.join(" · ", mapRotation.maps().stream().map(m -> m.name() + " [" + m.id() + "]").toList()));
                if (round != null && activeMap != null) tell(sender, "현재 맵: " + activeMap.name());
            }
            case "lobby" -> {
                if (sender instanceof Player player && mapRotation != null && mapRotation.ready()) {
                    leave(player); mapRotation.sendToLobby(player);
                }
            }
            case "mapsave" -> {
                if (!admin(sender)) break;
                if (round != null) { tell(sender, "게임 종료 후 맵을 저장하세요."); break; }
                try {
                    if (args.length < 3) throw new IllegalArgumentException("/mh mapsave <영문ID> <맵 이름>");
                    mapRotation.saveArena(args[1], String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)));
                    tell(sender, "랜덤 맵 목록에 저장했습니다: " + args[1]);
                } catch (IllegalArgumentException | java.io.IOException e) { tell(sender, e.getMessage()); }
            }
            case "mapvisit" -> {
                if (!admin(sender) || !(sender instanceof Player player)) break;
                if (round != null) { tell(sender, "게임 종료 후 미리보기 가능합니다."); break; }
                ArenaMap map = args.length == 2 && mapRotation != null ? mapRotation.maps().stream().filter(m -> m.id().equals(args[1])).findFirst().orElse(null) : null;
                if (map == null) { tell(sender, "/mh mapvisit <맵ID> · /mh maps 로 목록 확인"); break; }
                player.teleport(map.start().clone()); tell(sender, map.name() + " 미리보기 · /mh lobby 로 복귀");
            }
            case "maprefine" -> {
                if (!admin(sender)) break;
                if (round != null) { tell(sender, "게임 종료 후 재편하세요."); break; }
                ArenaMap map = args.length == 2 && mapRotation != null ? mapRotation.maps().stream().filter(m -> m.id().equals(args[1])).findFirst().orElse(null) : null;
                if (map == null) { tell(sender, "/mh maprefine <맵ID>"); break; }
                try { tell(sender, "MAPREFINE " + map.id() + " / 추가 블록=" + MapRefinements.apply(this, map)); }
                catch (IllegalArgumentException | java.io.IOException e) { tell(sender, "재편 취소: " + e.getMessage()); }
            }
            case "mapcheck" -> {
                if (!admin(sender) || mapRotation == null || !mapRotation.ready()) break;
                for (ArenaMap map : mapRotation.maps()) {
                    int safeCount = 0;
                    if (map.boundary() instanceof CuboidBoundary box)
                        for (int x = box.minX(); x < box.maxX(); x += 4) for (int z = box.minZ(); z < box.maxZ(); z += 4)
                            if (SpawnFloor.find(map.start().getWorld(), x + .5, z + .5, map.minSpawnY(), map.maxSpawnY(), map.start().getBlockY(), SafeLocation::isSafe) != null) safeCount++;
                    tell(sender, "MAPCHECK " + map.id() + " / " + map.name() + " / start=" + safe(map.start()) + " / safe-samples=" + safeCount);
                }
                tell(sender, "LOBBYCHECK safe=" + safe(mapRotation.lobby()));
            }
            case "disaster", "event" -> {
                if (!admin(sender)) break;
                if (round == null) { tell(sender, "경기 중에만 재해를 일으킬 수 있습니다."); break; }
                if (disasters == null) { tell(sender, "오류로 이 경기의 재해 기능이 꺼졌습니다. 서버 로그를 확인하세요. 다음 경기에서 다시 쓸 수 있습니다."); break; }
                if (countdownRemaining > 0 || round.isPreparing(elapsed())) { tell(sender, "카운트다운·숨을 시간이 끝난 뒤 일으킬 수 있습니다."); break; }
                Disasters.Kind kind = args.length < 2 || args[1].equalsIgnoreCase("random") ? Disasters.Kind.random(ThreadLocalRandom.current()) : Disasters.Kind.parse(args[1]);
                if (kind == null) { tell(sender, "/mh disaster [random|tornado|meteor|quake|lightning] · 토네이도·유성우·지진·낙뢰"); break; }
                if (!disasters.trigger(kind)) { tell(sender, "이미 재해(" + disasters.current().label + ")가 진행 중입니다. 끝난 뒤 다시 일으키세요."); break; }
                tell(sender, "§c재해 예고: " + kind.label + " §7(3초 뒤 시작 / 플레이어·AI 모두 영향)");
            }
            case "tauntfx" -> {
                // 혼자 검증용: 술래 자리에서 멀리 있는 블록에 도발 효과만 띄워 보이는 거리·소리를 확인합니다.
                if (!admin(sender)) break;
                if (!(sender instanceof Player player)) { tell(sender, "플레이어 전용 명령어입니다."); break; }
                var block = player.getTargetBlockExact(160);
                if (block == null) { tell(player, "160블록 안의 블록을 바라보고 실행하세요."); break; }
                Location at = block.getLocation().add(0.5, 1, 0.5);
                Color color = tauntEffect(at);
                // 혼자서도 벽 너머 발광이 보이는지 확인할 수 있게 같은 기둥을 띄웠다가 지웁니다.
                int glow = tauntGlowSeconds();
                var marker = glow > 0 ? TauntMarkers.spawn(at, color) : null;
                if (marker != null) Bukkit.getScheduler().runTaskLater(this, marker::remove, glow * 20L);
                tell(player, "도발 폭죽 미리보기: " + Math.round(at.distance(player.getLocation())) + "블록 거리 / 점수 없음. 소리는 약 96블록까지 들립니다."
                        + (glow > 0 ? " 발광 기둥 " + glow + "초." : ""));
            }
            case "taunt" -> {
                if (sender instanceof Player player && motions.containsKey(player.getUniqueId())) taunt(player);
                else tell(sender, "변신한 숨는 플레이어만 도발할 수 있습니다.");
            }
            case "wand", "pos1", "pos2", "expandvert", "selclear" -> {
                if (admin(sender) && sender instanceof Player player) arenaSetup.command(player, action);
            }
            case "sandbox" -> { if (admin(sender)) start(sender, false, null, false, true); }
            case "leave" -> {
                if (sender instanceof Player player) {
                    if (!participants.containsKey(player.getUniqueId())) { tell(player, "현재 게임에 참가하고 있지 않습니다."); break; }
                    leave(player);
                    tell(player, "현재 게임에서 나왔습니다. 다음 시작 때는 자동으로 참가합니다.");
                } else tell(sender, "플레이어 전용 명령어입니다.");
            }
            case "start" -> { if (admin(sender)) start(sender, false, null, false); }
            case "test" -> {
                if (!admin(sender)) break;
                EntityType requested = null;
                boolean seekerTest = args.length == 2 && args[1].equalsIgnoreCase("seeker");
                if (args.length > 2) { tell(sender, "/mh test [" + mobNames() + "|seeker]"); break; }
                if (args.length > 1 && !seekerTest) {
                    try {
                        requested = EntityType.valueOf(args[1].toUpperCase(java.util.Locale.ROOT));
                        if (!SAFE_MOBS.contains(requested)) throw new IllegalArgumentException();
                    } catch (IllegalArgumentException e) { tell(sender, "/mh test [" + mobNames() + "|seeker]"); break; }
                }
                start(sender, true, requested, seekerTest);
            }
            case "sethits" -> {
                if (!admin(sender)) break;
                if (round != null) { tell(sender, "게임을 종료한 뒤 오답 횟수를 변경하세요."); break; }
                try {
                    if (args.length != 2) throw new IllegalArgumentException();
                    int limit = Integer.parseInt(args[1]);
                    if (limit < 1 || limit > 20) throw new IllegalArgumentException();
                    getConfig().set("seeker-mistake-limit", limit);
                    saveConfig();
                    tell(sender, "술래가 AI 몹을 " + limit + "회 잘못 때리면 HP가 소진되어 패배하도록 저장했습니다.");
                } catch (IllegalArgumentException e) { tell(sender, "오답 횟수는 1~20으로 지정하세요. 예: /mh sethits 5"); }
            }
            case "view" -> {
                if (!(sender instanceof Player player) || !motions.containsKey(player.getUniqueId())) {
                    tell(sender, "몹으로 변신한 숨는 플레이어만 사용할 수 있습니다."); break;
                }
                try {
                    tell(player, disguises.toggleView(player) ? "자기 변신 표시를 켰습니다. F5로 확인하세요." : "자기 변신 표시를 껐습니다.");
                    animations.reset(player);
                }
                catch (ReflectiveOperationException | RuntimeException | LinkageError e) { failure(sender, e); }
            }
            case "setradius" -> {
                if (!admin(sender)) break;
                if (round != null) { tell(sender, "게임을 종료한 뒤 반경을 변경하세요."); break; }
                Integer requested = parseRadius(sender, args);
                if (requested == null) break;
                getConfig().set("arena-radius", requested);
                getConfig().set("arena.shape", "circle");
                saveConfig();
                tell(sender, "경기장 반경을 " + requested + "블록으로 저장했습니다. 지름은 " + (requested * 2) + "블록입니다.");
            }
            case "spawnheight" -> {
                if (!admin(sender)) break;
                if (round != null) { tell(sender, "게임을 종료한 뒤 소환 높이를 변경하세요."); break; }
                if (args.length == 2 && args[1].equalsIgnoreCase("auto")) {
                    getConfig().set("arena.spawn-min-y", null); getConfig().set("arena.spawn-max-y", null);
                    saveConfig(); tell(sender, "소환 높이를 시작점 기준 위아래 5블록으로 초기화했습니다."); break;
                }
                try {
                    if (args.length != 3) throw new IllegalArgumentException();
                    int min = Integer.parseInt(args[1]), max = Integer.parseInt(args[2]);
                    World world = Bukkit.getWorld(getConfig().getString("arena.world", ""));
                    if (world == null || min > max || min <= world.getMinHeight() || max > world.getMaxHeight() - 2)
                        throw new IllegalArgumentException();
                    getConfig().set("arena.spawn-min-y", min); getConfig().set("arena.spawn-max-y", max);
                    saveConfig(); tell(sender, "몹·숨는 플레이어 소환 발 높이: Y=" + min + "~" + max + " / 경기장 경계는 유지됩니다.");
                } catch (IllegalArgumentException e) { tell(sender, "/mh spawnheight <최소Y> <최대Y> 또는 /mh spawnheight auto. 먼저 경기장을 지정하세요."); }
            }
            case "stop" -> {
                if (admin(sender)) {
                    if (round == null) tell(sender, "진행 중인 라운드가 없습니다.");
                    else finish("관리자가 게임을 종료했습니다.");
                }
            }
            case "setarena" -> {
                if (!admin(sender)) break;
                if (!(sender instanceof Player player)) { tell(sender, "경기장 중앙에서 실행하세요."); break; }
                if (round != null) { tell(sender, "라운드를 종료한 뒤 경기장을 변경하세요."); break; }
                if (args.length == 1 && arenaSetup.hasSelection(player)) {
                    try {
                        CuboidBoundary box = arenaSetup.selection(player);
                        Location here = player.getLocation();
                        if (!box.contains(here.getX(), here.getY(), here.getZ()) || !safe(here)) {
                            tell(sender, "선택한 영역 안의 안전한 바닥 위에서 저장하세요. 현재 위치가 술래 시작점이 됩니다."); break;
                        }
                        getConfig().set("arena.world", here.getWorld().getName());
                        getConfig().set("arena.x", here.getX()); getConfig().set("arena.y", here.getY()); getConfig().set("arena.z", here.getZ());
                        getConfig().set("arena.shape", "cuboid");
                        getConfig().set("arena.min", List.of(box.minX(), box.minY(), box.minZ()));
                        getConfig().set("arena.max", List.of(box.maxX(), box.maxY(), box.maxZ()));
                        getConfig().set("arena.spawn-min-y", null); getConfig().set("arena.spawn-max-y", null);
                        saveConfig(); tell(sender, "직육면체 경기장 저장: " + box.dimensions() + "블록 / 현재 위치를 술래 시작점으로 저장했습니다.");
                    } catch (IllegalArgumentException e) { tell(sender, e.getMessage()); }
                    break;
                }
                Integer requested = args.length > 1 ? parseRadius(sender, args) : clamp(getConfig().getInt("arena-radius", 20), 5, 64);
                if (requested == null) break;
                Location here = player.getLocation();
                getConfig().set("arena.world", here.getWorld().getName());
                getConfig().set("arena.x", here.getX());
                getConfig().set("arena.y", here.getY());
                getConfig().set("arena.z", here.getZ());
                getConfig().set("arena-radius", requested);
                getConfig().set("arena.shape", "circle");
                getConfig().set("arena.spawn-min-y", null); getConfig().set("arena.spawn-max-y", null);
                saveConfig();
                tell(sender, "현재 위치를 경기장 중앙으로 저장했습니다. 반경: " + requested + "블록 / 지름: " + (requested * 2) + "블록");
            }
            case "status" -> tell(sender, round == null ? "대기 중 / 자동 참가 가능 " + availablePlayers().size() + "명 / 오답 한도 "
                    + clamp(getConfig().getInt("seeker-mistake-limit", 20), 1, 20) + "회"
                    : (round.isSeekerTest() ? "혼자 술래 테스트 중" : round.isSoloTest() ? "혼자 변신 테스트 중" : "진행 중 / 생존자 " + round.hiders().size() + "명")
                            + (round.sandbox() ? " / 샌드박스·시간 제한 없음" : " / 남은 시간 " + round.phaseRemaining(elapsed()) + "초")
                            + " / " + arenaDescription()
                            + (round.seeker() == null ? "" : " / 술래 기회 " + round.mistakesRemaining() + "/" + round.mistakeLimit())
                            + (sender instanceof Player player && round.hiders().contains(player.getUniqueId())
                                    ? " / 내 남은 피격 횟수 " + round.hiderHitsRemaining(player.getUniqueId()) + "/" + round.maximumHiderHits(player.getUniqueId()) : ""));
            default -> tell(sender, "/mh maps, lobby, view, taunt, leave, status, top | 관리자: admin, start, sandbox, test [몹|seeker], stop, disaster [종류], mapvisit, mapsave, setarena");
        }
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("test") && sender.hasPermission("mobhide.admin")) {
            List<String> kinds = new ArrayList<>(SAFE_MOBS.stream().map(type -> type.name().toLowerCase(java.util.Locale.ROOT)).toList());
            kinds.add("seeker");
            return kinds.stream().filter(name -> name.startsWith(args[1].toLowerCase(java.util.Locale.ROOT))).toList();
        }
        if (args.length != 1) return List.of();
        List<String> options = new ArrayList<>(List.of("view", "leave", "status", "top", "taunt", "maps", "lobby"));
        if (sender.hasPermission("mobhide.admin")) options.addAll(List.of("mapsave", "mapvisit", "mapcheck", "maprefine"));
        if (sender.hasPermission("mobhide.admin")) options.addAll(List.of("admin", "tauntfx", "sandbox", "wand", "pos1", "pos2", "expandvert", "selclear", "start", "test", "stop", "setarena", "setradius", "sethits", "spawnheight"));
        String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
        return options.stream().filter(s -> s.startsWith(prefix)).toList();
    }

    private List<Player> availablePlayers() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        return online.stream()
                .filter(player -> !player.isDead() && player.getGameMode() != GameMode.SPECTATOR).toList();
    }

    private Integer parseRadius(CommandSender sender, String[] args) {
        try {
            if (args.length != 2) throw new IllegalArgumentException();
            int requested = Integer.parseInt(args[1]);
            if (requested < 5 || requested > 64) throw new IllegalArgumentException();
            return requested;
        } catch (IllegalArgumentException e) { tell(sender, "반경은 5~64 사이의 정수로 지정하세요. 예: /mh setradius 30 또는 /mh setarena 30"); return null; }
    }

    private void start(CommandSender sender, boolean testOnly, EntityType requestedType, boolean seekerTest) {
        start(sender, testOnly, requestedType, seekerTest, false);
    }

    private void start(CommandSender sender, boolean testOnly, EntityType requestedType, boolean seekerTest, boolean sandbox) {
        if (round != null) { tell(sender, "이미 진행 중입니다."); return; }
        if (testOnly && !(sender instanceof Player)) { tell(sender, "혼자 테스트는 플레이어가 /mh test 로 시작하세요."); return; }
        List<Player> players = testOnly ? List.of((Player) sender) : availablePlayers();
        if (players.isEmpty()) { tell(sender, "접속 중인 생존 플레이어가 필요합니다. 관전자는 자동 참가에서 제외됩니다."); return; }
        boolean solo = players.size() == 1;
        activeMap = null;
        if (mapRotation != null) {
            try { activeMap = mapRotation.choose(); }
            catch (IllegalStateException e) { tell(sender, e.getMessage()); return; }
        }
        String worldName = getConfig().getString("arena.world");
        World world = activeMap != null ? activeMap.start().getWorld() : worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) { tell(sender, "먼저 사용할 월드의 경기장 중앙에서 /mh setarena 를 실행하세요."); return; }
        center = activeMap != null ? activeMap.start().clone() : new Location(world, getConfig().getDouble("arena.x"), getConfig().getDouble("arena.y"), getConfig().getDouble("arena.z"));
        radius = clamp(getConfig().getInt("arena-radius", 20), 5, 64);
        boundary = new ArenaBoundary(center.getX(), center.getY(), center.getZ(), radius);
        if (activeMap != null) { boundary = activeMap.boundary(); radius = activeMap.radius(); }
        else if (getConfig().getString("arena.shape", "circle").equals("cuboid")) {
            try {
                List<Integer> min = getConfig().getIntegerList("arena.min"), max = getConfig().getIntegerList("arena.max");
                if (min.size() != 3 || max.size() != 3) throw new IllegalArgumentException("영역 좌표가 없습니다.");
                boundary = new CuboidBoundary(min.get(0), min.get(1), min.get(2), max.get(0), max.get(1), max.get(2));
            } catch (IllegalArgumentException e) { tell(sender, "경기장 두 지점을 다시 저장하세요: " + e.getMessage()); return; }
        }
        List<EntityType> pool;
        try { pool = mobPool(); }
        catch (IllegalArgumentException e) { tell(sender, "몹 목록을 확인하세요. /mh admin 에서 지원하는 " + SAFE_MOBS.size() + "종 중 하나 이상을 선택할 수 있습니다."); return; }
        if (requestedType != null) pool = List.of(requestedType);
        if (!safe(center) || !inside(center)) { tell(sender, "술래 시작점은 경기장 안의 안전한 바닥과 두 칸 높이의 빈 공간이 필요합니다."); return; }
        try {
            for (Player player : players) {
                if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR || disguises.isDisguised(player)) {
                    tell(sender, player.getName() + "님의 사망/관전/기존 변신 상태를 먼저 해제하세요."); return;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { failure(sender, e); return; }
        Player seeker = solo && !seekerTest ? null : players.get(ThreadLocalRandom.current().nextInt(players.size()));
        String fixed = getConfig().getString("fixed-seeker", "");
        if (!testOnly && !solo && !fixed.isBlank()) {
            seeker = players.stream().filter(player -> player.getUniqueId().toString().equals(fixed)).findFirst().orElse(null);
            if (seeker == null) { tell(sender, "지정 술래가 참가 가능한 상태가 아닙니다. /mh admin 에서 술래를 바꾸세요."); return; }
        }
        final Player selectedSeeker = seeker;
        List<Player> hiders = players.stream().filter(player -> player != selectedSeeker).toList();
        List<EntityType> hiderTypes = new ArrayList<>();
        for (Player player : hiders) hiderTypes.add(requestedType != null ? requestedType : configuredMob(player, pool));
        MobPlan mobs = MobPlan.forHiders(pool, hiderTypes, clamp(getConfig().getInt("decoy-count", 25), 1, 100), ThreadLocalRandom.current());
        if (world.getDifficulty() == Difficulty.PEACEFUL && mobs.decoys().stream().anyMatch(MobCatalog::hostile)) {
            tell(sender, "좀비·스켈레톤·크리퍼 AI는 평화로움에서 사라집니다. /difficulty easy 이상으로 설정하거나 관리자 패널에서 해당 몹을 빼세요."); return;
        }
        // 철골렘처럼 큰 몹은 자기 몸 크기로 자리를 찾아야 벽·천장에 끼지 않습니다.
        // 같은 블록 중앙에 두 명이 소환되면 중심이 완전히 겹쳐 서로 밀어내지도 못하고 몸 안에 갇혔습니다. 자리 사이 간격을 둡니다.
        List<Location> hiderSpots = new ArrayList<>(), decoySpots = new ArrayList<>(), taken = new ArrayList<>();
        taken.add(center);
        for (EntityType type : mobs.hiders()) {
            Location spot = spacedSpot(type, taken);
            if (spot == null) { tell(sender, "경기장에 안전한 소환 위치가 부족합니다. 평평하고 넓은 곳으로 옮기세요."); return; }
            hiderSpots.add(randomFacing(spot));
        }
        for (EntityType type : mobs.decoys()) {
            Location spot = spacedSpot(type, taken);
            if (spot == null) { tell(sender, "경기장에 안전한 소환 위치가 부족합니다. 평평하고 넓은 곳으로 옮기세요."); return; }
            decoySpots.add(randomFacing(spot));
        }
        if (villagerCalm == null && mobs.decoys().contains(EntityType.VILLAGER) && mobs.decoys().stream().anyMatch(MobCatalog::scaresVillagers))
            tell(sender, "§e주의: 주민 AI는 좀비·허스크 AI를 보면 도망쳐서, 도망치지 않는 변신 주민이 티 날 수 있습니다. 랜덤 몹 목록에서 둘 중 하나를 빼면 사라집니다.");
        int hiding = clamp(getConfig().getInt("hide-seconds", 15), 0, 60);
        int chasing = clamp(getConfig().getInt("round-seconds", 180), 30, 3600);
        int mistakeLimit = seeker == null ? clamp(getConfig().getInt("seeker-mistake-limit", 20), 1, 20) : configuredSeekerLives(seeker);
        round = seekerTest ? RoundState.seekerTest(seeker.getUniqueId(), chasing, mistakeLimit)
                : solo ? RoundState.soloTest(hiders.getFirst().getUniqueId(), chasing)
                : new RoundState(seeker.getUniqueId(), hiders.stream().map(Player::getUniqueId).toList(), hiding, chasing, mistakeLimit);
        round.setHiderHitLimit(clamp(getConfig().getInt("hider-hit-limit", 2), 1, 20));
        for (Player player : hiders) round.setHiderLives(player.getUniqueId(), configuredLives(player));
        if (sandbox) round.enableSandbox();
        scoringEligible = !sandbox && !round.isSoloTest();
        originalHiders = round.hiders();
        startedAt = System.nanoTime();
        countdownRemaining = 3;
        chaseAnnounced = solo;
        motionTick = 0;
        score = new RoundScore(clamp(getConfig().getInt("score.seconds-per-point", 10), 0, 600),
                clamp(getConfig().getInt("score.survive-bonus", 5), 0, 100), clamp(getConfig().getInt("score.catch-bonus", 5), 0, 100),
                clamp(getConfig().getInt("score.all-caught-bonus", 5), 0, 100),
                tauntPoints(), tauntCooldownSeconds() * 20L);
        tauntMarkers = new TauntMarkers();
        disasters = new Disasters(new DisasterStage(), ThreadLocalRandom.current());
        try {
            sidebar = new ScoreSidebar(Bukkit.getScoreboardManager().getNewScoreboard(), scoreTitle());
            int hiderIndex = 0;
            for (Player player : players) {
                HealthSnapshot health = new HealthSnapshot(player.getHealth(), player.isHealthScaled(), player.getHealthScale());
                participants.put(player.getUniqueId(), new Snapshot(player.getLocation().clone(), player.getGameMode(), player.getWalkSpeed(), player.isSprinting(), player.isCollidable(), health, player.getFireTicks()));
                // LibsDisguises가 변신 시 현재 점수판을 보므로 변신보다 먼저 경기 점수판으로 바꿉니다.
                sidebar.show(player);
                player.setFireTicks(0);
                player.setGameMode(GameMode.ADVENTURE);
                player.setCollidable(true);
                Location destination = player == seeker ? center : hiderSpots.get(hiderIndex);
                restoring = true;
                try {
                    if (!player.teleport(destination)) throw new IllegalStateException("Teleport cancelled: " + player.getName());
                } finally { restoring = false; }
                lastInside.put(player.getUniqueId(), destination.clone());
                RoundState starting = round;
                Location spawnSpot = destination.clone();
                resendTeleport(player, spawnSpot, () -> round == starting && countdownRemaining > 0 && participants.containsKey(player.getUniqueId()));
                if (player == seeker) {
                    applySeekerHealth(player);
                    tell(player, sandbox ? "샌드박스 술래입니다! 카운트다운 후 자유롭게 테스트할 수 있습니다." : seekerTest ? "혼자 술래 테스트입니다! 카운트다운 후 AI 몹을 때려 HP 감소를 확인하세요."
                            : "당신은 술래입니다! 카운트다운과 숨을 시간 " + hiding + "초 후 몹 사이의 플레이어를 공격해 찾으세요.");
                    tell(player, "술래 체력: " + mistakeLimit + "하트. AI 오답마다 하트 한 칸이 줄어듭니다."
                            + (sandbox ? " 소진하면 관전하며 관리자 패널에서 회복할 수 있습니다." : " 모두 소진하면 패배합니다."));
                    if (score.tauntPoints() > 0) tell(player, "§d폭죽§f이 터지면 숨는 플레이어가 도발한 것입니다. 폭죽 아래를 노리세요!");
                } else {
                    EntityType type = mobs.hiders().get(hiderIndex++);
                    disguises.disguise(player, type, true);
                    animations.track(player, type);
                    applyHiderHealth(player);
                    player.setWalkSpeed(configuredSpeed(player, type));
                    player.setSprinting(false);
                    motions.put(player.getUniqueId(), new MobAnimation(type, player.getLocation(), AnimationBridge.grounded(player)));
                    tell(player, "변신: " + type.name() + (sandbox ? " / 샌드박스입니다. 시간 제한 없이 테스트하고 /mh admin 에서 설정하세요." : solo
                            ? " / 혼자 테스트입니다. F5로 변신 모습을 확인하고 자유롭게 움직여 보세요. /mh stop 으로 종료합니다."
                            : " / 숨는 팀입니다! F5로 자기 움직임을 확인하고 AI 몹처럼 행동하세요. " + chasing + "초 동안 살아남으세요."));
                    tell(player, "/mh view 로 자기 변신 표시를 켜거나 끌 수 있습니다. 숨는 팀은 질주할 수 없습니다.");
                    tell(player, "술래에게 " + round.maximumHiderHits(player.getUniqueId()) + "번 맞으면 탈락합니다. /mh status 에서 남은 횟수를 확인하세요.");
                    if (score.tauntPoints() > 0) tell(player, "§dF(손 바꾸기)§f: 폭죽 도발! 위치가 드러나지만 §e+" + score.tauntPoints()
                            + "점§f. 추격 중 " + tauntCooldownSeconds() + "초마다 사용할 수 있습니다.");
                }
            }
            for (int i = 0; i < decoySpots.size(); i++) spawnDecoy(decoySpots.get(i), mobs.decoys().get(i));
            if (sandbox) {
                chaseAnnounced = true;
                announce("샌드박스 준비! 자동 참가 " + players.size() + "명 / AI " + mobs.decoys().size() + "마리 / 시간 제한·승점 없음 / /mh admin 으로 관리");
            } else if (solo) {
                announce((seekerTest ? "혼자 술래 테스트" : "혼자 변신 테스트") + " 준비! AI 몹 " + mobs.decoys().size() + "마리 / 제한 시간 " + chasing + "초");
            } else {
                applyPreparationBlindness(seeker);
                announce("자동 참가 " + players.size() + "명 / 술래: " + seeker.getName() + " / 준비 " + hiding + "초 / 추격 " + chasing + "초");
            }
            // 시작 전에 이미 참가자를 노리던 자연 생성 몹의 대상도 해제합니다.
            for (Mob mob : world.getEntitiesByClass(Mob.class)) {
                if (protectedParticipant(mob.getTarget())) mob.setTarget(null);
            }
            timerBar = Bukkit.createBossBar("몹 숨바꼭질", BarColor.YELLOW, BarStyle.SOLID);
            for (Player player : players) timerBar.addPlayer(player);
            updateTimerBar(0);
            refreshScore();
            if (activeMap != null) {
                mapRotation.started(activeMap);
                announce("§e선정된 맵: §b" + activeMap.name() + " §f— 게임을 시작합니다!");
            }
            showStartTitle("3", false);
            ticker = Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
            movementTicker = Bukkit.getScheduler().runTaskTimer(this, this::moveMobs, 1L, 1L);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failure(sender, e);
            finish("시작에 실패하여 플레이어 상태와 소환한 몹을 정리했습니다.");
        }
    }

    private List<EntityType> mobPool() {
        // 기존 mob-type만 있는 설정도 새 기본 랜덤 풀을 사용합니다.
        if (!getConfig().isSet("mob-types")) return SAFE_MOBS;
        Set<EntityType> pool = new LinkedHashSet<>();
        for (String name : getConfig().getStringList("mob-types")) {
            EntityType type = EntityType.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
            if (!SAFE_MOBS.contains(type)) throw new IllegalArgumentException("Unsupported mob: " + name);
            pool.add(type);
        }
        if (pool.isEmpty()) throw new IllegalArgumentException("Empty mob pool");
        return List.copyOf(pool);
    }

    private void tick() {
        if (round == null) return;
        if (countdownRemaining > 0) {
            countdownRemaining--;
            if (countdownRemaining > 0) {
                showStartTitle(Integer.toString(countdownRemaining), false);
                updateTimerBar(0);
                return;
            }
            // 준비/추격 시간은 카운트다운이 끝난 순간부터 계산합니다.
            startedAt = System.nanoTime();
            showStartTitle("시작!", true);
            announce(round.isPreparing(0) ? "시작! 숨을 시간 " + round.phaseRemaining(0) + "초입니다." : "시작!");
            if (!round.isPreparing(0)) restoreBlindness();
        }
        long seconds = elapsed();
        round.advance(seconds);
        if (endIfWon()) return;
        updateTimerBar(seconds);
        refreshScore();
        if (!round.isPreparing(seconds) && !chaseAnnounced) {
            chaseAnnounced = true;
            restoreBlindness();
            announce("추격 시작! 술래는 플레이어로 의심되는 몹을 공격하세요.");
        }
        if (!round.sandbox() && chaseAnnounced && (round.remaining(seconds) <= 10 || round.remaining(seconds) % 30 == 0))
            announce("남은 시간: " + round.remaining(seconds) + "초"
                    + (round.isSoloTest() ? " / 혼자 테스트" : " / 생존자: " + round.hiders().size() + "명"));
    }

    private void showStartTitle(String text, boolean started) {
        Title title = Title.title(
                Component.text(text, started ? NamedTextColor.GREEN : NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                Component.text((activeMap == null ? "" : "맵: " + activeMap.name() + " · ") + "플러그인 by beddeak(침댁)", NamedTextColor.GRAY),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(started ? 1500 : 1100), Duration.ofMillis(150)));
        for (UUID id : participants.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.showTitle(title);
        }
    }

    private void updateTimerBar(long seconds) {
        if (timerBar == null || round == null) return;
        if (countdownRemaining > 0) {
            timerBar.setTitle("§6몹 숨바꼭질 §f| 시작까지 §e" + countdownRemaining + "초");
            timerBar.setColor(BarColor.YELLOW);
            timerBar.setProgress(countdownRemaining / 3.0);
            return;
        }
        String phase = round.sandbox() ? "샌드박스" : round.isSeekerTest() ? "술래 테스트" : round.isSoloTest() ? "혼자 테스트" : round.isPreparing(seconds) ? "숨을 시간" : "추격 중";
        timerBar.setTitle("§6몹 숨바꼭질 §f| " + phase + " §e" + (round.sandbox() ? "시간 무제한" : round.phaseRemaining(seconds) + "초")
                + (round.isSoloTest() ? "" : " §f| 생존자 " + round.hiders().size() + "명")
                + (round.seeker() == null ? "" : " §c| 술래 기회 " + round.mistakesRemaining() + "/" + round.mistakeLimit()));
        timerBar.setColor(round.isSoloTest() ? BarColor.PURPLE : round.isPreparing(seconds) ? BarColor.YELLOW : BarColor.RED);
        timerBar.setProgress(round.sandbox() ? 1 : Math.max(0, Math.min(1, round.phaseRemaining(seconds) / (double) round.phaseDuration(seconds))));
    }

    private void moveMobs() {
        if (round == null) return;
        motionTick++;
        if (disasters != null) {
            // 관리자 재해 이벤트는 보조 기능이라 실패해도 경기를 끝내지 않고 재해만 멈춥니다.
            try { disasters.tick(); }
            catch (RuntimeException | LinkageError e) { getLogger().log(Level.SEVERE, "재해 이벤트 처리 실패. 이 경기의 재해를 중단합니다.", e); disasters.clear(); disasters = null; }
        }
        try {
            expireHitSpeedBoosts();
            if (tauntMarkers != null) tauntMarkers.expire(motionTick);
            for (Map.Entry<UUID, MobAnimation> entry : motions.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null) continue;
                MobAnimation motion = entry.getValue();
                player.setSprinting(false);
                var input = player.getCurrentInput();
                boolean steering = input == null || input.isForward() || input.isBackward() || input.isLeft() || input.isRight();
                var frame = motion.sample(player.getLocation(), AnimationBridge.grounded(player),
                        player.isInWater() || player.isInLava() || player.isClimbing() || player.isInsideVehicle(), steering, motionTick);
                if (frame.launchHop()) player.setVelocity(hopVelocity(frame));
                if (motion.type == EntityType.CHICKEN) chickenFall(player, motionTick);
                // 새로 보이는 관전자의 생성 패킷도 지금 몸 방향을 쓰게 합니다(멀리서 처음 보일 때 몸이 휙 도는 문제).
                if (motionTick % 40 == 0) {
                    // 보조 효과라 실패해도 경기를 끝내지 않습니다.
                    try { disguises.face(player, frame.yaw()); }
                    catch (ReflectiveOperationException | RuntimeException | LinkageError e) { getLogger().log(Level.FINE, "변신 방향 갱신 실패: " + player.getName(), e); }
                }
                animations.render(player, disguises.selfVisible(player), frame.yaw(), frame.headYaw(), frame.pitch(), frame.grounded(), frame.hop());
            }
            separateParticipants();
            for (Entity mob : decoys) {
                if (!mob.isValid()) continue;
                Location here = mob.getLocation();
                if (inside(here)) {
                    if (safe(here, mob.getType())) lastInside.put(mob.getUniqueId(), here.clone());
                    continue;
                }
                Location corrected = boundaryReturn(here, lastInside.get(mob.getUniqueId()), mob.getType());
                if (corrected == null) { mob.setVelocity(new Vector()); continue; }
                if (mob instanceof org.bukkit.entity.Mob ai) ai.getPathfinder().stopPathfinding();
                Vector arenaMiddle = boundary instanceof CuboidBoundary box
                        ? new Vector((box.minX() + box.maxX()) / 2.0, corrected.getY(), (box.minZ() + box.maxZ()) / 2.0) : center.toVector();
                Vector inward = arenaMiddle.subtract(corrected.toVector()).setY(0);
                if (inward.lengthSquared() > 0) corrected.setDirection(inward);
                if (mob.teleport(corrected)) {
                    lastInside.put(mob.getUniqueId(), corrected.clone());
                    mob.setVelocity(inward.lengthSquared() > 0 ? inward.normalize().multiply(0.08) : new Vector());
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            getLogger().log(Level.SEVERE, "몹 이동/애니메이션 처리 실패", e);
            finish("몹 이동 처리에 실패하여 게임을 종료하고 상태를 복구했습니다.");
        }
    }

    /**
     * 몸 충돌은 이동 경로만 막아, 머리 위로 떨어지거나 순간이동·소환으로 이미 겹친 참가자끼리는 계속 겹쳐 있었습니다
     * (술래 카메라가 숨는 사람 몸 안에 들어감). AI는 서버가 밀어 내지만 플레이어끼리는 밀지 않으므로 직접 떼어 놓습니다.
     */
    private void separateParticipants() {
        List<Player> bodies = new ArrayList<>();
        for (UUID id : participants.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.getGameMode() != GameMode.SPECTATOR && player.getBoundingBox() != null) bodies.add(player);
        }
        for (int i = 0; i < bodies.size(); i++) for (int j = i + 1; j < bodies.size(); j++) {
            Player a = bodies.get(i), b = bodies.get(j);
            var boxA = a.getBoundingBox();
            var boxB = b.getBoundingBox();
            if (a.getWorld() != b.getWorld() || !boxA.overlaps(boxB)) continue;
            Vector away = BodyCollision.separation(boxA, boxB, a.getUniqueId().compareTo(b.getUniqueId()) < 0);
            boolean aFrozen = frozen(a), bFrozen = frozen(b);
            // 준비 시간에 묶인 술래는 움직이지 않고 상대가 두 배로 비켜 납니다.
            if (!aFrozen) nudge(a, bFrozen ? away.clone().multiply(2) : away);
            if (!bFrozen) nudge(b, (aFrozen ? away.clone().multiply(2) : away.clone()).multiply(-1));
        }
    }

    private boolean frozen(Player player) {
        return countdownRemaining > 0 || (round.isSeeker(player.getUniqueId()) && round.isPreparing(elapsed()));
    }

    private static void nudge(Player player, Vector push) {
        Vector current = player.getVelocity();
        player.setVelocity(new Vector(push.getX(), current == null ? 0 : current.getY(), push.getZ()));
    }

    /**
     * 여러 명을 한 번에 다른 위치로 옮기면 클라이언트 위치가 서버와 어긋나 이전 경기장에 남는 경우가 있었습니다
     * (서버는 로비로 기록, 화면은 경기장). 0.5초 뒤 같은 곳으로 한 번 더 보내 맞춥니다. 그 사이 상태가 바뀌었으면 하지 않습니다.
     */
    private void resendTeleport(Player player, Location destination, java.util.function.BooleanSupplier stillWanted) {
        if (!isEnabled()) return;
        try {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!player.isOnline() || !stillWanted.getAsBoolean()) return;
                restoring = true;
                try { player.teleport(destination); } finally { restoring = false; }
            }, 10L);
        } catch (RuntimeException e) { getLogger().log(Level.FINE, "재이동 예약 실패: " + player.getName(), e); }
    }

    /**
     * 서버의 플레이어 속도는 걷는 수평 속도를 담지 않아(대략 0) 그대로 쓰면 도약마다 제자리 점프가 됩니다.
     * 이번 틱의 실제 이동량을 유지해 앞으로 뛰게 하고, 지연으로 몰린 이동은 상한을 둡니다.
     */
    static Vector hopVelocity(MobAnimation.Frame frame) {
        Vector step = new Vector(frame.stepX(), 0, frame.stepZ());
        if (step.lengthSquared() > 0.16) step.normalize().multiply(0.4);
        return step.setY(0.3);
    }

    @EventHandler(ignoreCancelled = true) public void onHit(EntityDamageByEntityEvent event) {
        if (round == null) return;
        Entity damager = event.getDamager();
        Player attacker = damager instanceof Player p ? p
                : damager instanceof Projectile projectile && projectile.getShooter() instanceof Player p ? p : null;
        boolean targetParticipant = participants.containsKey(event.getEntity().getUniqueId());
        boolean targetDecoy = decoys.contains(event.getEntity());
        boolean participantAttack = attacker != null && participants.containsKey(attacker.getUniqueId());
        if (!targetParticipant && !targetDecoy && !participantAttack) return;
        event.setCancelled(true);
        if (!participantAttack || countdownRemaining > 0) return;
        // 휩쓸기 부가 타격은 하나의 검 공격을 여러 오답으로 세지 않습니다.
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (targetDecoy) {
            UUID decoyId = event.getEntity().getUniqueId();
            // 실제 피해를 취소해 바닐라 무적 시간이 없으므로, 같은 AI를 0.5초 안에 연타하거나
            // 다중 발사 화살이 겹쳐도 오답 한 번으로 셉니다. 다른 AI를 때리면 따로 셉니다.
            if (motionTick < hitProtection.getOrDefault(decoyId, 0L)) return;
            UUID projectile = damager instanceof Projectile ? damager.getUniqueId() : null;
            if (round.wrongHit(attacker.getUniqueId(), projectile, elapsed())) {
                hitProtection.put(decoyId, motionTick + HIT_PROTECTION_TICKS);
                tell(attacker, "AI 몹을 공격했습니다! HP 감소 / 남은 기회 " + round.mistakesRemaining() + "/" + round.mistakeLimit());
                updateTimerBar(elapsed());
                if (endIfWon()) return;
                attacker.setHealth(Math.max(1, Math.min(maxHealth(attacker), round.seekerHealth())));
                if (round.sandbox() && round.mistakesRemaining() == 0) {
                    attacker.setGameMode(GameMode.SPECTATOR);
                    tell(attacker, "술래 기회를 모두 썼습니다. 관리자 패널에서 피를 다시 설정하면 복귀합니다.");
                }
                attacker.sendHurtAnimation(attacker.getLocation().getYaw());
            } else endIfWon();
            return;
        }
        UUID targetId = event.getEntity().getUniqueId();
        // 실제 피해를 취소하므로 바닐라 피격 간격 대신 직접 중복/연속 타격을 제한합니다.
        if (motionTick < hitProtection.getOrDefault(targetId, 0L)) return;
        int before = round.hiderHitsRemaining(targetId);
        UUID projectile = damager instanceof Projectile ? damager.getUniqueId() : null;
        if (round.catchHider(attacker.getUniqueId(), targetId, projectile, elapsed())) {
            String gained = "";
            if (score != null) {
                long chase = round.chaseElapsed(elapsed());
                int seekerBefore = score.points(attacker.getUniqueId(), false, chase);
                score.caught(targetId, attacker.getUniqueId(), chase, round.chaseLimit());
                if (round.winner() == RoundState.Winner.SEEKER) score.allCaught(attacker.getUniqueId());
                gained = " §e(술래 +" + (score.points(attacker.getUniqueId(), false, chase) - seekerBefore) + "점)";
            }
            Player caught = Bukkit.getPlayer(targetId);
            if (caught != null) {
                removeDisguise(caught);
                caught.setGameMode(GameMode.SPECTATOR);
                tell(caught, "피격 횟수를 모두 써 탈락했습니다! 남은 라운드를 관전하세요.");
                announce(attacker.getName() + "님이 " + caught.getName() + "님을 찾았습니다! §c탈락!" + gained);
                hitEffect(caught, true);
                updateTimerBar(elapsed());
                refreshScore();
            }
        } else if (round.hiderHitsRemaining(targetId) < before) {
            hitProtection.put(targetId, motionTick + HIT_PROTECTION_TICKS);
            int remaining = round.hiderHitsRemaining(targetId);
            tell(attacker, "숨는 플레이어를 찾았습니다! 앞으로 " + remaining + "번 더 맞히면 탈락합니다.");
            Player hit = Bukkit.getPlayer(targetId);
            if (hit != null) {
                tell(hit, "술래에게 맞았습니다! 남은 피격 횟수: " + remaining + "/" + round.maximumHiderHits(targetId));
                hit.setHealth(Math.max(1, Math.min(maxHealth(hit), remaining * 2.0)));
                hit.sendHurtAnimation(hit.getLocation().getYaw());
                applyHitSpeedBoost(hit);
                hitEffect(hit, false);
            }
        }
        endIfWon();
    }

    @EventHandler(priority = EventPriority.HIGH) public void onDamage(EntityDamageEvent event) {
        if (round != null && (participants.containsKey(event.getEntity().getUniqueId()) || decoys.contains(event.getEntity())))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void onHeal(EntityRegainHealthEvent event) {
        if (round != null && participants.containsKey(event.getEntity().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (blindnessApplied && round != null && (countdownRemaining > 0 || round.isPreparing(elapsed()))
                && round.isSeeker(event.getEntity().getUniqueId())
                && event.getModifiedType().equals(PotionEffectType.BLINDNESS)) event.setCancelled(true);
    }

    private void hitEffect(Player player, boolean caught) {
        Location at = player.getLocation().add(0, Math.min(1, player.getHeight() / 2), 0);
        player.getWorld().spawnParticle(Particle.FIREWORK, at, caught ? 40 : 18, 0.25, 0.3, 0.25, 0.06);
        player.getWorld().playSound(at, caught ? Sound.ENTITY_FIREWORK_ROCKET_TWINKLE : Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onTarget(EntityTargetLivingEntityEvent event) {
        // 맵에 원래 있던 철골렘·늑대 등이 게임 AI(좀비·양 등)를 공격하지 못하게 대상도 확인합니다.
        if (event.getTarget() != null && (decoys.contains(event.getEntity()) || decoys.contains(event.getTarget())
                || (event.getEntity() instanceof Mob && protectedParticipant(event.getTarget())))) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaSpawn(org.bukkit.event.entity.CreatureSpawnEvent event) {
        // 경기 중 경기장에는 게임이 소환한 AI만 둡니다. 밤 몬스터·주민의 철골렘 소환·알 부화 등은 구분이 안 되는 몹을 만듭니다.
        if (round != null && event.getSpawnReason() != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM
                && inside(event.getLocation())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onPrime(ExplosionPrimeEvent event) {
        if (decoys.contains(event.getEntity())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onCombust(EntityCombustEvent event) {
        if (decoys.contains(event.getEntity()) || (round != null && participants.containsKey(event.getEntity().getUniqueId())))
            event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onTransform(EntityTransformEvent event) {
        if (decoys.contains(event.getEntity())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onMobBlock(EntityChangeBlockEvent event) {
        if (decoys.contains(event.getEntity())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onMobProjectile(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Entity entity)) return;
        if (decoys.contains(entity)) event.setCancelled(true);
        else if (entity instanceof Mob mob && protectedParticipant(mob.getTarget())) {
            event.setCancelled(true);
            mob.setTarget(null);
        }
    }

    private boolean protectedParticipant(Entity entity) {
        return round != null && entity != null && participants.containsKey(entity.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobPotion(org.bukkit.event.entity.PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Mob source)) return;
        if (decoys.contains(source)) { event.setCancelled(true); return; }
        // 피해 이벤트만 취소하면 독·둔화 등 상태 효과는 그대로 적용되므로 명중 강도도 제거합니다.
        for (var affected : event.getAffectedEntities()) {
            if (protectedParticipant(affected) || decoys.contains(affected)) event.setIntensity(affected, 0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobPotionCloud(org.bukkit.event.entity.AreaEffectCloudApplyEvent event) {
        if (!(event.getEntity().getSource() instanceof Mob source)) return;
        if (decoys.contains(source)) { event.setCancelled(true); return; }
        event.getAffectedEntities().removeIf(entity -> protectedParticipant(entity) || decoys.contains(entity));
    }

    private static double maxHealth(Player player) {
        return Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue();
    }

    private void clearSeekerHealth(Player player) {
        AttributeInstance health = Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH));
        for (AttributeModifier modifier : new ArrayList<>(health.getModifiers())) {
            if (modifier.getKey().equals(roundHealthKey)) health.removeModifier(modifier);
        }
    }

    private void applySeekerHealth(Player player) {
        applyRoundHealth(player, round.maximumSeekerHealth());
    }
    private void applyHiderHealth(Player player) { applyRoundHealth(player, round.maximumHiderHits(player.getUniqueId()) * 2.0); }
    private void applyRoundHealth(Player player, double maximum) {
        clearSeekerHealth(player);
        AttributeInstance health = Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH));
        double multiplier = maximum / health.getValue() - 1;
        // 기본값/장비/다른 플러그인의 modifier를 유지하고 경기 소유의 임시 modifier만 추가합니다.
        health.addTransientModifier(new AttributeModifier(roundHealthKey, multiplier, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        player.setHealthScaled(false);
        player.setHealth(Math.min(health.getValue(), maximum));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onMove(PlayerMoveEvent event) {
        if (restoring || round == null || !participants.containsKey(event.getPlayer().getUniqueId()) || event.getTo() == null) return;
        boolean frozen = countdownRemaining > 0 || (round.isSeeker(event.getPlayer().getUniqueId()) && round.isPreparing(elapsed()));
        Location from = event.getFrom(), to = event.getTo();
        boolean moved = from.getWorld() != to.getWorld() || from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ();
        EntityType disguise = disguiseOf(event.getPlayer());
        if (inside(from) && safe(from, disguise)) lastInside.put(event.getPlayer().getUniqueId(), from.clone());
        if ((frozen && moved) || !inside(to)) {
            Location corrected = frozen && inside(from) ? from.clone()
                    : boundaryReturn(to, lastInside.get(event.getPlayer().getUniqueId()), disguise);
            if (corrected == null) { event.setCancelled(true); return; }
            corrected.setYaw(to.getYaw()); corrected.setPitch(to.getPitch());
            event.setTo(corrected);
            event.getPlayer().setVelocity(new Vector());
        }
        if (moved && !frozen && !(event instanceof PlayerTeleportEvent)
                && event.getPlayer().getGameMode() != GameMode.SPECTATOR) collide(event);
    }

    private void collide(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom(), to = event.getTo();
        if (to == null || from.getWorld() != to.getWorld()) return;
        var previous = player.getBoundingBox().clone().shift(from.toVector().subtract(player.getLocation().toVector()));
        var attempted = previous.clone().shift(to.toVector().subtract(from.toVector()));
        List<Entity> bodies = new ArrayList<>(decoys);
        for (UUID id : participants.keySet()) {
            Player other = Bukkit.getPlayer(id);
            if (other != null && !other.equals(player) && other.getGameMode() != GameMode.SPECTATOR) bodies.add(other);
        }
        Entity contact = null;
        Vector correction = null;
        Vector travel = to.toVector().subtract(from.toVector());
        double firstContact = Double.POSITIVE_INFINITY;
        for (Entity body : bodies) {
            if (!body.isValid() || body.getWorld() != player.getWorld()) continue;
            Vector candidate = BodyCollision.correction(previous, attempted, body.getBoundingBox());
            if (candidate.lengthSquared() == 0) continue;
            double movement = candidate.getX() != 0 ? travel.getX() : travel.getZ();
            double offset = candidate.getX() != 0 ? candidate.getX() : candidate.getZ();
            double fraction = movement == 0 ? 0 : Math.max(0, 1 + offset / movement);
            // 소환 순서가 아니라 이동 경로에서 먼저 만난 몸에 부딪혀야 합니다.
            if (fraction < firstContact) { firstContact = fraction; contact = body; correction = candidate; }
        }
        if (contact == null) return;
        Location corrected = to.clone().add(correction);
        if (inside(corrected) && corrected.getBlock().isPassable()
                && corrected.clone().add(0, player.getHeight() - 0.05, 0).getBlock().isPassable()) event.setTo(corrected);
        else event.setTo(from);
        // AI와 변신 플레이어 모두 동일한 약한 밀림을 받아 충돌 자체로 정체가 드러나지 않습니다.
        Vector push = correction.clone().normalize().multiply(-0.12);
        contact.setVelocity(contact.getVelocity().add(push));
        player.setVelocity(player.getVelocity().setX(0).setZ(0));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onSprint(PlayerToggleSprintEvent event) {
        if (event.isSprinting() && motions.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true) public void onTeleport(PlayerTeleportEvent event) {
        // 텔레포트 이벤트는 별도 HandlerList를 가지므로 이동 제한을 명시적으로 적용합니다.
        onMove(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnimationTeleport(PlayerTeleportEvent event) {
        MobAnimation motion = motions.get(event.getPlayer().getUniqueId());
        if (motion != null && event.getTo() != null) {
            motion.reset(event.getTo());
            animations.reset(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrack(PlayerTrackEntityEvent event) {
        if (event.getEntity() instanceof Player player) animations.resetViewer(player, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUntrack(PlayerUntrackEntityEvent event) {
        if (event.getEntity() instanceof Player player) animations.resetViewer(player, event.getPlayer());
    }

    @EventHandler public void onBreak(BlockBreakEvent event) {
        if (participants.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler public void onPlace(BlockPlaceEvent event) {
        if (participants.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    // 허공 우클릭은 처음부터 취소 상태로 오므로 ignoreCancelled를 쓰지 않습니다.
    @EventHandler(priority = EventPriority.HIGHEST) public void onInteract(PlayerInteractEvent event) {
        if (round == null || !participants.containsKey(event.getPlayer().getUniqueId())) return;
        if (event.getAction() == Action.PHYSICAL) {
            // 밭 밟기는 BlockBreakEvent를 거치지 않으므로 맵 보호를 위해 따로 막습니다. 압력판은 유지합니다.
            if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.FARMLAND) event.setCancelled(true);
            return;
        }
        // 음식·물약·활·진주·알을 쓰면 몹 외형에서 입자·소리·투사체가 나와 정체가 드러납니다.
        // 아이템 사용만 막고 문·버튼 같은 블록 조작은 유지합니다. 술래의 활 사용도 유지합니다.
        if (motions.containsKey(event.getPlayer().getUniqueId()) && event.getItem() != null
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK))
            event.setUseItemInHand(Event.Result.DENY);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onDrop(PlayerDropItemEvent event) {
        // 몹 외형에서 아이템이 튀어나오면 바로 정체가 드러납니다.
        if (round != null && motions.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onSwapHands(PlayerSwapHandItemsEvent event) {
        // 숨는 팀의 F(손 바꾸기)는 도발로 씁니다. 손 아이템은 어차피 몹 외형에 보이지 않습니다.
        if (round == null || score == null || score.tauntPoints() == 0 || !motions.containsKey(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        taunt(event.getPlayer());
    }

    /** 하이리스크 하이리턴: 자기 위치에 폭죽을 터뜨려 점수를 받습니다. */
    private void taunt(Player hider) {
        if (round == null || score == null || !round.hiders().contains(hider.getUniqueId())) return;
        if (score.tauntPoints() == 0) { tell(hider, "이번 경기는 도발이 꺼져 있습니다."); return; }
        // 술래가 실명·정지 상태일 때 공짜 점수를 받지 못하게 합니다.
        if (countdownRemaining > 0 || round.isPreparing(elapsed())) { tell(hider, "도발은 추격이 시작된 뒤에 할 수 있습니다."); return; }
        long wait = score.taunt(hider.getUniqueId(), motionTick);
        if (wait > 0) { tell(hider, "도발 대기 중: " + (wait + 19) / 20 + "초"); return; }
        Color color = tauntEffect(hider.getLocation());
        // 건물이 많으면 폭죽이 가려지므로 도발한 자리에 벽 너머로 보이는 발광 기둥을 잠깐 남깁니다.
        int glow = tauntGlowSeconds();
        if (glow > 0 && tauntMarkers != null) {
            var marker = TauntMarkers.spawn(hider.getLocation(), color);
            if (marker != null) tauntMarkers.add(marker, motionTick + glow * 20L);
        }
        tell(hider, "§d폭죽 도발! §e+" + score.tauntPoints() + "점 §f위치가 드러났습니다. 도망치세요!");
        for (UUID id : participants.keySet()) {
            Player other = id.equals(hider.getUniqueId()) ? null : Bukkit.getPlayer(id);
            if (other != null) tell(other, "§d어딘가에서 숨는 플레이어가 폭죽을 터뜨렸습니다!" + (glow > 0 ? " §f빛나는 기둥이 " + glow + "초 동안 남습니다." : ""));
        }
        refreshScore();
    }

    private static Color tauntEffect(Location feet) {
        World world = feet.getWorld();
        // 실제 폭죽 엔티티 대신 입자와 소리만 씁니다. force로 경기장 반대편에서도 보이게 합니다.
        for (int i = 0; i <= 12; i++)
            world.spawnParticle(Particle.FIREWORK, feet.clone().add(0, 0.5 + i * 0.5, 0), 2, 0.04, 0.04, 0.04, 0.01, null, true);
        Location burst = feet.clone().add(0, 7, 0);
        world.spawnParticle(Particle.FIREWORK, burst, 80, 0.4, 0.4, 0.4, 0.15, null, true);
        Color color = TAUNT_COLORS[ThreadLocalRandom.current().nextInt(TAUNT_COLORS.length)];
        world.spawnParticle(Particle.DUST, burst, 50, 1.2, 1.2, 1.2, 0.0, new Particle.DustOptions(color, 2f), true);
        // 음량 1 초과는 소리 크기가 아니라 들리는 거리(16블록 × 음량)를 늘립니다.
        world.playSound(feet, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 6f, 1f);
        world.playSound(burst, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 6f, 1f);
        return color;
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { leave(event.getPlayer()); }

    private void leave(Player player) {
        if (round == null || !participants.containsKey(player.getUniqueId())) return;
        if (round.isSeeker(player.getUniqueId())) restoreBlindness();
        round.leave(player.getUniqueId(), elapsed());
        restore(player);
        if (participants.isEmpty()) { finish(null); return; }
        endIfWon();
    }

    private boolean endIfWon() {
        if (round == null || round.winner() == RoundState.Winner.NONE) return false;
        if (countdownRemaining > 0) {
            finish("카운트다운 중 참가자가 부족해 시작을 취소했습니다.");
            return true;
        }
        finish(switch (round.winner()) {
            case SEEKER -> "술래 승리! 모든 플레이어를 찾았습니다.";
            case HIDERS -> round.mistakesRemaining() == 0 ? "술래의 HP가 소진되었습니다! 숨는 팀 승리!" : "숨는 팀 승리!";
            case TEST_FINISHED -> round.isSeekerTest() && round.mistakesRemaining() == 0
                    ? "술래 테스트: HP가 소진되어 테스트를 종료합니다." : "혼자 테스트를 종료합니다.";
            case NONE -> throw new IllegalStateException("Round has not finished");
        });
        return true;
    }

    private void finish(String message) {
        // 재진입해도 점수를 두 번 정산하지 않도록 먼저 비웁니다.
        RoundScore result = score;
        score = null;
        if (ticker != null) { ticker.cancel(); ticker = null; }
        if (movementTicker != null) { movementTicker.cancel(); movementTicker = null; }
        if (timerBar != null) { timerBar.removeAll(); timerBar = null; }
        if (message != null) announce(message);
        if (round != null && result != null && countdownRemaining == 0
                && (round.winner() == RoundState.Winner.SEEKER || round.winner() == RoundState.Winner.HIDERS)) {
            boolean record = scoringEligible && leaderboard != null;
            scoringEligible = false;
            settleScore(result, record);
        }
        restoreBlindness();
        for (UUID id : new ArrayList<>(participants.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) restore(player);
        }
        participants.clear();
        motions.clear();
        hitSpeedBoosts.clear();
        hitProtection.clear();
        if (animations != null) animations.clear();
        lastInside.clear();
        for (Entity mob : decoys) mob.remove();
        decoys.clear();
        sidebar = null;
        if (tauntMarkers != null) { tauntMarkers.clear(); tauntMarkers = null; }
        if (disasters != null) { disasters.clear(); disasters = null; }
        round = null;
        countdownRemaining = 0;
        boundary = null;
        activeMap = null;
    }

    /** 승패가 난 경기만 정산합니다. 중도 퇴장자는 제외하고, 먼저 잡혀 관전 중인 사람은 버틴 시간 점수를 받습니다. */
    private void settleScore(RoundScore result, boolean record) {
        if (round.winner() == RoundState.Winner.HIDERS) for (UUID id : round.hiders()) result.survived(id, round.chaseLimit());
        Set<UUID> winners = round.winner() == RoundState.Winner.SEEKER
                ? (round.seeker() == null ? Set.of() : Set.of(round.seeker())) : originalHiders;
        List<Map.Entry<String, Integer>> ranking = new ArrayList<>();
        for (UUID id : participants.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            int points = result.points(id, false, 0);
            boolean won = winners.contains(id);
            ranking.add(Map.entry(player.getName(), points));
            if (record) {
                leaderboard.record(id, player.getName(), points, won, round.isSeeker(id));
                tell(player, (won ? "§a승리! §f" : "") + "이번 판 +" + points + "점 / 누적 " + leaderboard.points(id) + "점 · /mh top");
            } else tell(player, "이번 판 " + points + "점 §7(관리자 개입 경기라 리더보드에 기록하지 않습니다)");
        }
        ranking.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        StringBuilder line = new StringBuilder("§e이번 판 점수 §f");
        for (int i = 0; i < ranking.size(); i++)
            line.append(i == 0 ? "" : " · ").append(i + 1).append("위 ").append(ranking.get(i).getKey()).append(' ').append(ranking.get(i).getValue()).append("점");
        announce(line.toString());
        if (record) {
            try { leaderboard.save(); } catch (java.io.IOException e) { getLogger().log(Level.SEVERE, "승점 저장 실패", e); }
        }
    }

    private Component scoreTitle() {
        return Component.text("몹 숨바꼭질 ", NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
                .append(Component.text(scoringEligible ? "이번 판 점수" : "연습 점수", scoringEligible ? NamedTextColor.WHITE : NamedTextColor.GRAY)
                        .decoration(TextDecoration.BOLD, false));
    }

    /** 오른쪽 점수판. 살아 있는 숨는 사람은 매초 버틴 시간만큼 오르고, 잡히면 그 시점 점수로 고정됩니다. */
    private void refreshScore() {
        if (sidebar == null || score == null || round == null) return;
        long chase = round.chaseElapsed(elapsed());
        Set<UUID> alive = round.hiders();
        Map<String, ScoreSidebar.Line> lines = new HashMap<>();
        for (UUID id : participants.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            boolean hiding = alive.contains(id);
            Component label = round.isSeeker(id) ? Component.text("술래 ", NamedTextColor.RED).append(Component.text(player.getName(), NamedTextColor.WHITE))
                    : hiding ? Component.text(player.getName(), NamedTextColor.GREEN)
                    : Component.text(player.getName() + " (탈락)", NamedTextColor.GRAY);
            lines.put(player.getName(), new ScoreSidebar.Line(label, score.points(id, hiding, chase)));
        }
        sidebar.title(scoreTitle());
        sidebar.update(lines);
    }

    private void restore(Player player) {
        Snapshot snapshot = participants.remove(player.getUniqueId());
        if (snapshot == null) return;
        player.clearTitle();
        if (timerBar != null) timerBar.removePlayer(player);
        removeDisguise(player);
        // 변신을 푼 뒤 원래 점수판으로 돌려야 LibsDisguises의 정리도 경기 점수판에서 끝납니다.
        if (sidebar != null) sidebar.hide(player);
        player.setGameMode(snapshot.gameMode());
        player.setWalkSpeed(snapshot.walkSpeed());
        player.setSprinting(snapshot.sprinting());
        player.setCollidable(snapshot.collidable());
        player.setFireTicks(snapshot.fireTicks());
        if (snapshot.health() != null) {
            clearSeekerHealth(player);
            if (!player.isDead()) player.setHealth(Math.min(snapshot.health().health(), maxHealth(player)));
            player.setHealthScale(snapshot.health().scale());
            player.setHealthScaled(snapshot.health().scaled());
        }
        player.setVelocity(new Vector());
        lastInside.remove(player.getUniqueId());
        restoring = true;
        try {
            Location destination = mapRotation != null && mapRotation.ready() ? mapRotation.lobby() : snapshot.location();
            if (!player.teleport(destination)) getLogger().warning("복귀 텔레포트가 취소되었습니다: " + player.getName());
            resendTeleport(player, destination, () -> !participants.containsKey(player.getUniqueId()));
        } finally { restoring = false; }
    }

    private void applyPreparationBlindness(Player seeker) {
        long seconds = elapsed();
        if (countdownRemaining == 0 && !round.isPreparing(seconds)) return;
        previousBlindness = seeker.getPotionEffect(PotionEffectType.BLINDNESS);
        blindnessStartedAt = System.nanoTime();
        int duration = countdownRemaining + (round.isPreparing(seconds) ? (int) round.phaseRemaining(seconds) : 0);
        seeker.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, duration * 20, 0, false, false));
        blindnessApplied = true;
    }

    private void restoreBlindness() {
        if (!blindnessApplied || round == null) return;
        // Remove our guard before changing effects, including an admin role switch during preparation.
        blindnessApplied = false;
        Player seeker = Bukkit.getPlayer(round.seeker());
        if (seeker != null) {
            seeker.removePotionEffect(PotionEffectType.BLINDNESS);
            if (previousBlindness != null) {
                int remaining = previousBlindness.getDuration() - (int) Math.min(Integer.MAX_VALUE,
                        Math.max(0, (System.nanoTime() - blindnessStartedAt) / 50_000_000L));
                if (previousBlindness.isInfinite()) seeker.addPotionEffect(previousBlindness);
                else if (remaining > 0) seeker.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, remaining,
                        previousBlindness.getAmplifier(), previousBlindness.isAmbient(), previousBlindness.hasParticles(), previousBlindness.hasIcon()));
            }
        }
        previousBlindness = null;
    }

    private void removeDisguise(Player player) {
        hitProtection.remove(player.getUniqueId());
        MobAnimation motion = motions.remove(player.getUniqueId());
        if (hitSpeedBoosts.remove(player.getUniqueId()) != null && motion != null)
            player.setWalkSpeed(configuredSpeed(player, motion.type));
        if (motion != null && motion.type == EntityType.CHICKEN) {
            PotionEffect fall = player.getPotionEffect(PotionEffectType.SLOW_FALLING);
            if (fall != null && chickenFallEffect(fall)) player.removePotionEffect(PotionEffectType.SLOW_FALLING);
        }
        if (animations != null) animations.remove(player);
        if (disguises == null) return;
        try { disguises.remove(player); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError e) { getLogger().log(Level.SEVERE, "변신 해제 실패: " + player.getName(), e); }
    }

    private void spawnDecoy(Location position, EntityType type) {
        Mob mob = position.getWorld().spawn(position, type.getEntityClass().asSubclass(Mob.class),
                org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM, false, ai -> {
                    ai.setPersistent(false);
                    DecoyBehavior.prepare(ai, getServer().getMobGoals(), player -> motions.containsKey(player.getUniqueId()));
                    // 주민 AI가 좀비·허스크 AI를 보고 도망치지 않게 적 감지만 끕니다. 실패해도 소환은 계속합니다.
                    if (ai instanceof org.bukkit.entity.Villager villager && villagerCalm != null) {
                        try { villagerCalm.calm(villager); }
                        catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                            getLogger().log(Level.WARNING, "주민 AI 좀비 감지 끄기 실패. 이 주민은 좀비를 보면 도망칠 수 있습니다.", e);
                        }
                    }
                });
        decoys.add(mob);
        if (!mob.isValid()) throw new IllegalStateException("AI 소환이 취소되었습니다: " + type);
        lastInside.put(mob.getUniqueId(), position.clone());
    }

    private void ensureMatchingDecoy(EntityType type) {
        if (MobCatalog.hostile(type) && center.getWorld().getDifficulty() == Difficulty.PEACEFUL)
            throw new IllegalStateException("적대 몹을 사용하려면 월드 난이도를 쉬움 이상으로 설정하세요.");
        if (decoys.stream().anyMatch(mob -> mob.isValid() && mob.getType() == type)) return;
        Location position = findSpot(type);
        if (position == null) throw new IllegalStateException("동일 종류 AI를 소환할 안전한 위치가 없습니다.");
        spawnDecoy(randomFacing(position), type);
    }

    // 모두 남쪽(yaw 0)을 보고 시작하면 제자리에 있는 숨는 플레이어만 AI와 다른 방향으로 남습니다.
    private static Location randomFacing(Location position) {
        position.setYaw(ThreadLocalRandom.current().nextFloat() * 360f);
        return position;
    }

    private Location findSpot() { return findSpot(null); }

    static final double SPOT_SPACING = 1.5;

    /** 이미 정한 자리와 수평으로 1.5블록 이상 떨어진 소환 위치. 좁은 경기장에서는 몇 번 시도한 뒤 마지막 후보를 그대로 씁니다. */
    private Location spacedSpot(EntityType type, List<Location> taken) {
        Location candidate = null;
        for (int attempt = 0; attempt < 12; attempt++) {
            candidate = findSpot(type);
            if (candidate == null) return null;
            if (spaced(candidate, taken)) break;
        }
        taken.add(candidate);
        return candidate;
    }

    static boolean spaced(Location candidate, List<Location> taken) {
        for (Location other : taken) {
            if (other.getWorld() != candidate.getWorld()) continue;
            double dx = other.getX() - candidate.getX(), dz = other.getZ() - candidate.getZ();
            if (dx * dx + dz * dz < SPOT_SPACING * SPOT_SPACING) return false;
        }
        return true;
    }

    private Location findSpot(EntityType type) {
        World world = center.getWorld();
        int preferredY = center.getBlockY();
        int minY = Math.max(world.getMinHeight() + 1, activeMap != null ? activeMap.minSpawnY() : getConfig().getInt("arena.spawn-min-y", preferredY - 5));
        int maxY = Math.min(world.getMaxHeight() - 2, activeMap != null ? activeMap.maxSpawnY() : getConfig().getInt("arena.spawn-max-y", preferredY + 5));
        if (boundary instanceof CuboidBoundary box) {
            minY = Math.max(minY, box.minY());
            maxY = Math.min(maxY, box.maxY() - 2);
            for (int attempt = 0; attempt < 100; attempt++) {
                int x = ThreadLocalRandom.current().nextInt(box.minX(), box.maxX());
                int z = ThreadLocalRandom.current().nextInt(box.minZ(), box.maxZ());
                Location point = SpawnFloor.find(world, x + 0.5, z + 0.5, minY, maxY, preferredY, p -> inside(p) && safe(p, type));
                if (point != null) return point;
            }
            return null;
        }
        for (int attempt = 0; attempt < 100; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double distance = Math.sqrt(ThreadLocalRandom.current().nextDouble()) * (radius - 2);
            double x = center.getX() + Math.cos(angle) * distance, z = center.getZ() + Math.sin(angle) * distance;
            Location position = SpawnFloor.find(world, x, z, minY, maxY, preferredY, p -> inside(p) && safe(p, type));
            if (position != null) return position;
        }
        return null;
    }

    private boolean safe(Location position, EntityType type) {
        double[] size = type == null ? null : MobCatalog.clearance(type);
        return size == null ? safe(position) : SafeLocation.isSafe(position, size[0], size[1]);
    }

    private EntityType disguiseOf(Player player) {
        MobAnimation motion = motions.get(player.getUniqueId());
        return motion == null ? null : motion.type;
    }

    // 닭은 날개를 퍼덕이며 천천히 내려옵니다. 입자·아이콘 없는 짧은 효과를 갱신해 서버가 꺼져도 오래 남지 않게 합니다.
    private static final int CHICKEN_FALL_TICKS = 200;

    private static void chickenFall(Player player, long tick) {
        PotionEffect current = player.getPotionEffect(PotionEffectType.SLOW_FALLING);
        // 플레이어가 원래 가진 느린 낙하(물약 등)는 건드리지 않습니다.
        if (current == null || (tick % 100 == 0 && chickenFallEffect(current)))
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, CHICKEN_FALL_TICKS, 0, false, false, false));
    }

    static boolean chickenFallEffect(PotionEffect effect) {
        return effect.getAmplifier() == 0 && !effect.isAmbient() && !effect.hasParticles() && !effect.hasIcon()
                && !effect.isInfinite() && effect.getDuration() <= CHICKEN_FALL_TICKS;
    }

    private static String mobNames() {
        return String.join("|", SAFE_MOBS.stream().map(type -> type.name().toLowerCase(java.util.Locale.ROOT)).toList());
    }

    private boolean safe(Location position) {
        return SafeLocation.isSafe(position);
    }

    private boolean inside(Location location) {
        return boundary != null && location.getWorld() == center.getWorld()
                && boundary.contains(location.getX(), location.getY(), location.getZ());
    }

    private Location boundaryReturn(Location attempted, Location fallback) { return boundaryReturn(attempted, fallback, null); }

    private Location boundaryReturn(Location attempted, Location fallback, EntityType type) {
        if (attempted.getWorld() == center.getWorld()) {
            ArenaBoundary.Point point = boundary.inset(attempted.getX(), attempted.getY(), attempted.getZ());
            Location corrected = new Location(center.getWorld(), point.x(), point.y(), point.z());
            if (inside(corrected) && safe(corrected, type)) return corrected;
            // 경계에 높낮이가 있으면 가까운 안전한 바닥을 찾습니다.
            for (int offset : new int[] {1, -1, 2, -2}) {
                Location candidate = corrected.clone().add(0, offset, 0);
                if (inside(candidate) && safe(candidate, type)) return candidate;
            }
        }
        // 위험한 지형/다른 월드로의 이동은 마지막 안전한 바닥 위치로 되돌립니다.
        if (fallback != null && inside(fallback) && safe(fallback, type)) return fallback.clone();
        return inside(center) && safe(center, type) ? center.clone() : findSpot(type);
    }
    private long elapsed() { return countdownRemaining > 0 ? 0 : Math.max(0, (System.nanoTime() - startedAt) / 1_000_000_000L); }
    boolean running() { return round != null; }
    boolean participating(Player player) { return participants.containsKey(player.getUniqueId()); }
    boolean sandboxRunning() { return round != null && round.sandbox(); }
    String arenaDescription() { return (activeMap == null ? "" : activeMap.name() + " / ") + (boundary instanceof CuboidBoundary box ? "영역 " + box.dimensions() : "반경 " + radius + "블록"); }
    List<Leaderboard.Entry> topScores() { return leaderboard.top(); }
    void showLeaderboard(CommandSender sender) {
        int seconds = clamp(getConfig().getInt("score.seconds-per-point", 10), 0, 600);
        tell(sender, "§e누적 점수 리더보드 §7(" + (seconds == 0 ? "시간 점수 끔" : "숨기: " + seconds + "초 버틸 때마다 1점 / 술래: 잡은 사람의 남은 시간")
                + " · 테스트·샌드박스 제외)");
        List<Leaderboard.Entry> top = leaderboard.top();
        if (top.isEmpty()) tell(sender, "아직 기록된 점수가 없습니다.");
        for (int i = 0; i < top.size(); i++) tell(sender, (i + 1) + "위 " + top.get(i).name() + " — " + leaderboardLine(top.get(i)));
    }
    static String leaderboardLine(Leaderboard.Entry entry) {
        return entry.points() + "점 · 승리 " + entry.wins() + " (술래 " + entry.seekerWins() + " / 숨기 " + entry.hiderWins() + ")";
    }
    private EntityType configuredMob(Player player, List<EntityType> pool) {
        String value = getConfig().getString("players." + player.getUniqueId() + ".mob", "RANDOM");
        try { EntityType type = EntityType.valueOf(value); if (SAFE_MOBS.contains(type)) return type; } catch (IllegalArgumentException ignored) { }
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }
    private int configuredLives(Player player) {
        return clamp(getConfig().getInt("players." + player.getUniqueId() + ".hits", getConfig().getInt("hider-hit-limit", 2)), 1, 20);
    }
    private int configuredSeekerLives(Player player) {
        return clamp(getConfig().getInt("players." + player.getUniqueId() + ".hits", getConfig().getInt("seeker-mistake-limit", 20)), 1, 20);
    }
    int displayedLives(Player player) {
        if (round != null && participants.containsKey(player.getUniqueId()))
            return round.isSeeker(player.getUniqueId()) ? round.mistakeLimit() : round.maximumHiderHits(player.getUniqueId());
        return configuredLives(player);
    }
    private float configuredSpeed(Player player, EntityType type) {
        double multiplier = configuredNumber("players." + player.getUniqueId() + ".speed", configuredNumber("speed-multiplier", 1));
        double base = Math.max(0.01, Math.min(0.5, MobMovement.walkSpeed(type) * multiplier));
        HitSpeedBoost boost = hitSpeedBoosts.get(player.getUniqueId());
        if (boost != null && motionTick < boost.expiresAt())
            return (float) Math.min(0.5, Math.max(base, Math.max(base * boost.multiplier(), boost.minimumSpeed())));
        return (float) base;
    }
    private void applyHitSpeedBoost(Player player) {
        MobAnimation motion = motions.get(player.getUniqueId());
        if (round == null || motion == null || !round.hiders().contains(player.getUniqueId())) return;
        int seconds = clamp(getConfig().getInt("hit-speed-boost.seconds", 3), 0, 10);
        if (seconds == 0) return;
        double multiplier = finiteClamp(getConfig().getDouble("hit-speed-boost.multiplier", 2), 2, 1, 4);
        double minimum = finiteClamp(getConfig().getDouble("hit-speed-boost.minimum-walk-speed", 0.30), 0.30, 0, 0.5);
        // 현재 속도를 곱하지 않고 매번 설정된 기본 속도에서 계산해 연속 피격 중첩을 막습니다.
        hitSpeedBoosts.put(player.getUniqueId(), new HitSpeedBoost(motionTick + seconds * 20L, multiplier, minimum));
        player.setWalkSpeed(configuredSpeed(player, motion.type));
        tell(player, "피격 가속! " + seconds + "초 동안 빨라집니다. 지금 도망치세요!");
    }
    private void expireHitSpeedBoosts() {
        hitProtection.values().removeIf(expiresAt -> motionTick >= expiresAt);
        var iterator = hitSpeedBoosts.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (motionTick < entry.getValue().expiresAt()) continue;
            UUID id = entry.getKey();
            iterator.remove();
            Player player = Bukkit.getPlayer(id);
            MobAnimation motion = motions.get(id);
            // 별도 예약 작업이 없어 다음 라운드나 변경된 역할의 속도를 뒤늦게 덮어쓰지 않습니다.
            if (player != null && motion != null && round != null && round.hiders().contains(id))
                player.setWalkSpeed(configuredSpeed(player, motion.type));
        }
    }
    private static double finiteClamp(double value, double fallback, double min, double max) {
        return Math.max(min, Math.min(max, Double.isFinite(value) ? value : fallback));
    }
    double configuredNumber(String key, double fallback) {
        double value = getConfig().getDouble(key, fallback);
        return Double.isFinite(value) ? value : fallback;
    }
    private int tauntPoints() { return clamp(getConfig().getInt("taunt.points", 5), 0, 100); }
    private int tauntCooldownSeconds() { return clamp(getConfig().getInt("taunt.cooldown-seconds", 10), 5, 600); }
    private int tauntGlowSeconds() { return clamp(getConfig().getInt("taunt.glow-seconds", 5), 0, 15); }

    // 관리자 패널 초기화 대상. 경기장·소환 높이·개인 설정·리더보드와 파일 전용 점수·가속 설정은 유지합니다.
    private static final Map<String, Object> PANEL_DEFAULTS = Map.of(
            "hider-hit-limit", 2, "seeker-mistake-limit", 20, "speed-multiplier", 1.0, "round-seconds", 180,
            "hide-seconds", 15, "decoy-count", 25, "taunt.points", 5, "taunt.cooldown-seconds", 10, "taunt.glow-seconds", 5);

    void adminResetSettings(Player admin) {
        if (round != null) { tell(admin, "게임을 종료한 뒤 설정을 초기화하세요."); return; }
        PANEL_DEFAULTS.forEach(getConfig()::set);
        getConfig().set("mob-types", SAFE_MOBS.stream().map(Enum::name).toList());
        getConfig().set("fixed-seeker", null);
        saveConfig();
        tell(admin, "게임 설정을 기본값으로 초기화했습니다. 경기장·개인 설정·리더보드는 그대로입니다.");
    }

    void adminNumber(Player admin, String key, double value) {
        if (!Double.isFinite(value)) { tell(admin, "유효한 숫자로 설정하세요."); return; }
        // 삼항 연산자는 int를 double로 다시 승격하므로 정수 설정을 따로 저장합니다.
        if (key.equals("speed-multiplier")) getConfig().set(key, value); else getConfig().set(key, (int) value);
        saveConfig();
        boolean taunt = key.startsWith("taunt.");
        if (round != null && (taunt || key.equals("hider-hit-limit") || key.equals("seeker-mistake-limit") || key.equals("speed-multiplier"))) {
            scoringEligible = false;
            // 도발은 진행 중인 쿨타임에도 바로 적용합니다. 점수 0이면 F가 손 바꾸기로 돌아갑니다.
            if (taunt && score != null) score.setTaunt(tauntPoints(), tauntCooldownSeconds() * 20L);
            if (key.equals("hider-hit-limit")) {
                round.setHiderHitLimit((int) value);
                for (UUID id : round.hiders()) { Player player = Bukkit.getPlayer(id); if (player != null) { round.setHiderLives(id, configuredLives(player)); applyHiderHealth(player); } }
            }
            if (key.equals("seeker-mistake-limit") && round.seeker() != null) {
                Player player = Bukkit.getPlayer(round.seeker());
                if (player != null) {
                    try { recoverPosition(player); }
                    catch (RuntimeException e) { failure(admin, e); finish("체력 변경 실패로 상태를 복구했습니다."); return; }
                    round.resetSeekerLives(configuredSeekerLives(player));
                    player.setGameMode(GameMode.ADVENTURE); applySeekerHealth(player);
                }
            }
            if (key.equals("speed-multiplier")) for (var entry : motions.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey()); if (player != null) player.setWalkSpeed(configuredSpeed(player, entry.getValue().type));
            }
            updateTimerBar(elapsed()); tell(admin, "설정을 즉시 적용했습니다. 관리자 개입 경기는 승점을 기록하지 않습니다.");
        } else tell(admin, "설정을 저장했습니다. 다음 게임부터 적용합니다.");
    }
    void adminMob(Player admin, Player player, EntityType type) {
        if (!player.isOnline()) { tell(admin, "플레이어가 퇴장했습니다."); return; }
        if (type != null && !SAFE_MOBS.contains(type)) return;
        getConfig().set("players." + player.getUniqueId() + ".mob", type == null ? "RANDOM" : type.name()); saveConfig();
        if (round == null || !participants.containsKey(player.getUniqueId()) || round.isSeeker(player.getUniqueId())) {
            tell(admin, player.getName() + "님의 다음 변신을 저장했습니다."); return;
        }
        scoringEligible = false;
        try { morphPlayer(player, type == null ? configuredMob(player, mobPool()) : type); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError e) { failure(admin, e); finish("변신 변경 실패로 상태를 복구했습니다."); }
    }
    private void morphPlayer(Player player, EntityType type) throws ReflectiveOperationException {
        ensureMatchingDecoy(type);
        removeDisguise(player);
        recoverPosition(player, type);
        round.reviveHider(player.getUniqueId());
        round.setHiderLives(player.getUniqueId(), configuredLives(player));
        player.setGameMode(GameMode.ADVENTURE);
        applyHiderHealth(player);
        disguises.disguise(player, type, true);
        animations.track(player, type);
        player.setWalkSpeed(configuredSpeed(player, type));
        player.setSprinting(false);
        motions.put(player.getUniqueId(), new MobAnimation(type, player.getLocation(), AnimationBridge.grounded(player)));
        tell(player, "관리자가 변신을 " + type + "(으)로 변경했습니다. 피격 기회가 복구되었습니다.");
        updateTimerBar(elapsed());
    }
    void adminLives(Player admin, Player player, int value) {
        if (!player.isOnline()) { tell(admin, "플레이어가 퇴장했습니다."); return; }
        getConfig().set("players." + player.getUniqueId() + ".hits", value); saveConfig();
        if (round == null || !participants.containsKey(player.getUniqueId())) return;
        scoringEligible = false;
        if (round.isSeeker(player.getUniqueId())) {
            try { recoverPosition(player); }
            catch (RuntimeException e) { failure(admin, e); finish("체력 변경 실패로 상태를 복구했습니다."); return; }
            round.resetSeekerLives(value); player.setGameMode(GameMode.ADVENTURE); applySeekerHealth(player);
        } else if (round.hiders().contains(player.getUniqueId())) { round.setHiderLives(player.getUniqueId(), value); applyHiderHealth(player); }
        else {
            // 탈락자 부활은 adminMob을 거치지 않습니다. 거치면 이번에 뽑힌 몹이 개인 고정 몹으로 저장됩니다.
            try { morphPlayer(player, configuredMob(player, mobPool())); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError e) { failure(admin, e); finish("체력 변경 실패로 상태를 복구했습니다."); return; }
        }
        if (round != null) updateTimerBar(elapsed());
    }
    void adminSpeed(Player player, double value) {
        if (!player.isOnline() || !Double.isFinite(value)) return;
        getConfig().set("players." + player.getUniqueId() + ".speed", value); saveConfig();
        MobAnimation motion = motions.get(player.getUniqueId());
        if (motion != null) { scoringEligible = false; player.setWalkSpeed(configuredSpeed(player, motion.type)); }
    }
    void adminSeeker(Player admin, Player chosen) {
        if (chosen != null && !chosen.isOnline()) { tell(admin, "플레이어가 퇴장했습니다."); return; }
        getConfig().set("fixed-seeker", chosen == null ? null : chosen.getUniqueId().toString()); saveConfig();
        if (chosen == null || round == null) { tell(admin, chosen == null ? "다음 게임 술래를 랜덤으로 설정했습니다." : chosen.getName() + "님을 다음 술래로 지정했습니다."); return; }
        if (!participants.containsKey(chosen.getUniqueId())) { tell(admin, "현재 참가자가 아니므로 다음 게임부터 적용합니다."); return; }
        scoringEligible = false;
        Player old = round.seeker() == null ? null : Bukkit.getPlayer(round.seeker());
        restoreBlindness();
        round.changeSeeker(chosen.getUniqueId());
        round.resetSeekerLives(configuredSeekerLives(chosen));
        removeDisguise(chosen);
        try { recoverPosition(chosen); }
        catch (RuntimeException e) { failure(admin, e); finish("술래 변경 실패로 상태를 복구했습니다."); return; }
        chosen.setGameMode(GameMode.ADVENTURE);
        chosen.setWalkSpeed(participants.get(chosen.getUniqueId()).walkSpeed());
        applySeekerHealth(chosen);
        applyPreparationBlindness(chosen);
        if (old != null && !old.equals(chosen)) {
            HealthSnapshot health = participants.get(old.getUniqueId()).health();
            clearSeekerHealth(old); old.setHealth(Math.min(health.health(), maxHealth(old)));
            old.setHealthScale(health.scale()); old.setHealthScaled(health.scaled());
            try { morphPlayer(old, configuredMob(old, mobPool())); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError e) { failure(admin, e); finish("술래 변경 실패로 상태를 복구했습니다."); return; }
        }
        announce("관리자가 술래를 " + chosen.getName() + "님으로 변경했습니다. 이 경기는 승점에서 제외됩니다.");
        updateTimerBar(elapsed());
    }
    void adminReset(Player admin, Player player) {
        if (!player.isOnline()) { tell(admin, "플레이어가 퇴장했습니다."); return; }
        getConfig().set("players." + player.getUniqueId(), null); saveConfig();
        if (round == null || !participants.containsKey(player.getUniqueId())) return;
        scoringEligible = false;
        if (round.isSeeker(player.getUniqueId())) {
            try { recoverPosition(player); }
            catch (RuntimeException e) { failure(admin, e); finish("설정 초기화 실패로 상태를 복구했습니다."); return; }
            round.resetSeekerLives(configuredSeekerLives(player));
            player.setGameMode(GameMode.ADVENTURE); applySeekerHealth(player); updateTimerBar(elapsed());
        } else {
            try { morphPlayer(player, configuredMob(player, mobPool())); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError e) { failure(admin, e); finish("설정 초기화 실패로 상태를 복구했습니다."); }
        }
    }
    private void recoverPosition(Player player) { recoverPosition(player, null); }

    private void recoverPosition(Player player, EntityType type) {
        if (inside(player.getLocation()) && safe(player.getLocation(), type)) return;
        Location destination = findSpot(type);
        if (destination == null) throw new IllegalStateException("안전한 복귀 위치가 없습니다.");
        restoring = true;
        try {
            if (!player.teleport(destination)) throw new IllegalStateException("복귀 텔레포트가 취소되었습니다: " + player.getName());
        } finally { restoring = false; }
        player.setVelocity(new Vector());
        lastInside.put(player.getUniqueId(), destination.clone());
    }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("mobhide.admin")) return true;
        tell(sender, "관리자 권한이 필요합니다."); return false;
    }
    private void failure(CommandSender sender, Throwable e) {
        getLogger().log(Level.SEVERE, "게임 준비 실패", e);
        tell(sender, "게임 준비에 실패했습니다. 서버 로그와 LibsDisguises 호환성을 확인하세요.");
    }
    private static void tell(CommandSender sender, String message) { sender.sendMessage(PREFIX + message); }
    /** 재해 이벤트가 보는 경기장. 몸은 관전자가 아닌 참가자와 살아 있는 AI입니다. */
    private final class DisasterStage implements Disasters.Stage {
        public World world() { return center.getWorld(); }
        public Location randomSpot() { return findSpot(); }
        public boolean inside(Location location) { return MobHidePlugin.this.inside(location); }
        public List<Entity> bodies() {
            List<Entity> bodies = new ArrayList<>();
            for (UUID id : participants.keySet()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && player.getGameMode() != GameMode.SPECTATOR) bodies.add(player);
            }
            for (Entity mob : decoys) if (mob.isValid()) bodies.add(mob);
            return bodies;
        }
        public List<Player> audience() {
            List<Player> players = new ArrayList<>();
            for (UUID id : participants.keySet()) { Player player = Bukkit.getPlayer(id); if (player != null) players.add(player); }
            return players;
        }
    }

    private void announce(String message) {
        for (UUID id : participants.keySet()) { Player player = Bukkit.getPlayer(id); if (player != null) tell(player, message); }
    }
}
