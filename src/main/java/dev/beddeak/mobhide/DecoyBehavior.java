package dev.beddeak.mobhide;

import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;

final class DecoyBehavior {
    static void prepare(Mob mob, MobGoals goals, Predicate<Player> hidden) {
        mob.setRemoveWhenFarAway(false);
        mob.setAI(true);
        mob.setCanPickupItems(false);
        mob.setCollidable(true);
        // 게임 AI는 아무것도 피해 도망가지 않습니다. 토끼는 원래 플레이어를 피해 변신한 숨는 사람 근처에서도
        // 도망가 정체가 드러났고, 크리퍼(고양이)·스켈레톤(늑대)도 실제 몹만 피해 변신한 사람과 구분됐습니다.
        if (mob instanceof Creature creature) goals.removeGoal(creature, VanillaGoal.AVOID_ENTITY);
        if (mob instanceof Rabbit rabbit) {
            rabbit.setRabbitType(Rabbit.Type.BROWN);
            goals.removeGoal(rabbit, VanillaGoal.RABBIT_AVOID_ENTITY);
            // 당근 밭 블록 변경을 막으므로 그대로 두면 같은 당근 앞에 계속 붙어 있습니다.
            goals.removeGoal(rabbit, VanillaGoal.RAID_GARDEN);
        }
        // 풀 뜯기 동작은 숨는 플레이어가 따라 할 수 없습니다.
        if (mob instanceof Sheep) goals.removeGoal(mob, VanillaGoal.EAT_BLOCK);
        if (mob instanceof Monster) {
            var equipment = mob.getEquipment();
            if (equipment != null) {
                equipment.clear();
                // 스트레이도 스켈레톤처럼 활을 듭니다. 변신 외형도 같은 활을 듭니다.
                if (mob instanceof AbstractSkeleton) equipment.setItemInMainHand(new ItemStack(Material.BOW));
            }
            goals.removeAllGoals(mob, GoalType.TARGET);
            mob.setTarget(null);
            mob.setAggressive(false);
            goals.removeGoal((Creature) mob, VanillaGoal.MELEE_ATTACK);
            // 불에 타지 않게 했으므로 해를 피해 그늘에만 머무는 스켈레톤·스트레이 행동도 필요 없습니다.
            goals.removeGoal((Creature) mob, VanillaGoal.RESTRICT_SUN);
            goals.removeGoal((Creature) mob, VanillaGoal.FLEE_SUN);
            if (mob instanceof com.destroystokyo.paper.entity.RangedEntity ranged)
                goals.removeGoal(ranged, VanillaGoal.RANGED_ATTACK);
            if (mob instanceof Zombie zombie) {
                zombie.setAdult(); zombie.setShouldBurnInDay(false); zombie.setCanBreakDoors(false);
                goals.removeGoal(zombie, VanillaGoal.ZOMBIE_ATTACK);
                goals.removeGoal(zombie, VanillaGoal.ZOMBIE_ATTACK_TURTLE_EGG);
            }
            if (mob instanceof AbstractSkeleton skeleton) {
                skeleton.setShouldBurnInDay(false);
                goals.removeGoal(skeleton, VanillaGoal.ABSTRACT_SKELETON_MELEE);
                goals.removeGoal((Monster) skeleton, VanillaGoal.RANGED_BOW_ATTACK);
            }
            if (mob instanceof Creeper creeper) {
                goals.removeGoal(creeper, VanillaGoal.SWELL);
                creeper.setPowered(false); creeper.setIgnited(false); creeper.setFuseTicks(0); creeper.setExplosionRadius(0);
            }
        }
        // 늑대·고양이는 양·토끼를 사냥하고 철골렘은 좀비 계열을 공격합니다. 대상 지정 이벤트도 막지만 목표를 없앱니다.
        if (mob instanceof Wolf || mob instanceof Cat || mob instanceof IronGolem) {
            goals.removeAllGoals(mob, GoalType.TARGET);
            mob.setTarget(null);
        }
        // LibsDisguises가 변신 외형을 랜덤으로 정하는 몹은 AI와 변신 모두 같은 모습으로 고정합니다(WatcherAppearance).
        if (mob instanceof Wolf wolf) {
            wolf.setAdult();
            wolf.setVariant(Wolf.Variant.PALE);
            // 뼈·고기를 든 사람에게 고개를 기울이며 조르는 동작은 숨는 사람이 따라 할 수 없습니다.
            goals.removeGoal(wolf, VanillaGoal.BEG);
            goals.removeGoal(wolf, VanillaGoal.WOLF_AVOID_ENTITY);
        }
        if (mob instanceof Cat cat) {
            cat.setAdult();
            cat.setCatType(Cat.Type.TABBY);
            // 길고양이는 플레이어를 피해 도망가고, 상자·침대에 앉고, 생선을 든 사람을 따라갑니다.
            goals.removeGoal(cat, VanillaGoal.CAT_AVOID_ENTITY);
            goals.removeGoal(cat, VanillaGoal.CAT_SIT_ON_BLOCK);
            goals.removeGoal(cat, VanillaGoal.CAT_LIE_ON_BED);
            goals.removeGoal(cat, VanillaGoal.CAT_TEMPT);
        }
        if (mob instanceof IronGolem golem) {
            golem.setPlayerCreated(false);
            // 주민에게 꽃을 내미는 동작은 숨는 사람이 따라 할 수 없습니다.
            goals.removeGoal(golem, VanillaGoal.OFFER_FLOWER);
        }
        if (mob instanceof Villager villager) {
            // 평원 멍청이(nitwit) 주민으로 고정합니다. 무직 주민은 통·작업대를 찾아 계속 걸어가 티가 났습니다.
            // 멍청이는 원래 직업 자리를 찾지 않습니다. 두뇌는 VillagerCalm이 새 직업 기준으로 다시 만듭니다.
            villager.setAdult();
            villager.setVillagerType(Villager.Type.PLAINS);
            villager.setProfession(Villager.Profession.NITWIT);
            villager.setVillagerLevel(1);
        }
        if (mob instanceof MushroomCow mooshroom) mooshroom.setVariant(MushroomCow.Variant.RED);
        DecoyPlayerGoal.install(goals, mob, hidden);
    }
}
