package dev.noclip.backrooms.level0;

import net.minecraft.core.Direction;

/**
 * Spatial layout for Level 0's {@link Maze.Zone#MEGATHALL} 1x1-sector macro-zones (6x6 cells = 60x60 blocks at CELL=10).
 *
 * Subdivides the 40x40-block interior into 2x2 monumental hall bays (20x20 blocks each) with soaring
 * ceilings (18 to 50 blocks high fading into fog), towering 2x2 structural columns, high coffered
 * ceiling beams, and accessible sunken plazas / raised Brutalist podiums connected by wide stairs.
 */
final class MegahallPlan {
	static final int MACRO_CELLS = Maze.SECTOR; // 6 cells = 60 blocks
	private static final int MEGA_SALT = 61;

	enum BayFloor {
		FLAT,
		SUNKEN_PLAZA,
		RAISED_PODIUM
	}

	enum Style {
		BAYS,
		COLONNADE,
		ROOM_GRID,
		TERRACED
	}

	private MegahallPlan() {
	}

	static boolean isRimCell(int cx, int cz) {
		int mcx = Math.floorMod(cx, MACRO_CELLS);
		int mcz = Math.floorMod(cz, MACRO_CELLS);
		return mcx == 0 || mcx == MACRO_CELLS - 1 || mcz == 0 || mcz == MACRO_CELLS - 1;
	}

	static int cellCeilingY(long seed, int cx, int cz) {
		if (isRimCell(cx, cz)) return Level0ChunkGenerator.CEILING_Y + 4; // 9-block transitional rim
		int baseCx = Math.floorDiv(cx, MACRO_CELLS) * MACRO_CELLS;
		int baseCz = Math.floorDiv(cz, MACRO_CELLS) * MACRO_CELLS;
		int bx = bayIndex(cx - baseCx), bz = bayIndex(cz - baseCz);
		int roll = Math.floorMod(hash(seed, baseCx + bx, baseCz + bz, MEGA_SALT), 4);
		return switch (roll) {
			case 0 -> 50; // 18 blocks tall
			case 1 -> 60; // 28 blocks tall
			case 2 -> 72; // 40 blocks tall
			default -> 84; // 52 blocks tall (vanishes into fog)
		};
	}

	static int walkableFloorY(long seed, int x, int z) {
		int cx = Math.floorDiv(x, Maze.CELL), cz = Math.floorDiv(z, Maze.CELL);
		if (isRimCell(cx, cz)) return Level0ChunkGenerator.FLOOR_Y;
		int baseCx = Math.floorDiv(cx, MACRO_CELLS) * MACRO_CELLS;
		int baseCz = Math.floorDiv(cz, MACRO_CELLS) * MACRO_CELLS;
		int mcx = cx - baseCx, mcz = cz - baseCz;
		if (styleAt(seed, baseCx, baseCz) == Style.TERRACED) return terraceFloorY(mcx, mcz);
		int bx = bayIndex(mcx), bz = bayIndex(mcz);
		BayFloor floor = bayFloor(seed, baseCx, baseCz, bx, bz);
		if (floor == BayFloor.FLAT) return Level0ChunkGenerator.FLOOR_Y;

		int centerMcx = bayCenter(bx), centerMcz = bayCenter(bz);
		int ix = Math.floorMod(x, Maze.CELL), iz = Math.floorMod(z, Maze.CELL);
		if (mcx == centerMcx && mcz == centerMcz) {
			if (ix == 0 || ix == Maze.CELL - 1 || iz == 0 || iz == Maze.CELL - 1) {
				return Level0ChunkGenerator.FLOOR_Y;
			}
			return floorYWithRamp(floor, ix, iz);
		}
		return Level0ChunkGenerator.FLOOR_Y;
	}

	private static int terraceFloorY(int mcx, int mcz) {
		int ring = Math.min(Math.min(mcx, mcz), Math.min(MACRO_CELLS - 1 - mcx, MACRO_CELLS - 1 - mcz));
		return Level0ChunkGenerator.FLOOR_Y - Math.min(2, Math.max(0, ring - 1));
	}

	static Style styleAt(long seed, int baseCx, int baseCz) {
		int roll = Math.floorMod(hash(seed, baseCx, baseCz, MEGA_SALT + 8), 10);
		if (roll < 4) return Style.BAYS;
		if (roll < 6) return Style.COLONNADE;
		if (roll < 8) return Style.ROOM_GRID;
		return Style.TERRACED;
	}

	private static int floorYWithRamp(BayFloor floor, int ix, int iz) {
		boolean onPlatform = iz >= 3 && iz <= 6 && ix >= 1 && ix <= 8;
		if (!onPlatform) return Level0ChunkGenerator.FLOOR_Y;
		if (floor == BayFloor.SUNKEN_PLAZA) {
			if (ix <= 4) return Level0ChunkGenerator.FLOOR_Y - (ix - 1);
			return Level0ChunkGenerator.FLOOR_Y - 4;
		} else {
			if (ix <= 4) return Level0ChunkGenerator.FLOOR_Y + ix;
			return Level0ChunkGenerator.FLOOR_Y + 4;
		}
	}

	static Direction stairFacingAt(long seed, int x, int z) {
		int cx = Math.floorDiv(x, Maze.CELL), cz = Math.floorDiv(z, Maze.CELL);
		if (isRimCell(cx, cz)) return null;
		int baseCx = Math.floorDiv(cx, MACRO_CELLS) * MACRO_CELLS;
		int baseCz = Math.floorDiv(cz, MACRO_CELLS) * MACRO_CELLS;
		int mcx = cx - baseCx, mcz = cz - baseCz;
		int bx = bayIndex(mcx), bz = bayIndex(mcz);
		if (mcx != bayCenter(bx) || mcz != bayCenter(bz)) return null;
		if (styleAt(seed, baseCx, baseCz) == Style.TERRACED) return null;

		BayFloor floor = bayFloor(seed, baseCx, baseCz, bx, bz);
		if (floor == BayFloor.FLAT) return null;
		int ix = Math.floorMod(x, Maze.CELL), iz = Math.floorMod(z, Maze.CELL);
		if (iz < 3 || iz > 6 || ix < 1 || ix > 4) return null;
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
				if (mcx == 0) return Math.floorMod(hash(seed, cx, cz, MEGA_SALT + 1), 3) == 0 && (iz < 3 || iz > 6);
				if ((mcx == 1 || mcx == MACRO_CELLS - 1) && mcz > 0 && mcz < MACRO_CELLS - 1) {
					return Math.floorMod(hash(seed, cx, cz, MEGA_SALT + 2), 2) == 0;
				}
				if (mcz == MACRO_CELLS - 1 && mcx == 3) {
					return Math.floorMod(hash(seed, cx, cz, MEGA_SALT + 2), 2) == 0;
				}
				return Math.floorMod(hash(seed, cx, cz, MEGA_SALT + 2), 4) == 0 && (iz < 3 || iz > 6);
			}
			if (ix != 0) {
				if (mcz == 0) return Math.floorMod(hash(seed, cx, cz, MEGA_SALT + 3), 3) == 0 && (ix < 3 || ix > 6);
				return Math.floorMod(hash(seed, cx, cz, MEGA_SALT + 4), 4) == 0 && (ix < 3 || ix > 6);
			}
			return true;
		}

		int bx = bayIndex(mcx), bz = bayIndex(mcz);
		Style style = styleAt(seed, baseCx, baseCz);
		if (style == Style.TERRACED) return false;
		if (style == Style.COLONNADE) {
			boolean center = mcx == bayCenter(bx) && mcz == bayCenter(bz);
			return ix >= 7 && ix <= 8 && iz >= 7 && iz <= 8 && !center;
		}
		if (ix != 0 && iz != 0) {
			if (mcx == bayCenter(bx) && mcz == bayCenter(bz)) return false;
			if (ix == Maze.CENTER && iz == Maze.CENTER) return false;
			// Monumental 2x2 columns at (7..8, 7..8) keeping 5-block wide clear center lanes
			if (ix >= 7 && ix <= 8 && iz >= 7 && iz <= 8) {
				return ((mcx + mcz) & 1) == 0
						&& Math.floorMod(hash(seed, cx, cz, MEGA_SALT + 5), 3) != 0;
			}
			return false;
		}

		if (style == Style.ROOM_GRID) {
			if (ix == 0 && iz != 0 && mcx == bayStart(bx)) return !(mcz == bayCenter(bz) && iz >= 3 && iz <= 6);
			if (iz == 0 && ix != 0 && mcz == bayStart(bz)) return !(mcx == bayCenter(bx) && ix >= 3 && ix <= 6);
			return ix == 0 && iz == 0 && mcx == bayStart(bx) && mcz == bayStart(bz);
		}

		if (iz != 0 && mcx != bayStart(bx)) return false;
		if (ix != 0 && mcz != bayStart(bz)) return false;
		if (ix == 0 && iz == 0) {
			return mcx == bayStart(bx) && mcz == bayStart(bz);
		}
		int offset = iz != 0 ? iz : ix;
		int roll = Math.floorMod(hash(seed, cx, cz, MEGA_SALT + 6), 10);
		if (roll < 7) return false;
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
		return ix >= 4 && ix <= 6 && iz >= 4 && iz <= 6;
	}

	static BayFloor bayFloor(long seed, int baseCx, int baseCz, int bx, int bz) {
		// Guarantee bay (0, 0) is always flat so the Megahall skylight lounge always has a valid floor!
		if (bx == 0 && bz == 0) return BayFloor.FLAT;
		int roll = Math.floorMod(hash(seed, baseCx + bx, baseCz + bz, MEGA_SALT + 7), 10);
		if (roll < 3) return BayFloor.SUNKEN_PLAZA;
		if (roll == 3) return BayFloor.RAISED_PODIUM;
		return BayFloor.FLAT;
	}

	// Interior cells 1..4 split cleanly into 2x2 bays: [1..2] (bay 0) and [3..4] (bay 1).
	private static int bayIndex(int mCoord) {
		return mCoord <= 2 ? 0 : 1;
	}

	private static int bayStart(int bayIdx) {
		return bayIdx == 0 ? 1 : 3;
	}

	static int bayCenter(int bayIdx) {
		return bayIdx == 0 ? 1 : 3;
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
