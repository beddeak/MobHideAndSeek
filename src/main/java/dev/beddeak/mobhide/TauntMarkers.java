package dev.beddeak.mobhide;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * 도발한 자리에 잠깐 남는 발광 기둥. 발광 윤곽은 블록을 통과해 보이므로 건물에 가려 폭죽이 안 보여도
 * 위치를 알 수 있습니다. 숨는 사람을 따라가지 않아 도발 후 도망칠 수 있습니다. 월드에 저장하지 않습니다.
 */
final class TauntMarkers {
    private record Marker(Entity entity, long expiresAt) {}
    private final List<Marker> markers = new ArrayList<>();

    static BlockDisplay spawn(Location feet, Color color) {
        return feet.getWorld().spawn(feet.clone(), BlockDisplay.class, display -> {
            display.setBlock(Material.WHITE_CONCRETE.createBlockData());
            // 0.3칸 굵기, 4칸 높이로 발 위치 중앙에 세웁니다. 작물·울타리보다 높아 멀리서도 윤곽이 보입니다.
            display.setTransformation(new Transformation(new Vector3f(-0.15f, 0f, -0.15f), new AxisAngle4f(),
                    new Vector3f(0.3f, 4f, 0.3f), new AxisAngle4f()));
            display.setGlowing(true);
            display.setGlowColorOverride(color);
            display.setBrightness(new Display.Brightness(15, 15));
            // 기본 시야 거리는 화살 정도라 넓은 맵 반대편에서는 사라집니다.
            display.setViewRange(4f);
            display.setPersistent(false);
        });
    }

    void add(Entity marker, long expiresAt) { markers.add(new Marker(marker, expiresAt)); }

    void expire(long tick) {
        markers.removeIf(marker -> {
            if (tick < marker.expiresAt()) return false;
            marker.entity().remove();
            return true;
        });
    }

    void clear() {
        for (Marker marker : markers) marker.entity().remove();
        markers.clear();
    }
}
