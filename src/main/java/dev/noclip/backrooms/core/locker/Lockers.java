package dev.noclip.backrooms.core.locker;

import com.mojang.serialization.Codec;
import dev.noclip.backrooms.BackroomsMod;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Each investigator's M.E.G. locker in the institute (docs/project/1-gameplay/README.md): six rows that stay with the
 * player through death, opened at any locker; and the relics, what M.E.G. collected from where they died outside the
 * Backrooms, which wait there until there is room in the locker.
 */
public final class Lockers {
	public static final int SIZE = 54;
	private static final Codec<List<ItemStack>> STACKS = ItemStack.OPTIONAL_CODEC.listOf();
	/** The locker's six rows. */
	public static final AttachmentType<List<ItemStack>> LOCKER = AttachmentRegistry.create(BackroomsMod.id("locker"),
			builder -> builder.persistent(STACKS).copyOnDeath());
	/** What did not fit in the locker yet. */
	public static final AttachmentType<List<ItemStack>> RELICS = AttachmentRegistry.create(BackroomsMod.id("locker_relics"),
			builder -> builder.persistent(STACKS).copyOnDeath());

	private Lockers() {
	}

	public static void register() {
		// The attachments register themselves; this makes sure the class is loaded at start-up.
	}

	/** The locker's contents, SIZE stacks (empty ones included). */
	public static List<ItemStack> contents(ServerPlayer player) {
		List<ItemStack> list = new ArrayList<>(player.getAttachedOrElse(LOCKER, List.of()));
		while (list.size() < SIZE) list.add(ItemStack.EMPTY);
		return list;
	}

	public static List<ItemStack> relics(ServerPlayer player) {
		return new ArrayList<>(player.getAttachedOrElse(RELICS, List.of()));
	}

	/** Opens the player's own locker, after moving in whatever relics now fit. */
	public static void open(ServerPlayer player) {
		List<ItemStack> contents = contents(player);
		List<ItemStack> left = new ArrayList<>();
		for (ItemStack relic : relics(player)) {
			ItemStack rest = insert(contents, relic.copy());
			if (!rest.isEmpty()) left.add(rest);
		}
		store(player, contents, left);
		SimpleContainer container = new SimpleContainer(SIZE) {
			@Override
			public void setChanged() {
				super.setChanged();
				List<ItemStack> now = new ArrayList<>();
				for (int i = 0; i < getContainerSize(); i++) now.add(getItem(i).copy());
				player.setAttached(LOCKER, now);
			}
		};
		for (int i = 0; i < SIZE; i++) container.setItem(i, contents.get(i));
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.sixRows(id, inventory, container),
				Component.translatable("container.noclip_backrooms.meg_locker")));
	}

	/**
	 * Dying outside the Backrooms: M.E.G. collects everything the player carried (except what vanishes on death) and puts
	 * it in their locker, or with the relics when the locker is full. Returns how many stacks it took.
	 */
	public static int collect(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		List<ItemStack> contents = contents(player);
		List<ItemStack> relics = relics(player);
		int taken = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) continue;
			inventory.setItem(i, ItemStack.EMPTY);
			if (EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) continue;
			taken++;
			ItemStack rest = insert(contents, stack);
			if (!rest.isEmpty()) relics.add(rest);
		}
		store(player, contents, relics);
		return taken;
	}

	private static void store(ServerPlayer player, List<ItemStack> contents, List<ItemStack> relics) {
		player.setAttached(LOCKER, List.copyOf(contents));
		if (relics.isEmpty()) player.removeAttached(RELICS);
		else player.setAttached(RELICS, List.copyOf(relics));
	}

	/** Puts a stack into the list, filling like stacks first; returns what did not fit. */
	static ItemStack insert(List<ItemStack> slots, ItemStack stack) {
		for (int i = 0; i < slots.size() && !stack.isEmpty(); i++) {
			ItemStack in = slots.get(i);
			if (in.isEmpty() || !ItemStack.isSameItemSameComponents(in, stack)) continue;
			int room = in.getMaxStackSize() - in.getCount();
			if (room <= 0) continue;
			int move = Math.min(room, stack.getCount());
			ItemStack grown = in.copy();
			grown.grow(move);
			slots.set(i, grown);
			stack.shrink(move);
		}
		for (int i = 0; i < slots.size() && !stack.isEmpty(); i++) {
			if (slots.get(i).isEmpty()) {
				slots.set(i, stack.copy());
				stack.setCount(0);
			}
		}
		return stack.isEmpty() ? ItemStack.EMPTY : stack;
	}

	/** For tests: how many of an item the locker holds. */
	public static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
		int n = 0;
		for (ItemStack stack : contents(player)) if (stack.is(item)) n += stack.getCount();
		for (ItemStack stack : relics(player)) if (stack.is(item)) n += stack.getCount();
		return n;
	}
}
