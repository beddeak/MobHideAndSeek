package dev.beddeak.mobhide;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;

/** Fixed, repeatable block plans. Only the dedicated mobhide_maps world uses these. */
final class MapBlueprint {
    record Box(int x1, int y1, int z1, int x2, int y2, int z2, Material material) {}
    private final List<Box> boxes = new ArrayList<>();
    List<Box> boxes() { return List.copyOf(boxes); }
    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        boxes.add(new Box(x1, y1, z1, x2, y2, z2, m));
    }
    Material at(int x, int y, int z) {
        Material result = Material.AIR;
        for (Box b : boxes) if (x >= b.x1 && x <= b.x2 && y >= b.y1 && y <= b.y2 && z >= b.z1 && z <= b.z2) result = b.material;
        return result;
    }
    static MapBlueprint create() {
        MapBlueprint p = new MapBlueprint();
        p.lobby(); p.desert(); p.snow();
        for(MapRefinements.Plan plan:MapRefinements.plans())
            if(plan.world().equals(MapRotation.WORLD)) p.boxes.addAll(plan.boxes());
        return p;
    }
    private void platform(int cx, int r, Material floor, Material wall) {
        fill(cx-r, 60, -r, cx+r, 62, r, Material.STONE);
        fill(cx-r, 63, -r, cx+r, 63, r, floor);
        for (int side : new int[] {-r, r}) {
            fill(cx-r, 64, side, cx+r, 67, side, wall);
            fill(cx+side, 64, -r, cx+side, 67, r, wall);
            fill(cx-r, 68, side, cx+r, 94, side, Material.BARRIER);
            fill(cx+side, 68, -r, cx+side, 94, r, Material.BARRIER);
        }
        fill(cx-r, 94, -r, cx+r, 94, r, Material.BARRIER);
    }
    private void lamp(int x, int z, Material post) {
        fill(x,64,z,x,67,z,post);
        fill(x,68,z,x,68,z,Material.SEA_LANTERN);
        fill(x-1,69,z-1,x+1,69,z+1,Material.DARK_OAK_SLAB);
    }
    private void house(int x, int z, int w, int d, Material wall, Material roof, boolean snowy) {
        fill(x,64,z,x+w,69,z+d,wall);
        fill(x+1,64,z+1,x+w-1,68,z+d-1,Material.AIR);
        // Two wide opposing exits, usable even by cow/sheep disguises.
        fill(x+w/2-1,64,z,x+w/2+1,66,z,Material.AIR);
        fill(x+w/2-1,64,z+d,x+w/2+1,66,z+d,Material.AIR);
        fill(x,66,z+2,x,67,z+4,Material.GLASS);
        fill(x+w,66,z+2,x+w,67,z+4,Material.GLASS);
        fill(x-1,70,z-1,x+w+1,70,z+d+1,roof);
        if (snowy) {
            for (int n=1;n<=3;n++) fill(x-1+n,70+n,z-1,x+w+1-n,70+n,z+d+1,roof);
            fill(x+3,74,z-1,x+w-3,74,z+d+1,Material.SNOW_BLOCK);
        } else {
            fill(x,71,z,x+w,71,z,wall); fill(x,71,z+d,x+w,71,z+d,wall);
        }
        fill(x+1,64,z+1,x+2,65,z+2,Material.BARREL);
        fill(x+w-2,64,z+d-2,x+w-1,64,z+d-1,Material.HAY_BLOCK);
        fill(x+w/2,69,z+d/2,x+w/2,69,z+d/2,Material.SEA_LANTERN);
    }
    private void lobby() {
        platform(0,20,Material.SMOOTH_QUARTZ,Material.WHITE_CONCRETE);
        fill(-3,63,-18,3,63,18,Material.CYAN_CONCRETE);
        fill(-18,63,-3,18,63,3,Material.CYAN_CONCRETE);
        fill(-6,63,-6,6,63,6,Material.POLISHED_DEEPSLATE);
        fill(-2,63,-2,2,63,2,Material.SEA_LANTERN);
        for(int x:new int[]{-14,14}) for(int z:new int[]{-14,14}) {
            fill(x-2,64,z-2,x+2,64,z+2,Material.QUARTZ_BLOCK);
            fill(x-1,65,z-1,x+1,65,z+1,Material.FLOWERING_AZALEA_LEAVES);
            lamp(x,z,Material.QUARTZ_PILLAR);
        }
        // Three colour-coded map displays on the north side.
        for(int i=0;i<3;i++) {
            int x=-12+i*12;
            Material m=new Material[]{Material.OAK_PLANKS,Material.CUT_SANDSTONE,Material.SPRUCE_PLANKS}[i];
            fill(x-4,64,-17,x+4,64,-11,m);
            fill(x-4,65,-17,x-4,68,-17,Material.QUARTZ_PILLAR);
            fill(x+4,65,-17,x+4,68,-17,Material.QUARTZ_PILLAR);
            fill(x-4,69,-18,x+4,69,-10,m);
        }
    }
    private void desert() {
        int c=160;
        platform(c,40,Material.SANDSTONE,Material.CUT_SANDSTONE);
        fill(c-37,63,-4,c+37,63,4,Material.SMOOTH_SANDSTONE);
        fill(c-4,63,-37,c+4,63,37,Material.SMOOTH_SANDSTONE);
        // Four courtyard houses plus two walk-through side houses.
        for(int dx:new int[]{-33,19}) for(int z:new int[]{-33,19})
            house(c+dx,z,13,12,Material.SMOOTH_SANDSTONE,Material.RED_TERRACOTTA,false);
        house(c-33,-10,10,14,Material.ORANGE_TERRACOTTA,Material.CUT_SANDSTONE,false);
        house(c+23,-10,10,14,Material.YELLOW_TERRACOTTA,Material.CUT_SANDSTONE,false);
        // Central stepped monument gives cover without water/hazardous cactus.
        fill(c-5,64,-5,c+5,64,5,Material.CUT_SANDSTONE);
        fill(c-3,65,-3,c+3,65,3,Material.CHISELED_SANDSTONE);
        fill(c-1,66,-1,c+1,72,1,Material.CUT_RED_SANDSTONE);
        fill(c-1,73,-1,c+1,73,1,Material.GOLD_BLOCK);
        for(int x:new int[]{c-15,c+10}) for(int z:new int[]{-19,10}) {
            for(int px:new int[]{x,x+5}) for(int pz:new int[]{z,z+5}) fill(px,64,pz,px,67,pz,Material.OAK_FENCE);
            for(int n=0;n<6;n++) fill(x+n,68,z,x+n,68,z+5,n%2==0?Material.RED_WOOL:Material.YELLOW_WOOL);
            fill(x+1,64,z+1,x+2,65,z+2,Material.HAY_BLOCK);
            fill(x+4,64,z+4,x+4,64,z+4,Material.BARREL);
        }
        for(int x:new int[]{c-10,c+10}) for(int z:new int[]{-30,30}) lamp(x,z,Material.SANDSTONE_WALL);
        for(int x:new int[]{c-35,c+35}) for(int z:new int[]{-17,15}) {
            fill(x,64,z,x,70,z,Material.JUNGLE_LOG);
            fill(x-3,71,z-1,x+3,72,z+1,Material.JUNGLE_LEAVES);
            fill(x-1,71,z-3,x+1,72,z+3,Material.JUNGLE_LEAVES);
        }
    }
    private void snow() {
        int c=320;
        platform(c,40,Material.SNOW_BLOCK,Material.STONE_BRICKS);
        fill(c-37,63,-3,c+37,63,3,Material.SPRUCE_PLANKS);
        fill(c-3,63,-37,c+3,63,37,Material.SPRUCE_PLANKS);
        for(int dx:new int[]{-32,18}) for(int z:new int[]{-32,18})
            house(c+dx,z,13,12,Material.SPRUCE_PLANKS,Material.DARK_OAK_PLANKS,true);
        // Open-sided sawmill, stacked timber, sheltered routes through the middle.
        for(int x:new int[]{c-9,c+9}) for(int z:new int[]{-8,8}) fill(x,64,z,x,70,z,Material.STRIPPED_SPRUCE_LOG);
        fill(c-11,71,-10,c+11,71,10,Material.DARK_OAK_PLANKS);
        fill(c-11,72,-10,c+11,72,10,Material.SNOW_BLOCK);
        fill(c-2,70,-2,c+2,70,2,Material.SEA_LANTERN);
        for(int x:new int[]{c-21,c+15}) for(int z:new int[]{-10,9}) {
            fill(x,64,z,x+5,65,z+2,Material.SPRUCE_LOG);
            fill(x+1,66,z,x+4,66,z+2,Material.SPRUCE_LOG);
            fill(x,67,z,x+5,67,z+2,Material.SNOW_BLOCK);
        }
        for(int x:new int[]{c-35,c-12,c+12,c+35}) for(int z:new int[]{-36,35}) tree(x,z);
        for(int x:new int[]{c-35,c+35}) for(int z:new int[]{-12,6}) tree(x,z);
        for(int x:new int[]{c-8,c+8}) for(int z:new int[]{-27,27}) lamp(x,z,Material.SPRUCE_FENCE);
        for(int x:new int[]{c-17,c+14}) for(int z:new int[]{-20,23}) fill(x,64,z,x+2,65,z+2,Material.BARREL);
    }
    private void tree(int x,int z) {
        fill(x,64,z,x,73,z,Material.SPRUCE_LOG);
        for(int y=68;y<=75;y++) {
            int r=Math.max(1,(76-y)/2);
            fill(x-r,y,z-r,x+r,y,z+r,Material.SPRUCE_LEAVES);
        }
        fill(x,76,z,x,76,z,Material.SNOW_BLOCK);
    }
}
