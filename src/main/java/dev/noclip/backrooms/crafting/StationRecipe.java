package dev.noclip.backrooms.crafting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Immutable data-driven workstation recipe shared by both server crafting validation and client/JEI display.
 * Supports multi-count ingredients (e.g. red_room_shard x8) and multi-item salvage outputs cleanly.
 */
public record StationRecipe(
		Identifier id,
		StationType station,
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

	public boolean canCraft(Player player) {
		if (player.isCreative()) return true;
		Inventory inv = player.getInventory();
		for (ItemCount req : inputs) {
			Item item = req.resolveItem();
			if (item == Items.AIR) return false;
			if (countInInventory(inv, item) < req.count()) return false;
		}
		return !outputs.isEmpty();
	}

	public boolean craftOnce(Player player) {
		if (!canCraft(player)) return false;
		if (!player.isCreative()) {
			Inventory inv = player.getInventory();
			for (ItemCount req : inputs) {
				Item item = req.resolveItem();
				int remaining = req.count();
				for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
					ItemStack stack = inv.getItem(i);
					if (!stack.isEmpty() && stack.is(item)) {
						int take = Math.min(remaining, stack.getCount());
						stack.shrink(take);
						remaining -= take;
					}
				}
			}
		}
		for (ItemCount out : outputs) {
			Item item = out.resolveItem();
			if (item == Items.AIR) continue;
			int remaining = out.count();
			int maxStack = Math.max(1, item.getDefaultMaxStackSize());
			while (remaining > 0) {
				int batch = Math.min(remaining, maxStack);
				ItemStack stack = new ItemStack(item, batch);
				if (!player.getInventory().add(stack)) {
					player.drop(stack, false);
				}
				remaining -= batch;
			}
		}
		return true;
	}
}
