package dev.noclip.backrooms.core.trip;

import com.mojang.brigadier.arguments.FloatArgumentType;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.economy.Economy;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.core.rpg.PlayerStats;
import dev.noclip.backrooms.core.trade.ShopOffer;
import dev.noclip.backrooms.core.trade.ShopRole;
import dev.noclip.backrooms.core.trade.Shops;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

/**
 * Mode 1 + Mode 2 Horror Loop: Overworld Backrooms Seepage &amp; Wretch Reality-Rejection
 * (docs/project/1-gameplay/README.md).
 *
 * <p>Replaces passive forced teleportation with escalating Overworld dread:
 * <ul>
 *   <li>Escaping alive grants 2 full in-game days of safety ({@link PullState#ESCAPE_GRACE_TICKS}).</li>
 *   <li>Afterward, rejection/seepage severity climbs from 0 to 100 across 4 stages in the Overworld:
 *       sanity stops recovering and starts draining, hunger/weakness set in, nearby natural ground
 *       blocks reversibly seep into damp yellow Backrooms carpet, and at Stage 2+ a glowing
 *       {@code backrooms_rift} tears open outdoors so the player must actively choose to step back in.</li>
 *   <li>Tier 1 Almond Stabilizer (Level 0/1) relieves symptoms for 2.0d -&gt; 0.5d with rising tolerance;
 *       Tier 2 Reality Serum (Level 2/3) grants 6d, lowers tolerance, and restores all seeped blocks + closes rift;
 *       Tier 3 Reality Anchor Core (deep/7x7) grants 12d, fully restores terrain, and resets tolerance to 0.</li>
 * </ul>
 */
public final class Pull {
	public static final AttachmentType<PullState> STATE = AttachmentRegistry.create(BackroomsMod.id("backrooms_pull"),
			builder -> builder
					.initializer(() -> PullState.NONE)
					.persistent(PullState.CODEC)
					.copyOnDeath()
					.syncWith(PullState.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	public static final float STAGE_1 = 25.0F;
	public static final float STAGE_2 = 50.0F;
	public static final float STAGE_3 = 75.0F;
	public static final float STAGE_4 = 95.0F;
	public static final float FALL = 100.0F;
	/** Warnings start here on the client. */
	public static final float UNEASY = STAGE_1, CLOSE = STAGE_2;
	/** Two in-game days of peace after escaping alive. */
	public static final long GRACE_TICKS = PullState.ESCAPE_GRACE_TICKS;

	private static final Identifier MOD_WRETCH_HP = BackroomsMod.id("wretch_rejection_hp");
	/** Base severity rise per second once the 2-day safety buffer expires (~0.83 in-game days per stage). */
	static final float BASE_RISE_PER_SEC = 0.025F;
	static final float DARK_BONUS = 0.012F;
	static final float SHAKEN_BONUS = 0.015F;
	static final float PER_RELIC_BONUS = 0.0012F;
	static final int MAX_RELICS = 15;

	private static Set<Item> supplies;

	private Pull() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Pull::tick);
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (level instanceof ServerLevel sl && sl.dimension() == Level.OVERWORLD) {
				SeepageData.get(sl).pruneIfBroken(sl, pos);
			}
		});
		registerCommands();
	}

	public static PullState of(ServerPlayer player) {
		return player.getAttachedOrElse(STATE, PullState.NONE);
	}

	public static void set(ServerPlayer player, PullState state) {
		player.setAttached(STATE, state);
		syncHealthPenalty(player, state);
	}

	/** Current rejection stage (0..4) in the Overworld; 0 outside the Overworld. */
	public static int stage(ServerPlayer player) {
		if (player.level().dimension() != Level.OVERWORLD) return 0;
		return of(player).stage();
	}

	/** Called when a player escapes the Backrooms alive: marks them and starts the 2-day safe buffer. */
	public static void grace(ServerPlayer player) {
		PullState next = of(player).markEscaped(now(player.level().getServer()));
		set(player, next);
	}

	public static boolean inGrace(ServerPlayer player) {
		return now(player.level().getServer()) < of(player).graceUntil();
	}

	/** Tier 1 Almond Stabilizer (Level 0/1): 2.0d -> 0.5d relief depending on tolerance, restores up to 12 blocks. */
	public static void useTier1Stabilizer(ServerPlayer player) {
		long now = now(player.level().getServer());
		PullState before = of(player);
		long duration = PullState.tier1ReliefTicks(before.tolerance());
		PullState after = before.applyTier1Stabilizer(now);
		set(player, after);

		int restored = 0;
		ServerLevel overworld = player.level().getServer().overworld();
		if (overworld != null) {
			restored = SeepageData.get(overworld).restoreFor(overworld, player.getUUID(), player.blockPosition(), 12);
		}
		String days = String.format("%.1f", duration / (double) PullState.TICKS_PER_DAY);
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.rejection.stabilizer_t1",
				days, after.tolerance(), PullState.MAX_TOLERANCE, restored).withStyle(ChatFormatting.AQUA));
		if (after.tolerance() >= 3) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.rejection.tolerance_warn")
					.withStyle(ChatFormatting.GOLD));
		}
	}

	/** Tier 2 High-Purity Reality Serum (Level 2/3): 6d relief, -1 tolerance, restores ALL seeped blocks &amp; closes rift. */
	public static void useTier2Serum(ServerPlayer player) {
		long now = now(player.level().getServer());
		PullState after = of(player).applyTier2Serum(now);
		set(player, after);

		int restored = 0;
		ServerLevel overworld = player.level().getServer().overworld();
		if (overworld != null) {
			restored = SeepageData.get(overworld).clearAllFor(overworld, player.getUUID(), player.blockPosition());
		}
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.rejection.serum_t2",
				restored, after.tolerance()).withStyle(ChatFormatting.GREEN));
	}

	/** Tier 3 Reality Anchor Core (deep/7x7): 12d safety, restores all terrain &amp; rift, resets tolerance to 0. */
	public static void useTier3Core(ServerPlayer player) {
		long now = now(player.level().getServer());
		PullState after = of(player).applyTier3Core(now);
		set(player, after);

		int restored = 0;
		ServerLevel overworld = player.level().getServer().overworld();
		if (overworld != null) {
			restored = SeepageData.get(overworld).clearAllFor(overworld, player.getUUID(), player.blockPosition());
		}
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.rejection.core_t3", restored)
				.withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	/** Minor temporary relief when drinking Almond Elixir / Aged Almond Reserve. */
	public static void applyMinorRelief(ServerPlayer player, float severityDrop, long extraTicks) {
		PullState before = of(player);
		if (!before.marked() && before.pull() <= 0.0F) return;
		PullState after = before.applyMinorRelief(now(player.level().getServer()), severityDrop, extraTicks);
		set(player, after);
		if (before.stage() > after.stage()) {
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.rejection.minor_relief")
					.withStyle(ChatFormatting.AQUA));
		}
	}

	private static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	private static void tick(MinecraftServer server) {
		int tickCount = server.getTickCount();
		if (tickCount % 20 != 0) return; // 1-second cadence
		long now = now(server);
		ServerLevel overworld = server.overworld();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			PullState state = of(player);
			if (player.level().dimension() != Level.OVERWORLD) {
				// Inside the Backrooms: rejection eases off gradually and Overworld HP penalty clears
				if (state.pull() > 0.0F) {
					set(player, state.withPull(Math.max(0.0F, state.pull() - 0.5F)));
				} else {
					syncHealthPenalty(player, state);
				}
				continue;
			}
			if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
				syncHealthPenalty(player, state);
				continue;
			}

			int prevStage = state.stage();
			if (!state.marked() && state.pull() <= 0.0F) {
				syncHealthPenalty(player, state);
				continue;
			}

			// During 2-day escape buffer or serum relief window, severity stays safely suppressed
			if (now < state.graceUntil()) {
				if (state.pull() > 0.0F) {
					set(player, state.withPull(Math.max(0.0F, state.pull() - 2.0F)));
				} else {
					syncHealthPenalty(player, state);
				}
				continue;
			}

			// Rejection escalates in the Overworld — NEVER passively teleports the player!
			float rate = rate(player);
			float nextPull = Math.min(FALL, state.pull() + rate);
			PullState next = nextPull != state.pull() ? state.withPull(nextPull) : state;
			if (next != state) {
				set(player, next);
			} else {
				syncHealthPenalty(player, next);
			}

			int curStage = next.stage();
			if (curStage > prevStage) {
				announceStage(player, curStage);
			}
			if (curStage >= 1) {
				applyStageEffects(overworld, player, curStage, tickCount);
			}
		}
	}

	private static void applyStageEffects(ServerLevel overworld, ServerPlayer player, int stage, int tickCount) {
		SeepageData seepage = SeepageData.get(overworld);

		// 1. Reversible natural-ground seepage into damp yellow Backrooms carpet (never touches buildings/chests!)
		int seepIntervalTicks = switch (stage) {
			case 1 -> 900;  // every 45s
			case 2 -> 500;  // every 25s
			case 3 -> 300;  // every 15s
			default -> 200; // every 10s
		};
		if (tickCount % seepIntervalTicks == 0) {
			int tries = stage >= 3 ? 2 : 1;
			for (int i = 0; i < tries; i++) {
				seepage.trySeepNear(overworld, player.getUUID(), player.blockPosition(), 4, 16, player.getRandom());
			}
		}

		// 2. At Stage 2+, open an outdoor glowing Backrooms Rift so the player can choose to step back in
		if (stage >= 2 && tickCount % 100 == 0) {
			BlockPos prev = seepage.riftFor(player.getUUID());
			BlockPos active = seepage.ensureRiftNear(overworld, player.getUUID(), player.blockPosition(), player.getRandom());
			if (active != null && (prev == null || !prev.equals(active))) {
				overworld.playSound(null, active.getX() + 0.5, active.getY() + 0.5, active.getZ() + 0.5,
						SoundEvents.PORTAL_TRIGGER, SoundSource.AMBIENT, 0.7F, 0.65F);
				player.sendSystemMessage(Component.translatable("message.noclip_backrooms.rift.opened",
						active.getX(), active.getY(), active.getZ()).withStyle(ChatFormatting.YELLOW));
			}
		}

		// 3. Wretch physiological symptoms (hunger / weakness / stage-4 darkness pulse)
		if (stage == 1 && tickCount % 600 == 0) {
			player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 140, 0, true, false, true));
		} else if (stage == 2 && tickCount % 400 == 0) {
			player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 0, true, false, true));
		} else if (stage == 3 && tickCount % 260 == 0) {
			player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 220, 1, true, false, true));
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 220, 0, true, false, true));
		} else if (stage >= 4 && tickCount % 160 == 0) {
			player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 240, 1, true, false, true));
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 240, 0, true, false, true));
			if (player.getRandom().nextFloat() < 0.35F) {
				player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, true, false, false));
			}
		}
	}

	private static void announceStage(ServerPlayer player, int stage) {
		String key = "message.noclip_backrooms.rejection.stage_" + stage;
		ChatFormatting color = switch (stage) {
			case 1 -> ChatFormatting.GRAY;
			case 2 -> ChatFormatting.YELLOW;
			case 3 -> ChatFormatting.GOLD;
			default -> ChatFormatting.RED;
		};
		player.sendSystemMessage(Component.translatable(key).withStyle(color));
	}

	private static void syncHealthPenalty(ServerPlayer player, PullState state) {
		AttributeInstance maxHp = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHp == null) return;
		boolean penalize = player.level().dimension() == Level.OVERWORLD
				&& !player.isCreative() && !player.isSpectator()
				&& state.stage() >= 4;
		if (penalize) {
			maxHp.addOrUpdateTransientModifier(new AttributeModifier(MOD_WRETCH_HP, -2.0, AttributeModifier.Operation.ADD_VALUE));
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
		} else {
			maxHp.removeModifier(MOD_WRETCH_HP);
		}
	}

	/** Escalation rate per second in the Overworld once the safety window expires. */
	static float rate(ServerPlayer player) {
		float rate = BASE_RISE_PER_SEC;
		BlockPos eyes = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
		Level level = player.level();
		if (level.getBrightness(LightLayer.BLOCK, eyes) <= 3 && level.getBrightness(LightLayer.SKY, eyes) <= 2) {
			rate += DARK_BONUS;
		}
		if (PlayerRpg.get(player).sanityRatio() < PlayerStats.HALLUCINATION_THRESHOLD) {
			rate += SHAKEN_BONUS;
		}
		rate += PER_RELIC_BONUS * Math.min(MAX_RELICS, relics(player));
		return rate;
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

	private static void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("backrooms")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("rejection")
								.then(Commands.literal("status").executes(ctx -> {
									ServerPlayer p = ctx.getSource().getPlayerOrException();
									PullState st = of(p);
									long rem = Math.max(0L, st.graceUntil() - now(ctx.getSource().getServer()));
									ServerLevel ow = ctx.getSource().getServer().overworld();
									int seeped = SeepageData.get(ow).countFor(p.getUUID());
									BlockPos rift = SeepageData.get(ow).riftFor(p.getUUID());
									ctx.getSource().sendSuccess(() -> Component.literal(String.format(
											"Wretch Rejection: stage=%d (%.1f/100), marked=%s, tolerance=%d/%d, safeTicks=%d, seeped=%d, rift=%s",
											st.stage(), st.pull(), st.marked(), st.tolerance(), PullState.MAX_TOLERANCE,
											rem, seeped, rift == null ? "none" : rift.toShortString())), false);
									return 1;
								}))
								.then(Commands.literal("set")
										.then(Commands.argument("severity", FloatArgumentType.floatArg(0.0F, 100.0F))
												.executes(ctx -> {
													ServerPlayer p = ctx.getSource().getPlayerOrException();
													float val = FloatArgumentType.getFloat(ctx, "severity");
													PullState cur = of(p);
													set(p, new PullState(val, 0L, val > 0.0F || cur.marked(), cur.tolerance()));
													ctx.getSource().sendSuccess(() -> Component.literal("Set rejection severity to " + val), false);
													return 1;
												})))
								.then(Commands.literal("clear").executes(ctx -> {
									ServerPlayer p = ctx.getSource().getPlayerOrException();
									set(p, PullState.NONE);
									ServerLevel ow = ctx.getSource().getServer().overworld();
									int restored = SeepageData.get(ow).clearAllFor(ow, p.getUUID(), p.blockPosition());
									ctx.getSource().sendSuccess(() -> Component.literal("Cleared rejection & restored " + restored + " seeped blocks."), false);
									return 1;
								}))
								.then(Commands.literal("seep").executes(ctx -> {
									ServerPlayer p = ctx.getSource().getPlayerOrException();
									ServerLevel ow = ctx.getSource().getServer().overworld();
									SeepageData data = SeepageData.get(ow);
									int placed = 0;
									for (int i = 0; i < 6; i++) {
										if (data.trySeepNear(ow, p.getUUID(), p.blockPosition(), 3, 14, p.getRandom()) != null) placed++;
									}
									BlockPos rift = data.ensureRiftNear(ow, p.getUUID(), p.blockPosition(), p.getRandom());
									int count = placed;
									ctx.getSource().sendSuccess(() -> Component.literal("Seeped " + count + " blocks; rift=" + (rift == null ? "none" : rift.toShortString())), false);
									return 1;
								})))));
	}
}
