package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.mockito.MockedStatic;

class AdminPanelTest {
    @Test void olderGlobalPanelIncrementsTheValueSavedByAnotherAdmin() {
        try (var ui = new PanelFixture()) {
            ui.config.set("round-seconds", 180);
            ui.panel.open(ui.first);
            ui.panel.open(ui.second);

            ui.clickAndRun(ui.first, 22, ClickType.LEFT);
            ui.clickAndRun(ui.first, 22, ClickType.LEFT);
            ui.clickAndRun(ui.second, 22, ClickType.LEFT);

            assertEquals(270, ui.config.getInt("round-seconds"));
            verify(ui.plugin).adminNumber(ui.second, "round-seconds", 270);
        }
    }

    @Test void tauntButtonsStartAtTheNewDefaultsAndRespectTheirLimits() {
        try (var ui = new PanelFixture()) {
            ui.panel.open(ui.first);
            ui.clickAndRun(ui.first, 25, ClickType.LEFT);
            verify(ui.plugin).adminNumber(ui.first, "taunt.cooldown-seconds", 15);
            ui.clickAndRun(ui.first, 26, ClickType.RIGHT);
            verify(ui.plugin).adminNumber(ui.first, "taunt.points", 4);
            ui.config.set("taunt.cooldown-seconds", 5);
            ui.clickAndRun(ui.first, 25, ClickType.RIGHT);
            verify(ui.plugin).adminNumber(ui.first, "taunt.cooldown-seconds", 5);
        }
    }

    @Test void disasterPageTriggersTheAdminCommandWithTheChosenKind() {
        try (var ui = new PanelFixture()) {
            ui.panel.open(ui.first);
            ui.clickAndRun(ui.first, 13, ClickType.LEFT);
            ui.clickAndRun(ui.first, 12, ClickType.LEFT);
            verify(ui.plugin).onCommand(eq(ui.first), any(), eq("mh"), org.mockito.AdditionalMatchers.aryEq(new String[]{"disaster", "tornado"}));
            ui.panel.open(ui.first);
            ui.clickAndRun(ui.first, 13, ClickType.LEFT);
            ui.clickAndRun(ui.first, 11, ClickType.RIGHT);
            verify(ui.plugin).onCommand(eq(ui.first), any(), eq("mh"), org.mockito.AdditionalMatchers.aryEq(new String[]{"disaster", "random"}));
        }
    }

    @Test void resetAsksForConfirmationAndCancelChangesNothing() {
        try (var ui = new PanelFixture()) {
            ui.panel.open(ui.first);
            ui.clickAndRun(ui.first, 34, ClickType.LEFT);
            ui.clickAndRun(ui.first, 15, ClickType.LEFT);
            verify(ui.plugin, never()).adminResetSettings(any());
            ui.clickAndRun(ui.first, 34, ClickType.LEFT);
            ui.clickAndRun(ui.first, 11, ClickType.LEFT);
            verify(ui.plugin).adminResetSettings(ui.first);
        }
    }

    @Test void queuedNumberActionReadsChangesMadeBeforeTheNextTick() {
        try (var ui = new PanelFixture()) {
            ui.config.set("round-seconds", 180);
            ui.panel.open(ui.first);
            ui.click(ui.first, 22, ClickType.RIGHT);
            ui.config.set("round-seconds", 240);

            ui.runPending();

            assertEquals(210, ui.config.getInt("round-seconds"));
        }
    }

    @Test void olderMobPoolPanelPreservesAnotherAdminsRemoval() {
        try (var ui = new PanelFixture()) {
            ui.config.set("mob-types", List.of("COW", "PIG", "SHEEP"));
            ui.openPool(ui.first);
            ui.openPool(ui.second);

            ui.clickAndRun(ui.first, 10, ClickType.LEFT);
            ui.clickAndRun(ui.second, 11, ClickType.RIGHT);

            assertEquals(List.of("SHEEP"), ui.config.getStringList("mob-types"));
        }
    }

    @Test void olderMobPoolPanelCannotRemoveTheLastRemainingMob() {
        try (var ui = new PanelFixture()) {
            ui.config.set("mob-types", List.of("COW", "PIG"));
            ui.openPool(ui.first);
            ui.openPool(ui.second);
            ui.clickAndRun(ui.first, 10, ClickType.LEFT);

            ui.clickAndRun(ui.second, 11, ClickType.LEFT);

            assertEquals(List.of("PIG"), ui.config.getStringList("mob-types"));
            verify(ui.second).sendMessage("§c최소 한 종류는 남겨야 합니다.");
            verify(ui.plugin, times(1)).saveConfig();
        }
    }

    @Test void olderPersonalPanelCyclesFromTheLatestMobChoice() {
        try (var ui = new PanelFixture()) {
            ui.openPerson(ui.first);
            ui.openPerson(ui.second);

            ui.clickAndRun(ui.first, 10, ClickType.LEFT);
            ui.clickAndRun(ui.second, 10, ClickType.LEFT);

            assertEquals("PIG", ui.config.getString(ui.prefix + ".mob"));
            verify(ui.plugin).adminMob(ui.second, ui.target, EntityType.PIG);
        }
    }

    @Test void personalLivesActionUsesTheCurrentDisplayedRoundLimit() {
        try (var ui = new PanelFixture()) {
            ui.openPerson(ui.first);
            ui.click(ui.first, 12, ClickType.RIGHT);
            when(ui.plugin.displayedLives(ui.target)).thenReturn(5);

            ui.runPending();

            verify(ui.plugin).adminLives(ui.first, ui.target, 4);
            assertEquals(4, ui.config.getInt(ui.prefix + ".hits"));
        }
    }

    @Test void personalSpeedActionUsesTheLatestIndividualMultiplier() {
        try (var ui = new PanelFixture()) {
            ui.config.set(ui.prefix + ".speed", 1.0);
            ui.openPerson(ui.first);
            ui.openPerson(ui.second);

            ui.clickAndRun(ui.first, 14, ClickType.LEFT);
            ui.clickAndRun(ui.second, 14, ClickType.LEFT);

            assertEquals(1.5, ui.config.getDouble(ui.prefix + ".speed"));
        }
    }

    @Test void personalSpeedActionUsesTheLatestGlobalFallback() {
        try (var ui = new PanelFixture()) {
            ui.config.set("speed-multiplier", 1.0);
            ui.openPerson(ui.first);
            ui.config.set("speed-multiplier", 2.0);

            ui.clickAndRun(ui.first, 14, ClickType.RIGHT);

            assertEquals(1.75, ui.config.getDouble(ui.prefix + ".speed"));
        }
    }

    @Test void onlyPlainLeftAndRightClicksCanSchedulePanelActions() {
        try (var ui = new PanelFixture()) {
            ui.panel.open(ui.first);
            for (ClickType click : ClickType.values()) {
                if (click == ClickType.LEFT || click == ClickType.RIGHT) continue;
                InventoryClickEvent event = ui.click(ui.first, 12, click);
                verify(event).setCancelled(true);
                assertTrue(ui.pending.isEmpty(), click.name());
            }
            verifyNoInteractions(ui.scheduler);
            verify(ui.plugin, never()).onCommand(any(), any(), anyString(), any());
        }
    }

    @Test void globalPanelCanRecoverAnInvalidSpeedSetting() {
        try (var ui = new PanelFixture()) {
            ui.config.set("speed-multiplier", Double.NaN);
            ui.panel.open(ui.first);
            ui.clickAndRun(ui.first, 21, ClickType.LEFT);
            assertEquals(1.25, ui.config.getDouble("speed-multiplier"));
        }
    }

    @Test void personalPanelUsesGlobalFallbackForNonFiniteValues() {
        try (var ui = new PanelFixture()) {
            ui.config.set("speed-multiplier", 2);
            ui.config.set(ui.prefix + ".speed", Double.NEGATIVE_INFINITY);
            ui.openPerson(ui.first);
            ui.clickAndRun(ui.first, 14, ClickType.RIGHT);
            assertEquals(1.75, ui.config.getDouble(ui.prefix + ".speed"));
        }
    }

    private static final class PanelFixture implements AutoCloseable {
        final MobHidePlugin plugin = mock(MobHidePlugin.class, withSettings().mockMaker(MockMakers.INLINE));
        final YamlConfiguration config = new YamlConfiguration();
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        final List<Runnable> pending = new ArrayList<>();
        final Map<UUID, InventoryView> views = new HashMap<>();
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, withSettings().mockMaker(MockMakers.INLINE));
        final MockedStatic<ItemStack> items = mockStatic(ItemStack.class, withSettings().mockMaker(MockMakers.INLINE));
        final AdminPanel panel = new AdminPanel(plugin);
        final Player first = player("First"), second = player("Second"), target = player("Target");
        final String prefix = "players." + target.getUniqueId();

        PanelFixture() {
            when(plugin.getConfig()).thenReturn(config);
            when(plugin.configuredNumber(anyString(), anyDouble())).thenCallRealMethod();
            items.when(() -> ItemStack.of(any(Material.class), anyInt())).thenAnswer(invocation -> {
                ItemStack item = mock(ItemStack.class);
                when(item.getItemMeta()).thenReturn(mock(ItemMeta.class));
                return item;
            });
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(target));
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), eq(54), anyString()))
                    .thenAnswer(invocation -> {
                        Inventory inventory = mock(Inventory.class);
                        when(inventory.getHolder()).thenReturn(invocation.getArgument(0));
                        return inventory;
                    });
            when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
                pending.add(invocation.getArgument(1));
                return null;
            });
            doAnswer(invocation -> {
                config.set(invocation.getArgument(1), invocation.<Double>getArgument(2));
                return null;
            }).when(plugin).adminNumber(any(Player.class), anyString(), anyDouble());
            doAnswer(invocation -> {
                EntityType type = invocation.getArgument(2);
                config.set(prefix + ".mob", type == null ? "RANDOM" : type.name());
                return null;
            }).when(plugin).adminMob(any(Player.class), eq(target), nullable(EntityType.class));
            when(plugin.displayedLives(target)).thenAnswer(invocation -> config.getInt(prefix + ".hits", 2));
            doAnswer(invocation -> {
                config.set(prefix + ".hits", invocation.<Integer>getArgument(2));
                return null;
            }).when(plugin).adminLives(any(Player.class), eq(target), anyInt());
            doAnswer(invocation -> {
                config.set(prefix + ".speed", invocation.<Double>getArgument(1));
                return null;
            }).when(plugin).adminSpeed(eq(target), anyDouble());
        }

        private Player player(String name) {
            Player player = mock(Player.class);
            UUID id = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(id);
            when(player.getName()).thenReturn(name);
            when(player.hasPermission("mobhide.admin")).thenReturn(true);
            when(player.isOnline()).thenReturn(true);
            when(player.getOpenInventory()).thenAnswer(invocation -> views.get(id));
            when(player.openInventory(any(Inventory.class))).thenAnswer(invocation -> {
                InventoryView view = mock(InventoryView.class);
                when(view.getTopInventory()).thenReturn(invocation.getArgument(0));
                views.put(id, view);
                return view;
            });
            return player;
        }

        void openPool(Player admin) {
            panel.open(admin);
            clickAndRun(admin, 14, ClickType.LEFT);
        }

        void openPerson(Player admin) {
            panel.open(admin);
            clickAndRun(admin, 31, ClickType.LEFT);
            clickAndRun(admin, 9, ClickType.LEFT);
        }

        InventoryClickEvent click(Player admin, int slot, ClickType click) {
            InventoryClickEvent event = mock(InventoryClickEvent.class);
            InventoryView currentView = admin.getOpenInventory();
            when(event.getView()).thenReturn(currentView);
            when(event.getWhoClicked()).thenReturn(admin);
            when(event.getRawSlot()).thenReturn(slot);
            when(event.getClick()).thenReturn(click);
            when(event.isRightClick()).thenReturn(click.isRightClick());
            panel.click(event);
            verify(event).setCancelled(true);
            return event;
        }

        void clickAndRun(Player admin, int slot, ClickType click) {
            click(admin, slot, click);
            runPending();
        }

        void runPending() {
            assertEquals(1, pending.size());
            Runnable action = pending.removeFirst();
            action.run();
        }

        @Override public void close() {
            items.close();
            bukkit.close();
        }
    }
}
