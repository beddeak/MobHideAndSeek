package dev.beddeak.mobhide;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

final class ArenaSetup implements Listener {
    private final MobHidePlugin plugin;
    private final NamespacedKey wandKey;
    private final Map<UUID, Location[]> selections = new HashMap<>();
    ArenaSetup(MobHidePlugin plugin) { this.plugin = plugin; wandKey = new NamespacedKey(plugin, "selection_wand"); }

    void command(Player player, String action) {
        if (plugin.running()) { message(player, "영역 설정은 게임 종료 후 가능합니다."); return; }
        if (action.equals("wand")) {
            ItemStack wand = new ItemStack(Material.GOLDEN_AXE);
            var meta = wand.getItemMeta();
            meta.setDisplayName("§6몹 숨바꼭질 영역 선택 도끼");
            meta.setLore(List.of("§f좌클릭: 첫 지점 / 우클릭: 두 번째 지점", "§f두 지점 선택 후 /mh setarena", "§f높이 전체 선택: /mh expandvert"));
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
            wand.setItemMeta(meta);
            if (!player.getInventory().addItem(wand).isEmpty()) { message(player, "인벤토리 빈 칸을 만든 뒤 다시 실행하세요."); return; }
            message(player, "도끼로 대각선 두 모서리를 좌/우클릭하세요. 월드에딧 설치는 필요 없습니다.");
        } else if (action.equals("selclear")) {
            selections.remove(player.getUniqueId()); message(player, "선택을 초기화했습니다. 저장된 경기장은 유지됩니다.");
        } else if (action.equals("expandvert")) {
            Location[] points = selections.get(player.getUniqueId());
            if (points == null || points[0] == null || points[1] == null) { message(player, "먼저 두 지점을 선택하세요."); return; }
            points[0].setY(points[0].getWorld().getMinHeight());
            points[1].setY(points[0].getWorld().getMaxHeight() - 1);
            message(player, "선택 높이를 월드 전체 높이로 확장했습니다. 영역 안에서 /mh setarena 로 저장하세요.");
        } else select(player, action.equals("pos1") ? 0 : 1, player.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void click(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (event.getHand() != EquipmentSlot.HAND || !isWand(item)) return;
        event.setCancelled(true);
        if (!event.getPlayer().hasPermission("mobhide.admin") || plugin.running()) return;
        if (event.getClickedBlock() == null) return;
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) select(event.getPlayer(), 0, event.getClickedBlock().getLocation());
        else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) select(event.getPlayer(), 1, event.getClickedBlock().getLocation());
    }
    private boolean isWand(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void breakBlock(BlockBreakEvent event) {
        if (isWand(event.getPlayer().getInventory().getItemInMainHand())) event.setCancelled(true);
    }
    private void select(Player player, int index, Location location) {
        Location[] points = selections.computeIfAbsent(player.getUniqueId(), key -> new Location[2]);
        if (points[1 - index] != null && points[1 - index].getWorld() != location.getWorld()) {
            points[1 - index] = null; message(player, "월드가 달라 이전 선택 지점을 초기화했습니다.");
        }
        points[index] = location.getBlock().getLocation();
        message(player, (index + 1) + "번 지점: " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ()
                + " / 두 지점 선택 후 영역 안에서 /mh setarena");
    }
    boolean hasSelection(Player player) { return selections.containsKey(player.getUniqueId()); }
    CuboidBoundary selection(Player player) {
        Location[] points = selections.get(player.getUniqueId());
        if (points == null || points[0] == null || points[1] == null) throw new IllegalArgumentException("두 지점을 모두 선택하세요.");
        if (points[0].getWorld() != player.getWorld()) throw new IllegalArgumentException("선택한 월드에서 저장하세요.");
        return CuboidBoundary.between(points[0].getBlockX(), points[0].getBlockY(), points[0].getBlockZ(),
                points[1].getBlockX(), points[1].getBlockY(), points[1].getBlockZ());
    }
    @EventHandler public void quit(PlayerQuitEvent event) { selections.remove(event.getPlayer().getUniqueId()); }
    private static void message(Player player, String text) { player.sendMessage("§6[몹 숨바꼭질] §f" + text); }
}
