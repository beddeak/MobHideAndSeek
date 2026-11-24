package dev.beddeak.mobhide;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Reviewed additive cover plans. Existing structures are never replaced by the retrofit command. */
final class MapRefinements {
    record Plan(String id,String world,int minX,int minZ,int size,List<MapBlueprint.Box> boxes) {}
    private static final List<Plan> PLANS=load();
    static List<Plan> plans() { return PLANS; }
    private static List<Plan> load() {
        try(var stream=MapRefinements.class.getResourceAsStream("/map-refinements.yml")) {
            if(stream==null) throw new IllegalStateException("map-refinements.yml missing");
            var yaml=new YamlConfiguration(); yaml.load(new InputStreamReader(stream,StandardCharsets.UTF_8));
            List<Plan> result=new ArrayList<>();
            for(String id:yaml.getKeys(false)) {
                var s=yaml.getConfigurationSection(id);
                List<MapBlueprint.Box> boxes=new ArrayList<>();
                for(Object value:s.getList("boxes",List.of())) {
                    List<?> b=(List<?>)value;
                    boxes.add(new MapBlueprint.Box(n(b,0),n(b,1),n(b,2),n(b,3),n(b,4),n(b,5),Material.valueOf(b.get(6).toString())));
                }
                result.add(new Plan(id,s.getString("world"),s.getInt("min-x"),s.getInt("min-z"),s.getInt("size"),List.copyOf(boxes)));
            }
            return List.copyOf(result);
        } catch(Exception e) { throw new IllegalStateException("Invalid refinement plans",e); }
    }
    private static int n(List<?> list,int i) { return ((Number)list.get(i)).intValue(); }
    static boolean mayReplace(Material current,Material wanted) {
        return current==Material.AIR || current==Material.CAVE_AIR || current==Material.VOID_AIR || current==wanted;
    }
    static boolean matches(Plan plan,String world,ArenaRegion boundary) {
        int inset=plan.world().equals(MapRotation.WORLD)?1:0;
        return world.equals(plan.world()) && boundary instanceof CuboidBoundary b
                && b.minX()==plan.minX()+inset && b.minZ()==plan.minZ()+inset
                && b.maxX()==plan.minX()+plan.size()-inset && b.maxZ()==plan.minZ()+plan.size()-inset;
    }
    static int apply(JavaPlugin plugin,ArenaMap map) throws IOException {
        Plan plan=PLANS.stream().filter(p->p.id().equals(map.id())).findFirst().orElseThrow(()->new IllegalArgumentException("재편 계획이 없는 맵입니다"));
        if(!matches(plan,map.start().getWorld().getName(),map.boundary()))
            throw new IllegalArgumentException("검토한 원본 경기장과 좌표가 다릅니다. 자동 적용하지 않습니다.");
        var world=map.start().getWorld();
        List<Map<String,Object>> journal=new ArrayList<>();
        for(MapBlueprint.Box box:plan.boxes())
            for(int x=box.x1();x<=box.x2();x++) for(int y=box.y1();y<=box.y2();y++) for(int z=box.z1();z<=box.z2();z++) {
                var block=world.getBlockAt(x,y,z);
                if(!mayReplace(block.getType(),box.material())) throw new IllegalArgumentException("기존 블록과 충돌하므로 취소: "+x+","+y+","+z+" "+block.getType());
                if(block.getType()!=box.material()) journal.add(Map.of("x",x,"y",y,"z",z,"before",block.getBlockData().getAsString()));
            }
        if(journal.isEmpty()) return 0;
        var backup=plugin.getDataFolder().toPath().resolve("map-backups"); Files.createDirectories(backup);
        var saved=new YamlConfiguration(); saved.set("world",world.getName()); saved.set("blocks",journal);
        saved.save(backup.resolve(map.id()+"-"+System.currentTimeMillis()+".yml").toFile());
        for(MapBlueprint.Box box:plan.boxes())
            for(int x=box.x1();x<=box.x2();x++) for(int y=box.y1();y<=box.y2();y++) for(int z=box.z1();z<=box.z2();z++)
                world.getBlockAt(x,y,z).setType(box.material(),false);
        world.save(); return journal.size();
    }
}
