package dev.noclip.backrooms.core.trade;

import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.economy.Wallet;
import dev.noclip.backrooms.core.quota.Quota;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.core.rpg.PlayerStats;
import dev.noclip.backrooms.items.BackroomsItems;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.mojang.brigadier.arguments.StringArgumentType;

/**
 * Paid professional geode appraisal & decryption service offered by M.E.G. Buyers and Backrooms Black Market dealers.
 * Yields stronger, LUCK-boosted loot pools compared to free field cracking of {@code rusted_office_safe} and
 * {@code encrypted_hard_drive}.
 */
public final class Appraisal {
	public static final long SAFE_FEE = 35L;
	public static final long DRIVE_FEE = 50L;

	private record Reward(Item item, int minCount, int maxCount) {
		ItemStack roll(RandomSource random) {
			int count = minCount == maxCount ? minCount : minCount + random.nextInt(maxCount - minCount + 1);
			return new ItemStack(item, count);
		}
	}

	private Appraisal() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
				Commands.literal("meg").then(Commands.literal("appraise")
						.executes(ctx -> appraise(ctx.getSource().getPlayerOrException(), "auto", true) ? 1 : 0)
						.then(Commands.argument("kind", StringArgumentType.word())
								.executes(ctx -> appraise(
										ctx.getSource().getPlayerOrException(),
										StringArgumentType.getString(ctx, "kind"),
										true) ? 1 : 0)))));
	}

	/** Pure LUCK-scaled jackpot probability (base 12%, rising asymptotically up to 40%). */
	public static double jackpotChance(int luckLevel) {
		if (luckLevel <= 0) return 0.12;
		return Math.min(0.40, 0.12 + 0.28 * (1.0 - Math.pow(0.95, luckLevel)));
	}

	public static boolean hasAppraisable(ServerPlayer player) {
		if (player == null) return false;
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(BackroomsItems.RUSTED_OFFICE_SAFE) || s.is(BackroomsItems.ENCRYPTED_HARD_DRIVE)) return true;
		}
		return false;
	}

	/** Sends clickable appraisal buttons to chat when talking to a Buyer or Black Market dealer while carrying geodes. */
	public static void offer(ServerPlayer player) {
		Inventory inv = player.getInventory();
		int safes = countItem(inv, BackroomsItems.RUSTED_OFFICE_SAFE);
		int drives = countItem(inv, BackroomsItems.ENCRYPTED_HARD_DRIVE);
		if (safes <= 0 && drives <= 0) return;

		MutableComponent line = Component.translatable("message.noclip_backrooms.appraisal.offer_header")
				.withStyle(ChatFormatting.GRAY);
		if (safes > 0) {
			line.append(" ").append(button("message.noclip_backrooms.appraisal.safe_btn", "/meg appraise safe", ChatFormatting.GOLD, SAFE_FEE));
		}
		if (drives > 0) {
			line.append(" ").append(button("message.noclip_backrooms.appraisal.drive_btn", "/meg appraise drive", ChatFormatting.AQUA, DRIVE_FEE));
		}
		player.sendSystemMessage(line);
	}

	/** Appraises one rusted safe or encrypted drive for credits + LUCK-scaled jackpot odds. */
	public static boolean appraise(ServerPlayer player, String kind, boolean checkVendor) {
		String resolved = resolveKind(player, kind);
		if (resolved == null) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.appraisal.none")
					.withStyle(ChatFormatting.RED));
			return false;
		}
		if (checkVendor) {
			boolean atBuyer = Shops.near(player, ShopRole.BUYER);
			boolean atBlackMarket = Shops.near(player, ShopRole.BLACK_MARKET);
			if (!atBuyer && !atBlackMarket) {
				player.sendSystemMessage(Component.translatable("message.noclip_backrooms.appraisal.no_vendor")
						.withStyle(ChatFormatting.RED));
				return false;
			}
			// Buyers serve at M.E.G. even in debt (same as Shops), black market operates independently.
		}

		boolean isSafe = "safe".equals(resolved);
		Item geodeItem = isSafe ? BackroomsItems.RUSTED_OFFICE_SAFE : BackroomsItems.ENCRYPTED_HARD_DRIVE;
		long fee = isSafe ? SAFE_FEE : DRIVE_FEE;

		if (countItem(player.getInventory(), geodeItem) < 1) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.appraisal.none")
					.withStyle(ChatFormatting.RED));
			return false;
		}
		if (!player.isCreative() && Wallet.of(player) < fee) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.appraisal.no_credits", fee)
					.withStyle(ChatFormatting.RED));
			return false;
		}
		if (!player.isCreative()) {
			Wallet.take(player, fee);
			consumeOne(player.getInventory(), geodeItem);
		}

		PlayerStats stats = PlayerRpg.get(player);
		int effectiveLuck = stats.luckUnlocked() ? stats.luck() : 0;
		RandomSource random = player.getRandom();
		boolean jackpot = random.nextDouble() < jackpotChance(effectiveLuck);

		Reward[] pool = isSafe
				? (jackpot
						? new Reward[] {
								new Reward(BackroomsItems.WEAPON_BLUEPRINT_BLANK, 1, 1),
								new Reward(BackroomsItems.FORBIDDEN_SCROLL_LUCK, 1, 1),
								new Reward(BackroomsItems.KEYPAD_HACKER_T2, 1, 1),
								new Reward(BackroomsItems.LOST_OLD_COIN, 8, 8)
						}
						: new Reward[] {
								new Reward(BackroomsItems.LOST_OLD_COIN, 4, 6),
								new Reward(BackroomsItems.BLUEPRINT_SCRAP, 2, 2),
								new Reward(BackroomsItems.STATIC_CASSETTE, 2, 2),
								new Reward(BackroomsItems.NOCLIP_SHARD, 2, 2),
								new Reward(BackroomsItems.ALMOND_ELIXIR, 1, 1)
						})
				: (jackpot
						? new Reward[] {
								new Reward(BackroomsItems.FORBIDDEN_SCROLL_TECH, 1, 1),
								new Reward(BackroomsItems.FORBIDDEN_SCROLL_PSY, 1, 1),
								new Reward(BackroomsItems.KEYPAD_HACKER_T3, 1, 1),
								new Reward(BackroomsItems.SCIENCE_PACK_GRAY, 1, 1)
						}
						: new Reward[] {
								new Reward(BackroomsItems.RED_CIRCUIT, 2, 2),
								new Reward(BackroomsItems.MICROCHIP, 2, 2),
								new Reward(BackroomsItems.SCIENCE_PACK_YELLOW, 1, 1),
								new Reward(BackroomsItems.ALMOND_MANA_CELL, 2, 2),
								new Reward(BackroomsItems.BATTERY_CELL, 3, 3)
						});

		ItemStack reward = pool[random.nextInt(pool.length)].roll(random);
		if (!player.getInventory().add(reward.copy())) {
			player.drop(reward.copy(), false);
		}

		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				jackpot ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.IRON_DOOR_OPEN,
				SoundSource.PLAYERS, 0.85F, jackpot ? 1.25F : 1.1F);
		player.sendSystemMessage(Component.translatable(
				jackpot ? "message.noclip_backrooms.appraisal.jackpot" : "message.noclip_backrooms.appraisal.done",
				reward.getHoverName(), reward.getCount(), fee)
				.withStyle(jackpot ? ChatFormatting.GOLD : ChatFormatting.AQUA));
		return true;
	}

	private static String resolveKind(ServerPlayer player, String rawKind) {
		if ("safe".equalsIgnoreCase(rawKind) || "rusted_office_safe".equalsIgnoreCase(rawKind)) return "safe";
		if ("drive".equalsIgnoreCase(rawKind) || "encrypted_hard_drive".equalsIgnoreCase(rawKind)) return "drive";
		ItemStack main = player.getMainHandItem();
		if (main.is(BackroomsItems.RUSTED_OFFICE_SAFE)) return "safe";
		if (main.is(BackroomsItems.ENCRYPTED_HARD_DRIVE)) return "drive";
		if (countItem(player.getInventory(), BackroomsItems.RUSTED_OFFICE_SAFE) > 0) return "safe";
		if (countItem(player.getInventory(), BackroomsItems.ENCRYPTED_HARD_DRIVE) > 0) return "drive";
		return null;
	}

	private static int countItem(Inventory inv, Item item) {
		int total = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item)) total += s.getCount();
		}
		return total;
	}

	private static void consumeOne(Inventory inv, Item item) {
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item)) {
				s.shrink(1);
				inv.setChanged();
				return;
			}
		}
	}

	private static MutableComponent button(String key, String cmd, ChatFormatting colour, Object... args) {
		return Component.translatable(key, args).withStyle(style -> style.withColor(colour)
				.withClickEvent(new ClickEvent.RunCommand(cmd))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(cmd))));
	}
}
