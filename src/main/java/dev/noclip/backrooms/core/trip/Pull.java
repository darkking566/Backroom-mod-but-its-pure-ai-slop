package dev.noclip.backrooms.core.trip;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.LevelExits;
import dev.noclip.backrooms.core.economy.Economy;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.core.rpg.PlayerStats;
import dev.noclip.backrooms.core.trade.ShopOffer;
import dev.noclip.backrooms.core.trade.ShopRole;
import dev.noclip.backrooms.core.trade.Shops;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

/**
 * The Backrooms pull people back in (docs/HORROR_ESCAPE_PLAN.md). In the Overworld a player's pull rises while they
 * stay somewhere dark and shut in, deep underground, with their sanity low, or carrying things brought out of the
 * Backrooms; it ebbs when none of that is true. At FALL they drop into Level 0. The client shows the warning signs as it
 * rises (core.trip.PullClient: a yellow cast, flickering, the hum of fluorescent lights). Falling asleep in a dark room
 * can also end in Level 0. For a day after getting out, nothing pulls.
 */
public final class Pull {
	public static final AttachmentType<PullState> STATE = AttachmentRegistry.create(BackroomsMod.id("backrooms_pull"),
			builder -> builder
					.initializer(() -> PullState.NONE)
					.persistent(PullState.CODEC)
					.copyOnDeath()
					.syncWith(PullState.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));
	public static final float FALL = 100.0F;
	/** Warnings start here (client). */
	public static final float UNEASY = 50.0F, CLOSE = 75.0F;
	/** One in-game day of peace after getting out. */
	public static final long GRACE_TICKS = 24_000L;
	/** Per second: shut in somewhere dark, deep underground, with sanity low, per stack of Backrooms things. */
	static final float DARK = 0.3F, DEEP = 0.2F, SHAKEN = 0.3F, PER_RELIC = 0.015F;
	static final int MAX_RELICS = 20;
	static final float EBB = 0.3F;
	/** Falling asleep where the bed's light is this or less risks waking up in Level 0. */
	static final int DARK_BED = 4;
	static final float SLEEP_CHANCE = 0.2F;

	private static final Set<UUID> DREAMING = new HashSet<>();
	private static Set<Item> supplies;

	private Pull() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Pull::tick);
		EntitySleepEvents.START_SLEEPING.register((entity, bed) -> {
			if (!(entity instanceof ServerPlayer player) || player.level().dimension() != Level.OVERWORLD) return;
			if (player.isCreative() || player.isSpectator() || inGrace(player)) return;
			int light = Math.max(player.level().getBrightness(LightLayer.BLOCK, bed), player.level().getBrightness(LightLayer.BLOCK, bed.above()));
			if (light > DARK_BED) return;
			if (player.getRandom().nextFloat() < SLEEP_CHANCE + of(player).pull() / 200.0F) DREAMING.add(player.getUUID());
		});
		EntitySleepEvents.STOP_SLEEPING.register((entity, bed) -> {
			if (!(entity instanceof ServerPlayer player) || !DREAMING.remove(player.getUUID())) return;
			// Only if they slept through the night: getting straight back up is just lying down for a moment.
			if (player.level().dimension() == Level.OVERWORLD && player.level().isBrightOutside()) {
				fall(player, "message.noclip_backrooms.pull.dream");
			}
		});
	}

	public static PullState of(ServerPlayer player) {
		return player.getAttachedOrElse(STATE, PullState.NONE);
	}

	public static void set(ServerPlayer player, PullState state) {
		player.setAttached(STATE, state);
	}

	/** Just out of the Backrooms: nothing pulls for a day. */
	public static void grace(ServerPlayer player) {
		set(player, new PullState(0.0F, now(player.level().getServer()) + GRACE_TICKS));
	}

	public static boolean inGrace(ServerPlayer player) {
		return now(player.level().getServer()) < of(player).graceUntil();
	}

	private static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) return;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			PullState state = of(player);
			if (player.level().dimension() != Level.OVERWORLD) {
				if (state.pull() > 0.0F) set(player, state.withPull(0.0F));
				continue;
			}
			if (player.isCreative() || player.isSpectator() || !player.isAlive() || player.isSleeping()) continue;
			float rate = inGrace(player) ? 0.0F : rate(player);
			float pull = rate > 0.0F ? state.pull() + rate : Math.max(0.0F, state.pull() - EBB);
			if (pull >= FALL) {
				set(player, state.withPull(0.0F));
				fall(player, "message.noclip_backrooms.pull.fell");
			} else if (pull != state.pull()) {
				set(player, state.withPull(pull));
			}
		}
	}

	/** How fast the pull rises this second; 0 when nothing about where the player is pulls. */
	static float rate(ServerPlayer player) {
		float rate = 0.0F;
		BlockPos eyes = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
		Level level = player.level();
		if (level.getBrightness(LightLayer.BLOCK, eyes) <= 3 && level.getBrightness(LightLayer.SKY, eyes) <= 2) rate += DARK;
		if (player.getY() < 0) rate += DEEP;
		if (PlayerRpg.get(player).sanityRatio() < PlayerStats.HALLUCINATION_THRESHOLD) rate += SHAKEN;
		rate += PER_RELIC * Math.min(MAX_RELICS, relics(player));
		return rate;
	}

	private static void fall(ServerPlayer player, String message) {
		DREAMING.remove(player.getUUID());
		player.sendSystemMessage(Component.translatable(message).withStyle(ChatFormatting.GRAY));
		LevelExits.fallInto(player, "0");
	}

	/** Stacks the player carries that were brought out of the Backrooms (not M.E.G. supplies, coins or gear). */
	public static int relics(ServerPlayer player) {
		int count = 0;
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (isRelic(inventory.getItem(i))) count++;
		}
		return count;
	}

	public static boolean isRelic(ItemStack stack) {
		if (stack.isEmpty() || stack.isDamageableItem()) return false;
		if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(BackroomsMod.MOD_ID)) return false;
		if (stack.is(Economy.LOST_COIN) || stack.is(Economy.STATIC_TAPE)) return false;
		return !supplies().contains(stack.getItem());
	}

	/** What the outpost's merchants sell: M.E.G. supplies, not things from the Backrooms. */
	private static synchronized Set<Item> supplies() {
		if (supplies == null) {
			Set<Item> set = new HashSet<>();
			for (ShopRole role : ShopRole.values()) {
				for (ShopOffer offer : Shops.offers(role)) {
					if (!offer.fromPlayer()) set.add(offer.goods().getItem());
				}
			}
			supplies = set;
		}
		return supplies;
	}
}
