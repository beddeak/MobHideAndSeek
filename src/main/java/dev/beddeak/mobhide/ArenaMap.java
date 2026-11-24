package dev.beddeak.mobhide;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

record ArenaMap(String id, String name, Location start, ArenaRegion boundary, int radius, int minSpawnY, int maxSpawnY) {
    static ArenaMap read(String id, ConfigurationSection section) {
        if(section == null) throw new IllegalArgumentException(id + ": 맵 설정 없음");
        World world = Bukkit.getWorld(section.getString("world", ""));
        if(world == null) throw new IllegalArgumentException(id + ": 월드가 로드되지 않았습니다");
        Location start = new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float)section.getDouble("yaw"), 0);
        if(!Double.isFinite(start.getX()) || !Double.isFinite(start.getY()) || !Double.isFinite(start.getZ()))
            throw new IllegalArgumentException(id + ": 유효하지 않은 좌표");
        int radius = Math.max(5, Math.min(64,section.getInt("radius",20)));
        ArenaRegion region;
        if(section.getString("shape","circle").equals("cuboid")) {
            List<Integer> min=section.getIntegerList("min"), max=section.getIntegerList("max");
            if(min.size()!=3 || max.size()!=3) throw new IllegalArgumentException(id + ": 경계 좌표 오류");
            region = new CuboidBoundary(min.get(0),min.get(1),min.get(2),max.get(0),max.get(1),max.get(2));
        } else region = new ArenaBoundary(start.getX(),start.getY(),start.getZ(),radius);
        int min = section.getInt("spawn-min-y", start.getBlockY()-5), max=section.getInt("spawn-max-y",start.getBlockY()+5);
        if(min>max || min<world.getMinHeight()+1 || max>world.getMaxHeight()-2 || !region.contains(start.getX(),start.getY(),start.getZ()))
            throw new IllegalArgumentException(id + ": 시작점/소환 높이가 경계를 벗어납니다");
        // 흙길·경작지(15/16칸)나 얕은 물 위에 서면 발이 소환 높이보다 조금 낮아집니다. 경계 바닥이 소환 높이와
        // 같으면 그 위를 걷는 순간 경기장 밖으로 판정되어 계속 되돌려지므로(농장 맵) 바닥에 두 칸 여유를 둡니다.
        if(region instanceof CuboidBoundary box && box.minY()>min-2)
            region = new CuboidBoundary(box.minX(),Math.max(world.getMinHeight(),min-2),box.minZ(),box.maxX(),box.maxY(),box.maxZ());
        return new ArenaMap(id,section.getString("name",id),start,region,radius,min,max);
    }
}
