package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Rabbit;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

public class WatcherSettingsTest {
    private WatcherSettings settings() throws NoSuchMethodException {
        // Paper 아이템 생성은 실행 중인 서버 레지스트리가 필요하므로 API 경계만 대역 처리합니다.
        return new WatcherSettings(Flags.class, Living.class, RabbitWatcher.class, material -> {
            var item = mock(org.bukkit.inventory.ItemStack.class);
            when(item.getType()).thenReturn(material);
            return item;
        });
    }
    public static class Flags {
        public boolean sneaking = true, sprinting = true;
        public Float pitch, yaw;
        public org.bukkit.inventory.ItemStack mainHand, offHand;
        public org.bukkit.inventory.ItemStack[] armor;
        public void setSneaking(boolean value) { sneaking = value; }
        public void setSprinting(boolean value) { sprinting = value; }
        public void setPitchLock(Float value) { pitch = value; }
        public void setYawLock(Float value) { yaw = value; }
        public void setItemInMainHand(org.bukkit.inventory.ItemStack value) { mainHand = value; }
        public void setItemInOffHand(org.bukkit.inventory.ItemStack value) { offHand = value; }
        public void setArmor(org.bukkit.inventory.ItemStack[] value) { armor = value; }
    }
    public static class Living extends Flags {
        public Double scale;
        public void setScale(Double value) { scale = value; }
    }
    public static class RabbitWatcher extends Living {
        public Rabbit.Type type;
        public void setType(Rabbit.Type value) { type = value; }
    }
    public static class MissingVariant {}
    public static class FutureWatcher extends Living {
        public MissingVariant getVariant() { return null; }
    }

    private Class<?> watcherWithoutNewApi() throws ClassNotFoundException {
        String future = FutureWatcher.class.getName();
        String missing = MissingVariant.class.getName();
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(missing)) throw new ClassNotFoundException(name);
                if (!name.equals(future)) return super.loadClass(name, resolve);
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (in == null) throw new ClassNotFoundException(name);
                        byte[] bytes = in.readAllBytes();
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException e) { throw new ClassNotFoundException(name, e); }
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        };
        return Class.forName(future, true, loader);
    }

    @Test void reproducesTheMissingVariantFailureFromTheServerLog() throws Exception {
        Class<?> watcher = watcherWithoutNewApi();
        NoClassDefFoundError error = assertThrows(NoClassDefFoundError.class,
                () -> watcher.getMethod("setSneaking", boolean.class));
        assertTrue(error.getMessage().contains("MissingVariant"));
    }

    @Test void configuresCowPigAndChickenDespiteMissingSubclassApi() throws Exception {
        var settings = settings();
        for (EntityType type : List.of(EntityType.COW, EntityType.PIG, EntityType.CHICKEN)) {
            Living watcher = (Living) watcherWithoutNewApi().getConstructor().newInstance();
            settings.configure(watcher, type, 90);
            assertEquals(1.0, watcher.scale);
            assertEquals(0f, watcher.pitch);
            assertEquals(90f, watcher.yaw);
            assertFalse(watcher.sneaking);
            assertFalse(watcher.sprinting);
            settings.face(watcher, -45);
            assertEquals(-45f, watcher.yaw);
        }
    }

    @Test void rabbitStillUsesItsOwnCompatibleTypeApi() throws Exception {
        var settings = settings();
        var watcher = new RabbitWatcher();
        settings.configure(watcher, EntityType.RABBIT, 180);
        assertEquals(Rabbit.Type.BROWN, watcher.type);
        assertEquals(1.0, watcher.scale);
    }

    @Test void disguisesUseExplicitEmptyEquipmentInsteadOfInheritingThePlayersGear() throws Exception {
        var settings = settings();
        for (EntityType type : List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CHICKEN)) {
            var watcher = new Living();
            settings.configure(watcher, type, 0);
            assertEquals(type == EntityType.SKELETON ? org.bukkit.Material.BOW : org.bukkit.Material.AIR, watcher.mainHand.getType());
            assertEquals(org.bukkit.Material.AIR, watcher.offHand.getType());
            assertEquals(4, watcher.armor.length);
            for (var item : watcher.armor) assertEquals(org.bukkit.Material.AIR, item.getType());
        }
    }
}
