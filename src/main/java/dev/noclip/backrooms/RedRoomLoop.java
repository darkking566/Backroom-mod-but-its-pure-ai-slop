package dev.noclip.backrooms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

/**
 * Enforces the Red Room's dead-loop once a survival/adventure player ventures past the outer ring:
 * - Behind-the-back perimeter doorways silently seal shut with red wallpaper.
 * - Reaching the sector's outer ring loops the player seamlessly back deep into an interior red corridor.
 * - Structural Red Room walls, floors, and ceilings cannot be mined through in survival.
 */
final class RedRoomLoop {
	private static final int SEAL_INTERVAL_TICKS = 15;
	private static final int LOOP_COOLDOWN_TICKS = 10;
	private static final double ADMIN_ESCAPE_DISTANCE = 8.0;
	private static final int SECTOR_BLOCKS = Maze.CELL * Maze.SECTOR;
	private static final int RED_ROOM_CELLS = Maze.LARGE_ZONE_SPAN * Maze.SECTOR;
	private static final int RED_ROOM_BLOCKS = Maze.CELL * RED_ROOM_CELLS;
	private static final Map<UUID, Integer> LAST_LOOP_TICK = new HashMap<>();

	private RedRoomLoop() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RedRoomLoop::tick);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
				canBreakBlock(level, player, pos, state));
	}

	static boolean isTrapped(ServerPlayer player) {
		if (player.level().dimension() != BackroomsMod.LEVEL_0) return false;
		TrapState state = player.level().getDataStorage().computeIfAbsent(TrapState.TYPE);
		return state.hasTrap(player.getUUID());
	}

	static boolean canBreakBlock(Level level, Player player, BlockPos pos, BlockState state) {
		if (player.isCreative() || level.dimension() != BackroomsMod.LEVEL_0) return true;
		if (!(level instanceof ServerLevel serverLevel)) return true;

		long seed = Level0ChunkGenerator.mazeSeed(serverLevel.getChunkSource().randomState());
		if (!isRedRoomStructurePos(seed, pos.getX(), pos.getZ())) return true;
		return !isStructuralBlock(state);
	}

	private static boolean isRedRoomStructurePos(long seed, int x, int z) {
		if (Maze.zoneAt(seed, x, z) == Maze.Zone.RED_ROOM) return true;
		// Also protect the shared outer wall facing west/north of a Red Room sector.
		return Maze.zoneAt(seed, x + 1, z) == Maze.Zone.RED_ROOM
				|| Maze.zoneAt(seed, x, z + 1) == Maze.Zone.RED_ROOM;
	}

	private static boolean isStructuralBlock(BlockState state) {
		return state.is(ModBlocks.RED_WALLPAPER)
				|| state.is(ModBlocks.RED_WALLPAPER_BASEBOARD)
				|| state.is(ModBlocks.PEELING_RED_WALLPAPER)
				|| state.is(ModBlocks.RED_FLUORESCENT_LIGHT)
				|| state.is(ModBlocks.BROKEN_FLUORESCENT_LIGHT)
				|| state.is(ModBlocks.THICK_CARPET)
				|| state.is(ModBlocks.CEILING_TILE)
				|| state.is(ModBlocks.WALLPAPER)
				|| state.is(ModBlocks.WALLPAPER_BASEBOARD)
				|| state.is(ModBlocks.CARPET);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(BackroomsMod.LEVEL_0);
		if (level == null) return;

		TrapState state = level.getDataStorage().computeIfAbsent(TrapState.TYPE);
		long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
		int now = server.getTickCount();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			if (player.level().dimension() != BackroomsMod.LEVEL_0
					|| player.isCreative()
					|| player.isSpectator()
					|| !player.isAlive()) {
				state.clearTrap(id);
				LAST_LOOP_TICK.remove(id);
				continue;
			}
			updatePlayerTrap(level, seed, state, player, now);
		}

		if (now % SEAL_INTERVAL_TICKS == 0 && !level.players().isEmpty()) {
			sealObservedTrappedPerimeters(level, seed, state);
		}
	}

	private static void updatePlayerTrap(ServerLevel level, long seed, TrapState state, ServerPlayer player, int now) {
		UUID id = player.getUUID();
		int bx = player.getBlockX(), bz = player.getBlockZ();
		int cx = Math.floorDiv(bx, Maze.CELL), cz = Math.floorDiv(bz, Maze.CELL);
		int originSx = Maze.largeZoneOriginSector(Math.floorDiv(cx, Maze.SECTOR));
		int originSz = Maze.largeZoneOriginSector(Math.floorDiv(cz, Maze.SECTOR));

		if (Maze.zoneAt(seed, bx, bz) == Maze.Zone.RED_ROOM) {
			if (Maze.isRedRoomInteriorCell(seed, cx, cz)) {
				state.setTrap(id, originSx, originSz);
				return;
			}
			if (state.hasTrap(id) && state.trappedSector(id) == ChunkPos.pack(originSx, originSz)) {
				// Stepped onto the outer perimeter ring of the trapped Red Room: loop back inside.
				loopPlayerInside(level, seed, player, originSx, originSz, now);
			}
			return;
		}

		if (!state.hasTrap(id)) return;

		long trapped = state.trappedSector(id);
		int tsx = Maze.largeZoneOriginSector(ChunkPos.getX(trapped));
		int tsz = Maze.largeZoneOriginSector(ChunkPos.getZ(trapped));
		double distOutside = distanceOutsideRedRoom(player.getX(), player.getZ(), tsx, tsz);
		if (distOutside > ADMIN_ESCAPE_DISTANCE) {
			// Teleported out via command or external mechanic: release the trap cleanly.
			state.clearTrap(id);
			LAST_LOOP_TICK.remove(id);
			return;
		}
		loopPlayerInside(level, seed, player, tsx, tsz, now);
	}

	private static double distanceOutsideRedRoom(double px, double pz, int originSx, int originSz) {
		double minX = originSx * SECTOR_BLOCKS;
		double maxX = minX + RED_ROOM_BLOCKS;
		double minZ = originSz * SECTOR_BLOCKS;
		double maxZ = minZ + RED_ROOM_BLOCKS;
		double cx = Math.clamp(px, minX, maxX);
		double cz = Math.clamp(pz, minZ, maxZ);
		return Math.hypot(px - cx, pz - cz);
	}

	private static void loopPlayerInside(ServerLevel level, long seed, ServerPlayer player, int originSx, int originSz, int now) {
		UUID id = player.getUUID();
		if (now - LAST_LOOP_TICK.getOrDefault(id, -LOOP_COOLDOWN_TICKS) < LOOP_COOLDOWN_TICKS) return;

		Vec3 dest = findLoopDestination(level, seed, player, originSx, originSz);
		if (dest == null) return;

		LAST_LOOP_TICK.put(id, now);
		player.teleportTo(level, dest.x, player.getY(), dest.z,
				Relative.union(Relative.DELTA, Relative.ROTATION), 0.0F, 0.0F, false);
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 45, 0, true, false, true));
		level.playSound(null, dest.x, player.getY(), dest.z,
				SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.45F, 0.60F);
	}

	private static Vec3 findLoopDestination(ServerLevel level, long seed, ServerPlayer player, int originSx, int originSz) {
		int bx = player.getBlockX(), bz = player.getBlockZ();
		int ix = Math.floorMod(bx, Maze.CELL), iz = Math.floorMod(bz, Maze.CELL);
		double fracX = player.getX() - bx;
		double fracZ = player.getZ() - bz;

		Vec3 look = player.getLookAngle();
		int dirX = Math.abs(look.x) >= Math.abs(look.z) ? (look.x >= 0 ? 1 : -1) : 0;
		int dirZ = dirX == 0 ? (look.z >= 0 ? 1 : -1) : 0;

		int curCx = Math.floorDiv(bx, Maze.CELL);
		int curCz = Math.floorDiv(bz, Maze.CELL);

		List<LoopCandidate> candidates = new ArrayList<>();

		// Search inner cells (rlx, rlz in 2..RED_ROOM_CELLS-3) so the player lands well away from any exit border.
		for (int rlx = 2; rlx <= RED_ROOM_CELLS - 3; rlx++) {
			for (int rlz = 2; rlz <= RED_ROOM_CELLS - 3; rlz++) {
				int cx = originSx * Maze.SECTOR + rlx;
				int cz = originSz * Maze.SECTOR + rlz;
				int tx = cx * Maze.CELL + ix;
				int tz = cz * Maze.CELL + iz;
				if (Maze.isWall(seed, tx, tz, false) || Maze.isWall(seed, tx, tz, true)) continue;

				int score = Math.abs(cx - curCx) + Math.abs(cz - curCz);
				if (!Maze.isWall(seed, tx + dirX, tz + dirZ, false)
						&& !Maze.isWall(seed, tx + dirX * 2, tz + dirZ * 2, false)) {
					score += 100;
				}
				candidates.add(new LoopCandidate(tx, tz, new Vec3(tx + fracX, player.getY(), tz + fracZ), score));
			}
		}

		candidates.sort((a, b) -> Integer.compare(b.score(), a.score()));
		for (LoopCandidate c : candidates) {
			if (isSafeSpot(level, player, c.tx(), c.tz(), c.pos())) return c.pos();
		}

		// Fallback to any open inner cell center.
		for (int rlx = 2; rlx <= RED_ROOM_CELLS - 3; rlx++) {
			for (int rlz = 2; rlz <= RED_ROOM_CELLS - 3; rlz++) {
				int tx = (originSx * Maze.SECTOR + rlx) * Maze.CELL + Maze.CENTER;
				int tz = (originSz * Maze.SECTOR + rlz) * Maze.CELL + Maze.CENTER;
				if (Maze.isWall(seed, tx, tz, false)) continue;
				Vec3 center = new Vec3(tx + 0.5, Level0ChunkGenerator.FLOOR_Y + 1, tz + 0.5);
				if (isSafeSpot(level, player, tx, tz, center)) return center;
			}
		}
		return null;
	}

	private record LoopCandidate(int tx, int tz, Vec3 pos, int score) {
	}

	private static boolean isSafeSpot(ServerLevel level, ServerPlayer player, int bx, int bz, Vec3 pos) {
		level.getChunk(new BlockPos(bx, Level0ChunkGenerator.FLOOR_Y, bz));
		BlockPos floorPos = new BlockPos(bx, Level0ChunkGenerator.FLOOR_Y, bz);
		if (level.getBlockState(floorPos).getCollisionShape(level, floorPos).isEmpty()) return false;
		if (!isOpenHeadroom(level, bx, bz)) return false;
		return level.noCollision(player.getDimensions(player.getPose()).makeBoundingBox(pos));
	}

	private static boolean isOpenHeadroom(ServerLevel level, int bx, int bz) {
		for (int y = Level0ChunkGenerator.FLOOR_Y + 1; y < Level0ChunkGenerator.CEILING_Y; y++) {
			BlockPos pos = new BlockPos(bx, y, bz);
			if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return false;
		}
		return true;
	}

	private static void sealObservedTrappedPerimeters(ServerLevel level, long seed, TrapState state) {
		List<Long> trappedSectors = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (!state.hasTrap(player.getUUID())) continue;
			long packed = state.trappedSector(player.getUUID());
			if (!trappedSectors.contains(packed)) trappedSectors.add(packed);
		}
		if (trappedSectors.isEmpty()) return;

		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (long packed : trappedSectors) {
			int sx = Maze.largeZoneOriginSector(ChunkPos.getX(packed));
			int sz = Maze.largeZoneOriginSector(ChunkPos.getZ(packed));
			sealRedRoomPerimeter(level, seed, sx, sz, pos);
		}
	}

	private static void sealRedRoomPerimeter(ServerLevel level, long seed, int originSx, int originSz,
			BlockPos.MutableBlockPos pos) {
		int baseCx = originSx * Maze.SECTOR;
		int baseCz = originSz * Maze.SECTOR;

		// West and East perimeter walls of the Red Room macro-zone.
		for (int lz = 0; lz < RED_ROOM_CELLS; lz++) {
			int cz = baseCz + lz;
			sealEdgeIfUnseen(level, seed, baseCx * Maze.CELL, cz * Maze.CELL, true, pos);
			sealEdgeIfUnseen(level, seed, (baseCx + RED_ROOM_CELLS) * Maze.CELL, cz * Maze.CELL, true, pos);
		}
		// North and South perimeter walls of the Red Room macro-zone.
		for (int lx = 0; lx < RED_ROOM_CELLS; lx++) {
			int cx = baseCx + lx;
			sealEdgeIfUnseen(level, seed, cx * Maze.CELL, baseCz * Maze.CELL, false, pos);
			sealEdgeIfUnseen(level, seed, cx * Maze.CELL, (baseCz + RED_ROOM_CELLS) * Maze.CELL, false, pos);
		}
	}

	private static void sealEdgeIfUnseen(ServerLevel level, long seed, int edgeX, int edgeZ, boolean alongZ,
			BlockPos.MutableBlockPos pos) {
		int cx = Math.floorDiv(edgeX, Maze.CELL);
		int cz = Math.floorDiv(edgeZ, Maze.CELL);
		int otherCx = alongZ ? cx - 1 : cx;
		int otherCz = alongZ ? cz : cz - 1;
		Maze.Zone zoneA = Maze.zoneAt(seed, cx * Maze.CELL, cz * Maze.CELL);
		Maze.Zone zoneB = Maze.zoneAt(seed, otherCx * Maze.CELL, otherCz * Maze.CELL);
		if (zoneA == zoneB
				|| zoneA == Maze.Zone.ARCHES
				|| zoneB == Maze.Zone.ARCHES
				|| Exits.isProtectedCell(seed, cx, cz)
				|| Exits.isProtectedCell(seed, otherCx, otherCz)) {
			return;
		}

		int cellMinX = cx * Maze.CELL;
		int cellMinZ = cz * Maze.CELL;
		if (!level.hasChunkAt(new BlockPos(edgeX, Level0ChunkGenerator.FLOOR_Y, edgeZ))) return;

		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) continue;
			double dx = edgeX - player.getX();
			double dz = edgeZ - player.getZ();
			if (dx * dx + dz * dz < 25.0) return;
			if (!EdgeShift.isOutOfObservation(player.getX(), player.getZ(),
					player.getLookAngle().x, player.getLookAngle().z, cellMinX, cellMinZ)) {
				return;
			}
		}

		for (int offset = 0; offset < Maze.CELL; offset++) {
			int x = alongZ ? edgeX : edgeX + offset;
			int z = alongZ ? edgeZ + offset : edgeZ;
			for (int y = Level0ChunkGenerator.FLOOR_Y + 1; y < Level0ChunkGenerator.CEILING_Y; y++) {
				pos.set(x, y, z);
				BlockState current = level.getBlockState(pos);
				if (!current.isAir()) continue;
				BlockState wall = (y == Level0ChunkGenerator.FLOOR_Y + 1
						? ModBlocks.RED_WALLPAPER_BASEBOARD
						: ModBlocks.RED_WALLPAPER).defaultBlockState();
				level.setBlock(pos, wall, Block.UPDATE_ALL);
			}
		}
	}

	static final class TrapState extends SavedData {
		static final Codec<TrapState> CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG)
				.optionalFieldOf("trapped", Map.of())
				.xmap(TrapState::fromSerialized, TrapState::toSerialized)
				.codec();

		static final SavedDataType<TrapState> TYPE = new SavedDataType<>(
				BackroomsMod.id("red_room_traps"),
				TrapState::new,
				CODEC,
				DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

		private final Map<UUID, Long> trapped = new HashMap<>();

		TrapState() {
		}

		boolean hasTrap(UUID id) {
			return trapped.containsKey(id);
		}

		long trappedSector(UUID id) {
			return trapped.get(id);
		}

		void setTrap(UUID id, int sx, int sz) {
			long packed = ChunkPos.pack(sx, sz);
			Long prev = trapped.put(id, packed);
			if (prev == null || prev != packed) setDirty();
		}

		void clearTrap(UUID id) {
			if (trapped.remove(id) != null) setDirty();
		}

		private Map<String, Long> toSerialized() {
			Map<String, Long> out = new HashMap<>(trapped.size());
			for (var entry : trapped.entrySet()) {
				out.put(entry.getKey().toString(), entry.getValue());
			}
			return out;
		}

		private static TrapState fromSerialized(Map<String, Long> raw) {
			TrapState state = new TrapState();
			for (var entry : raw.entrySet()) {
				try {
					state.trapped.put(UUID.fromString(entry.getKey()), entry.getValue());
				} catch (IllegalArgumentException ignored) {
				}
			}
			return state;
		}
	}
}
