package dev.beddeak.mobhide;

import com.destroystokyo.paper.event.entity.CreeperIgniteEvent;
import java.util.Objects;
import java.util.function.Predicate;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockShearEntityEvent;
import org.bukkit.event.entity.CreeperPowerEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;

/** 게임 소유 AI의 외형·장비 변경과 종료 뒤 남는 번식·아이템 부산물을 막습니다. */
final class DecoyProtection implements Listener {
    private final Predicate<Entity> owned;

    DecoyProtection(Predicate<Entity> owned) {
        this.owned = Objects.requireNonNull(owned);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        // 먹이·염료·양동이·안장·이름표·목줄은 바닐라가 아이템을 쓰기 전에 막습니다.
        if (owned.test(event.getRightClicked())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        // 위치별 상호작용은 별도 HandlerList이므로 두 패킷 경로를 모두 처리합니다.
        onInteract(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerShear(PlayerShearEntityEvent event) {
        if (owned.test(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockShear(BlockShearEntityEvent event) {
        if (owned.test(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLove(EntityEnterLoveModeEvent event) {
        if (owned.test(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (owned.test(event.getMother()) || owned.test(event.getFather())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(EntityDropItemEvent event) {
        // 샌드박스에서도 닭의 달걀 등이 종료 정리 밖에 남지 않게 합니다.
        if (owned.test(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCareer(VillagerCareerChangeEvent event) {
        // 맵의 통·작업대 등을 차지해 직업을 얻으면 옷이 바뀌어 무직으로 고정한 변신 외형과 달라집니다.
        if (owned.test(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(CreeperIgniteEvent event) {
        if (event.isIgnited() && owned.test(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPower(CreeperPowerEvent event) {
        if ((event.getCause() == CreeperPowerEvent.PowerCause.LIGHTNING
                || event.getCause() == CreeperPowerEvent.PowerCause.SET_ON)
                && owned.test(event.getEntity())) event.setCancelled(true);
    }
}
