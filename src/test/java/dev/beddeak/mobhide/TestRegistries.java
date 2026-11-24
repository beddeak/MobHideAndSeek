package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.potion.PotionEffectType;
import org.mockito.MockMakers;
import org.mockito.MockedStatic;

/** Minimal keyed registry values for API event tests; no Minecraft server registry is available in this JVM. */
final class TestRegistries {
    private static boolean initialized;

    private TestRegistries() {}

    static synchronized void initialize() {
        if (initialized) return;
        Map<Object, Object> registries = new HashMap<>();
        RegistryAccess access = mock(RegistryAccess.class, invocation -> {
            if (!invocation.getMethod().getName().equals("getRegistry")) return null;
            return registries.computeIfAbsent(invocation.getArgument(0), TestRegistries::registry);
        });
        try (MockedStatic<RegistryAccess> statics = mockStatic(RegistryAccess.class, withSettings().mockMaker(MockMakers.INLINE))) {
            statics.when(RegistryAccess::registryAccess).thenReturn(access);
            // Initialize API constants while their registry provider is installed.
            assertNotNull(PotionEffectType.BLINDNESS);
            assertNotNull(Attribute.MAX_HEALTH);
            assertNotNull(Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
            // 새 몹의 외형 고정 값(주민 직업·옷, 고양이·늑대 무늬)도 레지스트리 상수입니다.
            assertNotNull(org.bukkit.entity.Villager.Type.PLAINS);
            assertNotNull(org.bukkit.entity.Villager.Profession.NONE);
            assertNotNull(org.bukkit.entity.Villager.Profession.NITWIT);
            assertNotNull(org.bukkit.entity.Cat.Type.TABBY);
            assertNotNull(org.bukkit.entity.Wolf.Variant.PALE);
        }
        initialized = true;
    }

    private static Object registry(Object kind) {
        Map<Object, Object> values = new HashMap<>();
        return Proxy.newProxyInstance(Registry.class.getClassLoader(), new Class<?>[] {Registry.class}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "get", "getOrThrow" -> values.computeIfAbsent(args[0], key -> value(kind, (NamespacedKey) key));
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "TestRegistry[" + kind + "]";
                default -> null;
            };
        });
    }

    private static Object value(Object kind, NamespacedKey key) {
        if (kind.equals(RegistryKey.MOB_EFFECT)) {
            PotionEffectType type = mock(PotionEffectType.class, withSettings().mockMaker(MockMakers.SUBCLASS));
            when(type.getKey()).thenReturn(key);
            when(type.getName()).thenReturn(key.toString());
            return type;
        }
        if (kind.equals(RegistryKey.ATTRIBUTE)) {
            Attribute attribute = mock(Attribute.class, withSettings().mockMaker(MockMakers.SUBCLASS));
            when(attribute.getKey()).thenReturn(key);
            return attribute;
        }
        if (kind.equals(RegistryKey.SOUND_EVENT)) {
            Sound sound = mock(Sound.class, withSettings().mockMaker(MockMakers.SUBCLASS));
            when(sound.getKey()).thenReturn(key);
            return sound;
        }
        Class<?> type = kind.equals(RegistryKey.VILLAGER_TYPE) ? org.bukkit.entity.Villager.Type.class
                : kind.equals(RegistryKey.VILLAGER_PROFESSION) ? org.bukkit.entity.Villager.Profession.class
                : kind.equals(RegistryKey.CAT_VARIANT) ? org.bukkit.entity.Cat.Type.class
                : kind.equals(RegistryKey.WOLF_VARIANT) ? org.bukkit.entity.Wolf.Variant.class : null;
        if (type != null) {
            org.bukkit.Keyed value = (org.bukkit.Keyed) mock(type, withSettings().mockMaker(MockMakers.SUBCLASS));
            when(value.getKey()).thenReturn(key);
            return value;
        }
        throw new IllegalArgumentException("Unsupported test registry value: " + kind + " / " + key);
    }
}
