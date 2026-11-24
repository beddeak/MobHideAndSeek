package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.destroystokyo.paper.event.entity.CreeperIgniteEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.block.Block;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.block.BlockShearEntityEvent;
import org.bukkit.event.entity.CreeperPowerEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class DecoyProtectionTest {
    private final Creeper decoy = mock(Creeper.class);
    private final Set<Entity> decoys = new HashSet<>(Set.of(decoy));
    private final DecoyProtection protection = new DecoyProtection(decoys::contains);

    @Test void ownedDecoysRejectItemUseWithEitherHandBeforeVanillaInteraction() {
        Player player = mock(Player.class);
        Cow cow = mock(Cow.class);
        decoys.add(cow);
        for (var hand : List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND)) {
            var interact = new PlayerInteractEntityEvent(player, cow, hand);
            var interactAt = new PlayerInteractAtEntityEvent(player, cow, new Vector(), hand);
            protection.onInteract(interact);
            protection.onInteractAt(interactAt);
            assertTrue(interact.isCancelled(), hand.name());
            assertTrue(interactAt.isCancelled(), hand.name());
        }
    }

    @Test void ordinaryAnimalsKeepBothInteractionPacketPaths() {
        Player player = mock(Player.class);
        Cow cow = mock(Cow.class);
        var interact = new PlayerInteractEntityEvent(player, cow, EquipmentSlot.HAND);
        var interactAt = new PlayerInteractAtEntityEvent(player, cow, new Vector(), EquipmentSlot.OFF_HAND);
        protection.onInteract(interact);
        protection.onInteractAt(interactAt);
        assertFalse(interact.isCancelled());
        assertFalse(interactAt.isCancelled());
    }

    @Test void playerAndDispenserCannotShearOwnedSheep() {
        Sheep sheep = mock(Sheep.class);
        decoys.add(sheep);
        var player = new PlayerShearEntityEvent(mock(Player.class), sheep, mock(ItemStack.class),
                EquipmentSlot.HAND, List.of());
        var dispenser = new BlockShearEntityEvent(mock(Block.class), sheep, mock(ItemStack.class), List.of());
        protection.onPlayerShear(player);
        protection.onBlockShear(dispenser);
        assertTrue(player.isCancelled());
        assertTrue(dispenser.isCancelled());
    }

    @Test void ordinarySheepRemainShearableByPlayersAndDispensers() {
        Sheep sheep = mock(Sheep.class);
        var player = new PlayerShearEntityEvent(mock(Player.class), sheep, mock(ItemStack.class),
                EquipmentSlot.HAND, List.of());
        var dispenser = new BlockShearEntityEvent(mock(Block.class), sheep, mock(ItemStack.class), List.of());
        protection.onPlayerShear(player);
        protection.onBlockShear(dispenser);
        assertFalse(player.isCancelled());
        assertFalse(dispenser.isCancelled());
    }

    @Test void ownedAnimalsCannotEnterLoveModeButOrdinaryAnimalsCan() {
        Cow owned = mock(Cow.class), ordinary = mock(Cow.class);
        decoys.add(owned);
        var blocked = new EntityEnterLoveModeEvent(owned, mock(Player.class), 600);
        var allowed = new EntityEnterLoveModeEvent(ordinary, mock(Player.class), 600);
        protection.onLove(blocked);
        protection.onLove(allowed);
        assertTrue(blocked.isCancelled());
        assertFalse(allowed.isCancelled());
    }

    @Test void breedingIsBlockedWhenEitherParentBelongsToTheGame() {
        Cow owned = mock(Cow.class), ordinary = mock(Cow.class);
        decoys.add(owned);
        var ownedMother = new EntityBreedEvent(mock(Cow.class), owned, ordinary, null, null, 1);
        var ownedFather = new EntityBreedEvent(mock(Cow.class), ordinary, owned, null, null, 1);
        var ownedBoth = new EntityBreedEvent(mock(Cow.class), owned, owned, null, null, 1);
        protection.onBreed(ownedMother);
        protection.onBreed(ownedFather);
        protection.onBreed(ownedBoth);
        assertTrue(ownedMother.isCancelled());
        assertTrue(ownedFather.isCancelled());
        assertTrue(ownedBoth.isCancelled());
    }

    @Test void ordinaryParentsCanBreedAndRemovedDecoysCanInteractAgain() {
        Cow mother = mock(Cow.class), father = mock(Cow.class);
        var breed = new EntityBreedEvent(mock(Cow.class), mother, father, null, null, 1);
        protection.onBreed(breed);
        assertFalse(breed.isCancelled());
        decoys.add(mother);
        decoys.clear();
        var interact = new PlayerInteractEntityEvent(mock(Player.class), mother, EquipmentSlot.HAND);
        protection.onInteract(interact);
        assertFalse(interact.isCancelled());
    }

    @Test void ownedChickenCannotLeaveEggsButOrdinaryChickensCan() {
        Chicken owned = mock(Chicken.class), ordinary = mock(Chicken.class);
        decoys.add(owned);
        var blocked = new EntityDropItemEvent(owned, mock(Item.class));
        var allowed = new EntityDropItemEvent(ordinary, mock(Item.class));
        protection.onDrop(blocked);
        protection.onDrop(allowed);
        assertTrue(blocked.isCancelled());
        assertFalse(allowed.isCancelled());
    }

    @Test void ownedCreeperCannotBeIgnitedExternally() {
        var event = new CreeperIgniteEvent(decoy, true);
        protection.onIgnite(event);
        assertTrue(event.isCancelled());
    }

    @Test void ownedCreeperCanBeExtinguished() {
        var event = new CreeperIgniteEvent(decoy, false);
        protection.onIgnite(event);
        assertFalse(event.isCancelled());
    }

    @Test void ownedCreeperCannotBeChargedByLightningOrAnExplicitPowerChange() {
        for (var cause : Set.of(CreeperPowerEvent.PowerCause.LIGHTNING, CreeperPowerEvent.PowerCause.SET_ON)) {
            var event = new CreeperPowerEvent(decoy, cause);
            protection.onPower(event);
            assertTrue(event.isCancelled(), cause.name());
        }
    }

    @Test void ownedCreeperCanLoseItsCharge() {
        var event = new CreeperPowerEvent(decoy, CreeperPowerEvent.PowerCause.SET_OFF);
        protection.onPower(event);
        assertFalse(event.isCancelled());
    }

    @Test void unrelatedCreepersRetainTheirNormalIgnitionAndChargingBehavior() {
        Creeper unrelated = mock(Creeper.class);
        for (boolean ignited : new boolean[] {true, false}) {
            var event = new CreeperIgniteEvent(unrelated, ignited);
            protection.onIgnite(event);
            assertFalse(event.isCancelled());
        }
        for (var cause : CreeperPowerEvent.PowerCause.values()) {
            var event = new CreeperPowerEvent(unrelated, cause);
            protection.onPower(event);
            assertFalse(event.isCancelled(), cause.name());
        }
    }

    @Test void removingTheDecoyImmediatelyRestoresNormalBehavior() {
        decoys.clear();
        var ignite = new CreeperIgniteEvent(decoy, true);
        var power = new CreeperPowerEvent(decoy, CreeperPowerEvent.PowerCause.LIGHTNING);
        protection.onIgnite(ignite);
        protection.onPower(power);
        assertFalse(ignite.isCancelled());
        assertFalse(power.isCancelled());
    }
}
