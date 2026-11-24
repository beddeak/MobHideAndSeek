package dev.beddeak.mobhide;

import java.util.List;
import org.bukkit.entity.EntityType;

final class MobCatalog {
    static final List<EntityType> ALL = List.of(EntityType.COW, EntityType.PIG, EntityType.SHEEP,
            EntityType.CHICKEN, EntityType.RABBIT, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER,
            EntityType.VILLAGER, EntityType.IRON_GOLEM, EntityType.WOLF, EntityType.CAT,
            EntityType.MOOSHROOM, EntityType.HUSK, EntityType.STRAY);
    /** 1.6 이전의 기본 8종. 이 목록을 그대로 쓰던 설정만 새 전체 목록으로 넓힙니다. */
    static final List<EntityType> LEGACY_DEFAULT = ALL.subList(0, 8);

    static boolean hostile(EntityType type) {
        return switch (type) {
            case ZOMBIE, SKELETON, CREEPER, HUSK, STRAY -> true;
            default -> false;
        };
    }

    /** 주민은 이 몹들의 AI를 보면 도망칩니다. 변신한 플레이어에게서는 도망치지 않아 차이가 드러납니다. */
    static boolean scaresVillagers(EntityType type) { return type == EntityType.ZOMBIE || type == EntityType.HUSK; }

    /**
     * 공통 소환 공간(소·양 너비, 스켈레톤 높이)보다 큰 몹의 {반너비, 높이}. 철골렘은 1.4×2.7이라
     * 공통 기준으로 소환하면 벽·천장에 낍니다. null이면 공통 기준을 씁니다.
     */
    static double[] clearance(EntityType type) {
        return type == EntityType.IRON_GOLEM ? new double[] {0.7, 2.7} : null;
    }

    /** LibsDisguises의 DisguiseType 이름. 무시룸은 옛 이름 MUSHROOM_COW를 그대로 씁니다. */
    static String disguiseName(EntityType type) { return type == EntityType.MOOSHROOM ? "MUSHROOM_COW" : type.name(); }
}
