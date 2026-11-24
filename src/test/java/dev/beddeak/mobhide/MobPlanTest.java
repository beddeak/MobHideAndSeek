package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class MobPlanTest {
    private final List<EntityType> pool = List.of(
            EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN, EntityType.RABBIT);

    @Test void playersAndDecoysCanReceiveDifferentRandomMobs() {
        MobPlan plan = MobPlan.create(pool, 20, 25, new Random(42));
        assertEquals(20, plan.hiders().size());
        assertEquals(25, plan.decoys().size());
        assertTrue(Set.copyOf(plan.hiders()).size() > 1);
        assertTrue(Set.copyOf(plan.decoys()).size() > 1);
        assertTrue(pool.containsAll(plan.hiders()));
        assertTrue(pool.containsAll(plan.decoys()));
    }

    @Test void everyHiderTypeHasAtLeastOneMatchingDecoyEvenWithALowConfiguredCount() {
        MobPlan plan = MobPlan.create(pool, 20, 1, new Random(42));
        assertTrue(plan.decoys().containsAll(plan.hiders()));
        assertEquals(Set.copyOf(plan.hiders()), Set.copyOf(plan.decoys()));
        assertTrue(plan.decoys().size() > 1);
    }

    @Test void soloPlayerAlsoGetsAMatchingDecoy() {
        for (int seed = 0; seed < 50; seed++) {
            MobPlan plan = MobPlan.create(pool, 1, 25, new Random(seed));
            assertEquals(1, plan.hiders().size());
            assertEquals(25, plan.decoys().size());
            assertTrue(plan.decoys().contains(plan.hiders().getFirst()));
        }
    }

    @Test void duplicateAssignmentsAreAllowed() {
        MobPlan plan = MobPlan.create(List.of(EntityType.PIG), 3, 4, new Random(1));
        assertEquals(List.of(EntityType.PIG, EntityType.PIG, EntityType.PIG), plan.hiders());
        assertEquals(List.of(EntityType.PIG, EntityType.PIG, EntityType.PIG, EntityType.PIG), plan.decoys());
    }

    @Test void aNewRoundCanRerollTheSoloPlayer() {
        Random random = new Random(42);
        Set<EntityType> assignments = new java.util.HashSet<>();
        for (int i = 0; i < 30; i++) assignments.add(MobPlan.create(pool, 1, 25, random).hiders().getFirst());
        assertTrue(assignments.size() > 1);
    }

    @Test void assignmentsAreImmutableAndDoNotChangeWithTheirSourceLists() {
        List<EntityType> hiders = new ArrayList<>(List.of(EntityType.COW));
        List<EntityType> decoys = new ArrayList<>(List.of(EntityType.COW));
        MobPlan plan = new MobPlan(hiders, decoys);
        hiders.clear();
        decoys.clear();
        assertEquals(List.of(EntityType.COW), plan.hiders());
        assertEquals(List.of(EntityType.COW), plan.decoys());
        assertThrows(UnsupportedOperationException.class, () -> plan.hiders().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.decoys().clear());
    }

    @Test void missingPoolParticipantsOrDecoysAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> MobPlan.create(List.of(), 1, 25, new Random(1)));
        assertThrows(IllegalArgumentException.class, () -> MobPlan.create(pool, 0, 25, new Random(1)));
        assertThrows(IllegalArgumentException.class, () -> MobPlan.create(pool, 1, 0, new Random(1)));
    }
    @Test void theExpandedPoolProducesAllThreeNewMobsForPlayersAndMap() {
        var plan = MobPlan.create(MobCatalog.ALL, 100, 100, new Random(1));
        assertTrue(plan.hiders().containsAll(List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER)));
        assertTrue(plan.decoys().containsAll(MobCatalog.ALL));
    }
    @Test void adminSelectedHiderHasMatchingAiEvenWhenNotInRandomPool() {
        var plan = MobPlan.forHiders(List.of(EntityType.PIG), List.of(EntityType.ZOMBIE), 5, new Random(1));
        assertEquals(EntityType.ZOMBIE, plan.decoys().getFirst());
        assertTrue(plan.decoys().contains(EntityType.PIG));
    }
}
