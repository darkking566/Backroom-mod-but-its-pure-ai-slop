package dev.noclip.backrooms.core.locker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.economy.Wallet;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.quota.Quota;
import dev.noclip.backrooms.core.trade.ShopRole;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * Portable protected safebox (docs/project/1-gameplay/README.md, "保險箱"):
 * starts at 2 slots, persists through death anywhere (never dropped in the Backrooms and never moved into
 * M.E.G. lockers on surface death), and can be expanded up to 6 slots at the institute.
 */
public final class Safebox {
	public static final int INITIAL_SLOTS = 2;
	public static final int MAX_SLOTS = 6;
	private static final int CONTAINER_SIZE = 9;
	private static final long[] UPGRADE_COSTS = {300L, 600L, 1000L, 1600L};
	private static final String LOCKED_KEY = "noclip_safebox_locked";
	private static final double NEAR_COMMANDER = 8.0;

	public record SafeboxData(int capacity, List<ItemStack> items) {
		static final Codec<SafeboxData> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("capacity", INITIAL_SLOTS).forGetter(SafeboxData::capacity),
				ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(SafeboxData::items)
		).apply(i, SafeboxData::new));

		// Only capacity needs to sync to the owner's client (for the inventory button label);
		// item contents are synced via ChestMenu whenever opened.
		static final StreamCodec<RegistryFriendlyByteBuf, SafeboxData> STREAM_CODEC = StreamCodec.of(
				(buf, data) -> buf.writeVarInt(data.capacity()),
				buf -> new SafeboxData(buf.readVarInt(), List.of()));

		public SafeboxData {
			capacity = Math.clamp(capacity, INITIAL_SLOTS, MAX_SLOTS);
			List<ItemStack> padded = new ArrayList<>(MAX_SLOTS);
			for (int i = 0; i < MAX_SLOTS; i++) {
				ItemStack s = i < items.size() && items.get(i) != null ? items.get(i).copy() : ItemStack.EMPTY;
				padded.add(isLockedPlaceholder(s) ? ItemStack.EMPTY : s);
			}
			items = List.copyOf(padded);
		}

		public static SafeboxData initial() {
			return new SafeboxData(INITIAL_SLOTS, List.of());
		}

		SafeboxData withCapacity(int nextCapacity) {
			return new SafeboxData(nextCapacity, items);
		}

		SafeboxData withItems(List<ItemStack> nextItems) {
			return new SafeboxData(capacity, nextItems);
		}
	}

	private static final class Holders {
		static final AttachmentType<SafeboxData> DATA = AttachmentRegistry.create(
				BackroomsMod.id("safebox"),
				builder -> builder
						.initializer(SafeboxData::initial)
						.persistent(SafeboxData.CODEC)
						.copyOnDeath()
						.syncWith(SafeboxData.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));
	}

	private Safebox() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(SafeboxOpenPayload.TYPE, SafeboxOpenPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SafeboxOpenPayload.TYPE, (payload, context) -> open(context.player()));
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
				Commands.literal("meg").then(Commands.literal("safebox")
						.executes(ctx -> {
							open(ctx.getSource().getPlayerOrException());
							return 1;
						})
						.then(Commands.literal("upgrade").executes(ctx -> upgrade(ctx.getSource().getPlayerOrException(), true) ? 1 : 0)))));
	}

	/** Current unlocked slot count (2..6) for {@code player}. */
	public static int capacity(Player player) {
		return player.getAttachedOrElse(Holders.DATA, SafeboxData.initial()).capacity();
	}

	/** Upgrade price from {@code currentCapacity}, or {@code -1} if already at {@link #MAX_SLOTS}. */
	public static long upgradeCost(int currentCapacity) {
		int idx = currentCapacity - INITIAL_SLOTS;
		if (idx < 0 || idx >= UPGRADE_COSTS.length) return -1L;
		return UPGRADE_COSTS[idx];
	}

	/** Opens the player's safebox menu anywhere (Overworld, Frontrooms, or Backrooms). */
	public static void open(ServerPlayer player) {
		SafeboxData data = player.getAttachedOrElse(Holders.DATA, SafeboxData.initial());
		int cap = data.capacity();
		SafeboxContainer container = new SafeboxContainer(cap, player, data.items());
		player.openMenu(new SimpleMenuProvider(
				(syncId, inv, p) -> new SafeboxMenu(syncId, inv, container, cap, player),
				Component.translatable("container.noclip_backrooms.safebox", cap, MAX_SLOTS)));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.PLAYERS, 0.55F, 1.2F);
	}

	/** Expands the safebox by 1 slot at the institute/Overworld for credits. */
	public static boolean upgrade(ServerPlayer player, boolean checkCommander) {
		SafeboxData data = player.getAttachedOrElse(Holders.DATA, SafeboxData.initial());
		int cap = data.capacity();
		long cost = upgradeCost(cap);
		if (cost < 0) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.safebox.maxed", MAX_SLOTS)
					.withStyle(ChatFormatting.GOLD));
			return false;
		}
		String refusal = !Home.isHome(player.level().dimension()) ? "message.noclip_backrooms.safebox.not_home"
				: checkCommander && !nearCommander(player) ? "message.noclip_backrooms.safebox.no_commander"
				: Quota.locked(player.level().getServer()) ? "message.noclip_backrooms.safebox.locked" : null;
		if (refusal != null) {
			player.sendSystemMessage(Component.translatable(refusal).withStyle(ChatFormatting.RED));
			return false;
		}
		if (!Wallet.take(player, cost)) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.safebox.no_credits", cost)
					.withStyle(ChatFormatting.RED));
			return false;
		}
		int nextCap = cap + 1;
		player.setAttached(Holders.DATA, data.withCapacity(nextCap));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.65F, 1.15F);
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.safebox.upgraded", nextCap, MAX_SLOTS, cost)
				.withStyle(ChatFormatting.GREEN));
		return true;
	}

	/** Chat status + clickable upgrade button shown by the commander. */
	public static void talk(ServerPlayer player) {
		int cap = capacity(player);
		long cost = upgradeCost(cap);
		MutableComponent line = Component.translatable("message.noclip_backrooms.safebox.status", cap, MAX_SLOTS)
				.withStyle(ChatFormatting.AQUA);
		if (cost > 0) {
			line = line.append(" ").append(Component.translatable("message.noclip_backrooms.safebox.upgrade_btn", cost)
					.withStyle(style -> style.withColor(ChatFormatting.GOLD)
							.withClickEvent(new ClickEvent.RunCommand("/meg safebox upgrade"))
							.withHoverEvent(new HoverEvent.ShowText(Component.literal("/meg safebox upgrade")))));
		}
		player.sendSystemMessage(line);
	}

	/** Puts an item into unlocked slot {@code slot} (primarily for tests). */
	public static boolean put(ServerPlayer player, int slot, ItemStack stack) {
		SafeboxData data = player.getAttachedOrElse(Holders.DATA, SafeboxData.initial());
		if (slot < 0 || slot >= data.capacity()) return false;
		List<ItemStack> next = new ArrayList<>(data.items());
		next.set(slot, stack.copy());
		player.setAttached(Holders.DATA, data.withItems(next));
		return true;
	}

	/** Total count of {@code item} stored inside {@code player}'s safebox. */
	public static int count(ServerPlayer player, Item item) {
		SafeboxData data = player.getAttachedOrElse(Holders.DATA, SafeboxData.initial());
		int total = 0;
		for (int i = 0; i < data.capacity(); i++) {
			ItemStack stack = data.items().get(i);
			if (stack.is(item)) total += stack.getCount();
		}
		return total;
	}

	private static boolean nearCommander(ServerPlayer player) {
		return !player.level().getEntitiesOfClass(Person.class, player.getBoundingBox().inflate(NEAR_COMMANDER),
				p -> ShopRole.of(p) == ShopRole.MASTER).isEmpty();
	}

	private static boolean isLockedPlaceholder(ItemStack stack) {
		if (stack.isEmpty()) return false;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(LOCKED_KEY, false);
	}

	private static ItemStack lockedBar() {
		ItemStack bar = new ItemStack(Items.IRON_BARS);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(LOCKED_KEY, true);
		bar.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		bar.set(DataComponents.CUSTOM_NAME, Component.translatable("container.noclip_backrooms.safebox.locked")
				.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.RED)));
		return bar;
	}

	private static ItemStack spacerPane() {
		ItemStack pane = new ItemStack(Items.STAINED_GLASS_PANE.gray());
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(LOCKED_KEY, true);
		pane.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		pane.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
		return pane;
	}

	private static void persist(ServerPlayer owner, Container container, int cap) {
		SafeboxData current = owner.getAttachedOrElse(Holders.DATA, SafeboxData.initial());
		List<ItemStack> saved = new ArrayList<>(MAX_SLOTS);
		for (int i = 0; i < MAX_SLOTS; i++) {
			if (i < cap) {
				ItemStack stack = container.getItem(i);
				saved.add(isLockedPlaceholder(stack) ? ItemStack.EMPTY : stack.copy());
			} else {
				saved.add(current.items().get(i));
			}
		}
		owner.setAttached(Holders.DATA, current.withItems(saved));
	}

	private static final class SafeboxContainer extends SimpleContainer {
		private final int capacity;
		private final ServerPlayer owner;
		private boolean ready;

		SafeboxContainer(int capacity, ServerPlayer owner, List<ItemStack> items) {
			super(CONTAINER_SIZE);
			this.capacity = capacity;
			this.owner = owner;
			for (int i = 0; i < CONTAINER_SIZE; i++) {
				if (i < capacity) {
					setItem(i, items.get(i).copy());
				} else if (i < MAX_SLOTS) {
					setItem(i, lockedBar());
				} else {
					setItem(i, spacerPane());
				}
			}
			this.ready = true;
		}

		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return slot >= 0 && slot < capacity && !isLockedPlaceholder(stack);
		}

		@Override
		public void setChanged() {
			super.setChanged();
			if (ready) persist(owner, this, capacity);
		}
	}

	private static final class SafeboxMenu extends ChestMenu {
		private final int capacity;
		private final ServerPlayer owner;

		SafeboxMenu(int syncId, Inventory playerInv, SafeboxContainer container, int capacity, ServerPlayer owner) {
			super(MenuType.GENERIC_9x1, syncId, playerInv, container, 1);
			this.capacity = capacity;
			this.owner = owner;
			for (int i = capacity; i < CONTAINER_SIZE; i++) {
				Slot orig = this.slots.get(i);
				Slot locked = new Slot(orig.container, orig.getContainerSlot(), orig.x, orig.y) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return false;
					}

					@Override
					public boolean mayPickup(Player player) {
						return false;
					}

					@Override
					public boolean allowModification(Player player) {
						return false;
					}
				};
				locked.index = orig.index;
				this.slots.set(i, locked);
			}
		}

		@Override
		public boolean canDragTo(Slot slot) {
			return slot.container != getContainer() || slot.getContainerSlot() < capacity;
		}

		@Override
		public void clicked(int slotId, int button, ContainerInput input, Player player) {
			if (slotId >= capacity && slotId < CONTAINER_SIZE) {
				broadcastFullState();
				return;
			}
			super.clicked(slotId, button, input, player);
		}

		@Override
		public ItemStack quickMoveStack(Player player, int index) {
			if (index >= capacity && index < CONTAINER_SIZE) return ItemStack.EMPTY;
			Slot slot = this.slots.get(index);
			if (!slot.hasItem()) return ItemStack.EMPTY;
			ItemStack stack = slot.getItem();
			ItemStack copy = stack.copy();
			if (index < CONTAINER_SIZE) {
				if (!this.moveItemStackTo(stack, CONTAINER_SIZE, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!this.moveItemStackTo(stack, 0, capacity, false)) {
				return ItemStack.EMPTY;
			}
			if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
			else slot.setChanged();
			return copy;
		}

		@Override
		public void removed(Player player) {
			persist(owner, getContainer(), capacity);
			super.removed(player);
		}
	}
}
