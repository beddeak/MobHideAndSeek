package dev.beddeak.mobhide;

import java.util.Random;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

/** Void outside the three bounded builds; no terrain, caves, structures or natural mobs. */
final class ThemeWorldGenerator extends ChunkGenerator {
    private final MapBlueprint plan = MapBlueprint.create();
    @Override public void generateNoise(WorldInfo world, Random random, int cx, int cz, ChunkData data) {
        int ox=cx*16, oz=cz*16;
        for (MapBlueprint.Box b : plan.boxes()) {
            int x1=Math.max(0,b.x1()-ox), x2=Math.min(16,b.x2()-ox+1);
            int z1=Math.max(0,b.z1()-oz), z2=Math.min(16,b.z2()-oz+1);
            if(x1<x2 && z1<z2) data.setRegion(x1,b.y1(),z1,x2,b.y2()+1,z2,b.material());
        }
    }
    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }
}
