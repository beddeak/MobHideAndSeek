package dev.beddeak.mobhide;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.random.RandomGenerator;
import org.bukkit.entity.EntityType;

/** 플레이어마다 추첨하되, 모든 변신 종류가 AI 몹 사이에도 있게 합니다. */
record MobPlan(List<EntityType> hiders, List<EntityType> decoys) {
    MobPlan {
        hiders = List.copyOf(hiders);
        decoys = List.copyOf(decoys);
    }

    static MobPlan create(List<EntityType> pool, int hiderCount, int decoyCount, RandomGenerator random) {
        if (pool.isEmpty() || hiderCount < 1 || decoyCount < 1) {
            throw new IllegalArgumentException("A mob pool, hiders and decoys are required");
        }
        List<EntityType> hiders = new ArrayList<>();
        for (int i = 0; i < hiderCount; i++) hiders.add(pool.get(random.nextInt(pool.size())));
        return forHiders(pool, hiders, decoyCount, random);
    }

    static MobPlan forHiders(List<EntityType> pool, List<EntityType> hiders, int decoyCount, RandomGenerator random) {
        if (pool.isEmpty() || decoyCount < 1) throw new IllegalArgumentException("Invalid decoy pool or count");
        List<EntityType> decoys = new ArrayList<>(new LinkedHashSet<>(hiders));
        while (decoys.size() < decoyCount) decoys.add(pool.get(random.nextInt(pool.size())));
        return new MobPlan(hiders, decoys);
    }
}
