package dev.noclip.backrooms.level0;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Pure-math Level 0 mission helpers exposed outside the package so {@link dev.noclip.backrooms.core.field.Evidence}
 * and {@link dev.noclip.backrooms.core.story.Missions} can detect zones and compute compass coordinates without
 * loading distant chunks or breaking {@link Maze} encapsulation.
 */
public final class Level0MissionHelper {
	private static final int SEARCH_CELL_RADIUS = 36;

	private Level0MissionHelper() {
	}

	/** Returns the mission scene identifier at {@code pos} in Level 0, or null if in a standard hall. */
	public static String sceneAt(ServerLevel level, BlockPos pos) {
		long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
		int x = pos.getX(), z = pos.getZ();
		if (ManilaRoomPlan.roomAtBlock(seed, x, z) != null) return "manila_room";
		Maze.Zone zone = Maze.zoneAt(seed, x, z);
		return switch (zone) {
			case PITS -> "pitfalls";
			case CHASM -> "chasm_rift";
			case RED_ROOM -> "red_room_altar";
			default -> null;
		};
	}

	/**
	 * Finds the nearest pure-math target center {@code [x, z]} for a Level 0 scene feature
	 * without loading any chunks.
	 */
	public static int[] findNearestFeature(ServerLevel level, int px, int pz, String featureId) {
		long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
		int pcx = Math.floorDiv(px, Maze.CELL);
		int pcz = Math.floorDiv(pz, Maze.CELL);

		if ("manila_room".equals(featureId)) {
			int[] best = null;
			long bestDistSq = Long.MAX_VALUE;
			for (int r = 0; r <= SEARCH_CELL_RADIUS; r++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
						int cx = pcx + dx, cz = pcz + dz;
						ManilaRoomPlan.Room room = ManilaRoomPlan.roomForCell(seed, cx, cz);
						if (room == null) continue;
						int tx = (room.cx0() + 1) * Maze.CELL + 5;
						int tz = (room.cz0() + 1) * Maze.CELL + 5;
						long dSq = distSq(px, pz, tx, tz);
						if (dSq < bestDistSq) {
							bestDistSq = dSq;
							best = new int[]{tx, tz};
						}
					}
				}
				if (best != null) return best;
			}
			return null;
		}

		Maze.Zone targetZone = switch (featureId) {
			case "red_room_altar" -> Maze.Zone.RED_ROOM;
			case "pitfalls" -> Maze.Zone.PITS;
			case "chasm_rift" -> Maze.Zone.CHASM;
			default -> null;
		};
		if (targetZone == null) return null;

		int[] best = null;
		long bestDistSq = Long.MAX_VALUE;
		for (int r = 0; r <= SEARCH_CELL_RADIUS; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
					int cx = pcx + dx, cz = pcz + dz;
					if (Maze.zoneAt(seed, cx * Maze.CELL + 1, cz * Maze.CELL + 1) != targetZone) continue;
					if (targetZone == Maze.Zone.RED_ROOM && !ZonePoiPlan.isRedRoomPoiCell(seed, cx, cz)
							&& r < SEARCH_CELL_RADIUS - 4) {
						continue;
					}
					int tx = cx * Maze.CELL + Maze.CELL / 2;
					int tz = cz * Maze.CELL + Maze.CELL / 2;
					long dSq = distSq(px, pz, tx, tz);
					if (dSq < bestDistSq) {
						bestDistSq = dSq;
						best = new int[]{tx, tz};
					}
				}
			}
			if (best != null) return best;
		}
		return best;
	}

	private static long distSq(int x0, int z0, int x1, int z1) {
		long dx = (long) x1 - x0;
		long dz = (long) z1 - z0;
		return dx * dx + dz * dz;
	}

	/** Verifies inside a bootstrapped server/test that fast column fill matches worldBlockAt bit-for-bit. */
	public static boolean verifyColumnMatchesWorld(long seed, int x, int z) {
		var col = new net.minecraft.world.level.block.state.BlockState[Level0ChunkGenerator.HEIGHT];
		Level0ChunkGenerator.fillColumnStates(seed, x, z, col);
		for (int i = 0; i < Level0ChunkGenerator.HEIGHT; i++) {
			int y = Level0ChunkGenerator.MIN_Y + i;
			if (!col[i].equals(Level0ChunkGenerator.worldBlockAt(seed, x, y, z))) {
				return false;
			}
		}
		return true;
	}
}
