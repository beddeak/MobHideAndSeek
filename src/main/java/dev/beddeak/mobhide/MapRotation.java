package dev.beddeak.mobhide;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

/** Dedicated waiting lobby, persistent map catalogue and non-repeating random draws. */
final class MapRotation implements Listener {
    static final String WORLD = "mobhide_maps";
    private final MobHidePlugin plugin;
    private final Path file;
    private YamlConfiguration data;
    private Location lobby;
    private List<ArenaMap> maps=List.of();
    private String previous;
    private boolean ready;

    MapRotation(MobHidePlugin plugin) {
        this.plugin=plugin;
        file=plugin.getDataFolder().toPath().resolve("maps.yml");
    }
    boolean ready() { return ready; }
    Location lobby() { return lobby == null ? null : lobby.clone(); }
    List<ArenaMap> maps() { return maps; }
    void initialize() {
        try {
            data=new YamlConfiguration();
            if(Files.exists(file)) data.load(file.toFile());
            // Only our named, dedicated world gets a custom generator. Existing village blocks stay intact.
            World world=new WorldCreator(WORLD).generator(new ThemeWorldGenerator()).generateStructures(false).createWorld();
            if(world==null) throw new IllegalStateException("로비 월드 생성 실패");
            world.setDifficulty(Difficulty.EASY);
            world.setGameRule(GameRule.DO_MOB_SPAWNING,false);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE,false);
            world.setGameRule(GameRule.DO_FIRE_TICK,false);
            world.setGameRule(GameRule.MOB_GRIEFING,false);
            world.setGameRule(GameRule.RANDOM_TICK_SPEED,0);
            world.setGameRule(GameRule.SPAWN_RADIUS,0);
            world.setTime(6000); world.setStorm(false); world.setThundering(false);
            lobby=new Location(world,0.5,64,7.5,180,0);
            world.setSpawnLocation(lobby);
            if(!data.contains("maps")) {
                // A fresh install must also prefer the full catalogue over the last selected arena.
                int shipped=importLegacyMaps();
                ConfigurationSection legacy=plugin.getConfig().getConfigurationSection("arena");
                if(shipped==0 && legacy!=null && legacy.contains("world")) {
                    ConfigurationSection village=data.createSection("maps.village",legacy.getValues(true));
                    village.set("name","햇살 마을");
                    village.set("radius",plugin.getConfig().getInt("arena-radius",20));
                }
                builtin("desert","사막 시장",160);
                builtin("snow","눈 덮인 벌목장",320);
                save();
            }
            // The old travel pack contained farm/cave/village even when config.yml only selected village.
            int imported=importLegacyMaps();
            if(imported>0) { save(); plugin.getLogger().info("기존 맵 "+imported+"개를 랜덤 목록에 추가했습니다."); }
            reloadMaps();
            // Pre-generate all playable chunks once so the first round doesn't stall on terrain creation.
            for(int cx=-2;cx<=2;cx++) for(int cz=-2;cz<=2;cz++) world.getChunkAt(cx,cz);
            for(int center:new int[]{160,320})
                for(int cx=(center-40)>>4;cx<=(center+40)>>4;cx++)
                    for(int cz=-3;cz<=2;cz++) world.getChunkAt(cx,cz);
            if(!SafeLocation.isSafe(lobby)) throw new IllegalStateException("로비 바닥이 안전하지 않습니다");
            labels(world);
            world.save();
            ready=true;
            for(Player player:Bukkit.getOnlinePlayers()) sendToLobby(player);
            plugin.getLogger().info("맵 준비 완료: " + String.join(", ",maps.stream().map(ArenaMap::name).toList()) + " / 접속 위치: 로비");
        } catch(Exception e) {
            ready=false;
            plugin.getLogger().log(Level.SEVERE,"맵/로비 준비 실패. /mh start를 잠급니다. maps.yml과 서버 로그를 확인하세요.",e);
        }
    }
    private int importLegacyMaps() throws IOException {
        int imported=0;
        for(World legacyWorld:Bukkit.getWorlds())
            if(!legacyWorld.getName().equals(WORLD)) imported+=LegacyMapImport.merge(data,legacyWorld.getWorldFolder().toPath(),legacyWorld.getName());
        return imported;
    }
    private void builtin(String id,String name,int x) {
        ConfigurationSection s=data.createSection("maps."+id);
        s.set("name",name); s.set("world",WORLD); s.set("x",x+0.5); s.set("y",64); s.set("z",32.5); s.set("yaw",180);
        s.set("shape","cuboid"); s.set("min",List.of(x-39,64,-39)); s.set("max",List.of(x+40,94,40));
        s.set("spawn-min-y",64); s.set("spawn-max-y",64);
    }
    private void reloadMaps() {
        List<ArenaMap> loaded=new ArrayList<>();
        ConfigurationSection root=data.getConfigurationSection("maps");
        if(root!=null) for(String id:root.getKeys(false)) {
            ConfigurationSection s=root.getConfigurationSection(id);
            if(s!=null && s.getBoolean("enabled",true)) loaded.add(ArenaMap.read(id,s));
        }
        if(loaded.isEmpty()) throw new IllegalArgumentException("선택 가능한 맵이 없습니다");
        maps=List.copyOf(loaded);
    }
    static List<ArenaMap> candidates(List<ArenaMap> maps,String previous) {
        if(maps.size()<=1) return maps;
        return maps.stream().filter(map->!map.id().equals(previous)).toList();
    }
    ArenaMap choose() {
        if(!ready) throw new IllegalStateException("로비/맵 준비 중이거나 설정 오류가 있습니다. 서버 로그를 확인하세요.");
        List<ArenaMap> choices=candidates(maps,previous);
        return choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
    }
    void started(ArenaMap map) { previous=map.id(); }
    void saveArena(String id,String name) throws IOException {
        if(!ready || !id.matches("[a-z0-9_-]{1,32}") || name.isBlank() || name.length()>32)
            throw new IllegalArgumentException("/mh mapsave <영문ID> <맵 이름(32자 이하)>");
        ConfigurationSection arena=plugin.getConfig().getConfigurationSection("arena");
        if(arena==null) throw new IllegalArgumentException("먼저 /mh setarena 로 영역을 지정하세요");
        YamlConfiguration next=new YamlConfiguration();
        next.createSection("arena",arena.getValues(true));
        next.set("arena.name",name); next.set("arena.radius",plugin.getConfig().getInt("arena-radius",20));
        ArenaMap checked=ArenaMap.read(id,next.getConfigurationSection("arena"));
        if(!SafeLocation.isSafe(checked.start())) throw new IllegalArgumentException("술래 시작점이 안전하지 않습니다");
        data.set("maps."+id,null);
        data.createSection("maps."+id,next.getConfigurationSection("arena").getValues(true));
        save(); reloadMaps();
    }
    private void save() throws IOException {
        Path temp=file.resolveSibling("maps.yml.tmp");
        data.save(temp.toFile());
        Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);
    }
    void sendToLobby(Player player) {
        if(lobby==null || !player.isOnline()) return;
        player.setFallDistance(0); player.setFireTicks(0);
        if(!player.teleport(lobby())) plugin.getLogger().warning("로비 이동 취소: "+player.getName());
    }
    private void labels(World world) {
        NamespacedKey key=new NamespacedKey(plugin,"lobby_label");
        for(var entity:world.getEntities()) if(entity.getPersistentDataContainer().has(key,PersistentDataType.BYTE)) entity.remove();
        label(world,key,0,68,0,"§6§l몹 숨바꼭질 로비\n§f게임 시작 시 맵을 자동으로 추첨합니다\n§e/mh maps §7· §e/mh top\n§7관리자: /mh start 또는 /mh admin");
        label(world,key,-12,67,-13,"§a기존 맵\n§f농장 · 수정빛 폐광\n§f노을빛 마을");
        label(world,key,0,67,-13,"§6추가 테마\n§f사막 시장\n§f눈 덮인 벌목장");
        label(world,key,12,67,-13,"§b추첨 가능한 맵 "+maps.size()+"개\n§f"+String.join("\n",maps.stream().map(ArenaMap::name).toList()));
    }
    private void label(World world,NamespacedKey key,int x,int y,int z,String text) {
        world.spawn(new Location(world,x+.5,y,z+.5),TextDisplay.class,e->{
            e.text(LegacyComponentSerializer.legacySection().deserialize(text)); e.setBillboard(Display.Billboard.CENTER); e.setPersistent(false);
            e.setSeeThrough(false); e.setShadowed(true); e.setLineWidth(300);
            e.getPersistentDataContainer().set(key,PersistentDataType.BYTE,(byte)1);
        });
    }
    @EventHandler public void spawn(PlayerSpawnLocationEvent event) { if(ready) event.setSpawnLocation(lobby()); }
    @EventHandler public void join(PlayerJoinEvent event) {
        if(ready) Bukkit.getScheduler().runTask(plugin,()->{
            if(plugin.participating(event.getPlayer())) return;
            sendToLobby(event.getPlayer());
            event.getPlayer().sendMessage("§6[몹 숨바꼭질] §f로비에 도착했습니다. 게임이 시작되면 선정된 맵으로 이동합니다.");
        });
    }
    @EventHandler public void respawn(PlayerRespawnEvent event) { if(ready) event.setRespawnLocation(lobby()); }
    private boolean inLobby(Location p) { return lobby!=null && p.getWorld()==lobby.getWorld() && Math.abs(p.getX())<=22 && Math.abs(p.getZ())<=22; }
    @EventHandler(priority=EventPriority.HIGHEST) public void damage(EntityDamageEvent event) {
        if(ready && event.getEntity() instanceof Player p && inLobby(p.getLocation())) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void move(PlayerMoveEvent event) {
        // 경기 참가자는 경기장 경계 처리가 맡습니다. 여기서 로비 좌표로 바꾸면 경기장 반대편 벽으로 끌려갑니다.
        if(plugin.participating(event.getPlayer())) return;
        if(ready && event.getFrom().getWorld()==lobby.getWorld() && event.getTo()!=null && event.getTo().getY()<62) {
            event.setTo(lobby()); event.getPlayer().setFallDistance(0);
        }
    }
    private boolean protectedBuild(Player player) {
        return ready && player.getWorld()==lobby.getWorld()
                && !(player.hasPermission("mobhide.admin") && player.getGameMode()==GameMode.CREATIVE);
    }
    @EventHandler(ignoreCancelled=true) public void breakBlock(BlockBreakEvent event) {
        if(protectedBuild(event.getPlayer())) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void placeBlock(BlockPlaceEvent event) {
        if(protectedBuild(event.getPlayer())) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void explode(EntityExplodeEvent event) {
        if(ready && event.getLocation().getWorld()==lobby.getWorld()) event.blockList().clear();
    }
    @EventHandler(ignoreCancelled=true) public void explodeBlock(BlockExplodeEvent event) {
        if(ready && event.getBlock().getWorld()==lobby.getWorld()) event.blockList().clear();
    }
}
