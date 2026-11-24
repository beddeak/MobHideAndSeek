package dev.beddeak.mobhide;

import java.lang.reflect.Method;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Rabbit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** 메서드를 선언한 공통 API에서만 조회하여 최신 몹 전용 타입을 불필요하게 로드하지 않습니다. */
final class WatcherSettings {
    private final Method scale, sneak, sprint, pitch, yaw, rabbitType;
    private final Method mainHand, offHand, armor;
    private final java.util.function.Function<Material, ItemStack> items;

    WatcherSettings(ClassLoader loader) throws ReflectiveOperationException {
        this(Class.forName("me.libraryaddict.disguise.disguisetypes.FlagWatcher", false, loader),
                Class.forName("me.libraryaddict.disguise.disguisetypes.watchers.LivingWatcher", false, loader),
                Class.forName("me.libraryaddict.disguise.disguisetypes.watchers.RabbitWatcher", false, loader));
    }

    WatcherSettings(Class<?> flags, Class<?> living, Class<?> rabbit) throws NoSuchMethodException {
        this(flags, living, rabbit, ItemStack::new);
    }

    WatcherSettings(Class<?> flags, Class<?> living, Class<?> rabbit,
                    java.util.function.Function<Material, ItemStack> items) throws NoSuchMethodException {
        this.items = items;
        // watcher.getClass().getMethod(...)는 Cow/Pig/ChickenWatcher의 Variant 메서드도 해석합니다.
        // 1.21.4에는 해당 Bukkit Variant 타입이 없으므로 부모 클래스에서 직접 조회해야 합니다.
        scale = living.getMethod("setScale", Double.class);
        sneak = flags.getMethod("setSneaking", boolean.class);
        sprint = flags.getMethod("setSprinting", boolean.class);
        pitch = flags.getMethod("setPitchLock", Float.class);
        yaw = flags.getMethod("setYawLock", Float.class);
        rabbitType = rabbit.getMethod("setType", Rabbit.Type.class);
        mainHand = flags.getMethod("setItemInMainHand", ItemStack.class);
        offHand = flags.getMethod("setItemInOffHand", ItemStack.class);
        armor = flags.getMethod("setArmor", ItemStack[].class);
    }

    void configure(Object watcher, EntityType type, float facing) throws ReflectiveOperationException {
        scale.invoke(watcher, Double.valueOf(1));
        pitch.invoke(watcher, Float.valueOf(0));
        face(watcher, facing);
        sneak.invoke(watcher, false);
        sprint.invoke(watcher, false);
        if (type == EntityType.RABBIT) rabbitType.invoke(watcher, Rabbit.Type.BROWN);
        // 스켈레톤·스트레이는 AI와 같은 활을 들고, 플레이어 원래 갑옷·손 아이템이 정체를 드러내지 않게 합니다.
        // null은 LibsDisguises에서 원래 플레이어 장비를 상속한다는 뜻이므로 AIR를 명시합니다.
        mainHand.invoke(watcher, items.apply(type == EntityType.SKELETON || type == EntityType.STRAY ? Material.BOW : Material.AIR));
        offHand.invoke(watcher, items.apply(Material.AIR));
        ItemStack[] emptyArmor = new ItemStack[4];
        java.util.Arrays.setAll(emptyArmor, index -> items.apply(Material.AIR));
        armor.invoke(watcher, (Object) emptyArmor);
    }

    void face(Object watcher, float facing) throws ReflectiveOperationException {
        yaw.invoke(watcher, Float.valueOf(facing));
    }
}
