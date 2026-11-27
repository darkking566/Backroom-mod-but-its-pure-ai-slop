package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.core.LevelExits;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.core.rpg.PlayerStats;
import dev.noclip.backrooms.items.BackroomsItems;
import dev.noclip.backrooms.level1.Level1Items;
import dev.noclip.backrooms.props.BackroomsProps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Pure psychological-horror micro-event & POI engine for Level 0 (zero roaming monsters):
 * 1. Loose ceiling tile search (perception / sanity linked)
 * 2. Hollow peeling wallpaper knock
 * 3. Mirror / peek-window uncanny reflection & Level 0.5 giant mirror gaze crossing
 * 4. Damaged ceiling water drips & deep darkened puddle crossing (Level 0.11)
 * 5. Domino corridor blackout (progressive lamp shutoff -> recovery, photo-able as blackout_moment)
 * 6. Unplugged phantom phone ringing near office/POI zones
 * 7. Delayed phantom footsteps behind the player in DARKENED / CORRIDORS zones
 * 8. Self-activating CRT TV static & temperamental vending machine slaps
 * 9. Fake door hall eerie surprises
 * 10. Manila Room sanctuary (positive Regeneration & sanity recovery only while inside; interactive bed
 *     giving a deliberate choice to sleep and transfer randomly to Level 1 or Level 2)
 * 11. Locked utility room feedback (unlocked via nearby FuseBoxEvents)
 * 12. Wanderer graffiti walls (truthful directions at high sanity, warped lies at low sanity)
 * Plus 3 dynamic environmental anomaly exits (green beckoning figure -> Level 14,
 * flickering green corridor lure -> Level 0.01, flooded deep wade -> Level 0.11)
 * and left-click tear/smash crossing for AnomalyExitBlock forms (1980 Appalachia poster -> Level 161, etc.).
 */
public final class Level0MicroEvents {
	private static final int AMBIENT_COOLDOWN_MIN = 2800; // 2m 20s minimum between ambient scares
	private static final int AMBIENT_COOLDOWN_RANGE = 1800; // up to 3m 50s
	private static final int FOOTSTEP_COOLDOWN_TICKS = 2400; // 2m between delayed footsteps
	private static final int INITIAL_GRACE_TICKS = 1200; // 60s calm opening on entering Level 0

	private static final Map<UUID, DominoWave> ACTIVE_DOMINO = new HashMap<>();
	private static final Map<UUID, RingingPhone> RINGING_PHONES = new HashMap<>();
	private static final Map<UUID, PendingFootstep> DELAYED_STEPS = new HashMap<>();
	private static final Map<UUID, Vec3> PREV_POS = new HashMap<>();
	private static final Map<UUID, Integer> MIRROR_GAZE_TICKS = new HashMap<>();
	private static final Map<UUID, Integer> AMBIENT_COOLDOWN = new HashMap<>();
	private static final Map<UUID, Integer> FOOTSTEP_COOLDOWN = new HashMap<>();
	private static final Map<UUID, DynamicLure> ACTIVE_LURES = new HashMap<>();
	private static final Map<Long, Integer> VENDING_COOLDOWN = new HashMap<>();
	private static final Map<Long, Integer> FAKE_DOOR_COOLDOWN = new HashMap<>();
	private static final Set<UUID> IN_MANILA = new HashSet<>();
	private static final Map<UUID, Integer> MANILA_BED_CONFIRM = new HashMap<>();
	private static final Map<UUID, PendingManilaSleep> PENDING_MANILA_SLEEP = new HashMap<>();

	private Level0MicroEvents() {
	}

	/** Whether a domino blackout wave is currently running for this player (used by Evidence camera). */
	public static boolean isDominoBlackout(ServerPlayer player) {
		return ACTIVE_DOMINO.containsKey(player.getUUID());
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> clearAll());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ServerLevel level0 = server.getLevel(Level0.LEVEL_0);
			if (level0 != null) {
				for (DominoWave wave : ACTIVE_DOMINO.values()) {
					restoreWave(level0, wave);
				}
			}
			clearAll();
		});

		// Left-click tear/smash trigger for posters (Level 161), peeling carpet (Level 27), hollow plaster (Level 194)
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (level.isClientSide() || player.isCreative() || player.isSpectator()) return InteractionResult.PASS;
			if (!(level instanceof ServerLevel serverLevel) || serverLevel.dimension() != Level0.LEVEL_0) {
				return InteractionResult.PASS;
			}
			BlockState state = serverLevel.getBlockState(pos);
			if (state.getBlock() instanceof AnomalyExitBlock exit && player instanceof ServerPlayer sp) {
				if (exit.triggerTearOrSmash(serverLevel, sp, pos, state)) {
					return InteractionResult.SUCCESS;
				}
			}
			return InteractionResult.PASS;
		});

		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (player.isCreative() || level.dimension() != Level0.LEVEL_0) return true;
			if (state.getBlock() instanceof AnomalyExitBlock exit && player instanceof ServerPlayer sp
					&& level instanceof ServerLevel sl) {
				if (exit.triggerTearOrSmash(sl, sp, pos, state)) {
					return false;
				}
			}
			return true;
		});

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || level.isClientSide()) return InteractionResult.PASS;
			if (!(level instanceof ServerLevel sl) || sl.dimension() != Level0.LEVEL_0) return InteractionResult.PASS;
			if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

			BlockPos pos = hit.getBlockPos();
			BlockState state = sl.getBlockState(pos);

			if (state.is(ModBlocks.LOOSE_CEILING_TILE)) {
				return handleLooseCeilingTile(sl, sp, pos, state);
			}
			if (state.is(ModBlocks.HOLLOW_WALL)) {
				return handleHollowWall(sl, sp, pos, state);
			}
			if (state.is(ModBlocks.GRAFFITI_WALL)) {
				return handleGraffitiWall(sl, sp, pos, state);
			}
			if (state.is(ModBlocks.LOCKED_UTILITY_DOOR)
					&& !state.getValue(Level0EventBlocks.LockedUtilityDoorBlock.OPEN)) {
				sl.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.7F, 0.65F);
				sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.utility_locked")
						.withStyle(ChatFormatting.YELLOW));
				return InteractionResult.SUCCESS;
			}
			if (isPhoneBlock(state)) {
				return handlePhoneInteract(sl, sp, pos);
			}
			if (state.is(BackroomsProps.PROP_VENDING_MACHINE) || state.is(BackroomsProps.NOCLIPPED_VENDING_MACHINE)) {
				return handleVendingSlap(sl, sp, pos);
			}
			if (state.is(ModBlocks.MANILA_DOOR) || state.is(ModBlocks.MANILA_DOOR_TOP)) {
				return handleFakeDoorCheck(sl, sp, pos);
			}
			if (state.getBlock() instanceof BedBlock) {
				long seed = Level0ChunkGenerator.mazeSeed(sl.getChunkSource().randomState());
				if (ManilaRoomPlan.roomAtBlock(seed, pos.getX(), pos.getZ()) != null) {
					return handleManilaBedInteract(sl, sp, pos);
				}
			}
			return InteractionResult.PASS;
		});

		ServerTickEvents.END_SERVER_TICK.register(Level0MicroEvents::tick);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("backrooms")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("l0event")
								.then(Commands.literal("domino").executes(ctx -> {
									ServerPlayer p = ctx.getSource().getPlayer();
									if (p == null || p.level().dimension() != Level0.LEVEL_0) return 0;
									boolean started = startDominoBlackout(p.level(), p, p.level().getServer().getTickCount());
									ctx.getSource().sendSuccess(() -> Component.literal("Domino blackout triggered=" + started), false);
									return started ? 1 : 0;
								}))
								.then(Commands.literal("green_figure").executes(ctx -> {
									ServerPlayer p = ctx.getSource().getPlayer();
									if (p == null || p.level().dimension() != Level0.LEVEL_0) return 0;
									spawnDynamicLure(p.level(), p, LureKind.GREEN_FIGURE_14, p.level().getServer().getTickCount());
									ctx.getSource().sendSuccess(() -> Component.literal("Spawned Level 14 green beckoning lure ahead."), false);
									return 1;
								}))
								.then(Commands.literal("green_corridor").executes(ctx -> {
									ServerPlayer p = ctx.getSource().getPlayer();
									if (p == null || p.level().dimension() != Level0.LEVEL_0) return 0;
									spawnDynamicLure(p.level(), p, LureKind.GREEN_CORRIDOR_0P01, p.level().getServer().getTickCount());
									ctx.getSource().sendSuccess(() -> Component.literal("Spawned Level 0.01 flickering green corridor lure ahead."), false);
									return 1;
								})))));
	}

	private static void clearAll() {
		ACTIVE_DOMINO.clear();
		RINGING_PHONES.clear();
		DELAYED_STEPS.clear();
		PREV_POS.clear();
		MIRROR_GAZE_TICKS.clear();
		AMBIENT_COOLDOWN.clear();
		FOOTSTEP_COOLDOWN.clear();
		ACTIVE_LURES.clear();
		VENDING_COOLDOWN.clear();
		FAKE_DOOR_COOLDOWN.clear();
		IN_MANILA.clear();
		MANILA_BED_CONFIRM.clear();
		PENDING_MANILA_SLEEP.clear();
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		ServerLevel level = server.getLevel(Level0.LEVEL_0);
		if (level == null) return;

		tickDominoWaves(level, now);
		tickDelayedSteps(level, now);
		tickPendingManilaSleeps(server, now);

		long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
		RandomSource random = level.getRandom();

		// Strip Manila sanctuary buff immediately if a player left Level 0 altogether
		IN_MANILA.removeIf(uuid -> {
			ServerPlayer p = server.getPlayerList().getPlayer(uuid);
			if (p == null || p.level().dimension() != Level0.LEVEL_0) {
				if (p != null) p.removeEffect(MobEffects.REGENERATION);
				return true;
			}
			return false;
		});

		for (ServerPlayer player : level.players()) {
			UUID id = player.getUUID();
			if (player.isSpectator() || !player.isAlive()) {
				PREV_POS.remove(id);
				MIRROR_GAZE_TICKS.remove(id);
				if (IN_MANILA.remove(id)) {
					player.removeEffect(MobEffects.REGENERATION);
				}
				continue;
			}

			// First time seen in Level 0: give a calm opening grace window so ambient scares don't spam right away
			AMBIENT_COOLDOWN.putIfAbsent(id, now + INITIAL_GRACE_TICKS);

			tickRingingPhone(level, player, now);
			tickDynamicLure(level, player, now);

			if (now % 10 == 0) {
				tickManilaSanctuary(seed, player);
				tickDelayedFootstepDetector(level, seed, player, now, random);
				tickMirrorAndWindowGaze(level, seed, player);
			}

			if (now % 20 == 0 && !player.isCreative()) {
				tickDampCarpetAndDrips(level, seed, player, random);
				tickAmbientMicroEventRolls(level, seed, player, now, random);
			}
		}
	}

	// --- #1 Loose Ceiling Tile ---
	private static InteractionResult handleLooseCeilingTile(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		if (state.getValue(Level0EventBlocks.LooseCeilingTileBlock.SEARCHED)) {
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.ceiling_empty")
					.withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		level.setBlockAndUpdate(pos, state.setValue(Level0EventBlocks.LooseCeilingTileBlock.SEARCHED, true));
		level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() - 0.1, pos.getZ() + 0.5, 10, 0.3, 0.1, 0.3, 0.02);
		RandomSource rng = level.getRandom();
		if (rng.nextInt(4) == 0) {
			level.playSound(null, pos, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 0.9F, 0.65F);
			player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, true, false, true));
			PlayerRpg.drainSanity(player, 2.5F);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.ceiling_collapse")
					.withStyle(ChatFormatting.RED));
		} else {
			level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.7F, 0.85F);
			ItemStack drop = switch (rng.nextInt(4)) {
				case 0 -> new ItemStack(Level1Items.ALMOND_WATER);
				case 1 -> new ItemStack(Level1Items.BANDAGE);
				case 2 -> new ItemStack(BackroomsItems.BATTERY_CELL);
				default -> new ItemStack(Level1Items.PHOSPHOR_POWDER, 2);
			};
			ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() - 0.3, pos.getZ() + 0.5, drop);
			level.addFreshEntity(entity);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.ceiling_found",
					drop.getHoverName()).withStyle(ChatFormatting.GREEN));
		}
		return InteractionResult.SUCCESS;
	}

	// --- #2 Hollow Peeling Wall ---
	private static InteractionResult handleHollowWall(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		if (state.getValue(Level0EventBlocks.HollowWallBlock.OPENED)) {
			level.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.5F, 0.7F);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.wall_already_checked")
					.withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		level.setBlockAndUpdate(pos, state.setValue(Level0EventBlocks.HollowWallBlock.OPENED, true));
		RandomSource rng = level.getRandom();
		if (rng.nextInt(3) == 0) {
			level.playSound(null, pos, ModSounds.WALL_SCRATCH, SoundSource.BLOCKS, 0.85F, 0.8F);
			PlayerRpg.drainSanity(player, 3.0F);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.wall_scratch_back")
					.withStyle(ChatFormatting.RED));
		} else {
			level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.75F, 1.1F);
			ItemStack drop = rng.nextBoolean() ? new ItemStack(Level1Items.ALMOND_WATER) : new ItemStack(BackroomsItems.OLD_CIRCUIT_BOARD);
			level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), drop));
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.wall_cache",
					drop.getHoverName()).withStyle(ChatFormatting.YELLOW));
		}
		return InteractionResult.SUCCESS;
	}

	// --- #12 Graffiti Wall (truth at high sanity, warped at low sanity) ---
	private static InteractionResult handleGraffitiWall(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		int variant = state.getValue(Level0EventBlocks.GraffitiWallBlock.VARIANT);
		PlayerStats stats = PlayerRpg.get(player);
		if (stats.sanityRatio() < 0.40F) {
			PlayerRpg.drainSanity(player, 1.5F);
			level.playSound(null, pos, ModSounds.DISTANT_WHISPER, SoundSource.AMBIENT, 0.4F, 0.72F);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.graffiti_warped")
					.withStyle(ChatFormatting.DARK_RED));
			return InteractionResult.SUCCESS;
		}
		long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
		if (variant == 3) {
			// Pitfalls undercroft escape guidance -> point to the sector's escape ladder shaft
			int sx = Math.floorDiv(Math.floorDiv(pos.getX(), Maze.CELL), Maze.SECTOR);
			int sz = Math.floorDiv(Math.floorDiv(pos.getZ(), Maze.CELL), Maze.SECTOR);
			int shaftX = (sx * Maze.SECTOR + PitfallsPlan.SHAFT_LX) * Maze.CELL + 4;
			int shaftZ = (sz * Maze.SECTOR + PitfallsPlan.SHAFT_LZ) * Maze.CELL + 4;
			String dir = compassDirection(pos.getX(), pos.getZ(), shaftX, shaftZ);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.graffiti_escape", dir)
					.withStyle(ChatFormatting.AQUA));
			return InteractionResult.SUCCESS;
		}
		if (variant == 0) {
			int stx = StageLoop.stageXOfBlock(pos.getX());
			int stz = StageLoop.stageZOfBlock(pos.getZ());
			String code = (char) ('A' + Math.floorMod(stx * 7 + stz * 3, 26)) + "-" + (10 + Math.floorMod(pos.getX() + pos.getZ(), 89));
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.graffiti_code", code)
					.withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		// Point toward this Stage's Level 1 oil vent exit
		int stx = StageLoop.stageXOfBlock(pos.getX());
		int stz = StageLoop.stageZOfBlock(pos.getZ());
		for (AnomalyExitPlan.Placement p : AnomalyExitPlan.placementsInStage(seed, stx, stz)) {
			if (p.kind() == AnomalyExitPlan.EntryKind.OIL_VENT_1) {
				String dir = compassDirection(pos.getX(), pos.getZ(), p.cx() * Maze.CELL + Maze.CENTER, p.cz() * Maze.CELL + Maze.CENTER);
				player.sendOverlayMessage(Component.translatable(
						variant == 1 ? "message.noclip_backrooms.level0.event.graffiti_arrow" : "message.noclip_backrooms.level0.event.graffiti_warn",
						dir).withStyle(ChatFormatting.YELLOW));
				return InteractionResult.SUCCESS;
			}
		}
		player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.graffiti_warn", "?")
				.withStyle(ChatFormatting.YELLOW));
		return InteractionResult.SUCCESS;
	}

	// --- #6 Phantom Phone Ringing ---
	private static InteractionResult handlePhoneInteract(ServerLevel level, ServerPlayer player, BlockPos pos) {
		RingingPhone ringing = RINGING_PHONES.get(player.getUUID());
		if (ringing == null || !ringing.pos().equals(pos)) {
			level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, 0.9F);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.phone_dead")
					.withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		RINGING_PHONES.remove(player.getUUID());
		int roll = level.getRandom().nextInt(3);
		if (roll == 0) {
			long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
			int stx = StageLoop.stageXOfBlock(pos.getX());
			int stz = StageLoop.stageZOfBlock(pos.getZ());
			String dir = "N";
			for (AnomalyExitPlan.Placement p : AnomalyExitPlan.placementsInStage(seed, stx, stz)) {
				if (p.kind() == AnomalyExitPlan.EntryKind.STEAM_GRATE_2 || p.kind() == AnomalyExitPlan.EntryKind.OIL_VENT_1) {
					dir = compassDirection(pos.getX(), pos.getZ(), p.cx() * Maze.CELL + Maze.CENTER, p.cz() * Maze.CELL + Maze.CENTER);
					break;
				}
			}
			level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.45F, 0.7F);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.phone_clue", dir)
					.withStyle(ChatFormatting.AQUA));
		} else if (roll == 1) {
			level.playSound(null, pos, ModSounds.FLUORESCENT_HUM, SoundSource.BLOCKS, 0.95F, 1.85F);
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60, 0, true, false, true));
			PlayerRpg.drainSanity(player, 3.5F);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.phone_static")
					.withStyle(ChatFormatting.RED));
		} else {
			level.playSound(null, pos, ModSounds.DISTANT_WHISPER, SoundSource.BLOCKS, 0.75F, 0.72F);
			PlayerRpg.drainSanity(player, 2.5F);
			ShadowHallucination.triggerNear(level, player);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.phone_behind")
					.withStyle(ChatFormatting.DARK_RED));
		}
		return InteractionResult.SUCCESS;
	}

	// --- #8 Vending Machine Slap ---
	private static InteractionResult handleVendingSlap(ServerLevel level, ServerPlayer player, BlockPos pos) {
		int now = level.getServer().getTickCount();
		if (now < VENDING_COOLDOWN.getOrDefault(pos.asLong(), 0)) {
			level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.4F, 0.7F);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.vending_empty")
					.withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		VENDING_COOLDOWN.put(pos.asLong(), now + 1800);
		RandomSource rng = level.getRandom();
		if (rng.nextInt(100) < 65) {
			level.playSound(null, pos, SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS, 0.85F, 0.9F);
			ItemStack drop = rng.nextBoolean() ? new ItemStack(Level1Items.ALMOND_WATER) : new ItemStack(Level1Items.BANDAGE);
			level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), drop));
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.vending_drop",
					drop.getHoverName()).withStyle(ChatFormatting.GREEN));
		} else {
			level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0F, 0.5F);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
					12, 0.3, 0.3, 0.3, 0.02);
			PlayerRpg.drainSanity(player, 2.5F);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.vending_jam")
					.withStyle(ChatFormatting.RED));
		}
		return InteractionResult.SUCCESS;
	}

	// --- #9 Fake Door Hall ---
	private static InteractionResult handleFakeDoorCheck(ServerLevel level, ServerPlayer player, BlockPos pos) {
		long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
		ZonePoiPlan.Vignette v = ZonePoiPlan.vignetteAtCell(seed, Math.floorDiv(pos.getX(), Maze.CELL), Math.floorDiv(pos.getZ(), Maze.CELL));
		if (v == null || v.type() != ZonePoiPlan.VignetteType.FAKE_DOOR_HALL) return InteractionResult.PASS;
		int now = level.getServer().getTickCount();
		if (now < FAKE_DOOR_COOLDOWN.getOrDefault(pos.asLong(), 0)) return InteractionResult.PASS;
		FAKE_DOOR_COOLDOWN.put(pos.asLong(), now + 600);
		if (level.getRandom().nextInt(4) == 0) {
			level.playSound(null, pos, ModSounds.WALL_SCRATCH, SoundSource.BLOCKS, 0.6F, 0.78F);
			PlayerRpg.drainSanity(player, 1.5F);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.fake_door_scare")
					.withStyle(ChatFormatting.RED));
		} else {
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.fake_door_wall")
					.withStyle(ChatFormatting.GRAY));
		}
		return InteractionResult.PASS;
	}

	// --- #3 Mirror & Peek Chamber Gaze ---
	private static void tickMirrorAndWindowGaze(ServerLevel level, long seed, ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(5.5));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			MIRROR_GAZE_TICKS.remove(player.getUUID());
			return;
		}
		BlockPos hitPos = hit.getBlockPos();
		BlockState state = level.getBlockState(hitPos);
		boolean isGiantMirror = state.is(ModBlocks.GIANT_MIRROR_EXIT);
		boolean isMirrorOrPeek = isGiantMirror
				|| state.is(BackroomsProps.PROP_STANDING_MIRROR)
				|| (state.is(Blocks.GLASS) && isPeekChamberCell(seed, hitPos));
		if (!isMirrorOrPeek) {
			MIRROR_GAZE_TICKS.remove(player.getUUID());
			return;
		}

		int ticks = MIRROR_GAZE_TICKS.merge(player.getUUID(), 10, Integer::sum);
		PlayerStats stats = PlayerRpg.get(player);
		boolean keen = stats.sanityStat() >= 3 || stats.level() >= 4;

		if (isGiantMirror && ticks >= (keen ? 20 : 40)) {
			MIRROR_GAZE_TICKS.remove(player.getUUID());
			level.playSound(null, hitPos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.85F, 0.7F);
			level.sendParticles(ParticleTypes.END_ROD, hitPos.getX() + 0.5, hitPos.getY() + 0.8, hitPos.getZ() + 0.5,
					16, 0.35, 0.6, 0.35, 0.02);
			LevelExits.use(player, "0.5");
			return;
		}

		if (!isGiantMirror && ticks == 20) {
			if (keen && stats.sanityRatio() >= 0.45F) {
				int stx = StageLoop.stageXOfBlock(hitPos.getX());
				int stz = StageLoop.stageZOfBlock(hitPos.getZ());
				String dir = "?";
				for (AnomalyExitPlan.Placement p : AnomalyExitPlan.placementsInStage(seed, stx, stz)) {
					if (p.kind() == AnomalyExitPlan.EntryKind.POSTER_161 || p.kind() == AnomalyExitPlan.EntryKind.OIL_VENT_1) {
						dir = compassDirection(hitPos.getX(), hitPos.getZ(), p.cx() * Maze.CELL + Maze.CENTER, p.cz() * Maze.CELL + Maze.CENTER);
						break;
					}
				}
				player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.mirror_clue", dir)
						.withStyle(ChatFormatting.AQUA));
			} else {
				level.playSound(null, hitPos, ModSounds.DISTANT_WHISPER, SoundSource.AMBIENT, 0.45F, 0.75F);
				player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 35, 0, true, false, true));
				PlayerRpg.drainSanity(player, 2.5F);
				player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.mirror_uncanny")
						.withStyle(ChatFormatting.DARK_RED));
			}
		}
	}

	private static boolean isPeekChamberCell(long seed, BlockPos pos) {
		ZonePoiPlan.Vignette v = ZonePoiPlan.vignetteAtCell(seed, Math.floorDiv(pos.getX(), Maze.CELL), Math.floorDiv(pos.getZ(), Maze.CELL));
		return v != null && v.type() == ZonePoiPlan.VignetteType.PEEK_CHAMBER;
	}

	// --- #10 Manila Room Sanctuary Buff & Interactive Sleep Choice ---
	static boolean isInsideManilaSanctuary(ServerPlayer player) {
		return IN_MANILA.contains(player.getUUID());
	}

	private static void tickManilaSanctuary(long seed, ServerPlayer player) {
		UUID id = player.getUUID();
		ManilaRoomPlan.Room room = ManilaRoomPlan.roomAtBlock(seed, player.getBlockX(), player.getBlockZ());
		boolean inside = room != null && room.isInteriorBlock(player.getBlockX(), player.getBlockZ());
		if (inside) {
			if (IN_MANILA.add(id)) {
				player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.manila.enter")
						.withStyle(ChatFormatting.GOLD));
			}
			// Positive sanctuary effect ONLY while inside Manila Room (short rolling duration, cleared immediately on exit)
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, true, false, true));
			player.removeEffect(MobEffects.DARKNESS);
			player.removeEffect(MobEffects.NAUSEA);
			PlayerRpg.restoreSanity(player, 0.85F);
		} else if (IN_MANILA.remove(id)) {
			player.removeEffect(MobEffects.REGENERATION);
			MANILA_BED_CONFIRM.remove(id);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.manila.leave")
					.withStyle(ChatFormatting.GRAY));
		}
	}

	static InteractionResult handleManilaBedInteract(ServerLevel level, ServerPlayer player, BlockPos pos) {
		UUID id = player.getUUID();
		int now = level.getServer().getTickCount();
		if (PENDING_MANILA_SLEEP.containsKey(id)) {
			return InteractionResult.SUCCESS;
		}
		int confirmUntil = MANILA_BED_CONFIRM.getOrDefault(id, -1);
		if (now <= confirmUntil) {
			MANILA_BED_CONFIRM.remove(id);
			String dest = beginManilaBedSleep(level, player, now, 25);
			if (dest != null) {
				level.playSound(null, pos, ModSounds.MANILA_PIANO, SoundSource.AMBIENT, 0.65F, 0.85F);
			}
		} else {
			MANILA_BED_CONFIRM.put(id, now + 120);
			level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.55F, 0.9F);
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.manila.bed_prompt")
					.withStyle(ChatFormatting.YELLOW));
		}
		return InteractionResult.SUCCESS;
	}

	/** Starts sleeping on the Manila Room bed: brief blackout fadeout followed by random transfer to Level 1 or Level 2. */
	static String beginManilaBedSleep(ServerLevel level, ServerPlayer player, int now, int delayTicks) {
		String target = level.getRandom().nextBoolean() ? "1" : "2";
		if (!LevelExits.isOpen(target)) {
			target = LevelExits.isOpen("1") ? "1" : "2";
		}
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, Math.max(30, delayTicks + 15), 0, true, false, false));
		player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.manila.bed_sleeping")
				.withStyle(ChatFormatting.AQUA));
		PENDING_MANILA_SLEEP.put(player.getUUID(), new PendingManilaSleep(target, now + delayTicks));
		return target;
	}

	private static void tickPendingManilaSleeps(MinecraftServer server, int now) {
		for (Iterator<Map.Entry<UUID, PendingManilaSleep>> it = PENDING_MANILA_SLEEP.entrySet().iterator(); it.hasNext(); ) {
			var entry = it.next();
			PendingManilaSleep pending = entry.getValue();
			if (now < pending.atTick()) continue;
			it.remove();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive() || player.level().dimension() != Level0.LEVEL_0) continue;
			if (IN_MANILA.remove(player.getUUID())) {
				player.removeEffect(MobEffects.REGENERATION);
			}
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.manila.bed_wake", pending.targetLevel())
					.withStyle(ChatFormatting.GOLD));
			LevelExits.use(player, pending.targetLevel());
		}
	}

	// --- #4 Ceiling Drips & Deep Puddle Crossing ---
	private static void tickDampCarpetAndDrips(ServerLevel level, long seed, ServerPlayer player, RandomSource rng) {
		BlockPos feet = player.blockPosition();

		// Ceiling drip particles under DAMAGED_TILES cells
		int cx = Math.floorDiv(feet.getX(), Maze.CELL), cz = Math.floorDiv(feet.getZ(), Maze.CELL);
		if (Maze.ceilingStyle(seed, cx, cz) == Maze.CeilingStyle.DAMAGED_TILES && rng.nextInt(4) == 0) {
			double dx = cx * Maze.CELL + 4 + rng.nextDouble() * 3.0;
			double dz = cz * Maze.CELL + 4 + rng.nextDouble() * 2.0;
			level.sendParticles(player, ParticleTypes.DRIPPING_WATER, false, false,
					dx, Level0ChunkGenerator.CEILING_Y - 0.1, dz, 2, 0.25, 0.0, 0.25, 0.0);
			if (rng.nextInt(4) == 0) {
				level.playSound(null, dx, Level0ChunkGenerator.FLOOR_Y + 1.0, dz,
						SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, SoundSource.AMBIENT, 0.30F, 1.05F);
			}
		}

		// Dynamic Water Damage (Level 0.11) exit when intentionally crouching in DARKENED puddles
		if (Maze.zoneAt(seed, feet.getX(), feet.getZ()) == Maze.Zone.DARKENED
				&& level.getBlockState(feet).is(Blocks.WATER) && player.isShiftKeyDown() && rng.nextInt(8) == 0) {
			player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.exit.water_0p11.clue")
					.withStyle(ChatFormatting.AQUA));
			LevelExits.use(player, "0.11");
		}
	}

	// --- #7 Delayed Phantom Footsteps (Zone-gated to DARKENED & CORRIDORS with 2-min cooldown) ---
	private static void tickDelayedFootstepDetector(ServerLevel level, long seed, ServerPlayer player, int now, RandomSource rng) {
		UUID id = player.getUUID();
		Vec3 cur = player.position();
		Vec3 prev = PREV_POS.put(id, cur);
		if (prev == null || DELAYED_STEPS.containsKey(id)) return;
		if (now < FOOTSTEP_COOLDOWN.getOrDefault(id, 0)) return;
		Maze.Zone zone = Maze.zoneAt(seed, player.getBlockX(), player.getBlockZ());
		if (zone != Maze.Zone.DARKENED && zone != Maze.Zone.CORRIDORS) return;
		double dist = cur.distanceTo(prev);
		if (dist < 0.05 && player.onGround() && rng.nextInt(42) == 0) {
			BlockState below = level.getBlockState(player.blockPosition().below());
			if (below.is(ModBlocks.CARPET) || below.is(ModBlocks.THICK_CARPET)) {
				FOOTSTEP_COOLDOWN.put(id, now + FOOTSTEP_COOLDOWN_TICKS + rng.nextInt(1200));
				Vec3 back = player.getLookAngle().multiply(-1, 0, -1);
				double len = Math.hypot(back.x, back.z);
				double bx = player.getX() + (len > 1e-4 ? back.x / len * 2.6 : 0);
				double bz = player.getZ() + (len > 1e-4 ? back.z / len * 2.6 : 2.6);
				DELAYED_STEPS.put(id, new PendingFootstep(bx, player.getY(), bz, now + 12, 2));
			}
		}
	}

	private static void tickDelayedSteps(ServerLevel level, int now) {
		for (Iterator<Map.Entry<UUID, PendingFootstep>> it = DELAYED_STEPS.entrySet().iterator(); it.hasNext(); ) {
			var entry = it.next();
			PendingFootstep step = entry.getValue();
			if (now < step.atTick()) continue;
			level.playSound(null, step.x(), step.y(), step.z(),
					SoundEvents.WOOL_STEP, SoundSource.AMBIENT, 0.55F, 0.72F);
			if (step.remaining() > 1) {
				entry.setValue(new PendingFootstep(step.x(), step.y(), step.z(), now + 9, step.remaining() - 1));
			} else {
				it.remove();
			}
		}
	}

	// --- #5 Domino Corridor Blackout ---
	static boolean startDominoBlackout(ServerLevel level, ServerPlayer player, int now) {
		UUID id = player.getUUID();
		if (ACTIVE_DOMINO.containsKey(id)) return false;
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle().multiply(1, 0, 1);
		double len = look.length();
		Vec3 fwd = len > 1e-4 ? look.scale(1.0 / len) : new Vec3(0, 0, 1);

		List<BlockPos> lamps = new ArrayList<>();
		BlockPos origin = player.blockPosition();
		for (int dx = -16; dx <= 16; dx++) {
			for (int dz = -16; dz <= 16; dz++) {
				BlockPos p = new BlockPos(origin.getX() + dx, Level0ChunkGenerator.CEILING_Y, origin.getZ() + dz);
				if (!level.isLoaded(p)) continue;
				if (!level.getBlockState(p).is(ModBlocks.FLUORESCENT_LIGHT)) continue;
				Vec3 to = Vec3.atCenterOf(p).subtract(eye);
				if (to.x * fwd.x + to.z * fwd.z >= 1.5) {
					lamps.add(p);
				}
			}
		}
		if (lamps.isEmpty()) return false;
		// Sort from far to near so darkness marches toward the player!
		lamps.sort((a, b) -> Double.compare(b.distSqr(origin), a.distSqr(origin)));
		if (lamps.size() > 14) lamps = new ArrayList<>(lamps.subList(0, 14));

		ACTIVE_DOMINO.put(id, new DominoWave(lamps, 0, now, now + 150, new ArrayList<>()));
		player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.domino_blackout")
				.withStyle(ChatFormatting.GRAY));
		return true;
	}

	private static void tickDominoWaves(ServerLevel level, int now) {
		for (Iterator<Map.Entry<UUID, DominoWave>> it = ACTIVE_DOMINO.entrySet().iterator(); it.hasNext(); ) {
			var entry = it.next();
			DominoWave wave = entry.getValue();
			if (now >= wave.restoreAtTick()) {
				restoreWave(level, wave);
				it.remove();
				continue;
			}
			if (wave.nextIdx() < wave.lamps().size() && now >= wave.nextStepTick()) {
				BlockPos p = wave.lamps().get(wave.nextIdx());
				if (level.isLoaded(p) && level.getBlockState(p).is(ModBlocks.FLUORESCENT_LIGHT)) {
					level.setBlock(p, ModBlocks.BROKEN_FLUORESCENT_LIGHT.defaultBlockState(), Block.UPDATE_ALL);
					wave.turnedOff().add(p);
					level.playSound(null, p, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.7F, 0.55F);
				}
				entry.setValue(new DominoWave(wave.lamps(), wave.nextIdx() + 1, now + 4, wave.restoreAtTick(), wave.turnedOff()));
			}
		}
	}

	private static void restoreWave(ServerLevel level, DominoWave wave) {
		for (BlockPos p : wave.turnedOff()) {
			if (level.isLoaded(p) && level.getBlockState(p).is(ModBlocks.BROKEN_FLUORESCENT_LIGHT)) {
				level.setBlock(p, ModBlocks.FLUORESCENT_LIGHT.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
	}

	// --- Zone/POI-Paced Ambient Micro-Event Director (Phone Ring, CRT Static, Domino Blackout, Green Lures) ---
	private static void tickAmbientMicroEventRolls(ServerLevel level, long seed, ServerPlayer player, int now, RandomSource rng) {
		UUID id = player.getUUID();
		if (now < AMBIENT_COOLDOWN.getOrDefault(id, 0)) return;
		BlockPos origin = player.blockPosition();
		Maze.Zone zone = Maze.zoneAt(seed, origin.getX(), origin.getZ());
		if (zone == Maze.Zone.RED_ROOM || ManilaRoomPlan.nearbyRoom(seed, origin.getX(), origin.getZ(), 18) != null) {
			return;
		}
		int cx = Math.floorDiv(origin.getX(), Maze.CELL);
		int cz = Math.floorDiv(origin.getZ(), Maze.CELL);
		boolean nearPoi = ZonePoiPlan.isStandardPoiCell(seed, cx, cz)
				|| ZonePoiPlan.vignetteAtCell(seed, cx, cz) != null
				|| ZonePoiPlan.isDarkenedPoiCell(seed, cx, cz);

		// POI-bound phone ring or CRT TV static when exploring office/darkened/wanderer POIs
		if (nearPoi && !RINGING_PHONES.containsKey(id) && rng.nextInt(14) == 0) {
			for (BlockPos p : BlockPos.betweenClosed(origin.offset(-10, -1, -10), origin.offset(10, 2, 10))) {
				BlockState st = level.getBlockState(p);
				if (isPhoneBlock(st)) {
					RINGING_PHONES.put(id, new RingingPhone(p.immutable(), now + 400));
					AMBIENT_COOLDOWN.put(id, now + AMBIENT_COOLDOWN_MIN + rng.nextInt(AMBIENT_COOLDOWN_RANGE));
					player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.phone_ringing")
							.withStyle(ChatFormatting.YELLOW));
					return;
				}
				if ((st.is(BackroomsProps.PROP_CRT_TV) || st.is(BackroomsProps.NOCLIPPED_CRT_TV)) && rng.nextBoolean()) {
					level.playSound(null, p, ModSounds.FLUORESCENT_HUM, SoundSource.BLOCKS, 0.65F, 1.55F);
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5, p.getY() + 0.7, p.getZ() + 0.5,
							10, 0.25, 0.25, 0.25, 0.04);
					AMBIENT_COOLDOWN.put(id, now + AMBIENT_COOLDOWN_MIN + rng.nextInt(AMBIENT_COOLDOWN_RANGE));
					player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level0.event.crt_static")
							.withStyle(ChatFormatting.AQUA));
					return;
				}
			}
		}

		long distSqFromOrigin = (long) origin.getX() * origin.getX() + (long) origin.getZ() * origin.getZ();

		// Rare domino blackout wave restricted to CORRIDORS / deep CLASSIC sectors away from spawn
		if ((zone == Maze.Zone.CORRIDORS || (zone == Maze.Zone.CLASSIC && distSqFromOrigin > 3600))
				&& !ACTIVE_DOMINO.containsKey(id) && rng.nextInt(260) == 0) {
			if (startDominoBlackout(level, player, now)) {
				AMBIENT_COOLDOWN.put(id, now + AMBIENT_COOLDOWN_MIN + rng.nextInt(AMBIENT_COOLDOWN_RANGE));
				return;
			}
		}

		// Rare dynamic non-block anomalous exit lures restricted to deep CORRIDORS / PILLARS / ARCHES
		if ((zone == Maze.Zone.CORRIDORS || zone == Maze.Zone.PILLARS || zone == Maze.Zone.ARCHES)
				&& distSqFromOrigin > 6400 && !ACTIVE_LURES.containsKey(id) && rng.nextInt(320) == 0) {
			LureKind kind = (zone == Maze.Zone.CORRIDORS && rng.nextBoolean())
					? LureKind.GREEN_CORRIDOR_0P01
					: LureKind.GREEN_FIGURE_14;
			spawnDynamicLure(level, player, kind, now);
			AMBIENT_COOLDOWN.put(id, now + AMBIENT_COOLDOWN_MIN + rng.nextInt(AMBIENT_COOLDOWN_RANGE));
		}
	}

	private static void tickRingingPhone(ServerLevel level, ServerPlayer player, int now) {
		RingingPhone phone = RINGING_PHONES.get(player.getUUID());
		if (phone == null) return;
		if (now >= phone.expiresAtTick() || !phone.pos().closerThan(player.blockPosition(), 20)) {
			RINGING_PHONES.remove(player.getUUID());
			return;
		}
		if (now % 24 == 0) {
			level.playSound(null, phone.pos(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.6F, 1.65F);
			level.sendParticles(ParticleTypes.NOTE, phone.pos().getX() + 0.5, phone.pos().getY() + 0.9,
					phone.pos().getZ() + 0.5, 2, 0.15, 0.15, 0.15, 0.01);
		}
	}

	private static void spawnDynamicLure(ServerLevel level, ServerPlayer player, LureKind kind, int now) {
		Vec3 look = player.getLookAngle().multiply(1, 0, 1);
		double len = look.length();
		Vec3 fwd = len > 1e-4 ? look.scale(1.0 / len) : new Vec3(0, 0, 1);
		long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
		for (int dist = 10; dist <= 16; dist += 2) {
			int bx = (int) Math.floor(player.getX() + fwd.x * dist);
			int bz = (int) Math.floor(player.getZ() + fwd.z * dist);
			if (Maze.isWall(seed, bx, bz, false) || Maze.isPit(seed, bx, bz)) continue;
			BlockPos pos = new BlockPos(bx, player.getBlockY(), bz);
			ACTIVE_LURES.put(player.getUUID(), new DynamicLure(pos, kind, now + 320));
			player.sendOverlayMessage(Component.translatable(kind.cueKey).withStyle(ChatFormatting.GREEN));
			level.playSound(null, pos, ModSounds.DISTANT_WHISPER, SoundSource.AMBIENT, 0.45F, 1.15F);
			return;
		}
	}

	private static void tickDynamicLure(ServerLevel level, ServerPlayer player, int now) {
		DynamicLure lure = ACTIVE_LURES.get(player.getUUID());
		if (lure == null) return;
		if (now >= lure.expiresAtTick() || !lure.pos().closerThan(player.blockPosition(), 28)) {
			ACTIVE_LURES.remove(player.getUUID());
			return;
		}
		if (now % 5 == 0) {
			double x = lure.pos().getX() + 0.5;
			double y = lure.pos().getY() + 0.9;
			double z = lure.pos().getZ() + 0.5;
			level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, false, false, x, y, z, 3, 0.25, 0.65, 0.25, 0.01);
		}
		if (player.blockPosition().closerThan(lure.pos(), 2.8)) {
			ACTIVE_LURES.remove(player.getUUID());
			level.playSound(null, lure.pos(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.AMBIENT, 0.7F, 0.8F);
			LevelExits.use(player, lure.kind().targetKey);
		}
	}

	private static boolean isPhoneBlock(BlockState state) {
		return state.is(BackroomsProps.PROP_PHONE)
				|| state.is(BackroomsProps.PROP_PHONE_CRACKED)
				|| state.is(BackroomsProps.PROP_PHONE_NOCLIPPED);
	}

	private static String compassDirection(int fromX, int fromZ, int toX, int toZ) {
		int dx = toX - fromX, dz = toZ - fromZ;
		if (Math.abs(dx) < 8 && Math.abs(dz) < 8) return "NEAR";
		String ns = dz < -6 ? "N" : (dz > 6 ? "S" : "");
		String ew = dx > 6 ? "E" : (dx < -6 ? "W" : "");
		return (ns + ew).isEmpty() ? "N" : (ns + ew);
	}

	private enum LureKind {
		GREEN_FIGURE_14("14", "message.noclip_backrooms.level0.exit.green_figure_14.clue"),
		GREEN_CORRIDOR_0P01("0.01", "message.noclip_backrooms.level0.exit.corridor_0p01.clue");

		final String targetKey;
		final String cueKey;

		LureKind(String targetKey, String cueKey) {
			this.targetKey = targetKey;
			this.cueKey = cueKey;
		}
	}

	private record DominoWave(List<BlockPos> lamps, int nextIdx, int nextStepTick, int restoreAtTick, List<BlockPos> turnedOff) {
	}

	private record RingingPhone(BlockPos pos, int expiresAtTick) {
	}

	private record PendingFootstep(double x, double y, double z, int atTick, int remaining) {
	}

	private record DynamicLure(BlockPos pos, LureKind kind, int expiresAtTick) {
	}

	private record PendingManilaSleep(String targetLevel, int atTick) {
	}
}
