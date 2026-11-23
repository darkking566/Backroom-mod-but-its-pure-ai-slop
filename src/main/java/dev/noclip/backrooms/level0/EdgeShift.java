package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.BackroomsMod;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Level 0's Edge Shift effect: corridors, doorways, pillars, lights, and player-placed markers
 * outside every player's direct field of view quietly reconfigure themselves while keeping the
 * maze 100% passable and leaving Arches and Exit doorways untouched.
 */
final class EdgeShift {
	private static GameRule<Boolean> edgeShiftRule;

	private static final int CHECK_INTERVAL_TICKS = 15;
	private static final int WATCH_INTERVAL_TICKS = 10;
	private static final int CELL_SCAN_RADIUS = 6;
	private static final int WATCH_CELL_RADIUS = 3;
	private static final int WATCH_SHIFTS_PER_PULSE = 3;
	private static final double MIN_DISTANCE_LIT = 9.0;
	private static final double MIN_DISTANCE_DARK = 7.0;
	private static final double NEAR_PRIORITY_DISTANCE = 22.0;
	private static final double MAX_DISTANCE = 45.0;
	private static final double OBSERVED_RADIUS = 48.0;
	// cos(81.4 deg) -> outside wide-FOV screen edges.
	private static final double MAX_UNSEEN_DOT = 0.15;

	private static final Set<UUID> WATCHING_PLAYERS = new HashSet<>();

	private EdgeShift() {
	}

	static void register() {
		if (edgeShiftRule == null) {
			edgeShiftRule = GameRuleBuilder.forBoolean(true)
					.category(GameRuleCategory.MISC)
					.buildAndRegister(BackroomsMod.id("edge_shift"));
		}
		ServerTickEvents.END_SERVER_TICK.register(EdgeShift::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("backrooms")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("edgeshift")
								.then(Commands.literal("watch")
										.executes(ctx -> toggleWatch(ctx.getSource(), null))
										.then(Commands.literal("on").executes(ctx -> toggleWatch(ctx.getSource(), true)))
										.then(Commands.literal("off").executes(ctx -> toggleWatch(ctx.getSource(), false))))
								.then(Commands.literal("pulse").executes(ctx -> pulseOnce(ctx.getSource())))
								.then(Commands.literal("status").executes(ctx -> showStatus(ctx.getSource()))))));
	}

	private static int toggleWatch(CommandSourceStack source, Boolean enable) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("Must be run by a player."));
			return 0;
		}
		boolean next = enable != null ? enable : !WATCHING_PLAYERS.contains(player.getUUID());
		if (next) {
			WATCHING_PLAYERS.add(player.getUUID());
			source.sendSuccess(() -> Component.literal(
					"Edge Shift watch mode ON: nearby corridors will morph every 0.5s right in front of you."), false);
		} else {
			WATCHING_PLAYERS.remove(player.getUUID());
			source.sendSuccess(() -> Component.literal("Edge Shift watch mode OFF."), false);
		}
		return 1;
	}

	private static int pulseOnce(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("Must be run by a player."));
			return 0;
		}
		if (player.level().dimension() != Level0.LEVEL_0) {
			source.sendFailure(Component.literal("You must be inside Level 0."));
			return 0;
		}
		ServerLevel level = player.level();
		ShiftState state = level.getDataStorage().computeIfAbsent(ShiftState.TYPE);
		long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
		int shifted = shiftRingAroundPlayer(level, seed, state, player, level.getServer().getTickCount(),
				WATCH_CELL_RADIUS, Integer.MAX_VALUE);
		source.sendSuccess(() -> Component.literal("Shifted " + shifted + " nearby cells."), false);
		return shifted;
	}

	private static int showStatus(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("Must be run by a player."));
			return 0;
		}
		ServerLevel level0 = source.getServer().getLevel(Level0.LEVEL_0);
		boolean ruleEnabled = level0 != null && edgeShiftRule != null && level0.getGameRules().get(edgeShiftRule);
		boolean watching = WATCHING_PLAYERS.contains(player.getUUID());
		if (player.level().dimension() != Level0.LEVEL_0 || level0 == null) {
			source.sendSuccess(() -> Component.literal(
					"Edge Shift rule=" + ruleEnabled + ", watch=" + watching + " (outside Level 0)"), false);
			return 1;
		}
		ShiftState state = level0.getDataStorage().computeIfAbsent(ShiftState.TYPE);
		long seed = Level0ChunkGenerator.mazeSeed(level0.getChunkSource().randomState());
		Maze.Zone zone = Maze.zoneAt(seed, player.getBlockX(), player.getBlockZ());
		int pcx = Math.floorDiv(player.getBlockX(), Maze.CELL);
		int pcz = Math.floorDiv(player.getBlockZ(), Maze.CELL);
		int shiftedNearby = 0;
		for (int dx = -CELL_SCAN_RADIUS; dx <= CELL_SCAN_RADIUS; dx++) {
			for (int dz = -CELL_SCAN_RADIUS; dz <= CELL_SCAN_RADIUS; dz++) {
				if (state.epochAt(pcx + dx, pcz + dz) > 0) shiftedNearby++;
			}
		}
		int nearby = shiftedNearby;
		source.sendSuccess(() -> Component.literal(
				"Zone=" + zone + ", rule=" + ruleEnabled + ", watch=" + watching
						+ ", shiftedCellsWithin6=" + nearby), false);
		return 1;
	}

	static boolean isWatching(ServerPlayer player) {
		return WATCHING_PLAYERS.contains(player.getUUID());
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		boolean runNormal = now % CHECK_INTERVAL_TICKS == 0;
		boolean runWatch = !WATCHING_PLAYERS.isEmpty() && now % WATCH_INTERVAL_TICKS == 0;
		if (!runNormal && !runWatch) return;

		ServerLevel level = server.getLevel(Level0.LEVEL_0);
		if (level == null) return;

		ShiftState state = level.getDataStorage().computeIfAbsent(ShiftState.TYPE);
		long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());

		if (runWatch) {
			WATCHING_PLAYERS.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
			for (ServerPlayer player : level.players()) {
				if (WATCHING_PLAYERS.contains(player.getUUID())) {
					shiftRingAroundPlayer(level, seed, state, player, now, WATCH_CELL_RADIUS, WATCH_SHIFTS_PER_PULSE);
				}
			}
		}

		if (!runNormal || edgeShiftRule == null || !level.getGameRules().get(edgeShiftRule)) return;

		List<ServerPlayer> activePlayers = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && !WATCHING_PLAYERS.contains(player.getUUID())) {
				activePlayers.add(player);
			}
		}
		if (activePlayers.isEmpty()) return;

		RandomSource random = level.getRandom();

		// Cap to at most 2 shifts per tick cycle across the dimension to keep server load negligible.
		int shiftsBudget = 2;
		for (ServerPlayer player : activePlayers) {
			if (shiftsBudget <= 0) break;
			if (tryShiftNearPlayer(level, seed, state, activePlayers, player, now, random)) {
				shiftsBudget--;
			}
		}
	}

	private static int shiftRingAroundPlayer(ServerLevel level, long seed, ShiftState state,
			ServerPlayer player, int now, int radius, int maxShifts) {
		int pcx = Math.floorDiv(player.getBlockX(), Maze.CELL);
		int pcz = Math.floorDiv(player.getBlockZ(), Maze.CELL);
		List<int[]> eligible = new ArrayList<>();

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				// Never morph the exact cell the player stands in.
				if (dx == 0 && dz == 0) continue;
				int cx = pcx + dx;
				int cz = pcz + dz;
				if (!canReshapeCell(level, seed, cx, cz)) continue;
				eligible.add(new int[] {cx, cz});
			}
		}
		if (eligible.isEmpty()) return 0;

		RandomSource random = level.getRandom();
		int count = Math.min(maxShifts, eligible.size());
		int shifted = 0;
		for (int i = 0; i < count; i++) {
			int pick = i + random.nextInt(eligible.size() - i);
			int[] cell = eligible.get(pick);
			eligible.set(pick, eligible.get(i));
			int cx = cell[0], cz = cell[1];
			Maze.Zone zone = cellZone(seed, cx, cz);
			state.bumpEpoch(cx, cz);
			state.setCooldown(cx, cz, now + Maze.shiftCooldownTicks(zone));
			applyCellShift(level, seed, cx, cz, state);
			shifted++;
		}
		return shifted;
	}

	private static boolean tryShiftNearPlayer(ServerLevel level, long seed, ShiftState state,
			List<ServerPlayer> allPlayers, ServerPlayer anchor, int now, RandomSource random) {
		int pcx = Math.floorDiv(anchor.getBlockX(), Maze.CELL);
		int pcz = Math.floorDiv(anchor.getBlockZ(), Maze.CELL);

		List<Candidate> candidates = new ArrayList<>();
		int totalWeight = 0;

		for (int dx = -CELL_SCAN_RADIUS; dx <= CELL_SCAN_RADIUS; dx++) {
			for (int dz = -CELL_SCAN_RADIUS; dz <= CELL_SCAN_RADIUS; dz++) {
				int cx = pcx + dx;
				int cz = pcz + dz;
				if (!isEligibleCell(level, seed, state, allPlayers, cx, cz, now)) continue;

				Maze.Zone zone = cellZone(seed, cx, cz);
				double distSq = distanceSqToCell(anchor.getX(), anchor.getZ(), cx * Maze.CELL, cz * Maze.CELL);
				int weight = Maze.shiftWeight(zone) * (distSq <= NEAR_PRIORITY_DISTANCE * NEAR_PRIORITY_DISTANCE ? 3 : 1);
				candidates.add(new Candidate(cx, cz, zone, weight));
				totalWeight += weight;
			}
		}

		if (totalWeight <= 0) return false;

		int roll = random.nextInt(totalWeight);
		for (Candidate c : candidates) {
			roll -= c.weight();
			if (roll < 0) {
				state.bumpEpoch(c.cx(), c.cz());
				state.setCooldown(c.cx(), c.cz(), now + Maze.shiftCooldownTicks(c.zone()));
				applyCellShift(level, seed, c.cx(), c.cz(), state);
				return true;
			}
		}
		return false;
	}

	private static boolean canReshapeCell(ServerLevel level, long seed, int cx, int cz) {
		Maze.Zone zone = cellZone(seed, cx, cz);
		if (Maze.shiftWeight(zone) <= 0) return false;
		if (Exits.isProtectedCell(seed, cx, cz)) return false;
		if (Maze.isStairwellCell(seed, cx, cz)) return false;

		// Keep Red Room perimeter rings sealed so trapped players never get an unexpected opening out.
		if (zone == Maze.Zone.RED_ROOM && !Maze.isRedRoomInteriorCell(seed, cx, cz)) {
			return false;
		}

		// Keep shared walls with stable ARCHES or protected Exit cells completely unchanged.
		if (cellZone(seed, cx - 1, cz) == Maze.Zone.ARCHES || Exits.isProtectedCell(seed, cx - 1, cz)) return false;
		if (cellZone(seed, cx, cz - 1) == Maze.Zone.ARCHES || Exits.isProtectedCell(seed, cx, cz - 1)) return false;

		return areCellChunksLoaded(level, cx * Maze.CELL, cz * Maze.CELL);
	}

	private static boolean isEligibleCell(ServerLevel level, long seed, ShiftState state,
			List<ServerPlayer> allPlayers, int cx, int cz, int now) {
		if (!state.isCooldownReady(cx, cz, now)) return false;
		if (!canReshapeCell(level, seed, cx, cz)) return false;

		Maze.Zone zone = cellZone(seed, cx, cz);
		int minX = cx * Maze.CELL;
		int minZ = cz * Maze.CELL;

		double minDist = zone == Maze.Zone.DARKENED ? MIN_DISTANCE_DARK : MIN_DISTANCE_LIT;
		boolean withinRange = false;
		for (ServerPlayer player : allPlayers) {
			double distSq = distanceSqToCell(player.getX(), player.getZ(), minX, minZ);
			if (distSq < minDist * minDist) return false;
			if (distSq <= MAX_DISTANCE * MAX_DISTANCE) withinRange = true;
			if (distSq <= OBSERVED_RADIUS * OBSERVED_RADIUS && isCellObserved(player, minX, minZ)) {
				return false;
			}
		}
		return withinRange;
	}

	private static boolean areCellChunksLoaded(ServerLevel level, int minX, int minZ) {
		int maxX = minX + Maze.CELL;
		int maxZ = minZ + Maze.CELL;
		int cMinX = SectionPos.blockToSectionCoord(minX);
		int cMaxX = SectionPos.blockToSectionCoord(maxX);
		int cMinZ = SectionPos.blockToSectionCoord(minZ);
		int cMaxZ = SectionPos.blockToSectionCoord(maxZ);
		for (int cx = cMinX; cx <= cMaxX; cx++) {
			for (int cz = cMinZ; cz <= cMaxZ; cz++) {
				if (!level.hasChunk(cx, cz)) return false;
			}
		}
		return true;
	}

	static boolean isCellObserved(ServerPlayer player, int cellMinX, int cellMinZ) {
		Vec3 look = player.getLookAngle();
		return isOutOfObservation(player.getX(), player.getZ(), look.x, look.z, cellMinX, cellMinZ) == false;
	}

	/**
	 * Returns true only if the entire cell footprint lies outside the player's horizontal field of view.
	 */
	static boolean isOutOfObservation(double playerX, double playerZ, double lookX, double lookZ,
			int cellMinX, int cellMinZ) {
		double lookLen = Math.hypot(lookX, lookZ);
		if (lookLen < 1.0E-4) return false;
		double lx = lookX / lookLen;
		double lz = lookZ / lookLen;

		double maxX = cellMinX + Maze.CELL;
		double maxZ = cellMinZ + Maze.CELL;
		double midX = cellMinX + Maze.CELL * 0.5;
		double midZ = cellMinZ + Maze.CELL * 0.5;

		double[] sx = {cellMinX, maxX, cellMinX, maxX, midX};
		double[] sz = {cellMinZ, cellMinZ, maxZ, maxZ, midZ};
		for (int i = 0; i < sx.length; i++) {
			double dx = sx[i] - playerX;
			double dz = sz[i] - playerZ;
			double dist = Math.hypot(dx, dz);
			if (dist < 1.0E-4) return false;
			double dot = (dx * lx + dz * lz) / dist;
			if (dot >= MAX_UNSEEN_DOT) return false;
		}
		return true;
	}

	private static double distanceSqToCell(double px, double pz, int minX, int minZ) {
		double clampedX = Math.clamp(px, minX, minX + Maze.CELL);
		double clampedZ = Math.clamp(pz, minZ, minZ + Maze.CELL);
		double dx = clampedX - px;
		double dz = clampedZ - pz;
		return dx * dx + dz * dz;
	}

	private static void applyCellShift(ServerLevel level, long seed, int cx, int cz, ShiftState state) {
		int minX = cx * Maze.CELL;
		int minZ = cz * Maze.CELL;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int x = minX; x < minX + Maze.CELL; x++) {
			for (int z = minZ; z < minZ + Maze.CELL; z++) {
				rewriteColumn(level, seed, x, z, state, pos);
			}
		}

		// Sync adjacent corners that depend on this cell's north/west edges.
		if (cellZone(seed, cx + 1, cz) != Maze.Zone.ARCHES && !Exits.isProtectedCell(seed, cx + 1, cz)) {
			rewriteColumn(level, seed, minX + Maze.CELL, minZ, state, pos);
		}
		if (cellZone(seed, cx, cz + 1) != Maze.Zone.ARCHES && !Exits.isProtectedCell(seed, cx, cz + 1)) {
			rewriteColumn(level, seed, minX, minZ + Maze.CELL, state, pos);
		}
	}

	private static void rewriteColumn(ServerLevel level, long seed, int x, int z,
			ShiftState state, BlockPos.MutableBlockPos pos) {
		for (int y = Level0ChunkGenerator.FLOOR_Y; y <= Level0ChunkGenerator.CEILING_Y + 1; y++) {
			pos.set(x, y, z);
			BlockState current = level.getBlockState(pos);
			if (isProtectedBlock(current)) continue;

			BlockState target = Level0ChunkGenerator.blockAt(seed, x, y, z, state);
			if (current.equals(target)) continue;

			if (!target.getCollisionShape(level, pos).isEmpty()
					&& !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos)).isEmpty()) {
				continue;
			}
			level.setBlock(pos, target, Block.UPDATE_ALL);
		}
	}

	private static boolean isProtectedBlock(BlockState state) {
		return state.is(Blocks.BEDROCK)
				|| state.is(ModBlocks.EXIT_DOOR)
				|| state.is(ModBlocks.EXIT_DOOR_TOP)
				|| state.is(ModBlocks.EXIT_SIGN)
				|| state.is(ModBlocks.EXIT_LIGHT);
	}

	private static Maze.Zone cellZone(long seed, int cx, int cz) {
		return Maze.zoneAt(seed, cx * Maze.CELL, cz * Maze.CELL);
	}

	private record Candidate(int cx, int cz, Maze.Zone zone, int weight) {
	}

	static final class ShiftState extends SavedData implements Maze.EpochSource {
		static final Codec<ShiftState> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT)
				.optionalFieldOf("epochs", Map.of())
				.xmap(ShiftState::fromSerialized, ShiftState::toSerialized)
				.codec();

		static final SavedDataType<ShiftState> TYPE = new SavedDataType<>(
				BackroomsMod.id("edge_shift"),
				ShiftState::new,
				CODEC,
				DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

		private final Long2IntOpenHashMap epochs = new Long2IntOpenHashMap();
		private final Long2IntOpenHashMap cooldownUntilTick = new Long2IntOpenHashMap();

		ShiftState() {
			epochs.defaultReturnValue(0);
			cooldownUntilTick.defaultReturnValue(0);
		}

		@Override
		public int epochAt(int cx, int cz) {
			return epochs.get(ChunkPos.pack(cx, cz));
		}

		int bumpEpoch(int cx, int cz) {
			long key = ChunkPos.pack(cx, cz);
			int next = epochs.get(key) + 1;
			epochs.put(key, next);
			setDirty();
			return next;
		}

		boolean isCooldownReady(int cx, int cz, int now) {
			return now >= cooldownUntilTick.get(ChunkPos.pack(cx, cz));
		}

		void setCooldown(int cx, int cz, int readyAtTick) {
			cooldownUntilTick.put(ChunkPos.pack(cx, cz), readyAtTick);
		}

		private Map<String, Integer> toSerialized() {
			Map<String, Integer> out = new HashMap<>(epochs.size());
			for (Long2IntMap.Entry entry : epochs.long2IntEntrySet()) {
				long packed = entry.getLongKey();
				out.put(ChunkPos.getX(packed) + "," + ChunkPos.getZ(packed), entry.getIntValue());
			}
			return out;
		}

		private static ShiftState fromSerialized(Map<String, Integer> raw) {
			ShiftState state = new ShiftState();
			for (var entry : raw.entrySet()) {
				String[] parts = entry.getKey().split(",", 2);
				if (parts.length != 2) continue;
				try {
					int cx = Integer.parseInt(parts[0]);
					int cz = Integer.parseInt(parts[1]);
					int epoch = entry.getValue();
					if (epoch > 0) state.epochs.put(ChunkPos.pack(cx, cz), epoch);
				} catch (NumberFormatException ignored) {
				}
			}
			return state;
		}
	}
}
