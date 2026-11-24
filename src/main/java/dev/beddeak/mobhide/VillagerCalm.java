package dev.beddeak.mobhide;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.entity.Villager;

/**
 * 주민 AI는 좀비·허스크 AI를 보면 도망칩니다(VillagerHostilesSensor → NEAREST_HOSTILE 기억 → VillagerPanicTrigger).
 * 좀비로 변신한 사람에게서는 도망치지 않아 정체가 드러나므로, 게임이 소환한 주민에게서만 적 감지 센서를 뺍니다.
 * 맞아서 도망치는 조건(HURT_BY)은 피해를 취소하므로 생기지 않습니다. Paper 1.21.4는 Mojang 이름으로 실행되며
 * 이름은 서버 JAR에서 확인했습니다. 조회에 실패하면 경고만 남기고 기존처럼 동작합니다.
 */
final class VillagerCalm {
    private final Method level;
    private final Method refreshBrain;
    private final Method getBrain;
    private final Field sensors;
    private final Object hostileSensor;
    private final Method eraseMemory;
    private final Object hostileMemory;

    private VillagerCalm(Method level, Method refreshBrain, Method getBrain, Field sensors, Object hostileSensor, Method eraseMemory, Object hostileMemory) {
        this.level = level;
        this.refreshBrain = refreshBrain;
        this.getBrain = getBrain;
        this.sensors = sensors;
        this.hostileSensor = hostileSensor;
        this.eraseMemory = eraseMemory;
        this.hostileMemory = hostileMemory;
    }

    static VillagerCalm create(Logger logger) {
        try {
            Class<?> entity = Class.forName("net.minecraft.world.entity.Entity");
            Class<?> villager = Class.forName("net.minecraft.world.entity.npc.Villager");
            Class<?> serverLevel = Class.forName("net.minecraft.server.level.ServerLevel");
            Class<?> living = Class.forName("net.minecraft.world.entity.LivingEntity");
            Class<?> brain = Class.forName("net.minecraft.world.entity.ai.Brain");
            Class<?> sensorType = Class.forName("net.minecraft.world.entity.ai.sensing.SensorType");
            Class<?> memoryType = Class.forName("net.minecraft.world.entity.ai.memory.MemoryModuleType");
            Field sensors = brain.getDeclaredField("sensors");
            sensors.setAccessible(true);
            return new VillagerCalm(entity.getMethod("level"), villager.getMethod("refreshBrain", serverLevel), living.getMethod("getBrain"), sensors, sensorType.getField("VILLAGER_HOSTILES").get(null),
                    brain.getMethod("eraseMemory", memoryType), memoryType.getField("NEAREST_HOSTILE").get(null));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logger.warning("주민 AI의 좀비 감지를 끄지 못했습니다. 주민과 좀비·허스크를 같이 쓰면 주민 AI가 도망칠 수 있습니다: " + e);
            return null;
        }
    }

    /** 세계에 추가되기 전(소환 콜백)에 부릅니다. 주민 두뇌를 다시 만들 때도 현재 센서 목록을 그대로 복사합니다. */
    void calm(Villager villager) throws ReflectiveOperationException {
        Object handle = villager.getClass().getMethod("getHandle").invoke(villager);
        // 직업을 바꿔도 두뇌는 처음 직업 기준으로 남아 작업대를 계속 찾습니다. 새 직업(멍청이) 기준으로 다시 만듭니다.
        refreshBrain.invoke(handle, level.invoke(handle));
        Object brain = getBrain.invoke(handle);
        ((Map<?, ?>) sensors.get(brain)).remove(hostileSensor);
        eraseMemory.invoke(brain, hostileMemory);
    }
}
