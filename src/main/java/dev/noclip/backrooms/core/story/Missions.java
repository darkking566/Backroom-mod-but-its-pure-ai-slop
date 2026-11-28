package dev.noclip.backrooms.core.story;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.LevelExits;
import dev.noclip.backrooms.core.LevelGates;
import dev.noclip.backrooms.core.economy.Wallet;
import dev.noclip.backrooms.core.field.FieldKit;
import dev.noclip.backrooms.core.floors.Floors;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.quota.Quota;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.core.story.MissionBoardPayload.BoardEntry;
import dev.noclip.backrooms.core.story.MissionBoardPayload.BountyEntry;
import dev.noclip.backrooms.core.story.MissionSyncPayload.ObjLine;
import dev.noclip.backrooms.core.trade.ShopRole;
import dev.noclip.backrooms.level0.Level0;
import dev.noclip.backrooms.level0.Level0MissionHelper;
import dev.noclip.backrooms.level1.Level1;
import dev.noclip.backrooms.level2.Level2;
import dev.noclip.backrooms.level2.Level2Blocks;
import dev.noclip.backrooms.level3.Level3;
import dev.noclip.backrooms.level3.Level3Blocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;

/**
 * M.E.G. Mission & Bounty Board system:
 * <ul>
 *   <li>Replaces automatic 5-day quota cycles with Commander-issued exploration contracts (photo, repair, recovery)
 *       and an optional Combat / Boss Bounty tab.</li>
 *   <li>Guides newcomers through the Institute via floor END_ROD breadcrumbs and a glowing Commander outline.</li>
 *   <li>Spawns a glowing {@link StoryBlocks#GUIDE_DOOR} evacuation door with a particle pillar upon finishing
 *       exploration objectives inside a Backrooms level.</li>
 * </ul>
 */
public final class Missions {
	public enum ObjType {
		PHOTO, INTERACT, COLLECT
	}

	public record ObjectiveSpec(String kind, String target, ObjType type, int goal, long rewardBonus, boolean needsCamera, boolean needsCharcoal) {
		public String labelKey() {
			return "mission.noclip_backrooms.obj." + kind;
		}
	}

	public record BountyDef(String id, String level, String target, int goal, long reward, boolean boss, int reqCompleted) {
		public String titleKey() {
			return "mission.noclip_backrooms.bounty." + id;
		}

		public String descKey() {
			return "mission.noclip_backrooms.bounty." + id + ".desc";
		}
	}

	private static final Map<String, List<ObjectiveSpec>> EXPLORATION_POOLS = Map.of(
			"0", List.of(
					new ObjectiveSpec("photo_red_room", "red_room_altar", ObjType.PHOTO, 1, 75, true, false),
					new ObjectiveSpec("photo_pitfalls", "pitfalls", ObjType.PHOTO, 1, 65, true, false),
					new ObjectiveSpec("photo_manila", "manila_room", ObjType.PHOTO, 1, 65, true, false),
					new ObjectiveSpec("photo_chasm", "chasm_rift", ObjType.PHOTO, 1, 75, true, false),
					new ObjectiveSpec("photo_flicker", "flickering_light", ObjType.PHOTO, 1, 50, true, false),
					new ObjectiveSpec("photo_entity_l0", "entity_any", ObjType.PHOTO, 1, 80, true, false),
					new ObjectiveSpec("repair_fuse_l0", "fuse_box", ObjType.INTERACT, 1, 60, false, false),
					new ObjectiveSpec("collect_tape_l0", "tape_l0", ObjType.COLLECT, 1, 70, false, false)
			),
			"1", List.of(
					new ObjectiveSpec("repair_gen_l1", "generator_l1", ObjType.INTERACT, 1, 90, false, false),
					new ObjectiveSpec("scavenge_vehicle_l1", "vehicle_l1", ObjType.INTERACT, 2, 100, false, false),
					new ObjectiveSpec("photo_eyed_crate", "eyed_crate", ObjType.PHOTO, 1, 90, true, false),
					new ObjectiveSpec("photo_entity_l1", "entity_any", ObjType.PHOTO, 1, 95, true, false),
					new ObjectiveSpec("collect_almond_l1", "almond_l1", ObjType.COLLECT, 2, 85, false, false)
			),
			"2", List.of(
					new ObjectiveSpec("inspect_valves_l2", "valve_l2", ObjType.INTERACT, 3, 130, false, false),
					new ObjectiveSpec("reset_breakers_l2", "breaker_l2", ObjType.INTERACT, 2, 120, false, false),
					new ObjectiveSpec("photo_hot_zone", "hot_zone", ObjType.PHOTO, 1, 110, true, false),
					new ObjectiveSpec("photo_entity_l2", "entity_any", ObjType.PHOTO, 1, 125, true, false)
			),
			"3", List.of(
					new ObjectiveSpec("restart_gen_l3", "generator_l3", ObjType.INTERACT, 2, 180, false, false),
					new ObjectiveSpec("flip_switchgear_l3", "switchgear_l3", ObjType.INTERACT, 3, 160, false, false),
					new ObjectiveSpec("rub_pedestal_l3", "pedestal_l3", ObjType.INTERACT, 1, 150, false, false),
					new ObjectiveSpec("photo_electrical_l3", "electrical_hazard", ObjType.PHOTO, 1, 150, true, false)
			),
			"4", List.of(
					new ObjectiveSpec("seal_windows_l4", "seal_window_l4", ObjType.INTERACT, 2, 220, false, true),
					new ObjectiveSpec("photo_trap_window", "trap_window", ObjType.PHOTO, 1, 200, true, false),
					new ObjectiveSpec("photo_entity_l4", "entity_any", ObjType.PHOTO, 1, 210, true, false),
					new ObjectiveSpec("collect_almond_l4", "almond_l1", ObjType.COLLECT, 3, 180, false, false)
			)
	);

	public static final List<BountyDef> BOUNTIES = List.of(
			new BountyDef("bounty_hounds", "1", "hound", 3, 320, false, 1),
			new BountyDef("bounty_smilers", "1", "smiler", 2, 380, false, 1),
			new BountyDef("bounty_skin_stealers", "2", "skin_stealer", 2, 480, false, 2),
			new BountyDef("bounty_clump_moth", "2", "clump_or_moth", 3, 520, false, 2),
			new BountyDef("bounty_bacteria_boss", "2", "bacteria", 1, 850, true, 3),
			new BountyDef("bounty_deep_boss", "3", "boss_any", 1, 1100, true, 3)
	);

	private static final Map<String, Long> BASE_REWARD = Map.of(
			"0", 90L, "1", 150L, "2", 240L, "3", 360L, "4", 480L
	);

	private static final Set<String> L0_TAPE_PATHS = Set.of("unlabeled_tape", "static_cassette", "manila_note", "field_note");
	private static final double COMPASS_NEAR_RADIUS = 20.0;

	private static final class TrailCache {
		List<BlockPos> path = List.of();
		int computedTick;
	}

	private static final Map<UUID, TrailCache> TRAIL_CACHE = new HashMap<>();
	private static final Map<BlockPos, Integer> PIPE_COOLDOWN = new HashMap<>();

	private Missions() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(MissionSyncPayload.TYPE, MissionSyncPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MissionBoardPayload.TYPE, MissionBoardPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MissionActionPayload.TYPE, MissionActionPayload.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(MissionActionPayload.TYPE, (payload, ctx) ->
				ctx.server().execute(() -> handleAction(ctx.player(), payload)));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				syncPlayer(handler.player, data(server)));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			TRAIL_CACHE.clear();
			PIPE_COOLDOWN.clear();
		});

		ServerTickEvents.END_SERVER_TICK.register(Missions::tick);

		// Track Level 2 & Level 3 mission interactable machinery cleanly without touching chunk gen.
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			if (!(world instanceof ServerLevel level)) return InteractionResult.PASS;
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			int now = level.getServer().getTickCount();

			if (level.dimension() == Level2.LEVEL_2) {
				if (state.is(Level2Blocks.VALVE_PIPES) || state.is(Level2Blocks.STEAM_PIPES)) {
					if (notCoolingDown(pos, now)) {
						PIPE_COOLDOWN.put(pos.immutable(), now + 20 * 30);
						level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.7F, 1.35F);
						level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 10, 0.3, 0.2, 0.3, 0.03);
						onBlockInteract(sp, "valve_l2", pos, "2");
						return InteractionResult.SUCCESS;
					}
				} else if (state.is(Level2Blocks.BREAKER_BOX)) {
					if (notCoolingDown(pos, now)) {
						PIPE_COOLDOWN.put(pos.immutable(), now + 20 * 30);
						level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.85F, 1.15F);
						level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 12, 0.25, 0.25, 0.25, 0.06);
						onBlockInteract(sp, "breaker_l2", pos, "2");
						return InteractionResult.SUCCESS;
					}
				}
			} else if (level.dimension() == Level3.LEVEL_3) {
				if (state.is(Level3Blocks.GENERATOR)) {
					if (notCoolingDown(pos, now)) {
						PIPE_COOLDOWN.put(pos.immutable(), now + 20 * 30);
						level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6F, 1.2F);
						level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 14, 0.3, 0.3, 0.3, 0.06);
						onBlockInteract(sp, "generator_l3", pos, "3");
						return InteractionResult.SUCCESS;
					}
				} else if (state.is(Level3Blocks.SWITCHGEAR) || state.is(Level3Blocks.BOILER_VALVE)) {
					if (notCoolingDown(pos, now)) {
						PIPE_COOLDOWN.put(pos.immutable(), now + 20 * 30);
						level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8F, 0.95F);
						level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.02);
						onBlockInteract(sp, "switchgear_l3", pos, "3");
						return InteractionResult.SUCCESS;
					}
				}
			}
			return InteractionResult.PASS;
		});

		// Track active Bounty monster & Boss kills.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof Monster monster) || !(entity.level() instanceof ServerLevel sl)) return;
			MinecraftServer server = sl.getServer();
			MissionData d = data(server);
			if (d.bounty == null || d.bounty.ready()) return;

			Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(monster.getType());
			String path = id.getPath();
			boolean isBoss = monster.entityTags().contains(LevelGates.BOSS_ENTITY_TAG)
					|| (BackroomsMod.MOD_ID.equals(id.getNamespace()) && monster.getMaxHealth() >= 80.0F);

			String tgt = d.bounty.target;
			boolean match = switch (tgt) {
				case "boss_any" -> isBoss;
				case "clump_or_moth" -> path.equals("clump") || path.equals("deathmoth");
				case "bacteria" -> path.equals("bacteria") || isBoss;
				default -> path.equals(tgt);
			};
			if (!match) return;

			d.bounty.progress = Math.min(d.bounty.goal, d.bounty.progress + 1);
			d.changed();
			if (d.bounty.ready()) {
				broadcastTeam(server, Component.translatable("mission.noclip_backrooms.bounty_ready",
						Component.translatable("mission.noclip_backrooms.bounty." + d.bounty.id), d.bounty.reward)
						.withStyle(ChatFormatting.GOLD));
				Quota.syncMissionClearance(server, d.completedCount);
			}
			syncAll(server, d);
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> {
			dispatcher.register(Commands.literal("meg").then(Commands.literal("mission")
					.executes(ctx -> {
						ServerPlayer p = ctx.getSource().getPlayerOrException();
						if (Home.isHome(p.level().dimension()) && nearCommander(p) != null) {
							interactCommander(p);
						} else {
							showTextStatus(p);
						}
						return 1;
					})));

			dispatcher.register(Commands.literal("backrooms").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.literal("mission")
							.then(Commands.literal("complete").executes(ctx -> {
								ServerPlayer p = ctx.getSource().getPlayerOrException();
								completeActiveForTest(p);
								return 1;
							}))
							.then(Commands.literal("reset").executes(ctx -> {
								MinecraftServer s = ctx.getSource().getServer();
								MissionData d = data(s);
								d.briefed = false;
								d.completedCount = 0;
								d.rerollSeed = 0;
								d.unlockedLevels.clear();
								d.unlockedLevels.add("0");
								d.exploration = null;
								d.bounty = null;
								d.returnDoorDim = "";
								d.hasReturnDoor = false;
								d.usedBlocks.clear();
								d.changed();
								syncAll(s, d);
								return 1;
							}))
							.then(Commands.literal("accept").then(Commands.argument("level", StringArgumentType.word())
									.executes(ctx -> {
										acceptExploration(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "level"));
										return 1;
									})))));
		});
	}

	private static boolean notCoolingDown(BlockPos pos, int now) {
		Integer until = PIPE_COOLDOWN.get(pos);
		return until == null || now >= until;
	}

	// ---- Pure testable math & selection helpers ----

	/** Samples 2 to 3 distinct randomized objectives for {@code level} deterministically from {@code seed}. */
	public static List<ObjectiveSpec> sampleObjectives(String level, long seed) {
		List<ObjectiveSpec> pool = EXPLORATION_POOLS.getOrDefault(level, EXPLORATION_POOLS.get("0"));
		List<ObjectiveSpec> copy = new ArrayList<>(pool);
		Collections.shuffle(copy, new Random(seed ^ (level.hashCode() * 0x9E3779B97F4A7C15L)));
		int count = Math.min(copy.size(), (Math.floorMod(seed, 3) == 0 && copy.size() >= 3) ? 3 : 2);
		return List.copyOf(copy.subList(0, count));
	}

	/** Formats pure horizontal compass bearing and distance relative to player yaw; returns "NEAR" inside 20m. */
	public static String formatCompass(double px, double pz, float yawDeg, int tx, int tz) {
		double dx = (tx + 0.5) - px;
		double dz = (tz + 0.5) - pz;
		double dist = Math.hypot(dx, dz);
		if (dist <= COMPASS_NEAR_RADIUS) return "NEAR";
		// Yaw 0 = +Z south, 90 = -X west, 180 = -Z north, 270 = +X east.
		double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
		double rel = ((targetYaw - yawDeg) % 360.0 + 540.0) % 360.0 - 180.0;
		String arrow;
		if (rel >= -22.5 && rel < 22.5) arrow = "↑";
		else if (rel >= 22.5 && rel < 67.5) arrow = "↗";
		else if (rel >= 67.5 && rel < 112.5) arrow = "→";
		else if (rel >= 112.5 && rel < 157.5) arrow = "↘";
		else if (rel >= 157.5 || rel < -157.5) arrow = "↓";
		else if (rel >= -157.5 && rel < -112.5) arrow = "↙";
		else if (rel >= -112.5 && rel < -67.5) arrow = "←";
		else arrow = "↖";
		return arrow + " ~" + Math.round(dist) + "m";
	}

	/** Returns whether a bounty is unlocked given team completed missions and unlocked levels. */
	public static boolean isBountyUnlocked(String bountyId, int completedCount, Collection<String> unlockedLevels) {
		for (BountyDef b : BOUNTIES) {
			if (!b.id.equals(bountyId)) continue;
			if (completedCount >= b.reqCompleted) return true;
			if (b.boss && (unlockedLevels.contains("3") || unlockedLevels.contains("4"))) return true;
			return !b.boss && b.reqCompleted <= 1 && unlockedLevels.contains("1");
		}
		return false;
	}

	// ---- Public API & hooks ----

	public static boolean isBriefed(MinecraftServer server) {
		return data(server).briefed;
	}

	public static int completedCount(MinecraftServer server) {
		return data(server).completedCount;
	}

	public static Set<String> unlockedLevels(MinecraftServer server) {
		return Collections.unmodifiableSet(data(server).unlockedLevels);
	}

	public static void unlockLevel(MinecraftServer server, String level) {
		if (level == null || level.isEmpty()) return;
		MissionData d = data(server);
		if (d.unlockedLevels.add(level)) {
			d.changed();
			syncAll(server, d);
		}
	}

	/** Called when a player returns to the Frontrooms / Institute; guides them without forcing a popup task. */
	public static void onPlayerHome(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		MissionData d = data(server);
		if (!d.briefed) {
			player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
			player.connection.send(new ClientboundSetTitleTextPacket(
					Component.translatable("mission.noclip_backrooms.arrival_title").withStyle(ChatFormatting.AQUA)));
			player.connection.send(new ClientboundSetSubtitleTextPacket(
					Component.translatable("mission.noclip_backrooms.arrival_subtitle").withStyle(ChatFormatting.YELLOW)));
			player.sendSystemMessage(Component.translatable("mission.noclip_backrooms.arrival_chat").withStyle(ChatFormatting.AQUA));
		} else if (d.isAnyMissionReady()) {
			player.sendSystemMessage(Component.translatable("mission.noclip_backrooms.turnin_hint").withStyle(ChatFormatting.GREEN));
		}
		syncPlayer(player, d);
	}

	/** Called when right-clicking the Commander NPC in the Institute. */
	public static void interactCommander(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		MissionData d = data(server);
		boolean first = !d.briefed;
		if (first) {
			d.briefed = true;
			d.changed();
			for (int i = 1; i <= 3; i++) {
				player.sendSystemMessage(Component.translatable("mission.noclip_backrooms.commander.story." + i)
						.withStyle(i == 1 ? ChatFormatting.AQUA : ChatFormatting.WHITE));
			}
			player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.8F, 1.0F);
		}
		syncAll(server, d);
		openBoard(player, d, first);
	}

	public static void onPhoto(ServerPlayer player, String subjectId, String levelKey) {
		MinecraftServer server = player.level().getServer();
		MissionData d = data(server);
		if (d.exploration == null || !d.exploration.level.equals(levelKey)) return;

		boolean changed = false;
		for (ActiveObjective obj : d.exploration.objectives) {
			if (obj.type != ObjType.PHOTO || obj.done()) continue;
			boolean match = "entity_any".equals(obj.target)
					? (!"scene".equals(subjectId) && !"blackout_moment".equals(subjectId) && !"red_room_altar".equals(subjectId)
					&& !"manila_room".equals(subjectId) && !"pitfalls".equals(subjectId) && !"chasm_rift".equals(subjectId)
					&& !"flickering_light".equals(subjectId) && !"eyed_crate".equals(subjectId) && !"hot_zone".equals(subjectId)
					&& !"trap_window".equals(subjectId) && !"anomaly_vault".equals(subjectId) && !"electrical_hazard".equals(subjectId))
					: obj.target.equals(subjectId);
			if (match) {
				obj.progress = Math.min(obj.goal, obj.progress + 1);
				changed = true;
			}
		}
		if (changed) {
			onExplorationProgress(server, player, d);
		}
	}

	public static void onBlockInteract(ServerPlayer player, String targetKind, BlockPos pos, String levelKey) {
		if (player == null || pos == null) return;
		MinecraftServer server = player.level().getServer();
		MissionData d = data(server);
		if (d.exploration == null || !d.exploration.level.equals(levelKey)) return;
		long packed = pos.asLong();
		if (d.usedBlocks.contains(packed)) return;

		boolean changed = false;
		for (ActiveObjective obj : d.exploration.objectives) {
			if (obj.type == ObjType.INTERACT && !obj.done() && obj.target.equals(targetKind)) {
				obj.progress = Math.min(obj.goal, obj.progress + 1);
				changed = true;
			}
		}
		if (changed) {
			d.usedBlocks.add(packed);
			onExplorationProgress(server, player, d);
		}
	}

	// ---- Server Tick: Commander glow, Institute trail, Collect check, Return Door Beam, Hint sync ----

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		MissionData d = data(server);

		if (now % 6 == 0) {
			tickHomeGuidance(server, d, now);
			tickReturnDoorBeam(server, d);
		}

		if (now % 20 == 0) {
			boolean progChanged = checkCollections(server, d);
			if (d.exploration != null && d.exploration.ready() && !d.hasReturnDoor) {
				for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
					if (Home.inBackrooms(sp.level().dimension()) && matchesLevel(sp.level(), d.exploration.level)) {
						spawnReturnDoor(sp, d);
						progChanged = true;
						break;
					}
				}
			}
			if (progChanged || !server.getPlayerList().getPlayers().isEmpty()) {
				syncAll(server, d);
			}
		}
	}

	private static void tickHomeGuidance(MinecraftServer server, MissionData d, int now) {
		ServerLevel home = server.getLevel(Home.FRONTROOMS);
		if (home == null || home.players().isEmpty()) return;

		boolean highlightCommander = !d.briefed || d.isAnyMissionReady();
		for (ServerPlayer player : home.players()) {
			Person commander = nearCommander(player, 80.0);
			if (commander == null) continue;
			if (highlightCommander) {
				commander.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, true, false, false));
				if (!player.isSpectator() && player.distanceToSqr(commander) > 16.0) {
					emitTrailParticles(home, player, commander, now);
				}
			} else if (commander.hasEffect(MobEffects.GLOWING)) {
				commander.removeEffect(MobEffects.GLOWING);
			}
		}
	}

	private static void emitTrailParticles(ServerLevel home, ServerPlayer player, Person commander, int now) {
		TrailCache cache = TRAIL_CACHE.computeIfAbsent(player.getUUID(), id -> new TrailCache());
		if (now - cache.computedTick >= 15 || cache.path.isEmpty()) {
			cache.path = bfsPath(home, player.blockPosition(), commander.blockPosition());
			cache.computedTick = now;
		}
		if (cache.path.isEmpty()) return;
		double y = commander.getY() + 0.2;
		int maxStep = Math.min(cache.path.size(), 16);
		for (int i = 1; i < maxStep; i += 2) {
			BlockPos step = cache.path.get(i);
			home.sendParticles(player, ParticleTypes.END_ROD, true, false,
					step.getX() + 0.5, y, step.getZ() + 0.5, 1, 0.05, 0.02, 0.05, 0.002);
		}
	}

	private static List<BlockPos> bfsPath(ServerLevel level, BlockPos start, BlockPos goal) {
		int y = goal.getY();
		BlockPos s = new BlockPos(start.getX(), y, start.getZ());
		BlockPos g = new BlockPos(goal.getX(), y, goal.getZ());
		if (s.distManhattan(g) > 96) return List.of();

		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		Map<BlockPos, BlockPos> parent = new HashMap<>();
		queue.add(s);
		parent.put(s, null);
		BlockPos found = null;
		int visited = 0;

		while (!queue.isEmpty() && visited++ < 2600) {
			BlockPos cur = queue.poll();
			if (cur.distManhattan(g) <= 2) {
				found = cur;
				break;
			}
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				BlockPos next = cur.relative(dir);
				if (parent.containsKey(next)) continue;
				if (Math.abs(next.getX() - g.getX()) > 55 || Math.abs(next.getZ() - g.getZ()) > 55) continue;
				if (!isGuidePassable(level, next)) continue;
				parent.put(next, cur);
				queue.add(next);
			}
		}
		if (found == null) return List.of();
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos c = found; c != null; c = parent.get(c)) out.add(c);
		Collections.reverse(out);
		return out;
	}

	private static boolean isGuidePassable(ServerLevel level, BlockPos feet) {
		if (!level.isLoaded(feet)) return false;
		BlockState bs = level.getBlockState(feet);
		BlockState head = level.getBlockState(feet.above());
		if (bs.getBlock() instanceof DoorBlock || head.getBlock() instanceof DoorBlock) return true;
		return bs.getCollisionShape(level, feet).isEmpty() && head.getCollisionShape(level, feet.above()).isEmpty();
	}

	private static void tickReturnDoorBeam(MinecraftServer server, MissionData d) {
		if (!d.hasReturnDoor || d.returnDoorDim.isEmpty()) return;
		ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(d.returnDoorDim)));
		if (level == null || !level.isLoaded(d.returnDoorPos)) return;
		for (double dy = 0.3; dy <= 6.5; dy += 1.0) {
			level.sendParticles(ParticleTypes.END_ROD,
					d.returnDoorPos.getX() + 0.5, d.returnDoorPos.getY() + dy, d.returnDoorPos.getZ() + 0.5,
					1, 0.08, 0.05, 0.08, 0.004);
		}
	}

	private static boolean checkCollections(MinecraftServer server, MissionData d) {
		if (d.exploration == null) return false;
		boolean changed = false;
		for (ActiveObjective obj : d.exploration.objectives) {
			if (obj.type != ObjType.COLLECT || obj.done()) continue;
			int total = 0;
			for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
				total += countCollectedItem(sp, obj.target);
			}
			int next = Math.min(obj.goal, total);
			if (next > obj.progress) {
				obj.progress = next;
				changed = true;
			}
		}
		if (changed) {
			d.changed();
			if (d.exploration.ready()) {
				for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
					if (Home.inBackrooms(sp.level().dimension()) && matchesLevel(sp.level(), d.exploration.level)) {
						spawnReturnDoor(sp, d);
						break;
					}
				}
			}
		}
		return changed;
	}

	private static int countCollectedItem(ServerPlayer player, String target) {
		int sum = 0;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.isEmpty()) continue;
			String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			boolean match = switch (target) {
				case "tape_l0" -> L0_TAPE_PATHS.contains(path);
				case "almond_l1" -> "almond_water".equals(path);
				default -> path.equals(target);
			};
			if (match) sum += stack.getCount();
		}
		return sum;
	}

	private static void onExplorationProgress(MinecraftServer server, ServerPlayer actor, MissionData d) {
		d.changed();
		if (d.exploration != null && d.exploration.ready()) {
			if (!d.hasReturnDoor && Home.inBackrooms(actor.level().dimension())) {
				spawnReturnDoor(actor, d);
			} else {
				actor.sendOverlayMessage(Component.translatable("mission.noclip_backrooms.obj_done_home").withStyle(ChatFormatting.GREEN));
			}
		} else if (d.exploration != null) {
			actor.level().playSound(null, actor.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 1.4F);
		}
		syncAll(server, d);
	}

	private static void spawnReturnDoor(ServerPlayer player, MissionData d) {
		ServerLevel level = player.level();
		BlockPos base = findOpenDoorSpot(level, player.blockPosition());
		if (base == null) return;
		Direction facing = player.getDirection().getOpposite();
		BlockState lower = StoryBlocks.GUIDE_DOOR.defaultBlockState()
				.setValue(StoryBlocks.Door.FACING, facing)
				.setValue(StoryBlocks.Door.HALF, DoubleBlockHalf.LOWER);
		BlockState upper = StoryBlocks.GUIDE_DOOR.defaultBlockState()
				.setValue(StoryBlocks.Door.FACING, facing)
				.setValue(StoryBlocks.Door.HALF, DoubleBlockHalf.UPPER);
		level.setBlockAndUpdate(base, lower);
		level.setBlockAndUpdate(base.above(), upper);
		d.hasReturnDoor = true;
		d.returnDoorDim = level.dimension().identifier().toString();
		d.returnDoorPos = base.immutable();
		d.changed();
		int dist = Math.round((float) Math.sqrt(player.blockPosition().distSqr(base)));
		level.playSound(null, base, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.9F, 1.15F);
		broadcastTeam(level.getServer(), Component.translatable("mission.noclip_backrooms.return_door_spawned", dist)
				.withStyle(ChatFormatting.GREEN));
	}

	private static BlockPos findOpenDoorSpot(ServerLevel level, BlockPos center) {
		for (int radius : new int[]{14, 18, 12, 22, 8}) {
			for (int a = 0; a < 16; a++) {
				double ang = a * (Math.PI * 2.0 / 16.0);
				int x = center.getX() + (int) Math.round(Math.cos(ang) * radius);
				int z = center.getZ() + (int) Math.round(Math.sin(ang) * radius);
				for (int dy = -2; dy <= 2; dy++) {
					BlockPos p = new BlockPos(x, center.getY() + dy, z);
					if (!level.isLoaded(p)) continue;
					if (level.getBlockState(p).isAir()
							&& level.getBlockState(p.above()).isAir()
							&& level.getBlockState(p.above(2)).isAir()
							&& level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
						return p;
					}
				}
			}
		}
		return null;
	}

	// ---- Actions & UI sync ----

	private static void handleAction(ServerPlayer player, MissionActionPayload payload) {
		MinecraftServer server = player.level().getServer();
		MissionData d = data(server);
		switch (payload.action()) {
			case "accept_exp" -> acceptExploration(player, payload.arg());
			case "reroll_exp" -> {
				d.rerollSeed++;
				d.changed();
				syncAll(server, d);
				openBoard(player, d, false);
			}
			case "turnin_exp" -> turnInExploration(player, d);
			case "abandon_exp" -> {
				d.exploration = null;
				d.hasReturnDoor = false;
				d.returnDoorDim = "";
				d.usedBlocks.clear();
				d.changed();
				syncAll(server, d);
				openBoard(player, d, false);
			}
			case "accept_bounty" -> acceptBounty(player, payload.arg());
			case "turnin_bounty" -> turnInBounty(player, d);
			case "abandon_bounty" -> {
				d.bounty = null;
				d.changed();
				syncAll(server, d);
				openBoard(player, d, false);
			}
			case "claim_camera" -> giveCamerasToAll(server, true);
			case "deploy" -> {
				String lvl = payload.arg();
				if (d.unlockedLevels.contains(lvl) || "0".equals(lvl)) {
					player.closeContainer();
					LevelExits.use(player, lvl);
				}
			}
			default -> {
			}
		}
	}

	public static void acceptExploration(ServerPlayer player, String level) {
		MinecraftServer server = player.level().getServer();
		MissionData d = data(server);
		if (!EXPLORATION_POOLS.containsKey(level)) level = "0";
		List<ObjectiveSpec> specs = sampleObjectives(level, d.rerollSeed + level.hashCode() * 31L);
		long reward = BASE_REWARD.getOrDefault(level, 100L);
		boolean needsCam = false;
		boolean needsCharcoal = false;
		List<ActiveObjective> list = new ArrayList<>();
		for (ObjectiveSpec s : specs) {
			reward += s.rewardBonus;
			if (s.needsCamera) needsCam = true;
			if (s.needsCharcoal) needsCharcoal = true;
			list.add(new ActiveObjective(s.kind, s.target, s.type, 0, s.goal));
		}
		d.exploration = new ActiveExploration(level, reward, list);
		d.hasReturnDoor = false;
		d.returnDoorDim = "";
		d.usedBlocks.clear();
		d.briefed = true;
		d.changed();
		if (needsCam) giveCamerasToAll(server, false);
		if (needsCharcoal) giveCharcoalToAll(server);
		player.level().playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6F, 1.1F);
		syncAll(server, d);
		openBoard(player, d, false);
	}

	private static void acceptBounty(ServerPlayer player, String bountyId) {
		MinecraftServer server = player.level().getServer();
		MissionData d = data(server);
		BountyDef chosen = null;
		for (BountyDef b : BOUNTIES) {
			if (b.id.equals(bountyId)) {
				chosen = b;
				break;
			}
		}
		if (chosen == null || !isBountyUnlocked(chosen.id, d.completedCount, d.unlockedLevels)) return;
		d.bounty = new ActiveBounty(chosen.id, chosen.level, chosen.target, 0, chosen.goal, chosen.reward);
		d.briefed = true;
		d.changed();
		player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7F, 1.2F);
		syncAll(server, d);
		openBoard(player, d, false);
	}

	private static void turnInExploration(ServerPlayer player, MissionData d) {
		if (d.exploration == null || !d.exploration.ready()) return;
		MinecraftServer server = player.level().getServer();
		long reward = d.exploration.reward;
		String finishedLevel = d.exploration.level;
		d.exploration = null;
		d.hasReturnDoor = false;
		d.returnDoorDim = "";
		d.usedBlocks.clear();
		d.completedCount++;
		d.rerollSeed++;
		unlockNextForCompletion(d, finishedLevel);
		d.changed();

		rewardOnlineTeam(server, reward, 45);
		Quota.syncMissionClearance(server, d.completedCount);
		broadcastTeam(server, Component.translatable("mission.noclip_backrooms.turned_in", reward, d.completedCount)
				.withStyle(ChatFormatting.GREEN));
		syncAll(server, d);
		openBoard(player, d, false);
	}

	private static void turnInBounty(ServerPlayer player, MissionData d) {
		if (d.bounty == null || !d.bounty.ready()) return;
		MinecraftServer server = player.level().getServer();
		long reward = d.bounty.reward;
		d.bounty = null;
		d.completedCount++;
		d.changed();

		rewardOnlineTeam(server, reward, 65);
		Quota.syncMissionClearance(server, d.completedCount);
		broadcastTeam(server, Component.translatable("mission.noclip_backrooms.turned_in", reward, d.completedCount)
				.withStyle(ChatFormatting.GOLD));
		syncAll(server, d);
		openBoard(player, d, false);
	}

	private static void rewardOnlineTeam(MinecraftServer server, long credits, int xp) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Wallet.add(p, credits);
			PlayerRpg.grantXp(p, xp);
			p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.25F);
		}
	}

	private static void unlockNextForCompletion(MissionData d, String finishedLevel) {
		if ("0".equals(finishedLevel) || d.completedCount >= 1) d.unlockedLevels.add("1");
		if ("1".equals(finishedLevel) || d.completedCount >= 2) d.unlockedLevels.add("2");
		if ("2".equals(finishedLevel) || d.completedCount >= 3) d.unlockedLevels.add("3");
		if ("3".equals(finishedLevel) || d.completedCount >= 4) d.unlockedLevels.add("4");
	}

	private static void giveCamerasToAll(MinecraftServer server, boolean manualRequest) {
		for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
			if (!hasItem(sp, FieldKit.CAMERA)) {
				ItemStack cam = new ItemStack(FieldKit.CAMERA);
				if (!sp.getInventory().add(cam)) sp.drop(cam, false);
				sp.sendSystemMessage(Component.translatable("mission.noclip_backrooms.camera_issued").withStyle(ChatFormatting.AQUA));
			} else if (manualRequest && server.getPlayerList().getPlayers().size() == 1) {
				sp.sendOverlayMessage(Component.translatable("mission.noclip_backrooms.camera_already_owned").withStyle(ChatFormatting.GRAY));
			}
		}
	}

	private static void giveCharcoalToAll(MinecraftServer server) {
		var blackDye = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("black_dye"));
		for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
			if (!hasItem(sp, Items.CHARCOAL) && !hasItem(sp, Items.INK_SAC) && !hasItem(sp, blackDye)) {
				ItemStack stack = new ItemStack(Items.CHARCOAL, 4);
				if (!sp.getInventory().add(stack)) sp.drop(stack, false);
				sp.sendSystemMessage(Component.translatable("mission.noclip_backrooms.charcoal_issued").withStyle(ChatFormatting.GRAY));
			}
		}
	}

	private static boolean hasItem(ServerPlayer player, net.minecraft.world.item.Item item) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(item)) return true;
		}
		return player.getOffhandItem().is(item);
	}

	private static void completeActiveForTest(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		MissionData d = data(server);
		if (d.exploration != null) {
			for (ActiveObjective o : d.exploration.objectives) o.progress = o.goal;
			if (!d.hasReturnDoor && Home.inBackrooms(player.level().dimension())) spawnReturnDoor(player, d);
		}
		if (d.bounty != null) d.bounty.progress = d.bounty.goal;
		d.changed();
		syncAll(server, d);
	}

	private static void showTextStatus(ServerPlayer p) {
		MissionData d = data(p.level().getServer());
		if (d.exploration == null && d.bounty == null) {
			p.sendSystemMessage(Component.translatable("mission.noclip_backrooms.none_active").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (d.exploration != null) {
			p.sendSystemMessage(Component.translatable("mission.noclip_backrooms.exp_title", d.exploration.level)
					.withStyle(ChatFormatting.AQUA));
			for (ActiveObjective o : d.exploration.objectives) {
				p.sendSystemMessage(Component.literal(" - ")
						.append(Component.translatable("mission.noclip_backrooms.obj." + o.kind))
						.append(" (" + o.progress + "/" + o.goal + ")")
						.withStyle(o.done() ? ChatFormatting.GREEN : ChatFormatting.WHITE));
			}
		}
	}

	public static void openBoard(ServerPlayer player, MissionData d, boolean firstBriefing) {
		List<String> unlocked = new ArrayList<>(d.unlockedLevels);
		Collections.sort(unlocked);
		List<BoardEntry> expOffers = new ArrayList<>();
		for (String lvl : List.of("0", "1", "2", "3", "4")) {
			List<ObjectiveSpec> specs = sampleObjectives(lvl, d.rerollSeed + lvl.hashCode() * 31L);
			long rew = BASE_REWARD.getOrDefault(lvl, 100L);
			List<ObjLine> lines = new ArrayList<>();
			for (ObjectiveSpec s : specs) {
				rew += s.rewardBonus;
				lines.add(new ObjLine(s.type.name(), s.labelKey(), 0, s.goal, false));
			}
			expOffers.add(new BoardEntry(lvl, rew, lines));
		}
		List<BountyEntry> bOffers = new ArrayList<>();
		for (BountyDef b : BOUNTIES) {
			boolean ok = isBountyUnlocked(b.id, d.completedCount, d.unlockedLevels);
			bOffers.add(new BountyEntry(b.id, b.level, b.titleKey(), b.descKey(), b.reward, b.goal, ok, b.boss, b.reqCompleted));
		}
		MissionSyncPayload sync = buildSync(player, d);
		ServerPlayNetworking.send(player, new MissionBoardPayload(firstBriefing, d.completedCount, unlocked, sync, expOffers, bOffers));
	}

	private static void syncAll(MinecraftServer server, MissionData d) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			syncPlayer(player, d);
		}
	}

	private static void syncPlayer(ServerPlayer player, MissionData d) {
		ServerPlayNetworking.send(player, buildSync(player, d));
	}

	private static MissionSyncPayload buildSync(ServerPlayer player, MissionData d) {
		boolean hasExp = d.exploration != null;
		String expLvl = hasExp ? d.exploration.level : "";
		boolean expReady = hasExp && d.exploration.ready();
		long expRew = hasExp ? d.exploration.reward : 0L;
		List<ObjLine> expLines = new ArrayList<>();
		if (hasExp) {
			for (ActiveObjective o : d.exploration.objectives) {
				expLines.add(new ObjLine(o.type.name(), "mission.noclip_backrooms.obj." + o.kind, o.progress, o.goal, o.done()));
			}
		}

		boolean hasBnt = d.bounty != null;
		String bId = hasBnt ? d.bounty.id : "";
		String bLvl = hasBnt ? d.bounty.level : "";
		String bTitle = hasBnt ? "mission.noclip_backrooms.bounty." + d.bounty.id : "";
		boolean bReady = hasBnt && d.bounty.ready();
		long bRew = hasBnt ? d.bounty.reward : 0L;
		int bProg = hasBnt ? d.bounty.progress : 0;
		int bGoal = hasBnt ? d.bounty.goal : 0;

		boolean hasHint = false;
		int hx = 0, hz = 0;
		String hLvl = "", hLbl = "";

		if (Home.isHome(player.level().dimension()) && (!d.briefed || d.isAnyMissionReady())) {
			Person cmd = nearCommander(player, 120.0);
			if (cmd != null) {
				hasHint = true;
				hx = cmd.getBlockX();
				hz = cmd.getBlockZ();
				hLbl = !d.briefed ? "mission.noclip_backrooms.hint.commander_brief" : "mission.noclip_backrooms.hint.commander_turnin";
			}
		} else if (hasExp && expReady && d.hasReturnDoor && player.level().dimension().identifier().toString().equals(d.returnDoorDim)) {
			hasHint = true;
			hx = d.returnDoorPos.getX();
			hz = d.returnDoorPos.getZ();
			hLvl = expLvl;
			hLbl = "mission.noclip_backrooms.hint.return_door";
		} else if (hasExp && !expReady && player.level().dimension() == Level0.LEVEL_0 && "0".equals(expLvl)) {
			for (ActiveObjective o : d.exploration.objectives) {
				if (o.done() || o.type != ObjType.PHOTO) continue;
				int[] target = Level0MissionHelper.findNearestFeature(player.level(), player.getBlockX(), player.getBlockZ(), o.target);
				if (target != null) {
					hasHint = true;
					hx = target[0];
					hz = target[1];
					hLvl = "0";
					hLbl = "mission.noclip_backrooms.obj." + o.kind;
					break;
				}
			}
		}

		return new MissionSyncPayload(d.briefed, d.completedCount,
				hasExp, expLvl, "mission.noclip_backrooms.exp_title", expReady, expRew, expLines,
				hasBnt, bId, bLvl, bTitle, bReady, bRew, bProg, bGoal,
				hasHint, hx, hz, hLvl, hLbl);
	}

	private static boolean matchesLevel(ServerLevel sl, String levelKey) {
		return switch (levelKey) {
			case "0" -> sl.dimension() == Level0.LEVEL_0;
			case "1" -> sl.dimension() == Level1.LEVEL_1;
			case "2" -> sl.dimension() == Level2.LEVEL_2;
			case "3" -> sl.dimension() == Level3.LEVEL_3;
			case "4" -> sl.dimension() == dev.noclip.backrooms.level4.Level4.LEVEL_4;
			default -> levelKey.equals(Floors.key(sl));
		};
	}

	private static Person nearCommander(ServerPlayer player) {
		return nearCommander(player, 10.0);
	}

	private static Person nearCommander(ServerPlayer player, double range) {
		List<Person> found = player.level().getEntitiesOfClass(Person.class, player.getBoundingBox().inflate(range),
				p -> p.isAlive() && p.entityTags().contains(ShopRole.MASTER.tag()));
		return found.isEmpty() ? null : found.getFirst();
	}

	private static void broadcastTeam(MinecraftServer server, Component msg) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			p.sendSystemMessage(msg);
		}
	}

	// ---- SavedData Persistence ----

	private static MissionData data(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(MissionData.TYPE);
	}

	private static final class ActiveObjective {
		final String kind, target;
		final ObjType type;
		int progress;
		final int goal;

		ActiveObjective(String kind, String target, ObjType type, int progress, int goal) {
			this.kind = kind;
			this.target = target;
			this.type = type;
			this.progress = progress;
			this.goal = goal;
		}

		boolean done() {
			return progress >= goal;
		}
	}

	private static final class ActiveExploration {
		final String level;
		final long reward;
		final List<ActiveObjective> objectives;

		ActiveExploration(String level, long reward, List<ActiveObjective> objectives) {
			this.level = level;
			this.reward = reward;
			this.objectives = objectives;
		}

		boolean ready() {
			for (ActiveObjective o : objectives) if (!o.done()) return false;
			return !objectives.isEmpty();
		}
	}

	private static final class ActiveBounty {
		final String id, level, target;
		int progress;
		final int goal;
		final long reward;

		ActiveBounty(String id, String level, String target, int progress, int goal, long reward) {
			this.id = id;
			this.level = level;
			this.target = target;
			this.progress = progress;
			this.goal = goal;
			this.reward = reward;
		}

		boolean ready() {
			return progress >= goal;
		}
	}

	private static final class MissionData extends SavedData {
		boolean briefed;
		int completedCount;
		int rerollSeed;
		final Set<String> unlockedLevels = new HashSet<>(Set.of("0"));
		ActiveExploration exploration;
		ActiveBounty bounty;
		final Set<Long> usedBlocks = new HashSet<>();
		boolean hasReturnDoor;
		String returnDoorDim = "";
		BlockPos returnDoorPos = BlockPos.ZERO;

		private record ObjSaved(String kind, String target, String type, int progress, int goal) {
			static final Codec<ObjSaved> CODEC = RecordCodecBuilder.create(i -> i.group(
					Codec.STRING.fieldOf("kind").forGetter(ObjSaved::kind),
					Codec.STRING.fieldOf("target").forGetter(ObjSaved::target),
					Codec.STRING.fieldOf("type").forGetter(ObjSaved::type),
					Codec.INT.fieldOf("progress").forGetter(ObjSaved::progress),
					Codec.INT.fieldOf("goal").forGetter(ObjSaved::goal)
			).apply(i, ObjSaved::new));
		}

		private record ExpSaved(boolean hasExp, String level, long reward, List<ObjSaved> objectives, List<Long> usedBlocks) {
			static final Codec<ExpSaved> CODEC = RecordCodecBuilder.create(i -> i.group(
					Codec.BOOL.optionalFieldOf("has", false).forGetter(ExpSaved::hasExp),
					Codec.STRING.optionalFieldOf("level", "0").forGetter(ExpSaved::level),
					Codec.LONG.optionalFieldOf("reward", 0L).forGetter(ExpSaved::reward),
					ObjSaved.CODEC.listOf().optionalFieldOf("objectives", List.of()).forGetter(ExpSaved::objectives),
					Codec.LONG.listOf().optionalFieldOf("used_blocks", List.of()).forGetter(ExpSaved::usedBlocks)
			).apply(i, ExpSaved::new));
		}

		private record BountySaved(boolean hasBounty, String id, String level, String target, int progress, int goal, long reward) {
			static final Codec<BountySaved> CODEC = RecordCodecBuilder.create(i -> i.group(
					Codec.BOOL.optionalFieldOf("has", false).forGetter(BountySaved::hasBounty),
					Codec.STRING.optionalFieldOf("id", "").forGetter(BountySaved::id),
					Codec.STRING.optionalFieldOf("level", "1").forGetter(BountySaved::level),
					Codec.STRING.optionalFieldOf("target", "").forGetter(BountySaved::target),
					Codec.INT.optionalFieldOf("progress", 0).forGetter(BountySaved::progress),
					Codec.INT.optionalFieldOf("goal", 1).forGetter(BountySaved::goal),
					Codec.LONG.optionalFieldOf("reward", 0L).forGetter(BountySaved::reward)
			).apply(i, BountySaved::new));
		}

		private record DoorSaved(boolean hasDoor, String dim, long pos) {
			static final Codec<DoorSaved> CODEC = RecordCodecBuilder.create(i -> i.group(
					Codec.BOOL.optionalFieldOf("has", false).forGetter(DoorSaved::hasDoor),
					Codec.STRING.optionalFieldOf("dim", "").forGetter(DoorSaved::dim),
					Codec.LONG.optionalFieldOf("pos", 0L).forGetter(DoorSaved::pos)
			).apply(i, DoorSaved::new));
		}

		private record Saved(
				boolean briefed,
				int completedCount,
				int rerollSeed,
				List<String> unlockedLevels,
				ExpSaved exp,
				BountySaved bounty,
				DoorSaved door) {
		}

		private static final Codec<Saved> SAVED_CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.optionalFieldOf("briefed", false).forGetter(Saved::briefed),
				Codec.INT.optionalFieldOf("completed_count", 0).forGetter(Saved::completedCount),
				Codec.INT.optionalFieldOf("reroll_seed", 0).forGetter(Saved::rerollSeed),
				Codec.STRING.listOf().optionalFieldOf("unlocked_levels", List.of("0")).forGetter(Saved::unlockedLevels),
				ExpSaved.CODEC.optionalFieldOf("exp", new ExpSaved(false, "0", 0L, List.of(), List.of())).forGetter(Saved::exp),
				BountySaved.CODEC.optionalFieldOf("bounty", new BountySaved(false, "", "1", "", 0, 1, 0L)).forGetter(Saved::bounty),
				DoorSaved.CODEC.optionalFieldOf("door", new DoorSaved(false, "", 0L)).forGetter(Saved::door)
		).apply(i, Saved::new));

		static final SavedDataType<MissionData> TYPE = new SavedDataType<>(
				BackroomsMod.id("missions"), MissionData::new,
				SAVED_CODEC.xmap(MissionData::fromSaved, MissionData::toSaved),
				DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

		MissionData() {
		}

		boolean isAnyMissionReady() {
			return (exploration != null && exploration.ready()) || (bounty != null && bounty.ready());
		}

		void changed() {
			setDirty();
		}

		private static MissionData fromSaved(Saved s) {
			MissionData d = new MissionData();
			d.briefed = s.briefed;
			d.completedCount = s.completedCount;
			d.rerollSeed = s.rerollSeed;
			d.unlockedLevels.clear();
			d.unlockedLevels.add("0");
			d.unlockedLevels.addAll(s.unlockedLevels);
			if (s.exp.hasExp) {
				List<ActiveObjective> objs = new ArrayList<>();
				for (ObjSaved o : s.exp.objectives) {
					ObjType t = ObjType.valueOf(o.type);
					objs.add(new ActiveObjective(o.kind, o.target, t, o.progress, o.goal));
				}
				d.exploration = new ActiveExploration(s.exp.level, s.exp.reward, objs);
			}
			d.usedBlocks.addAll(s.exp.usedBlocks);
			if (s.bounty.hasBounty && !s.bounty.id.isEmpty()) {
				d.bounty = new ActiveBounty(s.bounty.id, s.bounty.level, s.bounty.target, s.bounty.progress, s.bounty.goal, s.bounty.reward);
			}
			d.hasReturnDoor = s.door.hasDoor;
			d.returnDoorDim = s.door.dim;
			d.returnDoorPos = BlockPos.of(s.door.pos);
			return d;
		}

		private Saved toSaved() {
			List<ObjSaved> expObjs = new ArrayList<>();
			if (exploration != null) {
				for (ActiveObjective o : exploration.objectives) {
					expObjs.add(new ObjSaved(o.kind, o.target, o.type.name(), o.progress, o.goal));
				}
			}
			return new Saved(
					briefed, completedCount, rerollSeed, new ArrayList<>(unlockedLevels),
					new ExpSaved(exploration != null, exploration != null ? exploration.level : "0",
							exploration != null ? exploration.reward : 0L, expObjs, new ArrayList<>(usedBlocks)),
					new BountySaved(bounty != null, bounty != null ? bounty.id : "", bounty != null ? bounty.level : "1",
							bounty != null ? bounty.target : "", bounty != null ? bounty.progress : 0,
							bounty != null ? bounty.goal : 1, bounty != null ? bounty.reward : 0L),
					new DoorSaved(hasReturnDoor, returnDoorDim, returnDoorPos.asLong()));
		}
	}
}
