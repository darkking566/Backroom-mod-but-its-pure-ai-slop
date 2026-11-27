package dev.noclip.backrooms.core.locker;

import com.mojang.serialization.Codec;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.economy.Wallet;
import dev.noclip.backrooms.core.floors.Floors;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.quota.Quota;
import dev.noclip.backrooms.core.trade.ShopRole;
import dev.noclip.backrooms.core.trip.Report;
import dev.noclip.backrooms.props.BackroomsProps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Mid-run relay services (docs/project/1-gameplay/README.md, "中繼站"):
 * <ul>
 * <li><b>One-way Relay Drop-off Box</b> ({@link BackroomsProps#RELAY_DROPOFF_BOX}): ships placed items directly back to
 * the player's M.E.G. locker in the institute for {@value #FEE_PER_STACK} credits per stack when closed;</li>
 * <li><b>Single-Trip Gear Insurance</b>: costs {@value #INSURANCE_PRICE} credits; if the insured investigator dies in
 * the Backrooms, M.E.G. recovers their inventory straight into their institute locker instead of dropping it on the floor.</li>
 * </ul>
 */
public final class RelayService {
	public static final long FEE_PER_STACK = 15L;
	public static final long INSURANCE_PRICE = 120L;
	private static final int RELAY_SLOTS = 27;
	private static final double STAFF_REACH = 8.0;

	public static final AttachmentType<Boolean> INSURED = AttachmentRegistry.create(
			BackroomsMod.id("gear_insured"),
			builder -> builder.initializer(() -> false).persistent(Codec.BOOL).copyOnDeath());

	private static final Map<UUID, Integer> RELAYED_THIS_TRIP = new HashMap<>();

	private RelayService() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> RELAYED_THIS_TRIP.clear());

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			if (!level.getBlockState(hit.getBlockPos()).is(BackroomsProps.RELAY_DROPOFF_BOX)) {
				return InteractionResult.PASS;
			}
			if (!Home.inBackrooms(level.dimension())) {
				sp.sendSystemMessage(Component.translatable("message.noclip_backrooms.relay.backrooms_only")
						.withStyle(ChatFormatting.GRAY));
				return InteractionResult.SUCCESS;
			}
			if (sp.isShiftKeyDown()) {
				sendBoxMenu(sp);
			} else {
				openRelay(sp);
			}
			return InteractionResult.SUCCESS;
		});

		// In the Backrooms, insured investigators have their inventory recovered to their institute locker on death.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player
					&& Home.inBackrooms(player.level().dimension())
					&& isInsured(player)
					&& !((ServerLevel) player.level()).getGameRules().get(GameRules.KEEP_INVENTORY)) {
				player.setAttached(INSURED, false);
				int saved = Lockers.collect(player);
				player.sendSystemMessage(Component.translatable("message.noclip_backrooms.insurance.claimed", saved)
						.withStyle(ChatFormatting.GOLD));
			}
			return true;
		});

		Report.onReport((player, trip, notes) -> {
			Integer shipped = RELAYED_THIS_TRIP.remove(player.getUUID());
			if (shipped != null && shipped > 0) {
				notes.add(Component.translatable("report.noclip_backrooms.relayed", shipped));
			}
			if (isInsured(player)) {
				player.setAttached(INSURED, false);
				notes.add(Component.translatable("report.noclip_backrooms.insurance_expired"));
			}
		});

		// Safety cleanup if a player leaves via a path that skips Report (e.g. ending override/home)
		Floors.onHome((player, run) -> {
			// Report runs after a 30-tick delay and reads & clears these; nothing extra cleared prematurely here.
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
			dispatcher.register(Commands.literal("meg")
					.then(Commands.literal("insure").executes(ctx -> buyInsurance(ctx.getSource().getPlayerOrException(), true) ? 1 : 0))
					.then(Commands.literal("relay").executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						if (!Home.inBackrooms(player.level().dimension())) {
							player.sendSystemMessage(Component.translatable("message.noclip_backrooms.relay.backrooms_only")
									.withStyle(ChatFormatting.RED));
							return 0;
						}
						if (!nearRelayBox(player)) {
							player.sendSystemMessage(Component.translatable("message.noclip_backrooms.relay.too_far")
									.withStyle(ChatFormatting.RED));
							return 0;
						}
						openRelay(player);
						return 1;
					})));
		});
	}

	private static void sendBoxMenu(ServerPlayer player) {
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.relay.menu_header", FEE_PER_STACK)
				.withStyle(ChatFormatting.GOLD));
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.relay.open_btn")
				.withStyle(style -> style.withColor(ChatFormatting.AQUA)
						.withClickEvent(new ClickEvent.RunCommand("/meg relay"))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("/meg relay"))))
				.append(" ")
				.append(isInsured(player)
						? Component.translatable("message.noclip_backrooms.insurance.active").withStyle(ChatFormatting.GREEN)
						: Component.translatable("message.noclip_backrooms.insurance.buy_btn", INSURANCE_PRICE)
								.withStyle(style -> style.withColor(ChatFormatting.GOLD)
										.withClickEvent(new ClickEvent.RunCommand("/meg insure"))
										.withHoverEvent(new HoverEvent.ShowText(Component.literal("/meg insure"))))));
	}

	/** Opens the 27-slot one-way relay container; closing it ships all placed items to the institute locker. */
	public static void openRelay(ServerPlayer player) {
		SimpleContainer container = new SimpleContainer(RELAY_SLOTS);
		player.openMenu(new SimpleMenuProvider(
				(syncId, inv, p) -> new RelayMenu(syncId, inv, container, player),
				Component.translatable("container.noclip_backrooms.relay", FEE_PER_STACK)));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 0.55F, 1.3F);
	}

	/**
	 * Ships {@code stacks} directly into {@code player}'s M.E.G. locker in the institute for
	 * {@link #FEE_PER_STACK} credits per non-empty stack. Refunds/returns items if credits are short.
	 */
	public static boolean shipToLocker(ServerPlayer player, List<ItemStack> stacks) {
		List<ItemStack> nonEmpty = new ArrayList<>();
		for (ItemStack s : stacks) {
			if (s != null && !s.isEmpty()) nonEmpty.add(s.copy());
		}
		if (nonEmpty.isEmpty()) return true;

		long fee = FEE_PER_STACK * nonEmpty.size();
		if (!Wallet.take(player, fee)) {
			for (ItemStack s : nonEmpty) returnItem(player, s);
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.relay.no_credits", fee)
					.withStyle(ChatFormatting.RED));
			return false;
		}
		int deposited = Lockers.deposit(player, nonEmpty);
		RELAYED_THIS_TRIP.merge(player.getUUID(), deposited, Integer::sum);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENDER_CHEST_CLOSE, SoundSource.PLAYERS, 0.7F, 1.25F);
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.relay.sent", deposited, fee)
				.withStyle(ChatFormatting.GREEN));
		return true;
	}

	private static void returnItem(ServerPlayer player, ItemStack stack) {
		if (player.isAlive()) player.getInventory().placeItemBackInInventory(stack);
		else player.drop(stack, false);
	}

	public static boolean isInsured(ServerPlayer player) {
		return player.getAttachedOrElse(INSURED, false);
	}

	/** Purchases single-trip gear insurance from home staff or near a relay box in the Backrooms. */
	public static boolean buyInsurance(ServerPlayer player, boolean checkAccess) {
		if (isInsured(player)) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.insurance.already")
					.withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (Home.isHome(player.level().dimension()) && Quota.locked(player.level().getServer())) {
			player.sendSystemMessage(Component.translatable("shop.noclip_backrooms.locked").withStyle(ChatFormatting.RED));
			return false;
		}
		if (checkAccess && !hasInsuranceAccess(player)) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.insurance.no_access")
					.withStyle(ChatFormatting.RED));
			return false;
		}
		if (!Wallet.take(player, INSURANCE_PRICE)) {
			player.sendSystemMessage(Component.translatable("shop.noclip_backrooms.no_credits").withStyle(ChatFormatting.RED));
			return false;
		}
		player.setAttached(INSURED, true);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.8F, 1.1F);
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.insurance.bought", INSURANCE_PRICE)
				.withStyle(ChatFormatting.GREEN));
		return true;
	}

	/** Chat status + clickable insurance purchase button shown by the commander. */
	public static void talk(ServerPlayer player) {
		if (isInsured(player)) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.insurance.active")
					.withStyle(ChatFormatting.GREEN));
			return;
		}
		MutableComponent line = Component.translatable("message.noclip_backrooms.insurance.offer", INSURANCE_PRICE)
				.withStyle(ChatFormatting.GRAY)
				.append(" ")
				.append(Component.translatable("message.noclip_backrooms.insurance.buy_btn", INSURANCE_PRICE)
						.withStyle(style -> style.withColor(ChatFormatting.GOLD)
								.withClickEvent(new ClickEvent.RunCommand("/meg insure"))
								.withHoverEvent(new HoverEvent.ShowText(Component.literal("/meg insure")))));
		player.sendSystemMessage(line);
	}

	private static boolean hasInsuranceAccess(ServerPlayer player) {
		if (Home.inBackrooms(player.level().dimension())) return nearRelayBox(player);
		return !player.level().getEntitiesOfClass(Person.class, player.getBoundingBox().inflate(STAFF_REACH), p -> {
			ShopRole r = ShopRole.of(p);
			return r == ShopRole.MASTER || r == ShopRole.SUPPLIER || r == ShopRole.QUARTERMASTER;
		}).isEmpty();
	}

	private static boolean nearRelayBox(ServerPlayer player) {
		BlockPos center = player.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-8, -4, -8), center.offset(8, 4, 8))) {
			if (player.level().getBlockState(p).is(BackroomsProps.RELAY_DROPOFF_BOX)) return true;
		}
		return false;
	}

	private static final class RelayMenu extends ChestMenu {
		private final ServerPlayer owner;
		private boolean settled;

		RelayMenu(int syncId, Inventory playerInv, SimpleContainer container, ServerPlayer owner) {
			super(MenuType.GENERIC_9x3, syncId, playerInv, container, 3);
			this.owner = owner;
		}

		@Override
		public void removed(Player player) {
			if (!settled) {
				settled = true;
				List<ItemStack> placed = new ArrayList<>();
				for (int i = 0; i < getContainer().getContainerSize(); i++) {
					ItemStack s = getContainer().getItem(i);
					if (!s.isEmpty()) placed.add(s.copy());
					getContainer().setItem(i, ItemStack.EMPTY);
				}
				if (!placed.isEmpty()) {
					shipToLocker(owner, placed);
				}
			}
			super.removed(player);
		}
	}
}
