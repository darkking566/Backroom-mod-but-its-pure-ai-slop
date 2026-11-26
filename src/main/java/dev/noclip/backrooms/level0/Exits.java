package dev.noclip.backrooms.level0;

import java.util.SplittableRandom;

/**
 * Where the ways out of Level 0 are. Since the game turned to horror (docs/project/1-gameplay/README.md, 2026-11-26) they are
 * very rare: about one region of 10x10 sectors in RARITY has one, so the usual way home is to go deeper and settle at
 * a base.
 *
 * An exit only replaces blocks that are already wall, so it never changes which positions are passable
 * and new chunks still line up with ones generated before exits existed.
 */
final class Exits {
	static final int REGION_CELLS = Maze.SECTOR * 10;
	static final int REGION = REGION_CELLS * Maze.CELL;
	/** About one region in this many has an exit door. */
	static final int RARITY = 8;
	/** How many cells away the lamps start to turn green. */
	static final int HINT_CELLS = 3;

	/** The bottom of a door, in the middle of a solid wall that runs along Z and is open on both sides. */
	record Door(int x, int z) {
	}

	private Exits() {
	}

	static boolean isDoor(long seed, int x, int z) {
		// Cheap rejection first: this runs for every generated wall block.
		if (Math.floorMod(x, Maze.CELL) != 0 || Math.floorMod(z, Maze.CELL) != Maze.CENTER) return false;
		Door door = doorIn(seed, Math.floorDiv(x, REGION), Math.floorDiv(z, REGION));
		return door != null && door.x() == x && door.z() == z;
	}

	static boolean lampPointsToExit(long seed, int x, int z) {
		int cx = Math.floorDiv(x, Maze.CELL), cz = Math.floorDiv(z, Maze.CELL);
		// The hint reaches at most one region over, and one cell further on the door's west side.
		int minX = Math.floorDiv(cx - HINT_CELLS, REGION_CELLS), maxX = Math.floorDiv(cx + HINT_CELLS + 1, REGION_CELLS);
		int minZ = Math.floorDiv(cz - HINT_CELLS, REGION_CELLS), maxZ = Math.floorDiv(cz + HINT_CELLS, REGION_CELLS);

		for (int rx = minX; rx <= maxX; rx++) {
			for (int rz = minZ; rz <= maxZ; rz++) {
				Door door = doorIn(seed, rx, rz);
				if (door == null) continue;
				int east = Math.floorDiv(door.x(), Maze.CELL);
				int dx = Math.min(Math.abs(cx - east), Math.abs(cx - (east - 1)));
				if (dx + Math.abs(cz - Math.floorDiv(door.z(), Maze.CELL)) <= HINT_CELLS) return true;
			}
		}
		return false;
	}

	/** The door of a region, or null if it has none (most do not) or no solid wall to hold one. */
	static Door doorIn(long seed, int rx, int rz) {
		return DOOR_CACHE.get().getOrCompute(seed, rx, rz);
	}

	/** Whether a region is one of the rare ones with an exit door. */
	static boolean hasExit(long seed, int rx, int rz) {
		long h = (seed + 0x5DEECE66DL) ^ rx * 0xBF58476D1CE4E5B9L ^ rz * 0x94D049BB133111EBL;
		h = (h ^ (h >>> 31)) * 0x9E3779B97F4A7C15L;
		return Math.floorMod(h ^ (h >>> 29), RARITY) == 0;
	}

	private static Door computeDoorIn(long seed, int rx, int rz) {
		if (!hasExit(seed, rx, rz)) return null;
		int cells = REGION_CELLS * REGION_CELLS;
		long regionSeed = seed ^ rx * 0x9E3779B97F4A7C15L ^ rz * 0xC2B2AE3D27D4EB4FL;
		int first = new SplittableRandom(regionSeed).nextInt(cells);

		for (int i = 0; i < cells; i++) {
			int cell = (first + i) % cells;
			int cx = rx * REGION_CELLS + cell % REGION_CELLS, cz = rz * REGION_CELLS + cell / REGION_CELLS;
			// None in the first sector, so the first visit involves some searching.
			if (Math.floorDiv(cx, Maze.SECTOR) == 0 && Math.floorDiv(cz, Maze.SECTOR) == 0) continue;
			// Red Rooms have no easy way out: never place an exit door inside or bordering a RED_ROOM.
			if (Maze.zoneAt(seed, cx * Maze.CELL, cz * Maze.CELL) == Maze.Zone.RED_ROOM
					|| Maze.zoneAt(seed, (cx - 1) * Maze.CELL, cz * Maze.CELL) == Maze.Zone.RED_ROOM) continue;
			// Only place exit doors on standard flat FLOOR_Y = 32 cells (never over chasms, stairs, or megahall voids).
			if (!Maze.isStandardFloorCell(seed, cx, cz) || !Maze.isStandardFloorCell(seed, cx - 1, cz)) continue;
			if (ManilaRoomPlan.isProtectedCell(seed, cx, cz) || ManilaRoomPlan.isProtectedCell(seed, cx - 1, cz)) continue;
			if (ZonePoiPlan.isProtectedCell(seed, cx, cz) || ZonePoiPlan.isProtectedCell(seed, cx - 1, cz)) continue;
			if (hasSolidWestWall(seed, cx * Maze.CELL, cz * Maze.CELL)) {
				return new Door(cx * Maze.CELL, cz * Maze.CELL + Maze.CENTER);
			}
		}
		return null;
	}

	/** Whether a cell houses or directly borders an exit door and must never be reshaped by Edge Shift. */
	static boolean isProtectedCell(long seed, int cx, int cz) {
		int rxCenter = Math.floorDiv(cx, REGION_CELLS);
		int rzCenter = Math.floorDiv(cz, REGION_CELLS);
		int lx = Math.floorMod(cx, REGION_CELLS);
		int lz = Math.floorMod(cz, REGION_CELLS);
		int minRx = lx == 0 ? rxCenter - 1 : rxCenter;
		int maxRx = lx == REGION_CELLS - 1 ? rxCenter + 1 : rxCenter;
		int minRz = lz == 0 ? rzCenter - 1 : rzCenter;
		int maxRz = lz == REGION_CELLS - 1 ? rzCenter + 1 : rzCenter;
		for (int rx = minRx; rx <= maxRx; rx++) {
			for (int rz = minRz; rz <= maxRz; rz++) {
				Door door = doorIn(seed, rx, rz);
				if (door == null) continue;
				int doorCx = Math.floorDiv(door.x(), Maze.CELL);
				int doorCz = Math.floorDiv(door.z(), Maze.CELL);
				if (Math.abs(cx - doorCx) <= 1 && Math.abs(cz - doorCz) <= 1) return true;
			}
		}
		return false;
	}

	private static final ThreadLocal<DoorCache> DOOR_CACHE = ThreadLocal.withInitial(DoorCache::new);

	private static final class DoorCache {
		private static final int MASK = 255;
		private final long[] seeds = new long[MASK + 1];
		private final int[] rxs = new int[MASK + 1];
		private final int[] rzs = new int[MASK + 1];
		private final Door[] doors = new Door[MASK + 1];
		private final boolean[] valid = new boolean[MASK + 1];

		Door getOrCompute(long seed, int rx, int rz) {
			int idx = (int) ((seed ^ (rx * 0x9E3779B97F4A7C15L) ^ (rz * 0xC2B2AE3D27D4EB4FL)) >>> 32) & MASK;
			if (valid[idx] && seeds[idx] == seed && rxs[idx] == rx && rzs[idx] == rz) {
				return doors[idx];
			}
			Door computed = computeDoorIn(seed, rx, rz);
			seeds[idx] = seed;
			rxs[idx] = rx;
			rzs[idx] = rz;
			doors[idx] = computed;
			valid[idx] = true;
			return computed;
		}
	}

	private static boolean hasSolidWestWall(long seed, int x, int z) {
		for (int offset = 1; offset < Maze.CELL; offset++) {
			if (!Maze.isWall(seed, x, z + offset)) return false;
		}
		return true;
	}
}
