package dev.noclip.backrooms.crafting;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Container menu for all 4 Backrooms workstations. Shows player inventory at the bottom and lets the player
 * select and craft any recipe belonging to the opened workstation directly from their inventory.
 */
public final class BackroomsStationMenu extends AbstractContainerMenu {
	public static final int GUI_WIDTH = 248;
	public static final int GUI_HEIGHT = 214;
	public static final int INV_X = 44;
	public static final int INV_Y = 132;

	private final StationType stationType;
	private final ContainerLevelAccess access;
	private final Block stationBlock;

	public BackroomsStationMenu(MenuType<BackroomsStationMenu> type, int containerId, Inventory playerInv, StationType stationType) {
		this(type, containerId, playerInv, stationType, ContainerLevelAccess.NULL, null);
	}

	public BackroomsStationMenu(MenuType<BackroomsStationMenu> type, int containerId, Inventory playerInv,
			StationType stationType, ContainerLevelAccess access, Block stationBlock) {
		super(type, containerId);
		this.stationType = stationType;
		this.access = access;
		this.stationBlock = stationBlock;
		addStandardInventorySlots(playerInv, INV_X, INV_Y);
	}

	public StationType stationType() {
		return stationType;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		if (stationBlock == null || access == ContainerLevelAccess.NULL) return true;
		return stillValid(access, player, stationBlock);
	}
}
