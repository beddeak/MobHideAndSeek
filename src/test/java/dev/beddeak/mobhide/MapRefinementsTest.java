package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class MapRefinementsTest {
    @Test void fivePlansStayWithinTheirOriginalMapsAndKeepTheSpawnClear() {
        var plans=MapRefinements.plans(); assertEquals(5,plans.size());
        assertEquals(Set.of("farm","cave","village","desert","snow"),plans.stream().map(MapRefinements.Plan::id).collect(java.util.stream.Collectors.toSet()));
        for(var p:plans) for(var b:p.boxes()) {
            assertTrue(b.x1()>p.minX() && b.x2()<p.minX()+p.size()-1);
            assertTrue(b.z1()>p.minZ() && b.z2()<p.minZ()+p.size()-1);
            assertEquals(64,b.y1()); assertEquals(66,b.y2());
            assertFalse(Set.of(Material.CACTUS,Material.LAVA,Material.POWDER_SNOW).contains(b.material()));
            int sx=p.size()==96?p.minX()+47:p.minX()+40, sz=p.size()==96?p.minZ()+86:p.minZ()+72;
            assertFalse(sx>=b.x1() && sx<=b.x2() && sz>=b.z1() && sz<=b.z2());
        }
    }
    @Test void retrofitDoesNotReplaceOriginalBuildingsOrContainers() {
        assertTrue(MapRefinements.mayReplace(Material.AIR,Material.HAY_BLOCK));
        assertTrue(MapRefinements.mayReplace(Material.HAY_BLOCK,Material.HAY_BLOCK));
        for(Material m:List.of(Material.BARREL,Material.OAK_PLANKS,Material.GLASS,Material.WATER,Material.STONE))
            assertFalse(MapRefinements.mayReplace(m,Material.HAY_BLOCK));
    }
    @Test void plansMatchTheirActualPlayableBoundsButRejectMovedOrCustomMaps() {
        for(var p:MapRefinements.plans()) {
            int inset=p.world().equals(MapRotation.WORLD)?1:0;
            var bounds=new CuboidBoundary(p.minX()+inset,64,p.minZ()+inset,p.minX()+p.size()-inset,94,p.minZ()+p.size()-inset);
            assertTrue(MapRefinements.matches(p,p.world(),bounds));
            assertFalse(MapRefinements.matches(p,"unrelated_world",bounds));
            assertFalse(MapRefinements.matches(p,p.world(),new CuboidBoundary(bounds.minX()+1,64,bounds.minZ(),bounds.maxX()+1,94,bounds.maxZ())));
        }
    }
}
