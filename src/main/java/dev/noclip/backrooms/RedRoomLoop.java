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
		int sx = Math.floorDiv(cx, Maze.SECTOR), sz = Math.floorDiv(cz, Maze.SECTOR);
		int lx = Math.floorMod(cx, Maze.SECTOR), lz = Math.floorMod(cz, Maze.SECTOR);

		if (Maze.zoneAt(seed, bx, bz) == Maze.Zone.RED_ROOM) {
			if (isInteriorCell(lx, lz)) {
				state.setTrap(id, sx, sz);
				return;
			}
			if (state.hasTrap(id) && state.trappedSector(id) == ChunkPos.pack(sx, sz)) {
				// Stepped onto the perimeter ring of the trapped Red Room: loop back inside.
				loopPlayerInside(level, player, sx, sz, now);
			}
			return;
		}

		if (!state.hasTrap(id)) return;

		long trapped = state.trappedSector(id);
		int tsx = ChunkPos.getX(trapped), tsz = ChunkPos.getZ(trapped);
		double distOutside = distanceOutsideSector(player.getX(), player.getZ(), tsx, tsz);
		if (distOutside > ADMIN_ESCAPE_DISTANCE) {
			// Teleported out via command or external mechanic: release the trap cleanly.
			state.clearTrap(id);
			LAST_LOOP_TICK.remove(id);
			return;
		}
		loopPlayerInside(level, player, tsx, tsz, now);
	}

	private static boolean isInteriorCell(int lx, int lz) {
		return lx >= 1 && lx <= Maze.SECTOR - 2 && lz >= 1 && lz <= Maze.SECTOR - 2;
	}

	private static double distanceOutsideSector(double px, double pz, int sx, int sz) {
		double minX = sx * SECTOR_BLOCKS;
		double maxX = minX + SECTOR_BLOCKS;
		double minZ = sz * SECTOR_BLOCKS;
		double maxZ = minZ + SECTOR_BLOCKS;
		double cx = Math.clamp(px, minX, maxX);
		double cz = Math.clamp(pz, minZ, maxZ);
		return Math.hypot(px - cx, pz - cz);
	}

	private static void loopPlayerInside(ServerLevel level, ServerPlayer player, int sx, int sz, int now) {
		UUID id = player.getUUID();
		if (now - LAST_LOOP_TICK.getOrDefault(id, -LOOP_COOLDOWN_TICKS) < LOOP_COOLDOWN_TICKS) return;

		Vec3 dest = findLoopDestination(level, player, sx, sz);
		if (dest == null) return;

		LAST_LOOP_TICK.put(id, now);
		player.teleportTo(level, dest.x, player.getY(), dest.z,
				Relative.union(Relative.DELTA, Relative.ROTATION), 0.0F, 0.0F, false);
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 45, 0, true, false, true));
		level.playSound(null, dest.x, player.getY(), dest.z,
				SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.45F, 0.60F);
	}

	private static Vec3 findLoopDestination(ServerLevel level, ServerPlayer player, int sx, int sz) {
		int bx = player.getBlockX(), bz = player.getBlockZ();
		int ix = Math.floorMod(bx, Maze.CELL), iz = Math.floorMod(bz, Maze.CELL);
		double fracX = player.getX() - bx;
		double fracZ = player.getZ() - bz;

		Vec3 look = player.getLookAngle();
		int dirX = Math.abs(look.x) >= Math.abs(look.z) ? (look.x >= 0 ? 1 : -1) : 0;
		int dirZ = dirX == 0 ? (look.z >= 0 ? 1 : -1) : 0;

		int curCx = Math.floorDiv(bx, Maze.CELL);
		int curCz = Math.floorDiv(bz, Maze.CELL);

		Vec3 best = null;
		int bestScore = Integer.MIN_VALUE;

		// Search inner 4x4 cells (lx, lz in 2..5) so the player lands well away from any exit border.
		for (int lx = 2; lx <= Maze.SECTOR - 3; lx++) {
			for (int lz = 2; lz <= Maze.SECTOR - 3; lz++) {
				int cx = sx * Maze.SECTOR + lx;
				int cz = sz * Maze.SECTOR + lz;
				int tx = cx * Maze.CELL + ix;
				int tz = cz * Maze.CELL + iz;
				Vec3 candidate = new Vec3(tx + fracX, player.getY(), tz + fracZ);
				if (!isSafeSpot(level, player, tx, tz, candidate)) continue;

				int score = 0;
				// Prefer a corridor that remains open directly ahead of the player's gaze.
				if (isOpenHeadroom(level, tx + dirX, tz + dirZ) && isOpenHeadroom(level, tx + dirX * 2, tz + dirZ * 2)) {
					score += 100;
				}
				// Prefer wrapping across the room rather than staying near the same corner.
				score += Math.abs(cx - curCx) + Math.abs(cz - curCz);
				if (score > bestScore) {
					bestScore = score;
					best = candidate;
				}
			}
		}

		if (best != null) return best;

		// Fallback to any open inner cell center.
		for (int lx = 2; lx <= Maze.SECTOR - 3; lx++) {
			for (int lz = 2; lz <= Maze.SECTOR - 3; lz++) {
				int tx = (sx * Maze.SECTOR + lx) * Maze.CELL + Maze.CENTER;
				int tz = (sz * Maze.SECTOR + lz) * Maze.CELL + Maze.CENTER;
				Vec3 center = new Vec3(tx + 0.5, Level0ChunkGenerator.FLOOR_Y + 1, tz + 0.5);
				if (isSafeSpot(level, player, tx, tz, center)) return center;
			}
		}
		return null;
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
			int sx = ChunkPos.getX(packed), sz = ChunkPos.getZ(packed);
			sealSectorPerimeter(level, seed, sx, sz, pos);
		}
	}

	private static void sealSectorPerimeter(ServerLevel level, long seed, int sx, int sz,
			BlockPos.MutableBlockPos pos) {
		int baseCx = sx * Maze.SECTOR;
		int baseCz = sz * Maze.SECTOR;

		// West and East perimeter walls of the Red Room sector.
		for (int lz = 0; lz < Maze.SECTOR; lz++) {
			int cz = baseCz + lz;
			sealEdgeIfUnseen(level, seed, baseCx * Maze.CELL, cz * Maze.CELL, true, pos);
			sealEdgeIfUnseen(level, seed, (baseCx + Maze.SECTOR) * Maze.CELL, cz * Maze.CELL, true, pos);
		}
		// North and South perimeter walls of the Red Room sector.
		for (int lx = 0; lx < Maze.SECTOR; lx++) {
			int cx = baseCx + lx;
			sealEdgeIfUnseen(level, seed, cx * Maze.CELL, baseCz * Maze.CELL, false, pos);
			sealEdgeIfUnseen(level, seed, cx * Maze.CELL, (baseCz + Maze.SECTOR) * Maze.CELL, false, pos);
		}
	}

	private static void sealEdgeIfUnseen(ServerLevel level, long seed, int edgeX, int edgeZ, boolean alongZ,
			BlockPos.MutableBlockPos pos) {
		int cx = Math.floorDiv(edgeX, Maze.CELL);
		int cz = Math.floorDiv(edgeZ, Maze.CELL);
		int otherCx = alongZ ? cx - 1 : cx;
		int otherCz = alongZ ? cz : cz - 1;
		if (Maze.zoneAt(seed, cx * Maze.CELL, cz * Maze.CELL) == Maze.Zone.ARCHES
				|| Maze.zoneAt(seed, otherCx * Maze.CELL, otherCz * Maze.CELL) == Maze.Zone.ARCHES
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
