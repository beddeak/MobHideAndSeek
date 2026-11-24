package dev.beddeak.mobhide;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/** LibsDisguises의 공개 API만 호출하며 내부 패킷이나 버전별 NMS를 사용하지 않습니다. */
final class Disguises {
    private final Class<?> mobDisguise;
    private final Class<?> disguiseType;
    private final Method disguiseToAll;
    private final Method undisguiseToAll;
    private final Method isDisguised;
    private final Method selfId;
    private final Method viewSelf;
    private final Method selfVisible;
    private final Method selfSpawned;
    private final Method getWatcher;
    private final WatcherSettings watcherSettings;
    private final WatcherAppearance appearance;
    private final Method scaleToMob;
    private final Method modifyBox;
    private final Method setSelfSize;
    private final Object visibleSelfSize;
    private final Map<UUID, Object> owned = new HashMap<>();

    Disguises(ClassLoader loader, java.util.logging.Logger logger) throws ReflectiveOperationException {
        Class<?> api = Class.forName("me.libraryaddict.disguise.DisguiseAPI", true, loader);
        Class<?> disguise = Class.forName("me.libraryaddict.disguise.disguisetypes.Disguise", true, loader);
        disguiseType = Class.forName("me.libraryaddict.disguise.disguisetypes.DisguiseType", true, loader);
        mobDisguise = Class.forName("me.libraryaddict.disguise.disguisetypes.MobDisguise", true, loader);
        mobDisguise.getConstructor(disguiseType);
        mobDisguise.getMethod("setViewSelfDisguise", boolean.class);
        disguiseToAll = api.getMethod("disguiseToAll", Entity.class, disguise);
        undisguiseToAll = api.getMethod("undisguiseToAll", Entity.class);
        isDisguised = api.getMethod("isDisguised", Entity.class);
        selfId = api.getMethod("getSelfDisguiseId");
        viewSelf = disguise.getMethod("setViewSelfDisguise", boolean.class);
        selfVisible = disguise.getMethod("isSelfDisguiseVisible");
        selfSpawned = api.getMethod("isSelfDisguised", Player.class);
        getWatcher = disguise.getMethod("getWatcher");
        watcherSettings = new WatcherSettings(loader);
        appearance = new WatcherAppearance(loader, logger);
        scaleToMob = disguise.getMethod("setScalePlayerToDisguise", boolean.class);
        modifyBox = disguise.getMethod("setModifyBoundingBox", boolean.class);
        Class<?> selfSize = Class.forName("me.libraryaddict.disguise.DisguiseConfig$TallSelfDisguise", true, loader);
        visibleSelfSize = selfSize.getField("VISIBLE").get(null);
        setSelfSize = disguise.getMethod("setTallSelfDisguise", selfSize);
    }

    boolean isDisguised(Player player) throws ReflectiveOperationException {
        return (boolean) isDisguised.invoke(null, player);
    }

    void disguise(Player player, EntityType type, boolean viewSelf) throws ReflectiveOperationException {
        Object kind = disguiseType.getField(MobCatalog.disguiseName(type)).get(null);
        Object disguise = mobDisguise.getConstructor(disguiseType).newInstance(kind);
        // 서버 판정은 해당 몹의 기본 크기로, 클라이언트 몸/시점도 해당 몹 높이에 맞춥니다.
        modifyBox.invoke(disguise, true);
        scaleToMob.invoke(disguise, true);
        setSelfSize.invoke(disguise, visibleSelfSize);
        this.viewSelf.invoke(disguise, viewSelf);
        Object watcher = getWatcher.invoke(disguise);
        // 실제 플레이어 축소가 몹 외형까지 중복해서 축소하지 않도록 외형은 기본 크기로 고정합니다.
        watcherSettings.configure(watcher, type, player.getLocation().getYaw());
        appearance.apply(watcher, type);
        // API가 일부 적용 후 실패해도 시작 실패 정리에서 크기/변신을 회수할 수 있게 먼저 기록합니다.
        owned.put(player.getUniqueId(), disguise);
        disguiseToAll.invoke(null, player, disguise);
        // API가 적용하는 기존 자기 변신 표시 선호값보다 이번 모드의 표시 설정을 우선합니다.
        this.viewSelf.invoke(disguise, viewSelf);
        scaleToMob.invoke(disguise, true);
        if (!isDisguised(player)) throw new IllegalStateException("LibsDisguises did not disguise " + player.getName());
    }

    /**
     * LibsDisguises는 새 관전자에게 보내는 생성 패킷에 고정 방향(yaw lock)을 씁니다. 변신 때 방향으로 남아 있으면
     * 멀리서 처음 보이는 멈춘 숨는 플레이어의 몸이 엉뚱한 쪽을 보다가 돌아가므로 현재 몸 방향으로 갱신합니다.
     */
    void face(Player player, float yaw) throws ReflectiveOperationException {
        Object disguise = owned.get(player.getUniqueId());
        if (disguise != null) watcherSettings.face(getWatcher.invoke(disguise), yaw);
    }

    boolean toggleView(Player player) throws ReflectiveOperationException {
        Object disguise = owned.get(player.getUniqueId());
        if (disguise == null) throw new IllegalStateException("No owned disguise");
        boolean visible = !(boolean) selfVisible.invoke(disguise);
        viewSelf.invoke(disguise, visible);
        return visible;
    }

    int selfId() throws ReflectiveOperationException { return (int) selfId.invoke(null); }
    boolean selfVisible(Player player) throws ReflectiveOperationException {
        Object disguise = owned.get(player.getUniqueId());
        return disguise != null && (boolean) selfVisible.invoke(disguise) && (boolean) selfSpawned.invoke(null, player);
    }

    void remove(Player player) throws ReflectiveOperationException {
        try {
            Object disguise = owned.get(player.getUniqueId());
            if (disguise != null) scaleToMob.invoke(disguise, false);
        } finally {
            try { undisguiseToAll.invoke(null, player); }
            finally { owned.remove(player.getUniqueId()); }
        }
    }
}
