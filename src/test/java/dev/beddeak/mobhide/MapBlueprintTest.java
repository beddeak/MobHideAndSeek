package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MapBlueprintTest {
    @ParameterizedTest @ValueSource(ints={160,320})
    void groundIsConnectedWithTwoBlockClearanceAndNoStrandedInteriors(int center) {
        MapBlueprint plan=MapBlueprint.create();
        Set<String> walkable=new HashSet<>();
        for(int x=center-39;x<=center+39;x++) for(int z=-39;z<=39;z++) {
            assertNotEquals(Material.AIR,plan.at(x,63,z));
            if(plan.at(x,64,z)==Material.AIR && plan.at(x,65,z)==Material.AIR) walkable.add(x+":"+z);
        }
        String start=center+":32";
        assertTrue(walkable.contains(start),"Seeker start must be clear");
        Set<String> reached=new HashSet<>();
        ArrayDeque<String> todo=new ArrayDeque<>(); todo.add(start);
        while(!todo.isEmpty()) {
            String key=todo.removeFirst();
            if(!walkable.contains(key)||!reached.add(key)) continue;
            String[] v=key.split(":"); int x=Integer.parseInt(v[0]),z=Integer.parseInt(v[1]);
            todo.add((x+1)+":"+z); todo.add((x-1)+":"+z); todo.add(x+":"+(z+1)); todo.add(x+":"+(z-1));
        }
        assertEquals(walkable,reached,"Every spawnable interior must have a walking route to the start");
        assertTrue(reached.size()>3500,"Enough open floor for 100 decoys and players");
        for(int v=-40;v<=40;v++) for(int y=64;y<=94;y++) {
            assertNotEquals(Material.AIR,plan.at(center+v,y,-40));
            assertNotEquals(Material.AIR,plan.at(center+v,y,40));
            assertNotEquals(Material.AIR,plan.at(center-40,y,v));
            assertNotEquals(Material.AIR,plan.at(center+40,y,v));
        }
    }
    @Test void lobbyHasSafeArrivalAndIsSeparatedFromTheArenas() {
        MapBlueprint plan=MapBlueprint.create();
        assertNotEquals(Material.AIR,plan.at(0,63,7));
        assertEquals(Material.AIR,plan.at(0,64,7)); assertEquals(Material.AIR,plan.at(0,65,7));
        assertEquals(Material.AIR,plan.at(80,63,0)); assertEquals(Material.AIR,plan.at(240,63,0));
        assertTrue(plan.boxes().stream().noneMatch(b -> Set.of(Material.CACTUS,Material.LAVA,Material.POWDER_SNOW,Material.FIRE).contains(b.material())));
    }
    @ParameterizedTest @ValueSource(ints={160,320})
    void staggeredCoverLimitsLongSightlinesWithoutClosingTheMap(int center) {
        MapBlueprint plan=MapBlueprint.create();
        var distances=new java.util.ArrayList<Integer>();
        for(int x=center-35;x<center+40;x+=5) for(int z=-35;z<40;z+=5) {
            if(plan.at(x,64,z)!=Material.AIR || plan.at(x,65,z)!=Material.AIR) continue;
            for(int[] direction:new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{-1,1},{1,-1},{-1,-1}}) {
                int length=0;
                for(int n=1;n<81;n++) {
                    length=n; int xx=x+direction[0]*n,zz=z+direction[1]*n;
                    if(xx<=center-40 || xx>=center+40 || zz<=-40 || zz>=40) break;
                    Material m=plan.at(xx,65,zz);
                    if(m!=Material.AIR && !m.name().contains("GLASS") && !m.name().contains("FENCE") && !m.name().contains("WALL")) break;
                }
                distances.add(length);
            }
        }
        distances.sort(Integer::compareTo);
        assertTrue(distances.get((int)(distances.size()*.9))<=30,"90 percent of sampled eye-level rays must hit cover/edge within 30 grid steps");
    }
}
