package dev.noclip.backrooms.crafting;

import dev.noclip.backrooms.core.rpg.PlayerRpg;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * Physical shaped crafting container menu for all 6 Backrooms workstations:
 * - Salvage Bench (1 input -> 4 outputs)
 * - Workshop Bench (5x5 shaped grid -> 1 output)
 * - Almond Distiller (3x3 shaped grid -> 1 output)
 * - Anomaly Altar (3x3 star-altar shaped grid -> 1 output)
 * - Primary Spiderweb Crafting Table (7x7 shaped spiderweb grid -> 1 output)
 * - Ultimate Spiderweb Crafting Table (9x9 shaped spiderweb grid -> 1 output)
 */
public final class BackroomsStationMenu extends AbstractContainerMenu {
	private final StationType stationType;
	private final ContainerLevelAccess access;
	private final Block stationBlock;
	private final SimpleContainer inputGrid;
	private final ResultContainer resultSlots;
	private StationRecipe.Match activeMatch;
	private boolean updatingResult = false;

	public BackroomsStationMenu(MenuType<BackroomsStationMenu> type, int containerId, Inventory playerInv, StationType stationType) {
		this(type, containerId, playerInv, stationType, ContainerLevelAccess.NULL, null);
	}

	public BackroomsStationMenu(MenuType<BackroomsStationMenu> type, int containerId, Inventory playerInv,
			StationType stationType, ContainerLevelAccess access, Block stationBlock) {
		super(type, containerId);
		this.stationType = stationType;
		this.access = access;
		this.stationBlock = stationBlock;

		int inCount = stationType.inputSlotCount();
		int outCount = stationType.outputSlotCount();
		this.inputGrid = new SimpleContainer(inCount) {
			@Override
			public void setChanged() {
				super.setChanged();
				slotsChanged(this);
			}
		};
		this.resultSlots = new ResultContainer();

		// 1. Physical crafting grid slots: indices [0 .. inCount - 1]
		int gw = stationType.gridWidth();
		int gh = stationType.gridHeight();
		int gx0 = stationType.gridOriginX();
		int gy0 = stationType.gridOriginY();
		int step = stationType.cellStep();
		for (int row = 0; row < gh; row++) {
			for (int col = 0; col < gw; col++) {
				int idx = row * gw + col;
				addSlot(new Slot(inputGrid, idx, gx0 + col * step, gy0 + row * step));
			}
		}

		// 2. Physical result slot(s): indices [inCount .. inCount + outCount - 1]
		int ox0 = stationType.outputOriginX();
		int oy0 = stationType.outputOriginY();
		for (int i = 0; i < outCount; i++) {
			final int outIdx = i;
			int sx = outCount == 1 ? ox0 : (ox0 + (i % 2) * 20);
			int sy = outCount == 1 ? oy0 : (oy0 + (i / 2) * 20);
			addSlot(new Slot(resultSlots, outIdx, sx, sy) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return false;
				}

				@Override
				public void onTake(Player player, ItemStack taken) {
					handleResultTaken(player, outIdx);
					super.onTake(player, taken);
				}
			});
		}

		// 3. Standard 36 player inventory slots: indices [inCount + outCount .. +35]
		addStandardInventorySlots(playerInv, stationType.invOriginX(), stationType.invOriginY());
	}

	public StationType stationType() {
		return stationType;
	}

	public SimpleContainer inputGrid() {
		return inputGrid;
	}

	public int inputSlotCount() {
		return stationType.inputSlotCount();
	}

	public int outputSlotStart() {
		return stationType.inputSlotCount();
	}

	public int playerInvStart() {
		return stationType.inputSlotCount() + stationType.outputSlotCount();
	}

	@Override
	public void slotsChanged(Container container) {
		if (updatingResult) return;
		super.slotsChanged(container);
		if (container == inputGrid) {
			updateCraftResult();
		}
	}

	private void updateCraftResult() {
		updatingResult = true;
		try {
			activeMatch = BackroomsRecipeRegistry.findMatch(stationType, inputGrid);
			int outCount = stationType.outputSlotCount();
			for (int i = 0; i < outCount; i++) {
				resultSlots.setItem(i, ItemStack.EMPTY);
			}
			if (activeMatch != null) {
				List<ItemStack> outs = activeMatch.recipe().allOutputStacks();
				for (int i = 0; i < outCount && i < outs.size(); i++) {
					resultSlots.setItem(i, outs.get(i).copy());
				}
			}
		} finally {
			updatingResult = false;
		}
		broadcastChanges();
	}

	private void handleResultTaken(Player player, int takenOutputIndex) {
		StationRecipe.Match match = activeMatch;
		if (match == null) {
			match = BackroomsRecipeRegistry.findMatch(stationType, inputGrid);
		}
		if (match == null) return;

		StationRecipe recipe = match.recipe();
		updatingResult = true;
		try {
			recipe.consumeFromGrid(inputGrid, stationType.gridWidth(), match.offsetX(), match.offsetY());
			// For multi-output Salvage Bench, automatically grant the other salvaged outputs when player grabs one!
			List<ItemStack> outs = recipe.allOutputStacks();
			for (int i = 0; i < outs.size(); i++) {
				if (i == takenOutputIndex) continue;
				ItemStack extra = outs.get(i).copy();
				if (!extra.isEmpty() && !player.level().isClientSide()) {
					if (!player.getInventory().add(extra)) {
						player.drop(extra, false);
					}
				}
			}
			if (player instanceof ServerPlayer sp) {
				if (recipe.xpReward() > 0) {
					PlayerRpg.grantXp(sp, Math.min(40, recipe.xpReward()));
				}
				playCraftSound(sp, stationType);
			}
		} finally {
			updatingResult = false;
		}
		updateCraftResult();
	}

	/** Clears all items in the physical crafting grid safely back into the player's inventory. */
	public void clearGridToPlayer(Player player) {
		updatingResult = true;
		try {
			for (int i = 0; i < inputGrid.getContainerSize(); i++) {
				ItemStack stack = inputGrid.removeItemNoUpdate(i);
				if (!stack.isEmpty()) {
					if (!player.getInventory().add(stack)) {
						player.drop(stack, false);
					}
				}
			}
		} finally {
			updatingResult = false;
		}
		updateCraftResult();
	}

	/**
	 * Auto-fills a shaped recipe from the player's inventory (or Creative mode) into the exact
	 * centered physical grid slots. Powers both the Categorized Recipe Book and JEI '+' transfer!
	 */
	public boolean fillRecipeFromPlayer(ServerPlayer player, StationRecipe recipe, boolean fillMax) {
		if (recipe == null || recipe.station() != stationType) return false;
		clearGridToPlayer(player);

		int ox = recipe.centeredOffsetX();
		int oy = recipe.centeredOffsetY();
		int gw = stationType.gridWidth();
		int sets = 1;

		if (player.isCreative()) {
			sets = fillMax ? 8 : 1;
			updatingResult = true;
			try {
				for (int py = 0; py < recipe.patternHeight(); py++) {
					for (int px = 0; px < recipe.patternWidth(); px++) {
						StationRecipe.ItemCount req = recipe.cellAt(px, py);
						if (req == null) continue;
						Item item = req.resolveItem();
						if (item == Items.AIR) continue;
						int qty = Math.min(item.getDefaultMaxStackSize(), req.count() * sets);
						inputGrid.setItem((oy + py) * gw + (ox + px), new ItemStack(item, qty));
					}
				}
			} finally {
				updatingResult = false;
			}
			updateCraftResult();
			return true;
		}

		// Compute maximum craftable sets in survival inventory
		Inventory inv = player.getInventory();
		int maxSets = Integer.MAX_VALUE;
		for (StationRecipe.ItemCount req : recipe.inputs()) {
			Item item = req.resolveItem();
			if (item == Items.AIR) return false;
			int owned = StationRecipe.countInInventory(inv, item);
			maxSets = Math.min(maxSets, owned / req.count());
		}
		if (maxSets <= 0) return false;
		sets = fillMax ? Math.min(maxSets, 16) : 1;

		updatingResult = true;
		try {
			for (int py = 0; py < recipe.patternHeight(); py++) {
				for (int px = 0; px < recipe.patternWidth(); px++) {
					StationRecipe.ItemCount req = recipe.cellAt(px, py);
					if (req == null) continue;
					Item item = req.resolveItem();
					int want = Math.min(item.getDefaultMaxStackSize(), req.count() * sets);
					int pulled = pullFromInventory(inv, item, want);
					if (pulled > 0) {
						inputGrid.setItem((oy + py) * gw + (ox + px), new ItemStack(item, pulled));
					}
				}
			}
		} finally {
			updatingResult = false;
		}
		updateCraftResult();
		return true;
	}

	private static int pullFromInventory(Inventory inv, Item target, int want) {
		int pulled = 0;
		for (int i = 0; i < inv.getContainerSize() && pulled < want; i++) {
			ItemStack st = inv.getItem(i);
			if (!st.isEmpty() && st.is(target)) {
				int take = Math.min(want - pulled, st.getCount());
				st.shrink(take);
				if (st.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
				pulled += take;
			}
		}
		return pulled;
	}

	private static void playCraftSound(ServerPlayer player, StationType station) {
		SoundEvent sound = switch (station) {
			case SALVAGE -> SoundEvents.GRINDSTONE_USE;
			case WORKSHOP -> SoundEvents.SMITHING_TABLE_USE;
			case DISTILLER -> SoundEvents.BREWING_STAND_BREW;
			case ALTAR -> SoundEvents.ENCHANTMENT_TABLE_USE;
			case SPIDERWEB, ULTIMATE_SPIDERWEB -> SoundEvents.BEACON_ACTIVATE;
		};
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				sound, SoundSource.BLOCKS, 0.75F, 1.0F + player.getRandom().nextFloat() * 0.15F);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		int outStart = outputSlotStart();
		int invStart = playerInvStart();
		int invEnd = invStart + 36;
		Slot slot = slots.get(index);
		if (!slot.hasItem()) return ItemStack.EMPTY;

		ItemStack slotStack = slot.getItem();
		ItemStack copy = slotStack.copy();

		if (index >= outStart && index < invStart) {
			// Shift-clicking a crafted result slot: move crafted stack into player inventory & consume inputs!
			if (!moveItemStackTo(slotStack, invStart, invEnd, true)) {
				return ItemStack.EMPTY;
			}
			slot.onTake(player, copy);
			return copy;
		} else if (index < outStart) {
			// Shift-click from crafting grid -> player inventory
			if (!moveItemStackTo(slotStack, invStart, invEnd, false)) {
				return ItemStack.EMPTY;
			}
		} else {
			// Shift-click from player inventory -> crafting grid (or hotbar/main swap if grid full)
			if (!moveItemStackTo(slotStack, 0, outStart, false)) {
				int hotbarStart = invStart + 27;
				if (index < hotbarStart) {
					if (!moveItemStackTo(slotStack, hotbarStart, invEnd, false)) return ItemStack.EMPTY;
				} else {
					if (!moveItemStackTo(slotStack, invStart, hotbarStart, false)) return ItemStack.EMPTY;
				}
			}
		}

		if (slotStack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (slotStack.getCount() == copy.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTake(player, slotStack);
		return copy;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		clearGridToPlayer(player);
	}

	@Override
	public boolean stillValid(Player player) {
		if (stationBlock == null || access == ContainerLevelAccess.NULL) return true;
		return stillValid(access, player, stationBlock);
	}
}
