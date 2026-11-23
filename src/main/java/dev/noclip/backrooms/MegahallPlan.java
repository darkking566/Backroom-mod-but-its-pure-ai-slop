package dev.noclip.backrooms;

import net.minecraft.core.Direction;

/**
 * Spatial layout for Level 0's {@link Maze.Zone#MEGATHALL} 2x2-sector macro-zones (16x16 cells = 112x112 blocks).
 *
 * Subdivides the interior into varied multi-cell hall bays (21x21, 28x21, 28x28 blocks) with soaring
 * ceilings (16 to 50 blocks high fading into fog), towering 2x2 structural columns, high coffered
 * ceiling beams, and accessible sunken plazas / raised Brutalist podiums connected by wide stairs.
 */
final class MegahallPlan {
	static final int MACRO_CELLS = Maze.SECTOR * 2; // 16 cells = 112 blocks
	private static final int MEGA_SALT = 61;

	enum BayFloor {
		FLAT,
		SUNKEN_PLAZA,
		RAISED_PODIUM
	}

	private MegahallPlan() {
	}

	static boolean isRimCell(int cx, int cz) {
		int mcx = Math.floorMod(cx, MACRO_CELLS);
		int mcz = Math.floorMod(cz, MACRO_CELLS);
		return mcx == 0 || mcx == MACRO_CELLS - 1 || mcz == 0 || mcz == MACRO_CELLS - 1;
	}

	static int cellCeilingY(long seed, int cx, int cz) {
		if (isRimCell(cx, cz)) return Level0ChunkGenerator.CEILING_Y + 4; // 8-block transitional rim
		int baseCx = Math.floorDiv(cx, MACRO_CELLS) * MACRO_CELLS;
		int baseCz = Math.floorDiv(cz, MACRO_CELLS) * MACRO_CELLS;
		int bx = bayIndex(cx - baseCx), bz = bayIndex(cz - baseCz);
		int roll = Math.floorMod(hash(seed, baseCx + bx, baseCz + bz, MEGA_SALT), 4);
		return switch (roll) {
			case 0 -> 48; // 16 blocks tall
			case 1 -> 58; // 26 blocks tall
			case 2 -> 70; // 38 blocks tall
			default -> 82; // 50 blocks tall (vanishes into fog)
		};
	}

	static int walkableFloorY(long seed, int x, int z) {
		int cx = Math.floorDiv(x, Maze.CELL), cz = Math.floorDiv(z, Maze.CELL);
		if (isRimCell(cx, cz)) return Level0ChunkGenerator.FLOOR_Y;
		int baseCx = Math.floorDiv(cx, MACRO_CELLS) * MACRO_CELLS;
		int baseCz = Math.floorDiv(cz, MACRO_CELLS) * MACRO_CELLS;
		int mcx = cx - baseCx, mcz = cz - baseCz;
		int bx = bayIndex(mcx), bz = bayIndex(mcz);
		BayFloor floor = bayFloor(seed, baseCx, baseCz, bx, bz);
		if (floor == BayFloor.FLAT) return Level0ChunkGenerator.FLOOR_Y;

		// Only the center cell of a 3x3 or 4x4 bay becomes a sunken/raised platform; surrounding bay cells stay y=32.
		int centerMcx = bayCenter(bx), centerMcz = bayCenter(bz);
		int ix = Math.floorMod(x, Maze.CELL), iz = Math.floorMod(z, Maze.CELL);
		int delta = floor == BayFloor.SUNKEN_PLAZA ? -4 : 4;

		if (mcx == centerMcx && mcz == centerMcz) {
			if (ix == 0 || ix == Maze.CELL - 1 || iz == 0 || iz == Maze.CELL - 1) {
				return Level0ChunkGenerator.FLOOR_Y;
			}
			return floorYWithRamp(floor, ix, iz);
		}
		return Level0ChunkGenerator.FLOOR_Y;
	}

	private static int floorYWithRamp(BayFloor floor, int ix, int iz) {
		boolean onPlatform = iz >= 2 && iz <= 4 && ix >= 1 && ix <= 5;
		if (!onPlatform) return Level0ChunkGenerator.FLOOR_Y;
		if (floor == BayFloor.SUNKEN_PLAZA) {
			if (ix <= 4) return Level0ChunkGenerator.FLOOR_Y - (ix - 1);
			return Level0ChunkGenerator.FLOOR_Y - 4;
		} else {
			if (ix <= 4) return Level0ChunkGenerator.FLOOR_Y + ix;
			return Level0ChunkGenerator.FLOOR_Y + 4;
		}
	}

	/** Stair facing on the center platform ramp (ix in 1..4, iz in 2..4), or null if flat. */
	static Direction stairFacingAt(long seed, int x, int z) {
		int cx = Math.floorDiv(x, Maze.CELL), cz = Math.floorDiv(z, Maze.CELL);
		if (isRimCell(cx, cz)) return null;
		int baseCx = Math.floorDiv(cx, MACRO_CELLS) * MACRO_CELLS;
		int baseCz = Math.floorDiv(cz, MACRO_CELLS) * MACRO_CELLS;
		int mcx = cx - baseCx, mcz = cz - baseCz;
		int bx = bayIndex(mcx), bz = bayIndex(mcz);
		if (mcx != bayCenter(bx) || mcz != bayCenter(bz)) return null;

		BayFloor floor = bayFloor(seed, baseCx, baseCz, bx, bz);
		if (floor == BayFloor.FLAT) return null;
		int ix = Math.floorMod(x, Maze.CELL), iz = Math.floorMod(z, Maze.CELL);
		if (iz < 2 || iz > 4 || ix < 1 || ix > 4) return null;
		return floor == BayFloor.SUNKEN_PLAZA ? Direction.WEST : Direction.EAST;
	}

	static boolean isWallAt(long seed, int x, int z, boolean topLayer) {
		int cx = Math.floorDiv(x, Maze.CELL), cz = Math.floorDiv(z, Maze.CELL);
		int ix = Math.floorMod(x, Maze.CELL), iz = Math.floorMod(z, Maze.CELL);
		int baseCx = Math.floorDiv(cx, MACRO_CELLS) * MACRO_CELLS;
		int baseCz = Math.floorDiv(cz, MACRO_CELLS) * MACRO_CELLS;
		int mcx = cx - baseCx, mcz = cz - baseCz;

		if (isRimCell(cx, cz)) {
			if (ix != 0 && iz != 0) return false;
			if (iz != 0) {
				if (mcx == 0) return Math.floorMod(hash(seed, cx, cz, MEGA_SALT + 1), 3) == 0 && (iz < 2 || iz > 4);
				if ((mcx == 1 || mcx == MACRO_CELLS - 1) && mcz > 0 && mcz < MACRO_CELLS - 1) {
					return Math.floorMod(hash(seed, cx, cz, MEGA_SALT + 2), 2) == 0;
				}
				if (mcz == MACRO_CELLS - 1 && mcx > 1 && mcx < MACRO_CELLS - 1) {
					return Math.floorMod(hash(seed, cx, cz, MEGA_SALT + 2), 2) == 0;
				}
				return Math.floorMod(hash(seed, cx, cz, MEGA_SALT + 2), 4) == 0 && (iz < 2 || iz > 4);
			}
			if (ix != 0) {
				if (mcz == 0) return Math.floorMod(hash(seed, cx, cz, MEGA_SALT + 3), 3) == 0 && (ix < 2 || ix > 4);
				return Math.floorMod(hash(seed, cx, cz, MEGA_SALT + 4), 4) == 0 && (ix < 2 || ix > 4);
			}
			return true;
		}

		int bx = bayIndex(mcx), bz = bayIndex(mcz);
		if (ix != 0 && iz != 0) {
			// Never put columns on the central stair/platform cell or (3,3) or (1,1).
			if (mcx == bayCenter(bx) && mcz == bayCenter(bz)) return false;
			if (ix == Maze.CENTER && iz == Maze.CENTER) return false;
			// Monumental 2x2 structural columns at (4..5, 4..5) spaced across large bays.
			if (ix >= 4 && ix <= 5 && iz >= 4 && iz <= 5) {
				return ((mcx + mcz) & 1) == 0
						&& Math.floorMod(hash(seed, cx, cz, MEGA_SALT + 5), 3) != 0;
			}
			return false;
		}

		// Bay-to-bay edges: mostly huge open spans with occasional wide monumental portals.
		if (iz != 0 && mcx != bayStart(bx)) return false;
		if (ix != 0 && mcz != bayStart(bz)) return false;
		if (ix == 0 && iz == 0) {
			return mcx == bayStart(bx) && mcz == bayStart(bz);
		}
		int offset = iz != 0 ? iz : ix;
		int roll = Math.floorMod(hash(seed, cx, cz, MEGA_SALT + 6), 10);
		if (roll < 7) return false; // 70% wide open span between bays
		return offset == 1 || offset == Maze.CELL - 1;
	}

	static boolean isHighBeam(long seed, int x, int y, int z, int ceilY) {
		if (y < ceilY - 2 || y >= ceilY) return false;
		int ix = Math.floorMod(x, Maze.CELL), iz = Math.floorMod(z, Maze.CELL);
		return ix == 0 || iz == 0;
	}

	static boolean isLamp(long seed, int x, int z) {
		int cx = Math.floorDiv(x, Maze.CELL), cz = Math.floorDiv(z, Maze.CELL);
		if (isRimCell(cx, cz)) return Maze.isLamp(x, z);
		int ix = Math.floorMod(x, Maze.CELL), iz = Math.floorMod(z, Maze.CELL);
		return ix >= 2 && ix <= 4 && iz >= 2 && iz <= 4;
	}

	private static BayFloor bayFloor(long seed, int baseCx, int baseCz, int bx, int bz) {
		int roll = Math.floorMod(hash(seed, baseCx + bx, baseCz + bz, MEGA_SALT + 7), 10);
		if (roll < 2) return BayFloor.SUNKEN_PLAZA;
		if (roll == 2) return BayFloor.RAISED_PODIUM;
		return BayFloor.FLAT;
	}

	// Bays inside 1..14: [1..3] (0), [4..7] (1), [8..10] (2), [11..14] (3) -> 3x3, 4x3, 4x4 halls!
	private static int bayIndex(int mCoord) {
		if (mCoord <= 3) return 0;
		if (mCoord <= 7) return 1;
		if (mCoord <= 10) return 2;
		return 3;
	}

	private static int bayStart(int bayIdx) {
		return switch (bayIdx) {
			case 0 -> 1;
			case 1 -> 4;
			case 2 -> 8;
			default -> 11;
		};
	}

	private static int bayCenter(int bayIdx) {
		return switch (bayIdx) {
			case 0 -> 2;
			case 1 -> 5;
			case 2 -> 9;
			default -> 12;
		};
	}

	private static long hash(long seed, int x, int z, int salt) {
		long h = mix(seed + salt);
		h = mix(h ^ x);
		return mix(h ^ z);
	}

	private static long mix(long v) {
		v += 0x9E3779B97F4A7C15L;
		v = (v ^ (v >>> 30)) * 0xBF58476D1CE4E5B9L;
		v = (v ^ (v >>> 27)) * 0x94D049BB133111EBL;
		return v ^ (v >>> 31);
	}
}
