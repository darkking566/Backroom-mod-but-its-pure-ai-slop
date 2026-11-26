package dev.noclip.backrooms.level0;

import net.minecraft.core.Direction;

/**
 * Deterministic multi-depth terrain, terraced staircases, bridges, and sheer abyssal cliffs
 * for Level 0's {@link Maze.Zone#CHASM} 1x1-sector macro-zones (6x6 cells = 60x60 blocks at CELL=10).
 *
 * Each 1x1 cell pad (10x10 blocks) holds a quantized floor tier (y = 32 down to y = 8 in 4-block steps)
 * or plunges straight to an abyss floor at y = -31. Every walkable pad routes monotonically outward
 * toward the macro-zone rim (y = 32) along a parent tree of <= 2 tier drops per pad, guaranteeing
 * 100% stair-connected reachability while providing wide 4-block staircases and flat deep outpost pads.
 */
final class ChasmPlan {
	static final int MACRO_CELLS = Maze.SECTOR; // 6 cells = 60 blocks
	static final int PAD_CELLS = 1;
	static final int PADS = MACRO_CELLS / PAD_CELLS; // 6 pads per axis (0..5)
	static final int STEP_HEIGHT = 4;
	static final int ABYSS_TIER = -1;
	static final int ABYSS_FLOOR_Y = -31;

	private static final int CHASM_SALT = 31;

	private ChasmPlan() {
	}

	/** Standing floor Y at exact block (x, z) inside a CHASM zone, or Integer.MIN_VALUE if abyss. */
	static int walkableFloorY(long seed, int x, int z) {
		int cx = Math.floorDiv(x, Maze.CELL), cz = Math.floorDiv(z, Maze.CELL);
		int ix = Math.floorMod(x, Maze.CELL), iz = Math.floorMod(z, Maze.CELL);
		return floorYAt(seed, cx, cz, ix, iz);
	}

	static boolean isAbyssBlock(long seed, int x, int z) {
		return walkableFloorY(seed, x, z) == Integer.MIN_VALUE;
	}

	/** Whether cell (cx, cz) is a flat tier-0 cell suitable for standard Level 0 features/exits. */
	static boolean isFlatRimCell(long seed, int cx, int cz) {
		int baseCx = Math.floorDiv(cx, MACRO_CELLS) * MACRO_CELLS;
		int baseCz = Math.floorDiv(cz, MACRO_CELLS) * MACRO_CELLS;
		int px = cx - baseCx;
		int pz = cz - baseCz;
		return padTier(seed, baseCx, baseCz, px, pz) == 0;
	}

	/** Floor height at (cx, cz, ix, iz), or Integer.MIN_VALUE if open abyss void. */
	static int floorYAt(long seed, int cx, int cz, int ix, int iz) {
		int baseCx = Math.floorDiv(cx, MACRO_CELLS) * MACRO_CELLS;
		int baseCz = Math.floorDiv(cz, MACRO_CELLS) * MACRO_CELLS;
		int px = cx - baseCx, pz = cz - baseCz;

		if (ix == 0) {
			return westEdgeFloorY(seed, baseCx, baseCz, px, pz, iz);
		}
		if (iz == 0) {
			return northEdgeFloorY(seed, baseCx, baseCz, px, pz, ix);
		}
		return padInteriorFloorY(seed, baseCx, baseCz, px, pz, ix, iz);
	}

	private static int westEdgeFloorY(long seed, int baseCx, int baseCz, int px, int pz, int iz) {
		if (px == 0) return Level0ChunkGenerator.FLOOR_Y;
		int eastBase = padInteriorFloorY(seed, baseCx, baseCz, px, pz, 1, iz);
		if (eastBase == Integer.MIN_VALUE) return Integer.MIN_VALUE;
		if (isParentLink(px, pz, px - 1, pz)) {
			int westBase = padInteriorFloorY(seed, baseCx, baseCz, px - 1, pz, Maze.CELL - 1, iz);
			if (westBase != Integer.MIN_VALUE) return Math.max(westBase, eastBase);
		}
		return eastBase;
	}

	private static int northEdgeFloorY(long seed, int baseCx, int baseCz, int px, int pz, int ix) {
		if (pz == 0) return Level0ChunkGenerator.FLOOR_Y;
		int southBase = padInteriorFloorY(seed, baseCx, baseCz, px, pz, ix, 1);
		if (southBase == Integer.MIN_VALUE) return Integer.MIN_VALUE;
		if (isParentLink(px, pz, px, pz - 1)) {
			int northBase = padInteriorFloorY(seed, baseCx, baseCz, px, pz - 1, ix, Maze.CELL - 1);
			if (northBase != Integer.MIN_VALUE) return Math.max(northBase, southBase);
		}
		return southBase;
	}

	private static int padInteriorFloorY(long seed, int baseCx, int baseCz, int px, int pz, int ix, int iz) {
		int tier = padTier(seed, baseCx, baseCz, px, pz);
		if (tier == ABYSS_TIER) {
			if (!abyssHasBridge(seed, baseCx, baseCz, px, pz)) return Integer.MIN_VALUE;
			int dir = parentDir(px, pz);
			int bridgeY = tierFloorY(parentTier(seed, baseCx, baseCz, px, pz));
			int iv = canonicalIv(dir, ix, iz);
			return (iv >= 3 && iv <= 6) ? bridgeY : Integer.MIN_VALUE;
		}
		if (tier == 0) return Level0ChunkGenerator.FLOOR_Y;

		int dir = parentDir(px, pz);
		int pTier = parentTier(seed, baseCx, baseCz, px, pz);
		int delta = tier - pTier;
		if (delta <= 0) return tierFloorY(tier);

		int iu = canonicalIu(dir, ix, iz);
		int iv = canonicalIv(dir, ix, iz);
		int steps = delta * STEP_HEIGHT; // 4 or 8 total drop: steps 1..(steps-1) descend, iu >= steps is flat landing
		int highY = tierFloorY(pTier);
		if (iv < 3 || iv > 6) return tierFloorY(tier);
		if (iu == 0) return highY;
		if (iu < steps) return highY - iu;
		return tierFloorY(tier);
	}

	/** Stair direction for (x, z) if this block is a descending stair step, or null if flat/abyss. */
	static Direction stairFacingAt(long seed, int x, int z) {
		int cx = Math.floorDiv(x, Maze.CELL), cz = Math.floorDiv(z, Maze.CELL);
		int ix = Math.floorMod(x, Maze.CELL), iz = Math.floorMod(z, Maze.CELL);
		if (ix == 0 || iz == 0) return null;

		int baseCx = Math.floorDiv(cx, MACRO_CELLS) * MACRO_CELLS;
		int baseCz = Math.floorDiv(cz, MACRO_CELLS) * MACRO_CELLS;
		int px = cx - baseCx, pz = cz - baseCz;
		int tier = padTier(seed, baseCx, baseCz, px, pz);
		if (tier <= 0) return null;

		int pTier = parentTier(seed, baseCx, baseCz, px, pz);
		int delta = tier - pTier;
		if (delta <= 0) return null;

		int dir = parentDir(px, pz);
		int iu = canonicalIu(dir, ix, iz);
		int iv = canonicalIv(dir, ix, iz);
		int steps = delta * STEP_HEIGHT;
		if (iu < 1 || iu >= steps || iv < 3 || iv > 6) return null;
		return switch (dir) {
			case 0 -> Direction.WEST;
			case 1 -> Direction.EAST;
			case 2 -> Direction.NORTH;
			default -> Direction.SOUTH;
		};
	}

	/** Ceiling Y for a CHASM cell: either a buried sub-level room roof or a soaring cavern ceiling. */
	static int cellCeilingY(long seed, int cx, int cz) {
		int baseCx = Math.floorDiv(cx, MACRO_CELLS) * MACRO_CELLS;
		int baseCz = Math.floorDiv(cz, MACRO_CELLS) * MACRO_CELLS;
		int px = cx - baseCx, pz = cz - baseCz;
		int tier = padTier(seed, baseCx, baseCz, px, pz);

		int cavernY = 60 + Math.floorMod(hash(seed, baseCx + px, baseCz + pz, CHASM_SALT + 4), 16);
		if (tier == ABYSS_TIER || tier == 0) {
			return tier == 0 && !touchesDeepDrop(seed, baseCx, baseCz, px, pz)
					? Level0ChunkGenerator.CEILING_Y
					: cavernY;
		}
		if (!touchesAbyss(seed, baseCx, baseCz, px, pz) && !isStairPad(seed, baseCx, baseCz, px, pz)) {
			long h = hash(seed, baseCx + px, baseCz + pz, CHASM_SALT + 5);
			if (Math.floorMod(h, 2) == 0) {
				return tierFloorY(tier) + 6 + Math.floorMod(h >>> 4, 3);
			}
		}
		return cavernY;
	}

	static boolean isWallAt(long seed, int x, int z, boolean topLayer) {
		int cx = Math.floorDiv(x, Maze.CELL), cz = Math.floorDiv(z, Maze.CELL);
		int ix = Math.floorMod(x, Maze.CELL), iz = Math.floorMod(z, Maze.CELL);
		int baseCx = Math.floorDiv(cx, MACRO_CELLS) * MACRO_CELLS;
		int baseCz = Math.floorDiv(cz, MACRO_CELLS) * MACRO_CELLS;
		int px = cx - baseCx, pz = cz - baseCz;

		if (ix != 0 && iz != 0) {
			return isInteriorWall(seed, baseCx, baseCz, px, pz, ix, iz);
		}
		if (iz != 0) {
			return isWestBoundaryWall(seed, baseCx, baseCz, px, pz, iz);
		}
		if (ix != 0) {
			return isNorthBoundaryWall(seed, baseCx, baseCz, px, pz, ix);
		}
		int tier = padTier(seed, baseCx, baseCz, px, pz);
		if (tier == ABYSS_TIER) return false;
		return isWestBoundaryWall(seed, baseCx, baseCz, px, pz, 1)
				|| isNorthBoundaryWall(seed, baseCx, baseCz, px, pz, 1);
	}

	private static boolean isInteriorWall(long seed, int baseCx, int baseCz, int px, int pz, int ix, int iz) {
		int tier = padTier(seed, baseCx, baseCz, px, pz);
		if (tier == ABYSS_TIER || tier == 0) return false;

		int pTier = parentTier(seed, baseCx, baseCz, px, pz);
		int delta = tier - pTier;
		if (delta > 0) {
			int dir = parentDir(px, pz);
			int iu = canonicalIu(dir, ix, iz);
			int iv = canonicalIv(dir, ix, iz);
			int steps = delta * STEP_HEIGHT;
			if (iu < steps && (iv <= 2 || iv >= 7)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isWestBoundaryWall(long seed, int baseCx, int baseCz, int px, int pz, int iz) {
		if (px == 0) {
			return Math.floorMod(hash(seed, baseCx, baseCz + pz, CHASM_SALT + 7), 3) == 0 && (iz < 3 || iz > 6);
		}
		int tierE = padTier(seed, baseCx, baseCz, px, pz);
		int tierW = padTier(seed, baseCx, baseCz, px - 1, pz);
		if (tierE == ABYSS_TIER && tierW == ABYSS_TIER) return false;
		if (tierE == 0 && tierW == 0 && (pz == 0 || pz == PADS - 1)) {
			// At most one solid west wall on the north/south rim (at px == 3) so neither side can ever be pinched off.
			if (px == 3) return Math.floorMod(hash(seed, baseCx + px, baseCz + pz, CHASM_SALT + 10), 2) == 0;
			return false;
		}
		return isPadBoundaryWall(seed, baseCx, baseCz, px - 1, pz, px, pz, iz, tierE != ABYSS_TIER);
	}

	private static boolean isNorthBoundaryWall(long seed, int baseCx, int baseCz, int px, int pz, int ix) {
		if (pz == 0) {
			return Math.floorMod(hash(seed, baseCx + px, baseCz, CHASM_SALT + 8), 3) == 0 && (ix < 3 || ix > 6);
		}
		int tierS = padTier(seed, baseCx, baseCz, px, pz);
		int tierN = padTier(seed, baseCx, baseCz, px, pz - 1);
		if (tierS == ABYSS_TIER && tierN == ABYSS_TIER) return false;
		return isPadBoundaryWall(seed, baseCx, baseCz, px, pz - 1, px, pz, ix, tierS != ABYSS_TIER);
	}

	private static boolean isPadBoundaryWall(long seed, int baseCx, int baseCz,
			int pxA, int pzA, int pxB, int pzB, int offset, boolean edgeHasSolidGround) {
		int tierA = padTier(seed, baseCx, baseCz, pxA, pzA);
		int tierB = padTier(seed, baseCx, baseCz, pxB, pzB);

		if (isParentLink(pxA, pzA, pxB, pzB) || isParentLink(pxB, pzB, pxA, pzA)) {
			return offset < 3 || offset > 6;
		}

		if (tierA == ABYSS_TIER || tierB == ABYSS_TIER || Math.abs(tierA - tierB) >= 2) {
			return edgeHasSolidGround && (offset == 1 || offset == Maze.CELL - 1);
		}

		if (tierA == tierB
				&& !isStairPad(seed, baseCx, baseCz, pxA, pzA)
				&& !isStairPad(seed, baseCx, baseCz, pxB, pzB)) {
			int roll = Math.floorMod(hash(seed, baseCx + pxA + pxB, baseCz + pzA + pzB, CHASM_SALT + 9), 10);
			if (roll < 5) return false;
			return offset < 3 || offset > 6;
		}
		return edgeHasSolidGround && (offset == 1 || offset == Maze.CELL - 1);
	}

	private static boolean isParentLink(int childPx, int childPz, int candParentPx, int candParentPz) {
		if (childPx <= 0 || childPx >= PADS - 1 || childPz <= 0 || childPz >= PADS - 1) return false;
		int dir = parentDir(childPx, childPz);
		int ppx = childPx + dirDx(dir);
		int ppz = childPz + dirDz(dir);
		return ppx == candParentPx && ppz == candParentPz;
	}

	static boolean isStairPad(long seed, int baseCx, int baseCz, int px, int pz) {
		int tier = padTier(seed, baseCx, baseCz, px, pz);
		if (tier <= 0) return false;
		return tier > parentTier(seed, baseCx, baseCz, px, pz);
	}

	static int tierFloorY(int tier) {
		return tier == ABYSS_TIER ? ABYSS_FLOOR_Y : Level0ChunkGenerator.FLOOR_Y - tier * STEP_HEIGHT;
	}

	/**
	 * Guarantees every 6x6 CHASM sector has:
	 * - Outer ring (d=0): flat rim at y=32 (tier 0)
	 * - Mid ring (d=1): descends 2 tiers (y=24) or 1..2 tiers, with one deep branch reaching tier 4 (y=16)
	 * - Inner 2x2 core (d=2): guarantees at least one flat deep outpost pad (delta=0 at tier >= 2, no stairs)
	 *   and at least one sheer abyss drop to y=-31!
	 */
	static int padTier(long seed, int baseCx, int baseCz, int px, int pz) {
		if (px <= 0 || px >= PADS - 1 || pz <= 0 || pz >= PADS - 1) return 0;
		// Guarantee pad (2, 2) is always a flat deep outpost pad (tier >= 2, delta == 0) and (3, 3) is sheer abyss.
		if (px == 3 && pz == 3) return ABYSS_TIER;
		if (px == 2 && pz == 2) {
			return Math.max(2, parentTier(seed, baseCx, baseCz, px, pz));
		}
		if (isRawAbyss(seed, baseCx, baseCz, px, pz)) return ABYSS_TIER;
		int pTier = parentTier(seed, baseCx, baseCz, px, pz);
		long h = hash(seed, baseCx + px, baseCz + pz, CHASM_SALT);
		int d = ringDistance(px, pz);
		if (d == 1) {
			// Parent of (2,2) is (1,2): guarantee it drops by 2 tiers (tier 2, or tier 4? tier 2 -> y=24, deep enough for outpost).
			if (px == 1 && pz == 2) return 2;
			int delta = 1 + Math.floorMod(h, 2); // 1 or 2
			return Math.min(5, pTier + delta);
		}
		// d == 2 (other non-outpost, non-guaranteed-abyss inner pads): drop further down to tier 4..5 (y=16..12)
		int delta = 1 + Math.floorMod(h, 2);
		return Math.min(5, pTier + delta);
	}

	private static int parentTier(long seed, int baseCx, int baseCz, int px, int pz) {
		int dir = parentDir(px, pz);
		int ppx = px + dirDx(dir);
		int ppz = pz + dirDz(dir);
		int t = padTier(seed, baseCx, baseCz, ppx, ppz);
		if (t == ABYSS_TIER) {
			return parentTier(seed, baseCx, baseCz, ppx, ppz);
		}
		return t;
	}

	private static boolean isRawAbyss(long seed, int baseCx, int baseCz, int px, int pz) {
		int d = ringDistance(px, pz);
		if (d <= 1) return false;
		if (px == 2 && pz == 2) return false;
		if (px == 3 && pz == 3) return true;
		long h = hash(seed, baseCx + px, baseCz + pz, CHASM_SALT + 1);
		return Math.floorMod(h, 10) < 4;
	}

	private static boolean abyssHasBridge(long seed, int baseCx, int baseCz, int px, int pz) {
		int d = ringDistance(px, pz);
		if (d >= 2) return false;
		int dir = parentDir(px, pz);
		int childPx = px - dirDx(dir);
		int childPz = pz - dirDz(dir);
		if (childPx <= 0 || childPx >= PADS - 1 || childPz <= 0 || childPz >= PADS - 1) return false;
		if (ringDistance(childPx, childPz) <= d) return false;
		return padTier(seed, baseCx, baseCz, childPx, childPz) != ABYSS_TIER;
	}

	private static boolean touchesAbyss(long seed, int baseCx, int baseCz, int px, int pz) {
		return padTier(seed, baseCx, baseCz, px - 1, pz) == ABYSS_TIER
				|| padTier(seed, baseCx, baseCz, px + 1, pz) == ABYSS_TIER
				|| padTier(seed, baseCx, baseCz, px, pz - 1) == ABYSS_TIER
				|| padTier(seed, baseCx, baseCz, px, pz + 1) == ABYSS_TIER;
	}

	private static boolean touchesDeepDrop(long seed, int baseCx, int baseCz, int px, int pz) {
		int self = padTier(seed, baseCx, baseCz, px, pz);
		if (touchesAbyss(seed, baseCx, baseCz, px, pz)) return true;
		return Math.abs(padTier(seed, baseCx, baseCz, px - 1, pz) - self) >= 2
				|| Math.abs(padTier(seed, baseCx, baseCz, px + 1, pz) - self) >= 2
				|| Math.abs(padTier(seed, baseCx, baseCz, px, pz - 1) - self) >= 2
				|| Math.abs(padTier(seed, baseCx, baseCz, px, pz + 1) - self) >= 2;
	}

	private static int ringDistance(int px, int pz) {
		return Math.min(Math.min(px, PADS - 1 - px), Math.min(pz, PADS - 1 - pz));
	}

	private static int parentDir(int px, int pz) {
		int d = ringDistance(px, pz);
		if (px == d) return 0; // WEST
		if (px == PADS - 1 - d) return 1; // EAST
		if (pz == d) return 2; // NORTH
		return 3; // SOUTH
	}

	private static int dirDx(int dir) {
		return switch (dir) {
			case 0 -> -1;
			case 1 -> 1;
			default -> 0;
		};
	}

	private static int dirDz(int dir) {
		return switch (dir) {
			case 2 -> -1;
			case 3 -> 1;
			default -> 0;
		};
	}

	private static int canonicalIu(int dir, int ix, int iz) {
		return switch (dir) {
			case 0 -> ix;
			case 1 -> Maze.CELL - ix;
			case 2 -> iz;
			default -> Maze.CELL - iz;
		};
	}

	private static int canonicalIv(int dir, int ix, int iz) {
		return switch (dir) {
			case 0, 1 -> iz;
			default -> ix;
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
