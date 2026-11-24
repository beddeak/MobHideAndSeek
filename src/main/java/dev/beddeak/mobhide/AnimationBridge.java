package dev.beddeak.mobhide;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.EntityPositionData;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/** 외형/생성은 LD, 이동/회전/도약은 이 브리지가 담당합니다. 일반 AI 패킷은 변경하지 않습니다. */
final class AnimationBridge extends PacketListenerAbstract {
    private static final int RESYNC_TICKS = 200;
    private final int selfId;
    // 네트워크 스레드에는 ID만 게시하고 Bukkit 상태를 조회하지 않습니다.
    private final Map<Integer, UUID> controlled = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> owners = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<View> invalidated = new ConcurrentLinkedQueue<>();
    // 아래 가변 상태는 메인 스레드 전용입니다. 상대 이동 기준점은 관전자별로 따로 유지합니다.
    private final Map<View, Pose> poses = new HashMap<>();
    private final Map<Integer, Long> ticks = new HashMap<>();
    private record View(int sourceId, UUID viewer) {}
    private record Pose(long x, long y, long z, int yaw, int headYaw, int pitch, boolean grounded, UUID world, long syncedAt) {}

    AnimationBridge(int selfId) { super(PacketListenerPriority.LOWEST); this.selfId = selfId; }
    static boolean controls(EntityType type) { return MobCatalog.ALL.contains(type); }
    void track(Player player, EntityType type) {
        remove(player);
        if (controls(type)) {
            controlled.put(player.getEntityId(), player.getUniqueId());
            owners.put(player.getUniqueId(), player.getEntityId());
        }
    }
    void reset(Player player) { poses.keySet().removeIf(view -> view.sourceId() == player.getEntityId()); }
    void resetViewer(Player source, Player viewer) { poses.remove(new View(source.getEntityId(), viewer.getUniqueId())); }
    void remove(Player player) {
        controlled.remove(player.getEntityId());
        owners.remove(player.getUniqueId());
        ticks.remove(player.getEntityId());
        reset(player);
    }
    void clear() { controlled.clear(); owners.clear(); invalidated.clear(); poses.clear(); ticks.clear(); }

    @Override public void onPacketSend(PacketSendEvent event) {
        if (controlled.isEmpty() || event.isCancelled() || !(event.getPacketType() instanceof PacketType.Play.Server type)) return;
        UUID viewer = event.getUser().getUUID();
        if (type == PacketType.Play.Server.DESTROY_ENTITIES) {
            for (int id : new WrapperPlayServerDestroyEntities(event).getEntityIds()) invalidate(id, viewer);
            return;
        }
        if (type == PacketType.Play.Server.SPAWN_ENTITY) {
            invalidate(new WrapperPlayServerSpawnEntity(event).getEntityId(), viewer);
            return;
        }
        if (type == PacketType.Play.Server.ENTITY_ANIMATION) {
            var animation = new WrapperPlayServerEntityAnimation(event);
            var kind = animation.getType();
            int source = animation.getEntityId();
            // 게임 AI는 공격하지 않습니다. LD는 팔 휘두르기를 몹 외형에 그대로 전달하므로
            // 숨는 플레이어가 클릭하면 좀비·스켈레톤 외형이 팔을 휘둘러 정체가 드러납니다.
            if ((kind == WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM
                    || kind == WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_OFF_HAND)
                    && (controlled.containsKey(source) || source == selfId && owners.containsKey(viewer))) event.setCancelled(true);
            return;
        }
        int id = switch (type) {
            case ENTITY_RELATIVE_MOVE -> new WrapperPlayServerEntityRelativeMove(event).getEntityId();
            case ENTITY_RELATIVE_MOVE_AND_ROTATION -> new WrapperPlayServerEntityRelativeMoveAndRotation(event).getEntityId();
            case ENTITY_ROTATION -> new WrapperPlayServerEntityRotation(event).getEntityId();
            case ENTITY_TELEPORT -> new WrapperPlayServerEntityTeleport(event).getEntityId();
            case ENTITY_POSITION_SYNC -> new WrapperPlayServerEntityPositionSync(event).getId();
            case ENTITY_HEAD_LOOK -> new WrapperPlayServerEntityHeadLook(event).getEntityId();
            default -> Integer.MIN_VALUE;
        };
        // LD보다 먼저 막아야 silently 생성하는 자동 RABBIT_HOP까지 중복되지 않습니다.
        // 자기 변신 ID는 모든 플레이어가 공유하므로 수신자의 소유 여부도 확인합니다.
        if (controlled.containsKey(id) || id == selfId && owners.containsKey(viewer)) event.setCancelled(true);
    }

    private void invalidate(int id, UUID viewer) {
        Integer source = id == selfId ? owners.get(viewer) : controlled.containsKey(id) ? id : null;
        if (source != null) invalidated.add(new View(source, viewer));
    }

    void render(Player player, boolean selfVisible, float yaw, boolean grounded, boolean hop) {
        render(player, selfVisible, yaw, yaw, 0, grounded, hop);
    }

    void render(Player player, boolean selfVisible, float yaw, float headYaw, float pitch, boolean grounded, boolean hop) {
        if (!controlled.containsKey(player.getEntityId())) return;
        View invalid;
        while ((invalid = invalidated.poll()) != null) poses.remove(invalid);
        long tick = ticks.merge(player.getEntityId(), 1L, Long::sum);
        Set<UUID> viewers = new HashSet<>();
        Location at = player.getLocation();
        for (Player viewer : player.getTrackedBy()) {
            if (!viewer.equals(player)) {
                viewers.add(viewer.getUniqueId());
                send(player, viewer, player.getEntityId(), at, yaw, headYaw, pitch, grounded, hop, tick);
            }
        }
        if (selfVisible) {
            viewers.add(player.getUniqueId());
            send(player, player, selfId, at, yaw, headYaw, pitch, grounded, hop, tick);
        }
        poses.keySet().removeIf(view -> view.sourceId() == player.getEntityId() && !viewers.contains(view.viewer()));
    }

    private void send(Player source, Player viewer, int id, Location at, float yaw, float headYaw, float pitch, boolean grounded, boolean hop, long tick) {
        var manager = PacketEvents.getAPI().getPlayerManager();
        View key = new View(source.getEntityId(), viewer.getUniqueId());
        Pose old = poses.get(key);
        // 클라이언트와 같은 1/4096 단위로 누적하여 작은 이동의 버림 오차를 방지합니다.
        long x = encode(at.getX()), y = encode(at.getY()), z = encode(at.getZ());
        int angle = angle(yaw);
        int headAngle = angle(headYaw), pitchAngle = (byte) angle(pitch);
        float facing = angle * (360f / 256f);
        float headFacing = headAngle * (360f / 256f), tilt = pitchAngle * (360f / 256f);
        UUID world = at.getWorld().getUID();
        long dx = old == null ? 0 : x - old.x(), dy = old == null ? 0 : y - old.y(), dz = old == null ? 0 : z - old.z();
        boolean moved = dx != 0 || dy != 0 || dz != 0;
        boolean rotated = old == null || old.yaw() != angle || old.pitch() != pitchAngle;
        boolean headRotated = old == null || old.headYaw() != headAngle;
        boolean sync = old == null || !old.world().equals(world) || !fits(dx) || !fits(dy) || !fits(dz)
                || moved && tick - old.syncedAt() >= RESYNC_TICKS;
        if (sync) {
            var velocity = source.getVelocity();
            var data = new EntityPositionData(new Vector3d(x / 4096d, y / 4096d, z / 4096d),
                    new Vector3d(velocity.getX(), grounded ? 0 : velocity.getY(), velocity.getZ()), facing, tilt);
            manager.sendPacketSilently(viewer, new WrapperPlayServerEntityPositionSync(id, data, grounded));
        } else if (moved) {
            manager.sendPacketSilently(viewer, new WrapperPlayServerEntityRelativeMoveAndRotation(
                    id, dx / 4096d, dy / 4096d, dz / 4096d, facing, tilt, grounded));
        } else if (rotated || old.grounded() != grounded) {
            // 제자리 착지에도 지면 상태를 보내야 닭 날갯짓이 멈춥니다.
            manager.sendPacketSilently(viewer, new WrapperPlayServerEntityRotation(id, facing, tilt, grounded));
        }
        // 머리만 둘러볼 때는 몸 회전/위치 패킷을 보내지 않습니다. 시선의 상하각만 rotation에 포함됩니다.
        if (sync || headRotated) manager.sendPacketSilently(viewer, new WrapperPlayServerEntityHeadLook(id, headFacing));
        if (hop) manager.sendPacketSilently(viewer, new WrapperPlayServerEntityStatus(id, 1));
        poses.put(key, new Pose(x, y, z, angle, headAngle, pitchAngle, grounded, world, sync ? tick : old.syncedAt()));
    }

    private static long encode(double value) { return Math.round(value * 4096); }
    private static int angle(float value) { return (int) (((value % 360 + 360) % 360) * (256f / 360f)) & 255; }
    private static boolean fits(long value) { return value >= Short.MIN_VALUE && value <= Short.MAX_VALUE; }
    static boolean grounded(Player player) { return GroundContact.grounded(player); }
}
