package dev.noclip.backrooms;

import java.util.SplittableRandom;

/**
 * Where the ways out of Level 0 are. There is one in every region of 3x3 sectors.
 *
 * An exit only replaces blocks that are already wall, so it never changes which positions are passable
 * and new chunks still line up with ones generated before exits existed.
 */
final class Exits {
	static final int REGION_CELLS = Maze.SECTOR * 3;
	static final int REGION = REGION_CELLS * Maze.CELL;
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

	/** The door of a region, or null if it has no solid wall to hold one. */
	static Door doorIn(long seed, int rx, int rz) {
		int cells = REGION_CELLS * REGION_CELLS;
		long regionSeed = seed ^ rx * 0x9E3779B97F4A7C15L ^ rz * 0xC2B2AE3D27D4EB4FL;
		int first = new SplittableRandom(regionSeed).nextInt(cells);

		for (int i = 0; i < cells; i++) {
			int cell = (first + i) % cells;
			int cx = rx * REGION_CELLS + cell % REGION_CELLS, cz = rz * REGION_CELLS + cell / REGION_CELLS;
			// None in the first sector, so the first visit involves some searching.
			if (Math.floorDiv(cx, Maze.SECTOR) == 0 && Math.floorDiv(cz, Maze.SECTOR) == 0) continue;
			if (hasSolidWestWall(seed, cx * Maze.CELL, cz * Maze.CELL)) {
				return new Door(cx * Maze.CELL, cz * Maze.CELL + Maze.CENTER);
			}
		}
		return null;
	}

	private static boolean hasSolidWestWall(long seed, int x, int z) {
		for (int offset = 1; offset < Maze.CELL; offset++) {
			if (!Maze.isWall(seed, x, z + offset)) return false;
		}
		return true;
	}
}
