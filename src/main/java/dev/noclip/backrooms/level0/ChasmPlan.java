package dev.noclip.backrooms.level0;

import net.minecraft.core.Direction;

/**
 * Deterministic multi-depth terrain, terraced staircases, bridges, and sheer abyssal cliffs
 * for Level 0's {@link Maze.Zone#CHASM} 2x2-sector macro-zones (16x16 cells = 112x112 blocks).
 *
 * Each 2x2 cell pad (14x14 blocks) holds a quantized floor tier (y = 32 down to y = 4 in 4-block steps)
 * or plunges straight to an abyss floor at y = -31. Every walkable pad routes monotonically outward
 * toward the macro-zone rim (y = 32) along a parent tree of <= 2 tier drops per pad, guaranteeing
 * 100% stair-connected reachability while allowing sheer 30-63 block vertical cliffs beside them.
 */
final class ChasmPlan {
	static final int MACRO_CELLS = Maze.SECTOR * 2; // 16 cells = 112 blocks
	static final int PAD_CELLS = 2;
	static final int PADS = MACRO_CELLS / PAD_CELLS; // 8 pads per axis (0..7)
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
		int px = (cx - baseCx) >> 1;
		int pz = (cz - baseCz) >> 1;
		return padTier(seed, baseCx, baseCz, px, pz) == 0;
	}

	/** Floor height at (cx, cz, ix, iz), or Integer.MIN_VALUE if open abyss void. */
	static int floorYAt(long seed, int cx, int cz, int ix, int iz) {
		int baseCx = Math.floorDiv(cx, MACRO_CELLS) * MACRO_CELLS;
		int baseCz = Math.floorDiv(cz, MACRO_CELLS) * MACRO_CELLS;
		int mcx = cx - baseCx, mcz = cz - baseCz;

		if (ix == 0) {
			return westEdgeFloorY(seed, baseCx, baseCz, mcx, mcz, iz);
		}
		if (iz == 0) {
			return northEdgeFloorY(seed, baseCx, baseCz, mcx, mcz, ix);
		}
		return cellInteriorFloorY(seed, baseCx, baseCz, mcx >> 1, mcz >> 1, mcx & 1, mcz & 1, ix, iz);
	}

	private static int westEdgeFloorY(long seed, int baseCx, int baseCz, int mcx, int mcz, int iz) {
		if (mcx == 0) return Level0ChunkGenerator.FLOOR_Y;
		int px = mcx >> 1, pz = mcz >> 1;
		int prevMcx = mcx - 1;
		int prevPx = prevMcx >> 1;

		int eastBase = cellInteriorFloorY(seed, baseCx, baseCz, px, pz, mcx & 1, mcz & 1, 1, iz);
		if (eastBase == Integer.MIN_VALUE) return Integer.MIN_VALUE;
		if (px != prevPx && isParentLink(px, pz, prevPx, pz)) {
			int westBase = cellInteriorFloorY(seed, baseCx, baseCz, prevPx, pz, prevMcx & 1, mcz & 1, Maze.CELL - 1, iz);
			if (westBase != Integer.MIN_VALUE) return Math.max(westBase, eastBase);
		}
		return eastBase;
	}

	private static int northEdgeFloorY(long seed, int baseCx, int baseCz, int mcx, int mcz, int ix) {
		if (mcz == 0) return Level0ChunkGenerator.FLOOR_Y;
		int px = mcx >> 1, pz = mcz >> 1;
		int prevMcz = mcz - 1;
		int prevPz = prevMcz >> 1;

		int southBase = cellInteriorFloorY(seed, baseCx, baseCz, px, pz, mcx & 1, mcz & 1, ix, 1);
		if (southBase == Integer.MIN_VALUE) return Integer.MIN_VALUE;
		if (pz != prevPz && isParentLink(px, pz, px, prevPz)) {
			int northBase = cellInteriorFloorY(seed, baseCx, baseCz, px, prevPz, mcx & 1, prevMcz & 1, ix, Maze.CELL - 1);
			if (northBase != Integer.MIN_VALUE) return Math.max(northBase, southBase);
		}
		return southBase;
	}

	private static int cellInteriorFloorY(long seed, int baseCx, int baseCz, int px, int pz,
			int subX, int subZ, int ix, int iz) {
		int tier = padTier(seed, baseCx, baseCz, px, pz);
		if (tier == ABYSS_TIER) {
			if (!abyssHasBridge(seed, baseCx, baseCz, px, pz)) return Integer.MIN_VALUE;
			int dir = parentDir(px, pz);
			int bridgeY = tierFloorY(parentTier(seed, baseCx, baseCz, px, pz));
			int vSub = canonicalVSub(dir, subX, subZ);
			int iv = canonicalIv(dir, ix, iz);
			return (vSub == 0 && iv >= 2 && iv <= 4) ? bridgeY : Integer.MIN_VALUE;
		}
		if (tier == 0) return Level0ChunkGenerator.FLOOR_Y;

		int dir = parentDir(px, pz);
		int pTier = parentTier(seed, baseCx, baseCz, px, pz);
		int delta = tier - pTier;
		int uSub = canonicalUSub(dir, subX, subZ);
		int vSub = canonicalVSub(dir, subX, subZ);
		int iu = canonicalIu(dir, ix, iz);
		int iv = canonicalIv(dir, ix, iz);

		if (vSub == 0 && ((uSub == 0 && delta >= 1) || (uSub == 1 && delta >= 2))) {
			int highY = uSub == 0 ? tierFloorY(pTier) : tierFloorY(pTier + 1);
			if (iv < 2 || iv > 4) return highY - STEP_HEIGHT;
			if (iu == 0) return highY;
			if (iu <= 4) return highY - (iu - 1);
			return highY - STEP_HEIGHT;
		}
		return tierFloorY(tier);
	}

	/** Stair direction for (x, z) if this block is a descending stair step, or null if flat/abyss. */
	static Direction stairFacingAt(long seed, int x, int z) {
		int cx = Math.floorDiv(x, Maze.CELL), cz = Math.floorDiv(z, Maze.CELL);
		int ix = Math.floorMod(x, Maze.CELL), iz = Math.floorMod(z, Maze.CELL);
		if (ix == 0 || iz == 0) return null;

		int baseCx = Math.floorDiv(cx, MACRO_CELLS) * MACRO_CELLS;
		int baseCz = Math.floorDiv(cz, MACRO_CELLS) * MACRO_CELLS;
		int mcx = cx - baseCx, mcz = cz - baseCz;
		int px = mcx >> 1, pz = mcz >> 1;
		int tier = padTier(seed, baseCx, baseCz, px, pz);
		if (tier <= 0) return null;

		int dir = parentDir(px, pz);
		int pTier = parentTier(seed, baseCx, baseCz, px, pz);
		int delta = tier - pTier;
		int uSub = canonicalUSub(dir, mcx & 1, mcz & 1);
		int vSub = canonicalVSub(dir, mcx & 1, mcz & 1);
		if (vSub != 0) return null;
		if (!((uSub == 0 && delta >= 1) || (uSub == 1 && delta >= 2))) return null;

		int iu = canonicalIu(dir, ix, iz);
		int iv = canonicalIv(dir, ix, iz);
		if (iu < 1 || iu > 4 || iv < 2 || iv > 4) return null;
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
		int mcx = cx - baseCx, mcz = cz - baseCz;
		int px = mcx >> 1, pz = mcz >> 1;
		int tier = padTier(seed, baseCx, baseCz, px, pz);

		int cavernY = 60 + Math.floorMod(hash(seed, baseCx + px, baseCz + pz, CHASM_SALT + 4), 16);
		if (tier == ABYSS_TIER || tier == 0) {
			return tier == 0 && !touchesDeepDrop(seed, baseCx, baseCz, px, pz)
					? Level0ChunkGenerator.CEILING_Y
					: cavernY;
		}

		// Enclosed buried sub-level rooms on deep terraces away from abyss voids.
		if (!touchesAbyss(seed, baseCx, baseCz, px, pz)
				&& !isStairCell(seed, baseCx, baseCz, px, pz, mcx & 1, mcz & 1)) {
			long h = hash(seed, baseCx + px, baseCz + pz, CHASM_SALT + 5);
			if (Math.floorMod(h, 2) == 0) {
				return tierFloorY(tier) + 4 + Math.floorMod(h >>> 4, 3);
			}
		}
		return cavernY;
	}

	static boolean isWallAt(long seed, int x, int z, boolean topLayer) {
		int cx = Math.floorDiv(x, Maze.CELL), cz = Math.floorDiv(z, Maze.CELL);
		int ix = Math.floorMod(x, Maze.CELL), iz = Math.floorMod(z, Maze.CELL);
		int baseCx = Math.floorDiv(cx, MACRO_CELLS) * MACRO_CELLS;
		int baseCz = Math.floorDiv(cz, MACRO_CELLS) * MACRO_CELLS;
		int mcx = cx - baseCx, mcz = cz - baseCz;
		int px = mcx >> 1, pz = mcz >> 1;

		if (ix != 0 && iz != 0) {
			return isInteriorWall(seed, baseCx, baseCz, px, pz, mcx & 1, mcz & 1, ix, iz);
		}
		if (iz != 0) {
			return isWestBoundaryWall(seed, baseCx, baseCz, mcx, mcz, iz);
		}
		if (ix != 0) {
			return isNorthBoundaryWall(seed, baseCx, baseCz, mcx, mcz, ix);
		}
		int tier = padTier(seed, baseCx, baseCz, px, pz);
		if (tier == ABYSS_TIER) return false;
		return isWestBoundaryWall(seed, baseCx, baseCz, mcx, mcz, 1)
				|| isNorthBoundaryWall(seed, baseCx, baseCz, mcx, mcz, 1);
	}

	private static boolean isInteriorWall(long seed, int baseCx, int baseCz, int px, int pz,
			int subX, int subZ, int ix, int iz) {
		int tier = padTier(seed, baseCx, baseCz, px, pz);
		if (tier == ABYSS_TIER || tier == 0) return false;

		int dir = parentDir(px, pz);
		int pTier = parentTier(seed, baseCx, baseCz, px, pz);
		int delta = tier - pTier;
		int uSub = canonicalUSub(dir, subX, subZ);
		int vSub = canonicalVSub(dir, subX, subZ);
		int iv = canonicalIv(dir, ix, iz);

		// Keep stair flanks walled in along the stepped section (iu 0..4) with no trapped side slivers.
		if (vSub == 0 && ((uSub == 0 && delta >= 1) || (uSub == 1 && delta >= 2))) {
			int iu = canonicalIu(dir, ix, iz);
			return iu <= 4 && (iv == 1 || iv >= 5);
		}
		if (ix == Maze.CENTER && iz == Maze.CENTER) return false;

		if (subX == 1 && subZ == 1 && (ix == 2 || ix == 5) && (iz == 2 || iz == 5)) {
			return Math.floorMod(hash(seed, baseCx + px, baseCz + pz, CHASM_SALT + 6), 3) == 0;
		}
		return false;
	}

	private static boolean isWestBoundaryWall(long seed, int baseCx, int baseCz, int mcx, int mcz, int iz) {
		if (mcx == 0) {
			return Math.floorMod(hash(seed, baseCx, baseCz + mcz, CHASM_SALT + 7), 3) == 0 && (iz < 2 || iz > 4);
		}
		int px = mcx >> 1, pz = mcz >> 1;
		int subX = mcx & 1, subZ = mcz & 1;
		int prevMcx = mcx - 1;
		int prevPx = prevMcx >> 1;

		int tierE = padTier(seed, baseCx, baseCz, px, pz);
		int tierW = padTier(seed, baseCx, baseCz, prevPx, pz);
		if (tierE == ABYSS_TIER && tierW == ABYSS_TIER) return false;

		// Same pad internal vertical edge (subX == 1).
		if (px == prevPx) {
			if (tierE == ABYSS_TIER) return false;
			if (tierE == 0 && subZ == 1 && (pz == PADS - 1 || (pz > 0 && pz < PADS - 1))) {
				// Flat rim cells on subZ=1 can hold solid west walls for Exits while staying connected via North/South.
				return Math.floorMod(hash(seed, baseCx + mcx, baseCz + mcz, CHASM_SALT + 10), 2) == 0;
			}
			int dir = parentDir(px, pz);
			int delta = tierE - parentTier(seed, baseCx, baseCz, px, pz);
			if ((dir == 0 || dir == 1) && subZ == 0 && delta >= 1) {
				return iz < 2 || iz > 4;
			}
			if ((dir == 2 || dir == 3) && delta >= 1) {
				int uSub = canonicalUSub(dir, subX, subZ);
				if (delta == 1 && uSub == 1) return false;
				if (delta == 2 && uSub == 1) return canonicalIu(dir, 0, iz) < 5;
				return true;
			}
			return false;
		}

		// Pad boundary edge between prevPx and px.
		if (tierE > 0 && subX == 0 && (parentDir(px, pz) == 2 || parentDir(px, pz) == 3)
				&& isStairCell(seed, baseCx, baseCz, px, pz, subX, subZ)
				&& canonicalIu(parentDir(px, pz), 0, iz) <= 4) {
			return true;
		}
		return isPadBoundaryWall(seed, baseCx, baseCz, prevPx, pz, px, pz, iz, tierE != ABYSS_TIER);
	}

	private static boolean isNorthBoundaryWall(long seed, int baseCx, int baseCz, int mcx, int mcz, int ix) {
		if (mcz == 0) {
			return Math.floorMod(hash(seed, baseCx + mcx, baseCz, CHASM_SALT + 8), 3) == 0 && (ix < 2 || ix > 4);
		}
		int px = mcx >> 1, pz = mcz >> 1;
		int subX = mcx & 1, subZ = mcz & 1;
		int prevMcz = mcz - 1;
		int prevPz = prevMcz >> 1;

		int tierS = padTier(seed, baseCx, baseCz, px, pz);
		int tierN = padTier(seed, baseCx, baseCz, px, prevPz);
		if (tierS == ABYSS_TIER && tierN == ABYSS_TIER) return false;

		// Same pad internal horizontal edge (subZ == 1).
		if (pz == prevPz) {
			if (tierS == ABYSS_TIER) return false;
			int dir = parentDir(px, pz);
			int delta = tierS - parentTier(seed, baseCx, baseCz, px, pz);
			if ((dir == 2 || dir == 3) && subX == 0 && delta >= 1) {
				return ix < 2 || ix > 4;
			}
			if ((dir == 0 || dir == 1) && delta >= 1) {
				int uSub = canonicalUSub(dir, subX, subZ);
				if (delta == 1 && uSub == 1) return false;
				if (delta == 2 && uSub == 1) return canonicalIu(dir, ix, 0) < 5;
				return true;
			}
			return false;
		}

		// Pad boundary edge between prevPz and pz.
		if (tierS > 0 && subZ == 0 && (parentDir(px, pz) == 0 || parentDir(px, pz) == 1)
				&& isStairCell(seed, baseCx, baseCz, px, pz, subX, subZ)
				&& canonicalIu(parentDir(px, pz), ix, 0) <= 4) {
			return true;
		}
		return isPadBoundaryWall(seed, baseCx, baseCz, px, prevPz, px, pz, ix, tierS != ABYSS_TIER);
	}

	private static boolean isPadBoundaryWall(long seed, int baseCx, int baseCz,
			int pxA, int pzA, int pxB, int pzB, int offset, boolean edgeHasSolidGround) {
		int tierA = padTier(seed, baseCx, baseCz, pxA, pzA);
		int tierB = padTier(seed, baseCx, baseCz, pxB, pzB);

		if (isParentLink(pxA, pzA, pxB, pzB) || isParentLink(pxB, pzB, pxA, pzA)) {
			return offset < 2 || offset > 4;
		}

		if (tierA == ABYSS_TIER || tierB == ABYSS_TIER || Math.abs(tierA - tierB) >= 2) {
			// Only place corner cliff piers where the edge block actually sits on solid ground.
			return edgeHasSolidGround && (offset == 1 || offset == Maze.CELL - 1);
		}

		if (tierA == tierB) {
			int roll = Math.floorMod(hash(seed, baseCx + pxA + pxB, baseCz + pzA + pzB, CHASM_SALT + 9), 10);
			if (roll < 5) return false;
			return offset < 2 || offset > 4;
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

	private static boolean isStairCell(long seed, int baseCx, int baseCz, int px, int pz, int subX, int subZ) {
		int tier = padTier(seed, baseCx, baseCz, px, pz);
		if (tier <= 0) return false;
		int delta = tier - parentTier(seed, baseCx, baseCz, px, pz);
		int dir = parentDir(px, pz);
		int uSub = canonicalUSub(dir, subX, subZ);
		int vSub = canonicalVSub(dir, subX, subZ);
		return vSub == 0 && ((uSub == 0 && delta >= 1) || (uSub == 1 && delta >= 2));
	}

	static int tierFloorY(int tier) {
		return tier == ABYSS_TIER ? ABYSS_FLOOR_Y : Level0ChunkGenerator.FLOOR_Y - tier * STEP_HEIGHT;
	}

	static int padTier(long seed, int baseCx, int baseCz, int px, int pz) {
		if (px <= 0 || px >= PADS - 1 || pz <= 0 || pz >= PADS - 1) return 0;
		if (isRawAbyss(seed, baseCx, baseCz, px, pz)) return ABYSS_TIER;
		int pTier = parentTier(seed, baseCx, baseCz, px, pz);
		long h = hash(seed, baseCx + px, baseCz + pz, CHASM_SALT);
		int delta = 1 + Math.floorMod(h, 2);
		return Math.min(7, pTier + delta);
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
		long h = hash(seed, baseCx + px, baseCz + pz, CHASM_SALT + 1);
		if (d == 3) return Math.floorMod(h, 10) < 5;
		return Math.floorMod(h, 10) < 3;
	}

	private static boolean abyssHasBridge(long seed, int baseCx, int baseCz, int px, int pz) {
		int d = ringDistance(px, pz);
		if (d >= 3) return false;
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

	private static int canonicalUSub(int dir, int subX, int subZ) {
		return switch (dir) {
			case 0 -> subX;
			case 1 -> 1 - subX;
			case 2 -> subZ;
			default -> 1 - subZ;
		};
	}

	private static int canonicalVSub(int dir, int subX, int subZ) {
		return switch (dir) {
			case 0, 1 -> subZ;
			default -> subX;
		};
	}

	private static int canonicalIu(int dir, int ix, int iz) {
		return switch (dir) {
			case 0 -> ix;
			case 1 -> Maze.CELL - 1 - ix;
			case 2 -> iz;
			default -> Maze.CELL - 1 - iz;
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
