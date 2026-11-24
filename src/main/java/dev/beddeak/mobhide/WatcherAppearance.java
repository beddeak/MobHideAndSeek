package dev.beddeak.mobhide;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.entity.Cat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;

/**
 * LibsDisguises는 RandomDisguiseOptions(기본 켜짐)일 때 무시룸 색, 주민 직업·옷·레벨, 고양이·늑대 무늬를
 * 변신할 때마다 랜덤으로 정합니다. AI는 DecoyBehavior가 고정한 모습이므로 변신 외형도 같은 값으로 맞춥니다.
 * 메서드는 선언 클래스에서 하나씩 조회하고, 찾지 못한 항목은 경고만 남겨 다른 몹 변신은 계속 동작하게 합니다.
 */
final class WatcherAppearance {
    record Setter(Method method, Object value) {}
    private static final String WATCHERS = "me.libraryaddict.disguise.disguisetypes.watchers.";
    private final Map<EntityType, List<Setter>> setters;

    WatcherAppearance(ClassLoader loader, Logger logger) {
        Map<EntityType, List<Setter>> found = new EnumMap<>(EntityType.class);
        // 직업을 먼저 정해야 setType/setLevel이 새 직업을 기준으로 주민 데이터를 다시 만듭니다.
        add(found, logger, loader, EntityType.VILLAGER, "VillagerWatcher", "setProfession", Villager.Profession.class, Villager.Profession.NITWIT);
        add(found, logger, loader, EntityType.VILLAGER, "VillagerWatcher", "setType", Villager.Type.class, Villager.Type.PLAINS);
        add(found, logger, loader, EntityType.VILLAGER, "VillagerWatcher", "setLevel", int.class, 1);
        add(found, logger, loader, EntityType.CAT, "CatWatcher", "setType", Cat.Type.class, Cat.Type.TABBY);
        add(found, logger, loader, EntityType.WOLF, "WolfWatcher", "setVariant", Wolf.Variant.class, Wolf.Variant.PALE);
        add(found, logger, loader, EntityType.MOOSHROOM, "MushroomCowWatcher", "setVariant", MushroomCow.Variant.class, MushroomCow.Variant.RED);
        setters = found;
    }

    WatcherAppearance(Map<EntityType, List<Setter>> setters) { this.setters = setters; }

    private static void add(Map<EntityType, List<Setter>> found, Logger logger, ClassLoader loader, EntityType type,
                            String watcher, String name, Class<?> parameter, Object value) {
        try {
            Method method = Class.forName(WATCHERS + watcher, false, loader).getMethod(name, parameter);
            found.computeIfAbsent(type, key -> new ArrayList<>()).add(new Setter(method, value));
        } catch (ReflectiveOperationException | LinkageError e) {
            logger.severe(type + " 변신 외형을 AI와 같게 고정하지 못했습니다(" + watcher + "." + name + "). 이 몹은 AI와 다르게 보일 수 있습니다: " + e);
        }
    }

    void apply(Object watcher, EntityType type) throws ReflectiveOperationException {
        for (Setter setter : setters.getOrDefault(type, List.of())) setter.method().invoke(watcher, setter.value());
    }
}
