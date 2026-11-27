package dev.noclip.backrooms.core.rpg;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.people.People;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.trade.ShopRole;
import dev.noclip.backrooms.items.BackroomsItems;
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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Specialization badges (docs/企劃書/2-玩家能力值/README.md, "流派徽章", Plan B):
 * <ul>
 * <li>Unlocked permanently per-player using {@link BackroomsItems#LOST_OLD_COIN} from the Level 0 Martial Master
 * inside the Manila Room (outside the M.E.G. economy);</li>
 * <li>Toggled freely inside safe zones (institute/Overworld or close to a settled bed/cot in the Backrooms),
 * locked during active deep exploration;</li>
 * <li>Each badge provides meaningful build perks paired with a tactical tradeoff.</li>
 * </ul>
 */
public final class Badges {
	private static final double VENDOR_REACH = 8.0;
	private static final double SAFE_COT_RADIUS = 16.0;

	public enum Badge {
		ANOMALY_SCHOLAR("anomaly_scholar", 6),
		VANGUARD_BREAKER("vanguard_breaker", 6),
		TACTICAL_SCOUT("tactical_scout", 6),
		SCAVENGER_SURVIVOR("scavenger_survivor", 6);

		public final String id;
		public final int coinCost;

		Badge(String id, int coinCost) {
			this.id = id;
			this.coinCost = coinCost;
		}

		public static Badge byId(String id) {
			if (id == null || id.isEmpty()) return null;
			for (Badge b : values()) {
				if (b.id.equals(id)) return b;
			}
			return null;
		}

		public Component title() {
			return Component.translatable("badge.noclip_backrooms." + id);
		}

		public Component perkText() {
			return Component.translatable("badge.noclip_backrooms." + id + ".perk");
		}

		public Component drawbackText() {
			return Component.translatable("badge.noclip_backrooms." + id + ".drawback");
		}
	}

	public record BadgeData(String activeId, List<String> unlocked) {
		public static final Codec<BadgeData> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.optionalFieldOf("active", "").forGetter(BadgeData::activeId),
				Codec.STRING.listOf().optionalFieldOf("unlocked", List.of()).forGetter(BadgeData::unlocked)
		).apply(i, BadgeData::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, BadgeData> STREAM_CODEC = StreamCodec.of(
				(buf, data) -> {
					buf.writeUtf(data.activeId());
					buf.writeVarInt(data.unlocked().size());
					for (String s : data.unlocked()) buf.writeUtf(s);
				},
				buf -> {
					String active = buf.readUtf();
					int size = Math.clamp(buf.readVarInt(), 0, 32);
					List<String> list = new ArrayList<>(size);
					for (int i = 0; i < size; i++) list.add(buf.readUtf());
					return new BadgeData(active, list);
				}
		);

		public BadgeData {
			activeId = activeId == null ? "" : activeId;
			unlocked = unlocked == null ? List.of() : List.copyOf(unlocked);
		}

		public static BadgeData initial() {
			return new BadgeData("", List.of());
		}

		public boolean hasUnlocked(Badge badge) {
			return badge != null && unlocked.contains(badge.id);
		}

		public Badge activeBadge() {
			return Badge.byId(activeId);
		}

		BadgeData withUnlocked(Badge badge) {
			if (hasUnlocked(badge)) return this;
			List<String> next = new ArrayList<>(unlocked);
			next.add(badge.id);
			return new BadgeData(activeId, next);
		}

		BadgeData withActive(Badge badge) {
			return new BadgeData(badge == null ? "" : badge.id, unlocked);
		}
	}

	private static final class Holders {
		static final AttachmentType<BadgeData> DATA = AttachmentRegistry.create(
				BackroomsMod.id("badges"),
				builder -> builder
						.initializer(BadgeData::initial)
						.persistent(BadgeData.CODEC)
						.copyOnDeath()
						.syncWith(BadgeData.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));
	}

	private Badges() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(BadgeActionPayload.TYPE, BadgeActionPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BadgeActionPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if ("equip".equals(payload.action())) {
				Badge target = payload.badgeId().isEmpty() ? null : Badge.byId(payload.badgeId());
				if (!payload.badgeId().isEmpty() && target == null) return;
				equip(player, target, true);
			} else if ("unlock".equals(payload.action())) {
				Badge target = Badge.byId(payload.badgeId());
				if (target != null) unlock(player, target, true);
			}
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
			dispatcher.register(Commands.literal("meg").then(Commands.literal("badge")
					.then(Commands.literal("status").executes(ctx -> {
						showStatus(ctx.getSource().getPlayerOrException());
						return 1;
					}))
					.then(Commands.literal("unlock").then(Commands.argument("id", StringArgumentType.word())
							.executes(ctx -> {
								Badge b = Badge.byId(StringArgumentType.getString(ctx, "id"));
								return b != null && unlock(ctx.getSource().getPlayerOrException(), b, true) ? 1 : 0;
							})))
					.then(Commands.literal("equip").then(Commands.argument("id", StringArgumentType.word())
							.executes(ctx -> {
								String raw = StringArgumentType.getString(ctx, "id");
								if ("none".equalsIgnoreCase(raw)) {
									return equip(ctx.getSource().getPlayerOrException(), null, true) ? 1 : 0;
								}
								Badge b = Badge.byId(raw);
								return b != null && equip(ctx.getSource().getPlayerOrException(), b, true) ? 1 : 0;
							})))));

			dispatcher.register(Commands.literal("backrooms")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.literal("badge")
							.then(Commands.literal("spawn").executes(ctx -> {
								ServerPlayer p = ctx.getSource().getPlayerOrException();
								spawnVendor(p.level(), p.getX(), p.getY(), p.getZ());
								return 1;
							}))
							.then(Commands.literal("unlock").then(Commands.argument("id", StringArgumentType.word())
									.executes(ctx -> {
										Badge b = Badge.byId(StringArgumentType.getString(ctx, "id"));
										return b != null && unlock(ctx.getSource().getPlayerOrException(), b, false) ? 1 : 0;
									})))));
		});
	}

	public static BadgeData get(Player player) {
		return player == null ? BadgeData.initial() : player.getAttachedOrElse(Holders.DATA, BadgeData.initial());
	}

	public static Badge active(Player player) {
		return get(player).activeBadge();
	}

	/** Safe zone: institute, Overworld, or standing within 16 blocks of a settled Backrooms cot/bed. */
	public static boolean inSafeZone(Player player) {
		if (player == null) return false;
		if (Home.isHome(player.level().dimension())) return true;
		if (player instanceof ServerPlayer sp) {
			ServerPlayer.RespawnConfig cfg = sp.getRespawnConfig();
			if (cfg != null && cfg.respawnData().dimension() == sp.level().dimension()
					&& sp.blockPosition().closerThan(cfg.respawnData().pos(), SAFE_COT_RADIUS)) {
				return true;
			}
		}
		return false;
	}

	/** Spawns the Martial Master NPC who trades specialization badges for Lost Old Coins. */
	public static Person spawnVendor(ServerLevel level, double x, double y, double z) {
		Person person = People.type(ShopRole.MARTIAL_MASTER.look()).create(level, EntitySpawnReason.EVENT);
		if (person == null) return null;
		person.snapTo(x, y, z, 0.0F, 0.0F);
		person.setNoAi(true);
		person.setInvulnerable(true);
		person.setPersistenceRequired();
		person.addTag(ShopRole.MARTIAL_MASTER.tag());
		person.setCustomName(ShopRole.MARTIAL_MASTER.title());
		person.setCustomNameVisible(true);
		level.addFreshEntity(person);
		return person;
	}

	/** Unlocks {@code badge} for {@code player} by spending Lost Old Coins (near Martial Master if {@code checkVendor}). */
	public static boolean unlock(ServerPlayer player, Badge badge, boolean checkVendor) {
		if (badge == null) return false;
		BadgeData data = get(player);
		if (data.hasUnlocked(badge)) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.badge.already_unlocked", badge.title())
					.withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (checkVendor && !nearVendor(player)) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.badge.no_vendor")
					.withStyle(ChatFormatting.RED));
			return false;
		}
		if (!player.isCreative() && !consumeCoins(player, badge.coinCost)) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.badge.no_coins", badge.coinCost)
					.withStyle(ChatFormatting.RED));
			return false;
		}
		player.setAttached(Holders.DATA, data.withUnlocked(badge));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.35F);
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.badge.unlocked", badge.title())
				.withStyle(ChatFormatting.GREEN));
		return true;
	}

	/** Equips or unequips ({@code badge == null}) a badge. Requires safe zone when {@code checkSafeZone} is true. */
	public static boolean equip(ServerPlayer player, Badge badge, boolean checkSafeZone) {
		BadgeData data = get(player);
		if (badge != null && !data.hasUnlocked(badge)) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.badge.locked", badge.title())
					.withStyle(ChatFormatting.RED));
			return false;
		}
		if (checkSafeZone && !inSafeZone(player)) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.badge.safe_zone_only")
					.withStyle(ChatFormatting.RED));
			return false;
		}
		player.setAttached(Holders.DATA, data.withActive(badge));
		PlayerRpg.applyAttributes(player, PlayerRpg.get(player));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.7F, 1.2F);
		if (badge == null) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.badge.unequipped")
					.withStyle(ChatFormatting.GRAY));
		} else {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.badge.equipped", badge.title())
					.withStyle(ChatFormatting.GOLD));
		}
		return true;
	}

	/** Martial Master NPC dialogue & Lost Old Coin exchange menu. */
	public static void talk(ServerPlayer player) {
		BadgeData data = get(player);
		int coins = countCoins(player.getInventory());
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.badge.vendor_header", coins)
				.withStyle(ChatFormatting.GOLD));
		for (Badge badge : Badge.values()) {
			MutableComponent line = badge.title().copy().withStyle(ChatFormatting.AQUA)
					.append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
					.append(badge.perkText().copy().withStyle(ChatFormatting.GREEN))
					.append(Component.literal(" / ").withStyle(ChatFormatting.DARK_GRAY))
					.append(badge.drawbackText().copy().withStyle(ChatFormatting.RED));
			if (data.hasUnlocked(badge)) {
				line.append(" ").append(Component.translatable("message.noclip_backrooms.badge.owned_tag")
						.withStyle(ChatFormatting.GRAY));
			} else {
				line.append(" ").append(Component.translatable("message.noclip_backrooms.badge.buy_btn", badge.coinCost)
						.withStyle(style -> style.withColor(ChatFormatting.GOLD)
								.withClickEvent(new ClickEvent.RunCommand("/meg badge unlock " + badge.id))
								.withHoverEvent(new HoverEvent.ShowText(
										Component.literal("/meg badge unlock " + badge.id)))));
			}
			player.sendSystemMessage(line);
		}
	}

	private static void showStatus(ServerPlayer player) {
		BadgeData data = get(player);
		Badge active = data.activeBadge();
		Component activeLabel = active != null
				? active.title()
				: Component.translatable("ui.noclip_backrooms.badge.none");
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.badge.status",
				activeLabel, data.unlocked().size(), Badge.values().length).withStyle(ChatFormatting.AQUA));
	}

	private static boolean nearVendor(ServerPlayer player) {
		return !player.level().getEntitiesOfClass(Person.class, player.getBoundingBox().inflate(VENDOR_REACH),
				p -> ShopRole.of(p) == ShopRole.MARTIAL_MASTER).isEmpty();
	}

	public static int countCoins(Inventory inv) {
		int total = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(BackroomsItems.LOST_OLD_COIN)) total += stack.getCount();
		}
		return total;
	}

	private static boolean consumeCoins(ServerPlayer player, int amount) {
		Inventory inv = player.getInventory();
		if (countCoins(inv) < amount) return false;
		int remaining = amount;
		for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
			ItemStack stack = inv.getItem(i);
			if (!stack.is(BackroomsItems.LOST_OLD_COIN)) continue;
			int take = Math.min(remaining, stack.getCount());
			stack.shrink(take);
			remaining -= take;
		}
		inv.setChanged();
		return true;
	}

	// ---- Pure badge modifier helpers (testable outside Fabric bootstrap) ----

	public static float magicSanityCostMultiplier(Badge active) {
		return active == Badge.ANOMALY_SCHOLAR ? 0.80F : 1.0F;
	}

	public static float magicSanityCostMultiplier(Player player) {
		return magicSanityCostMultiplier(active(player));
	}

	public static float passiveAnomalySanityPerSec(Badge active) {
		return active == Badge.ANOMALY_SCHOLAR ? 0.25F : 0.0F;
	}

	public static float passiveAnomalySanityPerSec(Player player) {
		return passiveAnomalySanityPerSec(active(player));
	}

	public static float meleeDamageMultiplier(Badge active) {
		return active == Badge.ANOMALY_SCHOLAR ? 0.85F : 1.0F;
	}

	public static float meleeDamageMultiplier(Player player) {
		return meleeDamageMultiplier(active(player));
	}

	public static float meleeHealthCostMultiplier(Badge active) {
		return active == Badge.VANGUARD_BREAKER ? 0.50F : 1.0F;
	}

	public static float meleeHealthCostMultiplier(Player player) {
		return meleeHealthCostMultiplier(active(player));
	}

	public static double knockbackMultiplier(Badge active) {
		return active == Badge.VANGUARD_BREAKER ? 1.25 : 1.0;
	}

	public static double knockbackMultiplier(Player player) {
		return knockbackMultiplier(active(player));
	}

	public static float firearmSpreadMultiplier(Badge active) {
		return active == Badge.VANGUARD_BREAKER ? 1.20F : 1.0F;
	}

	public static float firearmSpreadMultiplier(Player player) {
		return firearmSpreadMultiplier(active(player));
	}

	public static float extraFirearmCritChance(Badge active) {
		return active == Badge.TACTICAL_SCOUT ? 0.10F : 0.0F;
	}

	public static float extraFirearmCritChance(Player player) {
		return extraFirearmCritChance(active(player));
	}

	public static float extraAmmoSaveChance(Badge active) {
		return active == Badge.TACTICAL_SCOUT ? 0.10F : 0.0F;
	}

	public static float extraAmmoSaveChance(Player player) {
		return extraAmmoSaveChance(active(player));
	}

	public static double firearmNoiseRadius(Badge active) {
		return active == Badge.TACTICAL_SCOUT ? 30.0 : 24.0;
	}

	public static double firearmNoiseRadius(Player player) {
		return firearmNoiseRadius(active(player));
	}

	public static int regenDelayTicks(Badge active) {
		return active == Badge.SCAVENGER_SURVIVOR ? 70 : 100;
	}

	public static int regenDelayTicks(Player player) {
		return regenDelayTicks(active(player));
	}

	public static double extraLootChance(Badge active) {
		return active == Badge.SCAVENGER_SURVIVOR ? 0.10 : 0.0;
	}

	public static double extraLootChance(Player player) {
		return extraLootChance(active(player));
	}

	public static float monsterDamageMultiplier(Badge active) {
		return active == Badge.SCAVENGER_SURVIVOR ? 0.90F : 1.0F;
	}

	public static float monsterDamageMultiplier(Player player) {
		return monsterDamageMultiplier(active(player));
	}
}
