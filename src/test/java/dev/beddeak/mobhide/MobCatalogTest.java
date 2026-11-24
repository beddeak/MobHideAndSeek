package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class MobCatalogTest {
    // LibsDisguises 26.9.26 DisguiseType에 실제로 있는 이름(javap로 확인).
    private static final Set<String> LIBS_DISGUISE_TYPES = Set.of("COW", "PIG", "SHEEP", "CHICKEN", "RABBIT", "ZOMBIE",
            "SKELETON", "CREEPER", "VILLAGER", "IRON_GOLEM", "WOLF", "CAT", "MUSHROOM_COW", "HUSK", "STRAY");

    @Test void everyMobHasADisguiseAWalkSpeedAndAPanelIcon() {
        assertEquals(15, new HashSet<>(MobCatalog.ALL).size());
        for (EntityType type : MobCatalog.ALL) {
            assertTrue(LIBS_DISGUISE_TYPES.contains(MobCatalog.disguiseName(type)), type + " must map to a LibsDisguises type");
            assertTrue(MobMovement.walkSpeed(type) > 0 && MobMovement.walkSpeed(type) < 0.2f);
            assertNotNull(Material.valueOf(type.name() + "_SPAWN_EGG"));
        }
        assertEquals("MUSHROOM_COW", MobCatalog.disguiseName(EntityType.MOOSHROOM));
    }

    @Test void legacyDefaultsHostilesAndBodySizesAreClassifiedForTheNewMobs() {
        assertEquals(List.of(EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN, EntityType.RABBIT,
                EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER), MobCatalog.LEGACY_DEFAULT);
        assertTrue(MobCatalog.hostile(EntityType.HUSK) && MobCatalog.hostile(EntityType.STRAY));
        assertFalse(MobCatalog.hostile(EntityType.IRON_GOLEM) || MobCatalog.hostile(EntityType.WOLF) || MobCatalog.hostile(EntityType.VILLAGER));
        assertTrue(MobCatalog.scaresVillagers(EntityType.ZOMBIE) && MobCatalog.scaresVillagers(EntityType.HUSK));
        assertArrayEquals(new double[] {0.7, 2.7}, MobCatalog.clearance(EntityType.IRON_GOLEM));
        assertNull(MobCatalog.clearance(EntityType.COW));
    }
}
