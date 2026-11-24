package dev.beddeak.mobhide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

final class AdminPanel implements Listener {
    private final MobHidePlugin plugin;
    private static final class Page implements InventoryHolder {
        final UUID owner;
        final Inventory inventory;
        final Map<Integer, Consumer<Boolean>> actions = new HashMap<>();
        Page(Player player, String title) { owner = player.getUniqueId(); inventory = Bukkit.createInventory(this, 54, title); }
        public Inventory getInventory() { return inventory; }
    }
    AdminPanel(MobHidePlugin plugin) { this.plugin = plugin; }
    void open(Player player) {
        if (!player.hasPermission("mobhide.admin")) return;
        Page page = new Page(player, "몹 숨바꼭질 관리자");
        button(page, 4, Material.COMMAND_BLOCK, plugin.running() ? (plugin.sandboxRunning() ? "샌드박스 진행 중" : "경기 진행 중") : "대기 중",
                "좌클릭 + / 우클릭 - | 실시간 개입 경기는 승점 제외", null);
        button(page, 10, Material.LIME_DYE, "랜덤 맵 경기 시작", "맵 자동 선정·이름 안내 후 시작 / 종료 시 로비 복귀", right -> command(player, "start"));
        button(page, 16, Material.FILLED_MAP, "랜덤 맵 목록", "등록된 모든 맵 확인 / 기존 맵과 추가 테마 포함", right -> command(player, "maps"));
        button(page, 11, Material.CRAFTING_TABLE, "샌드박스 시작", "시간·승패로 자동 종료하지 않음 / 승점 제외", right -> command(player, "sandbox"));
        button(page, 12, Material.BARRIER, "게임 종료", "참가자 원래 상태 복구", right -> command(player, "stop"));
        button(page, 14, Material.ZOMBIE_SPAWN_EGG, "랜덤 몹 목록", "다음 경기의 변신·맵 AI 종류 선택", right -> pool(player));
        button(page, 15, Material.COMPASS, "술래 지정", "접속 플레이어 선택 / 랜덤 선택 가능", right -> players(player, 0, true));
        button(page, 13, Material.WIND_CHARGE, "재해 이벤트", "경기 중 토네이도·유성우·지진·낙뢰 / 빨간 경고 3초 뒤 시작", right -> disasters(player));
        number(page, player, 19, Material.RED_DYE, "숨는 팀 피격 기회", "hider-hit-limit", 2, 1, 20, 1, "진행 중 변경하면 살아 있는 숨는 팀의 기회 복구");
        number(page, player, 20, Material.GOLDEN_APPLE, "술래 하트·오답 기회", "seeker-mistake-limit", 20, 1, 20, 1, "1회당 한 하트 / 진행 중 즉시 회복");
        number(page, player, 21, Material.SUGAR, "숨는 팀 이동 속도 배율", "speed-multiplier", 1, 0.25, 3, 0.25, "몹별 기본 속도 기준 / 진행 중 즉시 적용");
        number(page, player, 22, Material.CLOCK, "추격 시간(초)", "round-seconds", 180, 30, 3600, 30, "다음 경기부터 적용");
        number(page, player, 23, Material.EGG, "맵 랜덤 AI 수", "decoy-count", 25, 1, 100, 5, "다음 경기부터 적용 / 변신 종류별 AI 최소 한 마리");
        number(page, player, 24, Material.SPYGLASS, "준비 시간(초)", "hide-seconds", 15, 0, 60, 5, "다음 경기부터 적용");
        number(page, player, 25, Material.FIREWORK_ROCKET, "도발 쿨타임(초)", "taunt.cooldown-seconds", 10, 5, 120, 5, "숨는 팀 F 폭죽 간격 / 진행 중 즉시 적용");
        number(page, player, 26, Material.FIREWORK_STAR, "도발 점수", "taunt.points", 5, 0, 20, 1, "0이면 도발 끔 / 진행 중 즉시 적용");
        number(page, player, 33, Material.GLOW_INK_SAC, "도발 발광(초)", "taunt.glow-seconds", 5, 0, 15, 1, "도발한 자리에 벽 너머로 보이는 기둥 / 0이면 끔");
        button(page, 28, Material.GOLDEN_AXE, "영역 선택 도끼 받기", "좌/우클릭 두 지점 → /mh setarena", right -> command(player, "wand"));
        button(page, 29, Material.GRASS_BLOCK, "경기장 저장 안내", "/mh setarena 후 /mh mapsave <ID> <이름>으로 맵 목록에 반영", right -> {
            player.closeInventory(); player.sendMessage("§e/mh wand → 두 지점 선택 → /mh setarena → /mh spawnheight 64 64 → /mh mapsave <ID> <이름>");
        });
        button(page, 31, Material.PLAYER_HEAD, "개별 플레이어 설정", "몹·피격 기회·속도·술래 / 탈락자 변신 변경 시 부활", right -> players(player, 0, false));
        button(page, 32, Material.GOLD_INGOT, "점수 리더보드", "판마다 점수 누적 / 재시작 후에도 유지", right -> board(player));
        button(page, 34, Material.MILK_BUCKET, "게임 설정 초기화", "숫자·몹 목록·지정 술래를 기본값으로 / 확인 화면이 열림", right -> confirmReset(player));
        player.openInventory(page.inventory);
    }
    private void number(Page page, Player player, int slot, Material icon, String label, String key,
                        double fallback, double min, double max, double step, String description) {
        double value = plugin.configuredNumber(key, fallback);
        button(page, slot, icon, label + ": " + value, description, right -> {
            double current = plugin.configuredNumber(key, fallback);
            plugin.adminNumber(player, key, Math.max(min, Math.min(max, current + (right ? -step : step)))); open(player);
        });
    }
    private void disasters(Player player) {
        Page page = new Page(player, "재해 이벤트");
        button(page, 4, Material.RED_BANNER, plugin.running() ? "클릭하면 \"무언가가 다가옵니다...\" 경고 3초 뒤 시작" : "경기 중에만 일으킬 수 있습니다",
                "플레이어와 AI 몹에 똑같이 작용 / 피해·블록 변화 없음 / 한 번에 하나씩", null);
        button(page, 11, Material.ENDER_EYE, "랜덤", "네 종류 중 하나", right -> command(player, "disaster", "random"));
        button(page, 12, Material.WIND_CHARGE, "토네이도", "13초 동안 경기장을 가로지르며 반경 안을 끌어올려 돌림", right -> command(player, "disaster", "tornado"));
        button(page, 13, Material.MAGMA_BLOCK, "유성우", "11초 동안 1초마다 불덩이 / 연기 나는 자리에 떨어져 주변을 날림", right -> command(player, "disaster", "meteor"));
        button(page, 14, Material.CRACKED_STONE_BRICKS, "지진", "9초 동안 모두 비틀거림 / 발밑 먼지", right -> command(player, "disaster", "quake"));
        button(page, 15, Material.LIGHTNING_ROD, "낙뢰", "10초 동안 0.9초마다 번개(불·피해 없음) / 절반은 누군가 근처", right -> command(player, "disaster", "lightning"));
        back(page, player);
        player.openInventory(page.inventory);
    }
    private void pool(Player player) {
        Page page = new Page(player, "랜덤 변신·맵 AI 몹 목록");
        List<String> selected = plugin.getConfig().getStringList("mob-types");
        for (int i = 0; i < MobCatalog.ALL.size(); i++) {
            EntityType type = MobCatalog.ALL.get(i);
            button(page, 10 + (i / 7) * 9 + i % 7, Material.valueOf(type.name() + "_SPAWN_EGG"), type + (selected.contains(type.name()) ? " §a사용" : " §c제외"),
                    "클릭하여 전환 / 다음 경기 적용", right -> {
                        List<String> current = new ArrayList<>(plugin.getConfig().getStringList("mob-types"));
                        if (current.contains(type.name())) {
                            if (current.size() <= 1) { player.sendMessage("§c최소 한 종류는 남겨야 합니다."); return; }
                            current.remove(type.name());
                        } else current.add(type.name());
                        plugin.getConfig().set("mob-types", current); plugin.saveConfig(); pool(player);
                    });
        }
        back(page, player); player.openInventory(page.inventory);
    }
    private void players(Player player, int index, boolean seeker) {
        Page page = new Page(player, seeker ? "술래 선택" : "플레이어 선택");
        List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        if (seeker) button(page, 4, Material.COMPASS, "술래 랜덤", "다음 경기부터 적용", right -> { plugin.adminSeeker(player, null); open(player); });
        for (int i = index * 28; i < Math.min(online.size(), (index + 1) * 28); i++) {
            Player target = online.get(i);
            button(page, 9 + i - index * 28, Material.PLAYER_HEAD, target.getName(), seeker ? "이 플레이어를 술래로 지정" : "몹·피·속도·역할 설정", right -> {
                if (!target.isOnline()) { open(player); return; }
                if (seeker) { plugin.adminSeeker(player, target); open(player); } else person(player, target);
            });
        }
        if (index > 0) button(page, 45, Material.ARROW, "이전", "", right -> players(player, index - 1, seeker));
        if (online.size() > (index + 1) * 28) button(page, 53, Material.ARROW, "다음", "", right -> players(player, index + 1, seeker));
        back(page, player); player.openInventory(page.inventory);
    }
    private void person(Player admin, Player target) {
        if (!target.isOnline()) { open(admin); return; }
        Page page = new Page(admin, target.getName() + " 설정");
        String prefix = "players." + target.getUniqueId();
        String selected = plugin.getConfig().getString(prefix + ".mob", "RANDOM");
        button(page, 10, Material.EGG, "변신: " + selected, "좌/우클릭으로 종류 변경 / 참가자는 즉시 적용·부활", right -> {
            List<String> types = new ArrayList<>(); types.add("RANDOM"); types.addAll(MobCatalog.ALL.stream().map(Enum::name).toList());
            String current = plugin.getConfig().getString(prefix + ".mob", "RANDOM");
            String next = types.get(Math.floorMod(types.indexOf(current) + (right ? -1 : 1), types.size()));
            plugin.adminMob(admin, target, next.equals("RANDOM") ? null : EntityType.valueOf(next)); person(admin, target);
        });
        int lives = plugin.displayedLives(target);
        button(page, 12, Material.RED_DYE, "피격 기회: " + lives, "좌클릭 +1 / 우클릭 -1 / 즉시 회복", right -> {
            int current = plugin.displayedLives(target);
            plugin.adminLives(admin, target, Math.max(1, Math.min(20, current + (right ? -1 : 1)))); person(admin, target);
        });
        double speed = plugin.configuredNumber(prefix + ".speed", plugin.configuredNumber("speed-multiplier", 1));
        button(page, 14, Material.SUGAR, "속도 배율: " + speed, "좌클릭 +0.25 / 우클릭 -0.25 / 숨는 플레이어 즉시 적용", right -> {
            double current = plugin.configuredNumber(prefix + ".speed", plugin.configuredNumber("speed-multiplier", 1));
            plugin.adminSpeed(target, Math.max(0.25, Math.min(3, current + (right ? -0.25 : 0.25)))); person(admin, target);
        });
        button(page, 16, Material.COMPASS, "술래로 지정", "진행 중이면 역할 변경 / 이전 술래는 숨는 팀", right -> { plugin.adminSeeker(admin, target); open(admin); });
        button(page, 30, Material.MILK_BUCKET, "개별 설정 초기화", "다음 변신 랜덤 / 기본 피·속도로 복구", right -> {
            plugin.adminReset(admin, target); person(admin, target);
        });
        back(page, admin); admin.openInventory(page.inventory);
    }
    private void confirmReset(Player player) {
        Page page = new Page(player, "게임 설정을 초기화할까요?");
        button(page, 11, Material.LIME_WOOL, "초기화", "피격 2·술래 20하트·속도 1배·추격 180초·준비 15초·AI 25·도발 5점/10초/발광 5초·몹 " + MobCatalog.ALL.size() + "종 전체·술래 랜덤",
                right -> { plugin.adminResetSettings(player); open(player); });
        button(page, 13, Material.MAP, "유지되는 항목", "경기장·소환 높이·개인별 설정·리더보드", null);
        button(page, 15, Material.RED_WOOL, "취소", "아무것도 바꾸지 않음", right -> open(player));
        player.openInventory(page.inventory);
    }
    private void board(Player player) {
        Page page = new Page(player, "몹 숨바꼭질 점수 TOP 10");
        var scores = plugin.topScores();
        if (scores.isEmpty()) button(page, 22, Material.PAPER, "아직 점수가 없습니다", "일반 경기를 완료하면 기록됩니다.", null);
        for (int i = 0; i < scores.size(); i++) button(page, 10 + i, Material.GOLD_INGOT,
                (i + 1) + "위 " + scores.get(i).name(), MobHidePlugin.leaderboardLine(scores.get(i)), null);
        back(page, player); player.openInventory(page.inventory);
    }
    private void back(Page page, Player player) { button(page, 49, Material.ARROW, "관리자 홈", "", right -> open(player)); }
    private void command(Player player, String... args) { player.closeInventory(); plugin.onCommand(player, plugin.getCommand("mobhide"), "mh", args); }
    private void button(Page page, int slot, Material icon, String title, String description, Consumer<Boolean> action) {
        ItemStack item = new ItemStack(icon); var meta = item.getItemMeta();
        meta.setDisplayName("§e" + title); meta.setLore(List.of("§7" + description)); item.setItemMeta(meta);
        page.inventory.setItem(slot, item); if (action != null) page.actions.put(slot, action);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Page page)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.hasPermission("mobhide.admin") || !page.owner.equals(player.getUniqueId())) return;
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        var action = page.actions.get(event.getRawSlot()); if (action == null) return;
        boolean right = event.getClick() == ClickType.RIGHT;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.hasPermission("mobhide.admin") && player.getOpenInventory().getTopInventory() == page.inventory) action.accept(right);
        });
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Page) event.setCancelled(true);
    }
}
