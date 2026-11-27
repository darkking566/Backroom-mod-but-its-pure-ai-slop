package dev.noclip.backrooms.core.ending;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.floors.Floors;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.quota.Quota;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.core.trade.ShopRole;
import dev.noclip.backrooms.core.trip.Pull;
import dev.noclip.backrooms.core.trip.PullState;
import dev.noclip.backrooms.frontrooms.Institute;
import dev.noclip.backrooms.items.BackroomsItems;
import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.monsters.hound.Hound;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Dual-ending endgame progression (docs/企劃書/1-遊戲玩法/README.md, "主線與雙結局"):
 * <ul>
 *   <li><b>Ending A — Cured &amp; Contract Released ("contract")</b>: research Purple Science Pack, craft and drink the
 *       9x9 {@code wretch_cure} (permanently eliminating Wretch rejection and healing to full; max health stays halved), reach full
 *       institute clearance (5 quotas met, debt-free), and formally terminate the M.E.G. contract with the commander to
 *       return freely to the real Overworld.</li>
 *   <li><b>Ending B — Defiance / True Escape ("true_escape")</b>: crack B2's archive hatch, read all 6 classified
 *       Frontrooms truth files, then either override a B1 transport pad with a Tier 3/4 Keypad Hacker or survive a
 *       45-second deep-level coordinate override ({@code floor >= 3} or Level 3) to break out of M.E.G.'s quarantine
 *       back to the real Overworld.</li>
 * </ul>
 */
public final class Endings {
	public static final String ENDING_CONTRACT = "contract";
	public static final String ENDING_TRUE_ESCAPE = "true_escape";
	public static final int TOTAL_ARCHIVE_FILES = 6;
	public static final int FULL_FILES_MASK = (1 << TOTAL_ARCHIVE_FILES) - 1;
	private static final int OVERRIDE_TICKS = 45 * 20;
	private static final double OVERRIDE_RADIUS = 10.0;

	public record State(boolean cured, String ending, int filesMask) {
		public static final State NONE = new State(false, "", 0);
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.optionalFieldOf("cured", false).forGetter(State::cured),
				Codec.STRING.optionalFieldOf("ending", "").forGetter(State::ending),
				Codec.INT.optionalFieldOf("files_mask", 0).forGetter(State::filesMask)
		).apply(i, State::new));

		public int filesCount() {
			return Integer.bitCount(filesMask & FULL_FILES_MASK);
		}

		public boolean truthKnown() {
			return (filesMask & FULL_FILES_MASK) == FULL_FILES_MASK;
		}

		public boolean freed() {
			return !ending.isEmpty();
		}
	}

	public static final AttachmentType<State> STATE = AttachmentRegistry.create(BackroomsMod.id("ending_state"),
			builder -> builder.initializer(() -> State.NONE).persistent(State.CODEC).copyOnDeath());

	private static final Map<UUID, OverrideRun> OVERRIDES = new HashMap<>();

	private static final class OverrideRun {
		final ResourceKey<Level> dimension;
		final Vec3 origin;
		final ServerBossEvent bar;
		int left;

		OverrideRun(ServerPlayer player) {
			this.dimension = player.level().dimension();
			this.origin = player.position();
			this.left = OVERRIDE_TICKS;
			this.bar = new ServerBossEvent(UUID.randomUUID(),
					Component.translatable("bossbar.noclip_backrooms.true_escape", OVERRIDE_TICKS / 20),
					BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
			this.bar.addPlayer(player);
		}
	}

	private Endings() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Endings::tickOverrides);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (OverrideRun run : OVERRIDES.values()) run.bar.removeAllPlayers();
			OVERRIDES.clear();
		});
		registerCommands();
	}

	public static State of(ServerPlayer player) {
		return player.getAttachedOrElse(STATE, State.NONE);
	}

	public static boolean isCured(ServerPlayer player) {
		return of(player).cured();
	}

	public static boolean isFree(ServerPlayer player) {
		return of(player).freed();
	}

	public static boolean truthKnown(ServerPlayer player) {
		return of(player).truthKnown();
	}

	public static int filesRead(ServerPlayer player) {
		return of(player).filesCount();
	}

	/** Permanently cures the player's Wretch cycle, clearing rejection and healing to full (max health stays halved). */
	public static void cure(ServerPlayer player) {
		State cur = of(player);
		if (!cur.cured()) {
			player.setAttached(STATE, new State(true, cur.ending(), cur.filesMask()));
		}
		Pull.set(player, PullState.NONE);
		PlayerRpg.applyAttributes(player, PlayerRpg.get(player));
		player.removeEffect(MobEffects.HUNGER);
		player.removeEffect(MobEffects.WEAKNESS);
		player.removeEffect(MobEffects.DARKNESS);
		player.removeEffect(MobEffects.NAUSEA);
		player.heal(player.getMaxHealth());
		PlayerRpg.restoreSanity(player, 100.0F);
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.ending.cured").withStyle(ChatFormatting.GREEN));
	}

	/** Records reading one of B2's six classified archive files; announces when the full truth is uncovered. */
	public static void recordFile(ServerPlayer player, int page) {
		if (page < 0 || page >= TOTAL_ARCHIVE_FILES) return;
		State cur = of(player);
		int nextMask = cur.filesMask() | (1 << page);
		if (nextMask == cur.filesMask()) return;
		State next = new State(cur.cured(), cur.ending(), nextMask);
		player.setAttached(STATE, next);
		int count = next.filesCount();
		if (next.truthKnown()) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.ending.truth_complete",
					count, TOTAL_ARCHIVE_FILES).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
		} else {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.ending.truth_progress",
					count, TOTAL_ARCHIVE_FILES).withStyle(ChatFormatting.AQUA));
		}
	}

	/** Ending A: Cured investigator terminates the M.E.G. contract with the commander (5 quotas met, debt-free). */
	public static boolean tryContractEnding(ServerPlayer player, boolean checkCommander) {
		MinecraftServer server = player.level().getServer();
		if (!isCured(player)) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.ending.need_cure").withStyle(ChatFormatting.RED));
			return false;
		}
		if (Quota.locked(server)) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.ending.has_debt").withStyle(ChatFormatting.RED));
			return false;
		}
		if (Quota.clearance(server) < Quota.DEEP_CLEARANCE) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.ending.need_clearance", Quota.DEEP_CLEARANCE)
					.withStyle(ChatFormatting.RED));
			return false;
		}
		if (checkCommander && !nearCommander(player)) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.quota.deploy_no_commander").withStyle(ChatFormatting.RED));
			return false;
		}
		completeEnding(player, ENDING_CONTRACT);
		return true;
	}

	/**
	 * Ending B: Defiance / True Escape.
	 * Can be triggered immediately by overriding a B1 transport pad with a Tier 3/4 Keypad Hacker,
	 * or by launching a 45-second deep-Backrooms override ({@code floor >= 3} or Level 3).
	 */
	public static boolean tryTrueEscape(ServerPlayer player, boolean fromTransportPad) {
		if (!truthKnown(player)) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.ending.need_truth",
					filesRead(player), TOTAL_ARCHIVE_FILES).withStyle(ChatFormatting.RED));
			return false;
		}
		ItemStack hacker = findHighHacker(player);
		if (hacker.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.ending.need_hacker").withStyle(ChatFormatting.RED));
			return false;
		}
		if (fromTransportPad || Institute.onPad(player)) {
			if (!player.isCreative()) hacker.hurtAndBreak(40, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
			completeEnding(player, ENDING_TRUE_ESCAPE);
			return true;
		}
		if (!Home.inBackrooms(player.level().dimension())) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.ending.escape_where").withStyle(ChatFormatting.RED));
			return false;
		}
		String levelKey = Floors.key(player.level());
		int floor = Floors.of(player).floor();
		if (floor < 3 && !"3".equals(levelKey)) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.ending.escape_too_shallow").withStyle(ChatFormatting.RED));
			return false;
		}
		if (OVERRIDES.containsKey(player.getUUID())) {
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.ending.override_running").withStyle(ChatFormatting.LIGHT_PURPLE));
			return false;
		}
		if (!player.isCreative()) hacker.hurtAndBreak(40, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
		OVERRIDES.put(player.getUUID(), new OverrideRun(player));
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.ending.override_started", OVERRIDE_TICKS / 20)
				.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.7F);
		return true;
	}

	public static boolean overriding(ServerPlayer player) {
		return OVERRIDES.containsKey(player.getUUID());
	}

	/** For GM and tests: finish an active Ending B deep override immediately. */
	public static void finishOverrideNow(ServerPlayer player) {
		OverrideRun run = OVERRIDES.get(player.getUUID());
		if (run != null) run.left = 1;
	}

	private static void tickOverrides(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, OverrideRun>> it = OVERRIDES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, OverrideRun> entry = it.next();
			OverrideRun run = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			String broken = player == null || player.hasDisconnected() ? ""
					: !player.isAlive() ? "message.noclip_backrooms.extraction.dead"
					: player.level().dimension() != run.dimension ? "message.noclip_backrooms.extraction.left_level"
					: player.position().distanceTo(run.origin) > OVERRIDE_RADIUS ? "message.noclip_backrooms.extraction.wandered" : null;
			if (broken != null) {
				run.bar.removeAllPlayers();
				it.remove();
				if (player != null && !broken.isEmpty()) {
					player.sendSystemMessage(Component.translatable(broken).withStyle(ChatFormatting.RED));
				}
				continue;
			}
			run.left--;
			run.bar.setProgress(1.0F - (float) run.left / OVERRIDE_TICKS);
			run.bar.setName(Component.translatable("bossbar.noclip_backrooms.true_escape", (run.left + 19) / 20));
			ServerLevel level = (ServerLevel) player.level();
			if (run.left % 40 == 0) {
				for (Monster m : level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(44), Monster::isAlive)) {
					m.setTarget(player);
				}
				level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 20, 1.0, 1.0, 1.0, 0.15);
			}
			if (run.left == OVERRIDE_TICKS * 2 / 3 || run.left == OVERRIDE_TICKS / 3) {
				Hound hound = Monsters.HOUND.create(level, EntitySpawnReason.EVENT);
				if (hound != null) {
					BlockPos at = player.blockPosition().offset(level.getRandom().nextBoolean() ? 10 : -10, 0, level.getRandom().nextInt(9) - 4);
					if (level.getBlockState(at).isAir()) {
						hound.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
						hound.setTarget(player);
						level.addFreshEntity(hound);
					}
				}
				player.sendSystemMessage(Component.translatable("message.noclip_backrooms.extraction.wave").withStyle(ChatFormatting.DARK_RED));
			}
			if (run.left <= 0) {
				run.bar.removeAllPlayers();
				it.remove();
				completeEnding(player, ENDING_TRUE_ESCAPE);
			}
		}
	}

	/** Finalizes Ending A or B, plays celebration title/audio, grants freedom, and sends player to the real Overworld. */
	private static void completeEnding(ServerPlayer player, String type) {
		State cur = of(player);
		player.setAttached(STATE, new State(cur.cured() || ENDING_CONTRACT.equals(type), type, cur.filesMask()));
		if (ENDING_CONTRACT.equals(type)) {
			Pull.set(player, PullState.NONE);
		}
		PlayerRpg.applyAttributes(player, PlayerRpg.get(player));
		sendToRealOverworld(player);

		boolean contract = ENDING_CONTRACT.equals(type);
		Component title = Component.translatable(contract ? "ending.noclip_backrooms.contract.title" : "ending.noclip_backrooms.escape.title")
				.withStyle(contract ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
		Component sub = Component.translatable(contract ? "ending.noclip_backrooms.contract.subtitle" : "ending.noclip_backrooms.escape.subtitle")
				.withStyle(ChatFormatting.WHITE);
		player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 120, 40));
		player.connection.send(new ClientboundSetSubtitleTextPacket(sub));
		player.connection.send(new ClientboundSetTitleTextPacket(title));
		ServerLevel ow = player.level().getServer().overworld();
		ow.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
		ow.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.2, player.getZ(), 60, 1.4, 1.2, 1.4, 0.12);

		Component announce = Component.translatable(contract ? "message.noclip_backrooms.ending.contract_done" : "message.noclip_backrooms.ending.escape_done",
				player.getDisplayName()).withStyle(contract ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE);
		for (ServerPlayer p : player.level().getServer().getPlayerList().getPlayers()) {
			p.sendSystemMessage(announce);
		}
	}

	/** Teleports a freed player safely back to the real Overworld (spawn point). */
	public static void sendToRealOverworld(ServerPlayer player) {
		ServerLevel overworld = player.level().getServer().overworld();
		BlockPos spawnPos = player.adjustSpawnLocation(overworld, overworld.getRespawnData().pos());
		overworld.getChunk(spawnPos);
		player.stopRiding();
		player.resetFallDistance();
		player.teleport(new TeleportTransition(overworld, Vec3.atBottomCenterOf(spawnPos), Vec3.ZERO, 0, 0, TeleportTransition.PLAY_PORTAL_SOUND));
	}

	private static ItemStack findHighHacker(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack st = player.getInventory().getItem(i);
			if (st.is(BackroomsItems.KEYPAD_HACKER_T3) || st.is(BackroomsItems.KEYPAD_HACKER_T4)) return st;
		}
		return ItemStack.EMPTY;
	}

	private static boolean nearCommander(ServerPlayer player) {
		return !player.level().getEntitiesOfClass(Person.class, new AABB(player.blockPosition()).inflate(8.0),
				person -> ShopRole.of(person) == ShopRole.MASTER).isEmpty();
	}

	private static void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
			dispatcher.register(Commands.literal("meg")
					.then(Commands.literal("ending")
							.then(Commands.literal("contract").executes(ctx ->
									tryContractEnding(ctx.getSource().getPlayerOrException(), true) ? 1 : 0))
							.then(Commands.literal("escape").executes(ctx ->
									tryTrueEscape(ctx.getSource().getPlayerOrException(), false) ? 1 : 0))
							.then(Commands.literal("home").executes(ctx -> {
								ServerPlayer p = ctx.getSource().getPlayerOrException();
								if (!isFree(p)) {
									p.sendSystemMessage(Component.translatable("message.noclip_backrooms.ending.not_free").withStyle(ChatFormatting.RED));
									return 0;
								}
								sendToRealOverworld(p);
								return 1;
							}))
							.then(Commands.literal("institute").executes(ctx -> {
								ServerPlayer p = ctx.getSource().getPlayerOrException();
								if (!isFree(p)) {
									p.sendSystemMessage(Component.translatable("message.noclip_backrooms.ending.not_free").withStyle(ChatFormatting.RED));
									return 0;
								}
								Institute.receive(p);
								return 1;
							}))));

			dispatcher.register(Commands.literal("backrooms")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.literal("ending")
							.then(Commands.literal("status").executes(ctx -> {
								ServerPlayer p = ctx.getSource().getPlayerOrException();
								State s = of(p);
								ctx.getSource().sendSuccess(() -> Component.literal(String.format(
										"Ending state: cured=%s, ending='%s', archiveFiles=%d/%d",
										s.cured(), s.ending(), s.filesCount(), TOTAL_ARCHIVE_FILES)), false);
								return 1;
							}))
							.then(Commands.literal("cure").executes(ctx -> {
								cure(ctx.getSource().getPlayerOrException());
								return 1;
							}))
							.then(Commands.literal("truth").executes(ctx -> {
								ServerPlayer p = ctx.getSource().getPlayerOrException();
								for (int i = 0; i < TOTAL_ARCHIVE_FILES; i++) recordFile(p, i);
								return 1;
							}))
							.then(Commands.literal("finish").executes(ctx -> {
								finishOverrideNow(ctx.getSource().getPlayerOrException());
								return 1;
							}))
							.then(Commands.literal("reset").executes(ctx -> {
								ServerPlayer p = ctx.getSource().getPlayerOrException();
								p.setAttached(STATE, State.NONE);
								PlayerRpg.applyAttributes(p, PlayerRpg.get(p));
								ctx.getSource().sendSuccess(() -> Component.literal("Reset ending state."), false);
								return 1;
							}))));
		});
	}
}
