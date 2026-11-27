package dev.noclip.backrooms.core.locker;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.LevelExits;
import dev.noclip.backrooms.core.LevelGates;
import dev.noclip.backrooms.core.economy.Wallet;
import dev.noclip.backrooms.core.floors.Floors;
import dev.noclip.backrooms.core.quota.Quota;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.core.rpg.PlayerStats;
import dev.noclip.backrooms.core.trade.ShopRole;
import dev.noclip.backrooms.core.trade.Shops;
import dev.noclip.backrooms.core.trip.Report;
import dev.noclip.backrooms.props.BackroomsProps;
import java.util.ArrayList;
import java.util.Collections;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Mid-run relay & outpost logistics services (docs/企劃書/1-遊戲玩法/README.md, "中繼站"):
 * <ul>
 *   <li><b>One-way Relay Drop-off Box</b> ({@link BackroomsProps#RELAY_DROPOFF_BOX}): ships placed items directly back
 *       to the player's M.E.G. locker in the institute for {@value #FEE_PER_STACK} credits per stack;</li>
 *   <li><b>Single-Trip Gear Insurance</b>: {@value #INSURANCE_PRICE} credits, recovering Backrooms death drops directly
 *       into the institute locker;</li>
 *   <li><b>Paid Guide Shortcut</b> ({@code /meg shortcut <level>}): fast-travel to visited/mapped levels in-run
 *       (30% cheaper when mapped with a topology beacon);</li>
 *   <li><b>Field Repair Service</b> ({@code /meg repair [all]}): restores weapon/tool/armor durability for credits,
 *       discounted by {@code ENGINEERING}.</li>
 * </ul>
 */
public final class RelayService {
	public static final long FEE_PER_STACK = 15L;
	public static final long INSURANCE_PRICE = 120L;
	private static final int RELAY_SLOTS = 27;

	private static final class Holders {
		static final AttachmentType<Boolean> INSURED = AttachmentRegistry.create(
				BackroomsMod.id("gear_insured"),
				builder -> builder.initializer(() -> false).persistent(Codec.BOOL).copyOnDeath());
	}

	private static final Map<UUID, Integer> RELAYED_THIS_TRIP = new HashMap<>();

	private RelayService() {
	}

	public static void register() {
		var unused = Holders.INSURED;
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
				player.setAttached(Holders.INSURED, false);
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
				player.setAttached(Holders.INSURED, false);
				notes.add(Component.translatable("report.noclip_backrooms.insurance_expired"));
			}
		});

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
					}))
					.then(Commands.literal("shortcut")
							.executes(ctx -> {
								offerShortcuts(ctx.getSource().getPlayerOrException());
								return 1;
							})
							.then(Commands.argument("level", StringArgumentType.word())
									.executes(ctx -> shortcut(
											ctx.getSource().getPlayerOrException(),
											StringArgumentType.getString(ctx, "level"),
											true) ? 1 : 0)))
					.then(Commands.literal("repair")
							.executes(ctx -> repairMainHand(ctx.getSource().getPlayerOrException(), true) ? 1 : 0)
							.then(Commands.literal("all")
									.executes(ctx -> repairAll(ctx.getSource().getPlayerOrException(), true) ? 1 : 0))));
		});
	}

	private static void sendBoxMenu(ServerPlayer player) {
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.relay.menu_header", FEE_PER_STACK)
				.withStyle(ChatFormatting.GOLD));
		MutableComponent actions = btn("message.noclip_backrooms.relay.open_btn", "/meg relay", ChatFormatting.AQUA)
				.append(" ")
				.append(isInsured(player)
						? Component.translatable("message.noclip_backrooms.insurance.active").withStyle(ChatFormatting.GREEN)
						: btn("message.noclip_backrooms.insurance.buy_btn", "/meg insure", ChatFormatting.GOLD, INSURANCE_PRICE))
				.append(" ")
				.append(btn("message.noclip_backrooms.repair.btn", "/meg repair", ChatFormatting.YELLOW))
				.append(" ")
				.append(btn("message.noclip_backrooms.repair.all_btn", "/meg repair all", ChatFormatting.YELLOW))
				.append(" ")
				.append(btn("message.noclip_backrooms.shortcut.btn", "/meg shortcut", ChatFormatting.LIGHT_PURPLE));
		player.sendSystemMessage(actions);
	}

	/** Displays clickable field-repair and paid-shortcut buttons when interacting with an underground Black Market dealer. */
	public static void offerBlackMarketExtras(ServerPlayer player) {
		MutableComponent line = btn("message.noclip_backrooms.repair.btn", "/meg repair", ChatFormatting.YELLOW)
				.append(" ")
				.append(btn("message.noclip_backrooms.repair.all_btn", "/meg repair all", ChatFormatting.YELLOW))
				.append(" ")
				.append(btn("message.noclip_backrooms.shortcut.btn", "/meg shortcut", ChatFormatting.LIGHT_PURPLE));
		player.sendSystemMessage(line);
	}

	// ---- Paid deep-level shortcut guide ----

	/** Pure shortcut travel fee; mapped levels receive a 30% guide discount. */
	public static long shortcutCost(String level, boolean mapped) {
		long base = switch (level == null ? "" : level) {
			case "0" -> 60L;
			case "1" -> 90L;
			case "0.1" -> 110L;
			case "2" -> 160L;
			case "3" -> 260L;
			case "4" -> 320L;
			default -> 350L;
		};
		return mapped ? Math.round(base * 0.70) : base;
	}

	/** Levels this player can pay a relay guide to reach from inside the Backrooms right now. */
	public static List<String> shortcutTargets(ServerPlayer player) {
		if (player == null) return List.of();
		MinecraftServer server = player.level().getServer();
		String current = Floors.key(player.level());
		List<String> open = new ArrayList<>(LevelExits.openLevels());
		Collections.sort(open);
		List<String> targets = new ArrayList<>();
		for (String lvl : open) {
			if (lvl.equals(current)) continue;
			if (!Quota.hasVisited(server, lvl) && !Quota.hasMapped(server, lvl)) continue;
			if (!LevelGates.isReachableFor(player, lvl)) continue;
			targets.add(lvl);
		}
		return List.copyOf(targets);
	}

	public static void offerShortcuts(ServerPlayer player) {
		if (!Home.inBackrooms(player.level().dimension())) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.relay.backrooms_only")
					.withStyle(ChatFormatting.RED));
			return;
		}
		List<String> targets = shortcutTargets(player);
		if (targets.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.shortcut.none")
					.withStyle(ChatFormatting.GRAY));
			return;
		}
		MinecraftServer server = player.level().getServer();
		MutableComponent line = Component.translatable("message.noclip_backrooms.shortcut.header")
				.withStyle(ChatFormatting.GOLD);
		for (String lvl : targets) {
			long fee = shortcutCost(lvl, Quota.hasMapped(server, lvl));
			line.append(" ").append(btn("message.noclip_backrooms.shortcut.target_btn", "/meg shortcut " + lvl,
					ChatFormatting.AQUA, lvl, fee));
		}
		player.sendSystemMessage(line);
	}

	/** Pays a relay/black-market guide to skip directly into an already visited or mapped level. */
	public static boolean shortcut(ServerPlayer player, String targetLevel, boolean checkAccess) {
		if (!Home.inBackrooms(player.level().dimension())) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.relay.backrooms_only")
					.withStyle(ChatFormatting.RED));
			return false;
		}
		if (checkAccess && !nearRelayBox(player) && !Shops.near(player, ShopRole.BLACK_MARKET)) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.shortcut.no_access")
					.withStyle(ChatFormatting.RED));
			return false;
		}
		String current = Floors.key(player.level());
		if (targetLevel == null || targetLevel.equals(current) || !LevelExits.isOpen(targetLevel)) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.shortcut.invalid")
					.withStyle(ChatFormatting.RED));
			return false;
		}
		MinecraftServer server = player.level().getServer();
		if (!Quota.hasVisited(server, targetLevel) && !Quota.hasMapped(server, targetLevel)) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.shortcut.unvisited", targetLevel)
					.withStyle(ChatFormatting.RED));
			return false;
		}
		if (!LevelGates.tryPass(player, targetLevel)) {
			return false;
		}
		long fee = shortcutCost(targetLevel, Quota.hasMapped(server, targetLevel));
		if (!player.isCreative() && !Wallet.take(player, fee)) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.shortcut.no_credits", fee)
					.withStyle(ChatFormatting.RED));
			return false;
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.85F, 1.15F);
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.shortcut.done", fee, targetLevel)
				.withStyle(ChatFormatting.GREEN));
		LevelExits.fallInto(player, targetLevel);
		return true;
	}

	// ---- Field repair service ----

	/** Pure credit cost formula for durability repair, discounted up to 30% by ENGINEERING. */
	public static long repairCost(int damage, int maxDamage, int engineeringLevel) {
		if (damage <= 0 || maxDamage <= 0) return 0L;
		double baseMax = maxDamage >= 1200 ? 100.0 : 60.0;
		long base = Math.max(10L, Math.round(baseMax * ((double) damage / maxDamage)));
		double discount = Math.min(0.30, Math.max(0, engineeringLevel) * 0.03);
		return Math.max(8L, Math.round(base * (1.0 - discount)));
	}

	private static boolean hasRepairAccess(ServerPlayer player) {
		if (Home.inBackrooms(player.level().dimension())) {
			return nearRelayBox(player) || Shops.near(player, ShopRole.BLACK_MARKET);
		}
		if (Quota.locked(player.level().getServer())) return false;
		return Shops.near(player, ShopRole.QUARTERMASTER) || Shops.near(player, ShopRole.SUPPLIER);
	}

	private static int engLevel(ServerPlayer player) {
		PlayerStats st = PlayerRpg.get(player);
		return st.engUnlocked() ? st.eng() : 0;
	}

	/** Repairs the main-hand damageable weapon/tool/armor item for credits. */
	public static boolean repairMainHand(ServerPlayer player, boolean checkAccess) {
		if (checkAccess && !hasRepairAccess(player)) {
			String key = Home.isHome(player.level().dimension()) && Quota.locked(player.level().getServer())
					? "shop.noclip_backrooms.locked"
					: "message.noclip_backrooms.repair.no_access";
			player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.RED));
			return false;
		}
		ItemStack stack = player.getMainHandItem();
		if (stack.isEmpty() || !stack.isDamageableItem() || stack.getDamageValue() <= 0) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.repair.nothing")
					.withStyle(ChatFormatting.GRAY));
			return false;
		}
		long fee = repairCost(stack.getDamageValue(), stack.getMaxDamage(), engLevel(player));
		if (!player.isCreative() && !Wallet.take(player, fee)) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.repair.no_credits", fee)
					.withStyle(ChatFormatting.RED));
			return false;
		}
		stack.setDamageValue(0);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.75F, 1.1F);
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.repair.done_one",
				stack.getHoverName(), fee).withStyle(ChatFormatting.GREEN));
		return true;
	}

	/** Repairs the main-hand item plus all worn armor pieces for credits. */
	public static boolean repairAll(ServerPlayer player, boolean checkAccess) {
		if (checkAccess && !hasRepairAccess(player)) {
			String key = Home.isHome(player.level().dimension()) && Quota.locked(player.level().getServer())
					? "shop.noclip_backrooms.locked"
					: "message.noclip_backrooms.repair.no_access";
			player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.RED));
			return false;
		}
		List<ItemStack> damaged = new ArrayList<>();
		ItemStack main = player.getMainHandItem();
		if (!main.isEmpty() && main.isDamageableItem() && main.getDamageValue() > 0) damaged.add(main);
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack armor = player.getItemBySlot(slot);
			if (!armor.isEmpty() && armor.isDamageableItem() && armor.getDamageValue() > 0) damaged.add(armor);
		}
		if (damaged.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.repair.nothing")
					.withStyle(ChatFormatting.GRAY));
			return false;
		}
		int eng = engLevel(player);
		long totalFee = 0L;
		for (ItemStack s : damaged) {
			totalFee += repairCost(s.getDamageValue(), s.getMaxDamage(), eng);
		}
		if (!player.isCreative() && !Wallet.take(player, totalFee)) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.repair.no_credits", totalFee)
					.withStyle(ChatFormatting.RED));
			return false;
		}
		for (ItemStack s : damaged) s.setDamageValue(0);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8F, 1.05F);
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.repair.done_all",
				damaged.size(), totalFee).withStyle(ChatFormatting.GREEN));
		return true;
	}

	// ---- Relay drop-off & gear insurance ----

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
		return player.getAttachedOrElse(Holders.INSURED, false);
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
		player.setAttached(Holders.INSURED, true);
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
				.append(btn("message.noclip_backrooms.insurance.buy_btn", "/meg insure", ChatFormatting.GOLD, INSURANCE_PRICE));
		player.sendSystemMessage(line);
	}

	private static boolean hasInsuranceAccess(ServerPlayer player) {
		if (Home.inBackrooms(player.level().dimension())) return nearRelayBox(player);
		return Shops.near(player, ShopRole.MASTER)
				|| Shops.near(player, ShopRole.SUPPLIER)
				|| Shops.near(player, ShopRole.QUARTERMASTER);
	}

	public static boolean nearRelayBox(ServerPlayer player) {
		BlockPos center = player.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-8, -4, -8), center.offset(8, 4, 8))) {
			if (player.level().getBlockState(p).is(BackroomsProps.RELAY_DROPOFF_BOX)) return true;
		}
		return false;
	}

	private static MutableComponent btn(String key, String cmd, ChatFormatting colour, Object... args) {
		return Component.translatable(key, args).withStyle(style -> style.withColor(colour)
				.withClickEvent(new ClickEvent.RunCommand(cmd))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(cmd))));
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
