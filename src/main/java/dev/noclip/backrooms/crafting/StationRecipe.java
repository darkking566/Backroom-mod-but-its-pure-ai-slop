package dev.noclip.backrooms.crafting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Immutable data-driven shaped workstation recipe shared by server crafting validation,
 * client physical grid screens (3x3, 5x5, 7x7, 9x9, and 1->4 Salvage), and JEI.
 */
public record StationRecipe(
		Identifier id,
		StationType station,
		String category,
		int patternWidth,
		int patternHeight,
		List<ItemCount> patternCells,
		List<ItemCount> inputs,
		List<ItemCount> outputs,
		int xpReward
) {
	public record ItemCount(Identifier itemId, int count) {
		public Item resolveItem() {
			return BuiltInRegistries.ITEM.getValue(itemId);
		}

		public ItemStack toStack() {
			Item item = resolveItem();
			if (item == Items.AIR) return ItemStack.EMPTY;
			return new ItemStack(item, Math.max(1, count));
		}
	}

	public record Match(StationRecipe recipe, int offsetX, int offsetY) {
	}

	public ItemCount cellAt(int px, int py) {
		if (px < 0 || py < 0 || px >= patternWidth || py >= patternHeight) return null;
		return patternCells.get(py * patternWidth + px);
	}

	public ItemStack primaryOutput() {
		return outputs.isEmpty() ? ItemStack.EMPTY : outputs.getFirst().toStack();
	}

	public List<ItemStack> allOutputStacks() {
		List<ItemStack> result = new ArrayList<>(outputs.size());
		for (ItemCount out : outputs) {
			ItemStack stack = out.toStack();
			if (!stack.isEmpty()) result.add(stack);
		}
		return result;
	}

	public static List<ItemCount> aggregateInputs(List<ItemCount> cells) {
		Map<Identifier, Integer> totals = new LinkedHashMap<>();
		for (ItemCount c : cells) {
			if (c == null) continue;
			totals.merge(c.itemId(), c.count(), Integer::sum);
		}
		List<ItemCount> out = new ArrayList<>(totals.size());
		for (Map.Entry<Identifier, Integer> e : totals.entrySet()) {
			out.add(new ItemCount(e.getKey(), e.getValue()));
		}
		return List.copyOf(out);
	}

	/**
	 * Matches this shaped recipe anywhere inside a (gridW x gridH) container (identical to Vanilla
	 * ShapedRecipe sliding bounding-box rules) with exact minimum per-cell counts.
	 */
	public Match matchInGrid(Container container, int gridW, int gridH) {
		if (patternWidth <= 0 || patternHeight <= 0 || patternWidth > gridW || patternHeight > gridH) return null;
		for (int ox = 0; ox <= gridW - patternWidth; ox++) {
			for (int oy = 0; oy <= gridH - patternHeight; oy++) {
				if (matchesAt(container, gridW, gridH, ox, oy)) {
					return new Match(this, ox, oy);
				}
			}
		}
		return null;
	}

	private boolean matchesAt(Container container, int gridW, int gridH, int ox, int oy) {
		for (int gy = 0; gy < gridH; gy++) {
			for (int gx = 0; gx < gridW; gx++) {
				ItemStack present = container.getItem(gy * gridW + gx);
				int px = gx - ox;
				int py = gy - oy;
				ItemCount req = (px >= 0 && px < patternWidth && py >= 0 && py < patternHeight)
						? patternCells.get(py * patternWidth + px) : null;
				if (req == null) {
					if (!present.isEmpty()) return false;
				} else {
					Item reqItem = req.resolveItem();
					if (reqItem == Items.AIR || present.isEmpty() || !present.is(reqItem) || present.getCount() < req.count()) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/** Consumes one set of shaped ingredients from the container at the matched offset. */
	public void consumeFromGrid(Container container, int gridW, int ox, int oy) {
		for (int py = 0; py < patternHeight; py++) {
			for (int px = 0; px < patternWidth; px++) {
				ItemCount req = patternCells.get(py * patternWidth + px);
				if (req == null) continue;
				int slot = (oy + py) * gridW + (ox + px);
				ItemStack present = container.getItem(slot);
				if (!present.isEmpty()) {
					present.shrink(req.count());
					if (present.isEmpty()) {
						container.setItem(slot, ItemStack.EMPTY);
					}
				}
			}
		}
	}

	public static int countInInventory(Inventory inv, Item target) {
		if (target == Items.AIR) return 0;
		int total = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (!stack.isEmpty() && stack.is(target)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	public boolean canCraftFromInventory(Player player) {
		if (player.isCreative()) return true;
		Inventory inv = player.getInventory();
		for (ItemCount req : inputs) {
			Item item = req.resolveItem();
			if (item == Items.AIR) return false;
			if (countInInventory(inv, item) < req.count()) return false;
		}
		return !outputs.isEmpty();
	}

	/** Default centered X offset inside the station's grid for ghost hints and recipe auto-fill. */
	public int centeredOffsetX() {
		return Math.max(0, (station.gridWidth() - patternWidth) / 2);
	}

	/** Default centered Y offset inside the station's grid for ghost hints and recipe auto-fill. */
	public int centeredOffsetY() {
		return Math.max(0, (station.gridHeight() - patternHeight) / 2);
	}
}
