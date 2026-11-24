package dev.beddeak.mobhide;
import static org.mockito.Mockito.*;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import org.bukkit.entity.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
class DecoyBehaviorTest {
    @BeforeAll static void registries() { TestRegistries.initialize(); }
    @Test void wolvesCatsAndGolemsStopHuntingAndDropMovesPlayersCannotCopy() {
        MobGoals goals = mock(MobGoals.class);
        Wolf wolf = mock(Wolf.class); when(wolf.getType()).thenReturn(EntityType.WOLF);
        Cat cat = mock(Cat.class); when(cat.getType()).thenReturn(EntityType.CAT);
        IronGolem golem = mock(IronGolem.class); when(golem.getType()).thenReturn(EntityType.IRON_GOLEM);
        DecoyBehavior.prepare(wolf, goals, p -> false);
        DecoyBehavior.prepare(cat, goals, p -> false);
        DecoyBehavior.prepare(golem, goals, p -> false);
        for (Mob mob : new Mob[] {wolf, cat, golem}) { verify(goals).removeAllGoals(mob, GoalType.TARGET); verify(mob).setTarget(null); }
        verify(goals).removeGoal(wolf, VanillaGoal.BEG);
        verify(wolf).setVariant(Wolf.Variant.PALE);
        verify(goals).removeGoal(cat, VanillaGoal.CAT_AVOID_ENTITY);
        verify(goals).removeGoal(cat, VanillaGoal.CAT_SIT_ON_BLOCK);
        verify(goals).removeGoal(cat, VanillaGoal.CAT_LIE_ON_BED);
        verify(goals).removeGoal(cat, VanillaGoal.CAT_TEMPT);
        verify(cat).setCatType(Cat.Type.TABBY);
        verify(goals).removeGoal(golem, VanillaGoal.OFFER_FLOWER);
        verify(golem).setPlayerCreated(false);
    }
    @Test void villagersAndMooshroomsKeepTheSameFixedLookAsTheirDisguises() {
        MobGoals goals = mock(MobGoals.class);
        Villager villager = mock(Villager.class); when(villager.getType()).thenReturn(EntityType.VILLAGER);
        MushroomCow mooshroom = mock(MushroomCow.class); when(mooshroom.getType()).thenReturn(EntityType.MOOSHROOM);
        DecoyBehavior.prepare(villager, goals, p -> false);
        DecoyBehavior.prepare(mooshroom, goals, p -> false);
        verify(villager).setVillagerType(Villager.Type.PLAINS);
        verify(villager).setProfession(Villager.Profession.NITWIT);
        verify(villager).setVillagerLevel(1);
        verify(mooshroom).setVariant(MushroomCow.Variant.RED);
        verify(goals, never()).removeAllGoals(villager, GoalType.TARGET);
    }
    @Test void straysLoseTheirAttacksAndSunBurnLikeSkeletons() {
        Stray stray = mock(Stray.class); when(stray.getType()).thenReturn(EntityType.STRAY); MobGoals goals = mock(MobGoals.class);
        DecoyBehavior.prepare(stray, goals, p -> false);
        verify(goals).removeAllGoals(stray, GoalType.TARGET);
        verify(goals).removeGoal(stray, VanillaGoal.ABSTRACT_SKELETON_MELEE);
        verify(goals).removeGoal(stray, VanillaGoal.RANGED_BOW_ATTACK);
        verify(stray).setShouldBurnInDay(false);
    }
    @Test void witchLosesTargetingAndPotionThrowingEvenOutsideTheOriginalCatalog() {
        Witch mob = mock(Witch.class); when(mob.getType()).thenReturn(EntityType.WITCH);
        MobGoals goals = mock(MobGoals.class);
        DecoyBehavior.prepare(mob, goals, p -> false);
        verify(goals).removeAllGoals(mob, GoalType.TARGET);
        verify(goals).removeGoal(mob, VanillaGoal.RANGED_ATTACK);
        verify(mob).setTarget(null); verify(mob).setAggressive(false);
        verify(mob).setAI(true); verify(goals, never()).removeAllGoals(mob);
    }
    @Test void zombieKeepsMovementButLosesTargetingAttacksAndSunBurn() {
        Zombie mob = mock(Zombie.class); when(mob.getType()).thenReturn(EntityType.ZOMBIE); MobGoals goals = mock(MobGoals.class);
        DecoyBehavior.prepare(mob, goals, p -> false);
        verify(goals).removeAllGoals(mob, GoalType.TARGET); verify(goals).removeGoal(mob, VanillaGoal.ZOMBIE_ATTACK);
        verify(goals).removeGoal(mob, VanillaGoal.ZOMBIE_ATTACK_TURTLE_EGG); verify(mob).setShouldBurnInDay(false);
        verify(mob).setCanBreakDoors(false); verify(mob).setAdult(); verify(mob).setAI(true);
        verify(goals, never()).removeAllGoals(mob); verify(goals, never()).removeAllGoals(mob, GoalType.MOVE);
    }
    @Test void skeletonCannotShootOrMeleeAndDoesNotBurn() {
        Skeleton mob = mock(Skeleton.class); when(mob.getType()).thenReturn(EntityType.SKELETON); MobGoals goals = mock(MobGoals.class);
        DecoyBehavior.prepare(mob, goals, p -> false);
        verify(goals).removeGoal(mob, VanillaGoal.RANGED_BOW_ATTACK); verify(goals).removeGoal(mob, VanillaGoal.ABSTRACT_SKELETON_MELEE);
        verify(mob).setShouldBurnInDay(false); verify(mob).setCanPickupItems(false);
    }
    @Test void creeperCannotStartItsFuseAndExplosionPowerIsZero() {
        Creeper mob = mock(Creeper.class); when(mob.getType()).thenReturn(EntityType.CREEPER); MobGoals goals = mock(MobGoals.class);
        DecoyBehavior.prepare(mob, goals, p -> false);
        verify(goals).removeGoal(mob, VanillaGoal.SWELL); verify(mob).setIgnited(false); verify(mob).setFuseTicks(0);
        verify(mob).setExplosionRadius(0); verify(mob).setPowered(false); verify(mob).setCollidable(true);
    }
    @Test void rabbitsNoLongerFleeFromPlayersOrCampOnCarrots() {
        Rabbit mob = mock(Rabbit.class); when(mob.getType()).thenReturn(EntityType.RABBIT); MobGoals goals = mock(MobGoals.class);
        DecoyBehavior.prepare(mob, goals, p -> false);
        verify(goals).removeGoal(mob, VanillaGoal.RABBIT_AVOID_ENTITY);
        verify(goals).removeGoal(mob, VanillaGoal.RAID_GARDEN);
        verify(goals).removeGoal(mob, VanillaGoal.AVOID_ENTITY);
        verify(mob).setRabbitType(Rabbit.Type.BROWN);
    }
    @Test void sheepDoNotGrazeAndCreepersDoNotFleeFromCats() {
        Sheep sheep = mock(Sheep.class); when(sheep.getType()).thenReturn(EntityType.SHEEP);
        Creeper creeper = mock(Creeper.class); when(creeper.getType()).thenReturn(EntityType.CREEPER);
        MobGoals goals = mock(MobGoals.class);
        DecoyBehavior.prepare(sheep, goals, p -> false);
        DecoyBehavior.prepare(creeper, goals, p -> false);
        verify(goals).removeGoal(sheep, VanillaGoal.EAT_BLOCK);
        verify(goals).removeGoal(creeper, VanillaGoal.AVOID_ENTITY);
        verify(goals, never()).removeGoal(creeper, VanillaGoal.EAT_BLOCK);
    }
    @Test void animalsKeepTheirNonPlayerBehavior() {
        Cow mob = mock(Cow.class); when(mob.getType()).thenReturn(EntityType.COW); MobGoals goals = mock(MobGoals.class);
        DecoyBehavior.prepare(mob, goals, p -> false);
        verify(goals, never()).removeAllGoals(mob, GoalType.TARGET); verify(goals, never()).removeAllGoals(mob);
        verify(mob).setAI(true);
    }
}
