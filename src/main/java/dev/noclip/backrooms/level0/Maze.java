package dev.noclip.backrooms.level0;

/**
 * The Level 0 floor plan as a pure function of seed and block position, so chunks can be generated in any order.
 *
 * 2026-11-26 Cinematic Overhaul (Plan C):
 * - Cell size scaled up to 10x10 blocks (9x9 interior) with 5-block clear ceiling height (y=33..37).
 * - Corridors & doorways are always 3..5 blocks wide (zero 1-block cramped slits).
 * - Sectors are 6x6 cells (60x60 blocks); every 5x5-sector Stage (300x300 blocks) deterministically
 *   guarantees a full roster of all 10 zones (Classic, Cruciform Pillars, Arches, Cubicles, Corridors,
 *   Pitfalls with escapable lower level, Darkened, Megahall, Chasm, and Red Room) so players encounter
 *   distinct landmarks every 1-2 minutes of walking.
 */
final class Maze {
	static final int CELL = 10;
	static final int CENTER = 5;
	static final int SECTOR = 6;
	static final int STAGE_SECTORS = 5;
	static final int STAGE_CELLS = STAGE_SECTORS * SECTOR; // 30 cells = 300 blocks
	static final int STAGE_BLOCKS = STAGE_CELLS * CELL; // 300 blocks
	static final int LARGE_ZONE_SPAN = 1; // 1x1 sector = 60x60 blocks fits cleanly inside every 5x5 Stage

	private static final int TREE = 1, WEST = 2, NORTH = 3, PILLAR = 4, LAMP = 5, ZONE = 6, FEATURE = 7, WARNING = 8,
			SHIFT_PILLAR = 9, ROOM = 11, CEILING = 12, FLOOR = 13, MERGE = 14, ATRIUM = 15, STAGE = 16;

	/** Deterministic 25-sector guaranteed quota inside every 5x5 Stage (300x300 blocks). */
	private static final Zone[] STAGE_QUOTA = {
			Zone.CLASSIC, Zone.CLASSIC, Zone.CLASSIC, Zone.CLASSIC, Zone.CLASSIC,
			Zone.PILLARS, Zone.PILLARS, Zone.PILLARS,
			Zone.ARCHES, Zone.ARCHES, Zone.ARCHES,
			Zone.CUBICLES, Zone.CUBICLES, Zone.CUBICLES,
			Zone.CORRIDORS, Zone.CORRIDORS, Zone.CORRIDORS,
			Zone.PITS, Zone.PITS,
			Zone.DARKENED, Zone.DARKENED,
			Zone.MEGATHALL, Zone.MEGATHALL,
			Zone.CHASM,
			Zone.RED_ROOM
	};

	@FunctionalInterface
	interface EpochSource {
		EpochSource ZERO = (cx, cz) -> 0;

		int epochAt(int cx, int cz);
	}

	enum Zone {
		CLASSIC,
		PILLARS,
		ARCHES,
		DARKENED,
		PITS,
		RED_ROOM,
		CUBICLES,
		CORRIDORS,
		MEGATHALL,
		CHASM
	}

	enum RoomLayout {
		OPEN,
		FOUR_PILLARS,
		DIAGONAL_PILLARS,
		SINGLE_PILLAR,
		OFFSET_MONOLITH,
		PARTITION_FULL,
		PARTITION_HALF,
		L_ALCOVE_FULL,
		L_ALCOVE_HALF,
		CUBICLE_BOOTH_FULL,
		CUBICLE_BOOTH_HALF,
		CORRIDOR_SPLIT_FULL,
		CORRIDOR_SPLIT_HALF,
		ARCH_RIBS
	}

	enum CeilingStyle {
		FLAT,
		COFFERED,
		BEAMED_X,
		BEAMED_Z,
		LOW_SOFFIT,
		DAMAGED_TILES
	}

	enum FloorAccent {
		DEFAULT,
		DRY_RUG,
		THICK_RUG
	}

	private Maze() {
	}

	static Zone zoneAt(long seed, int x, int z) {
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		return zoneForCell(seed, cx, cz);
	}

	private static Zone zoneForCell(long seed, int cx, int cz) {
		int sx = Math.floorDiv(cx, SECTOR), sz = Math.floorDiv(cz, SECTOR);
		return zoneForSector(seed, sx, sz);
	}

	static Zone baseZoneForSector(long seed, int sx, int sz) {
		return zoneForSector(seed, sx, sz);
	}

	static int stageCoord(int sectorCoord) {
		return Math.floorDiv(sectorCoord, STAGE_SECTORS);
	}

	static int stageOriginSector(int sectorCoord) {
		return stageCoord(sectorCoord) * STAGE_SECTORS;
	}

	private static Zone zoneForSector(long seed, int sx, int sz) {
		if (sx == 0 && sz == 0) return Zone.CLASSIC;
		int stx = stageCoord(sx);
		int stz = stageCoord(sz);
		int lx = Math.floorMod(sx, STAGE_SECTORS);
		int lz = Math.floorMod(sz, STAGE_SECTORS);
		return STAGE_CACHE.get().getOrCompute(seed, stx, stz)[lz * STAGE_SECTORS + lx];
	}

	private static Zone[] computeStageZones(long seed, int stx, int stz) {
		Zone[] grid = STAGE_QUOTA.clone();
		long rng = hash(seed, stx, stz, STAGE);
		// Keep slot 0 reserved for CLASSIC when stx==0 && stz==0, or center slot (2,2)=12 for every stage's safe hub.
		int safeSlot = (stx == 0 && stz == 0) ? 0 : 12;
		// Fisher-Yates shuffle indices 1..24 so index 0 (CLASSIC) is placed at safeSlot.
		for (int i = grid.length - 1; i > 1; i--) {
			rng = mix(rng);
			int j = 1 + Math.floorMod(rng, i);
			Zone tmp = grid[i];
			grid[i] = grid[j];
			grid[j] = tmp;
		}
		// Swap safeSlot with 0 so safeSlot is guaranteed CLASSIC.
		Zone tmp = grid[safeSlot];
		grid[safeSlot] = grid[0];
		grid[0] = tmp;

		// Ensure RED_ROOM never directly touches the origin sector (0,0) or the center hub slot 12.
		for (int idx = 0; idx < grid.length; idx++) {
			if (grid[idx] == Zone.RED_ROOM && touchesSlot(idx, safeSlot)) {
				// Move RED_ROOM to the farthest corner from safeSlot.
				int corner = (safeSlot == 0) ? 24 : 0;
				grid[idx] = grid[corner];
				grid[corner] = Zone.RED_ROOM;
				break;
			}
		}
		// Keep origin EdgeShift check cells around (0,0) on standard flat zones (never CHASM/MEGATHALL/PITS at (0,0), (0,-1)).
		if (stx == 0 && stz == 0) {
			ensureStandardAt(grid, 1); // (1,0)
			ensureStandardAt(grid, 5); // (0,1)
		}
		return grid;
	}

	private static void ensureStandardAt(Zone[] grid, int idx) {
		if (grid[idx] == Zone.CHASM || grid[idx] == Zone.MEGATHALL || grid[idx] == Zone.RED_ROOM) {
			for (int i = grid.length - 1; i >= 0; i--) {
				if (i == 0 || i == 1 || i == 5) continue;
				if (grid[i] == Zone.CLASSIC || grid[i] == Zone.CUBICLES || grid[i] == Zone.CORRIDORS) {
					Zone swap = grid[i];
					grid[i] = grid[idx];
					grid[idx] = swap;
					return;
				}
			}
		}
	}

	private static boolean touchesSlot(int a, int b) {
		int ax = a % STAGE_SECTORS, az = a / STAGE_SECTORS;
		int bx = b % STAGE_SECTORS, bz = b / STAGE_SECTORS;
		return Math.abs(ax - bx) <= 1 && Math.abs(az - bz) <= 1;
	}

	static int largeZoneOriginSector(int sectorCoord) {
		return Math.floorDiv(sectorCoord, LARGE_ZONE_SPAN) * LARGE_ZONE_SPAN;
	}

	/** Whether a RED_ROOM cell is strictly inside the zone (all four cardinal neighbors are also RED_ROOM). */
	static boolean isRedRoomInteriorCell(long seed, int cx, int cz) {
		return zoneForCell(seed, cx, cz) == Zone.RED_ROOM
				&& zoneForCell(seed, cx - 1, cz) == Zone.RED_ROOM
				&& zoneForCell(seed, cx + 1, cz) == Zone.RED_ROOM
				&& zoneForCell(seed, cx, cz - 1) == Zone.RED_ROOM
				&& zoneForCell(seed, cx, cz + 1) == Zone.RED_ROOM;
	}

	/** Whether (x, z) is in the 2-cell transition warning band outside a RED_ROOM sector. */
	static boolean isNearRedRoom(long seed, int x, int z) {
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		return nearRedRoom(seed, cx, cz);
	}

	private static boolean nearRedRoom(long seed, int cx, int cz) {
		if (zoneForCell(seed, cx, cz) == Zone.RED_ROOM) return false;
		int lx = Math.floorMod(cx, SECTOR), lz = Math.floorMod(cz, SECTOR);
		int sx = Math.floorDiv(cx, SECTOR), sz = Math.floorDiv(cz, SECTOR);
		if (lx < 2 && zoneForSector(seed, sx - 1, sz) == Zone.RED_ROOM) return true;
		if (lx >= SECTOR - 2 && zoneForSector(seed, sx + 1, sz) == Zone.RED_ROOM) return true;
		if (lz < 2 && zoneForSector(seed, sx, sz - 1) == Zone.RED_ROOM) return true;
		if (lz >= SECTOR - 2 && zoneForSector(seed, sx, sz + 1) == Zone.RED_ROOM) return true;
		return false;
	}

	/** Relative frequency of Edge Shift in this zone (0 means completely stable). */
	static int shiftWeight(Zone zone) {
		return switch (zone) {
			case ARCHES, MEGATHALL, CHASM, PITS -> 0;
			case RED_ROOM -> 1;
			case CUBICLES -> 3;
			case CLASSIC -> 4;
			case CORRIDORS -> 5;
			case PILLARS -> 7;
			case DARKENED -> 9;
		};
	}

	/** Minimum ticks before the same cell can shift again. */
	static int shiftCooldownTicks(Zone zone) {
		return switch (zone) {
			case DARKENED -> 200;
			case PILLARS -> 280;
			case CORRIDORS -> 340;
			case CLASSIC -> 400;
			case CUBICLES -> 450;
			case RED_ROOM -> 700;
			case ARCHES, MEGATHALL, CHASM, PITS -> Integer.MAX_VALUE;
		};
	}

	private static int effectiveEpoch(Zone zone, int cx, int cz, EpochSource epochs) {
		return (zone == Zone.ARCHES || zone == Zone.MEGATHALL || zone == Zone.CHASM || zone == Zone.PITS)
				? 0
				: epochs.epochAt(cx, cz);
	}

	/** Whether a wall block should show peeling yellow wallpaper exposing dark red underneath. */
	static boolean isRedWarningWall(long seed, int x, int z) {
		return isRedWarningWall(seed, x, z, EpochSource.ZERO);
	}

	static boolean isRedWarningWall(long seed, int x, int z, EpochSource epochs) {
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		Zone zone = zoneForCell(seed, cx, cz);
		if (!nearRedRoom(seed, cx, cz)) return false;
		int epoch = effectiveEpoch(zone, cx, cz, epochs);
		return (mixEpoch(hash(seed, x, z, WARNING), epoch) & 1) == 0;
	}

	/** Whether the floor at (x, z) uses thick soaked carpet (arches, red rooms, and red-room warning edges). */
	static boolean isThickCarpet(long seed, int x, int z) {
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		Zone zone = zoneForCell(seed, cx, cz);
		if (zone == Zone.ARCHES || zone == Zone.RED_ROOM) return true;
		return nearRedRoom(seed, cx, cz) && (hash(seed, x, z, WARNING + 1) & 1) == 0;
	}

	static boolean isPitCell(long seed, int cx, int cz) {
		if (cx == 0 && cz == 0) return false;
		if (zoneForCell(seed, cx, cz) != Zone.PITS) return false;
		if (PitfallsPlan.isEscapeShaftCell(cx, cz)) return false;
		return Math.floorMod(hash(seed, cx, cz, FEATURE), 6) != 0;
	}

	/** Whether (x, z) opens into a 3x3 / 4x4 / 5x3 Pitfalls opening dropping to the escapable lower level. */
	static boolean isPit(long seed, int x, int z) {
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		if (!isPitCell(seed, cx, cz)) return false;
		int ix = Math.floorMod(x, CELL), iz = Math.floorMod(z, CELL);
		// Keep a 2-block wide walkway beam around every pit inside the 10x10 cell (ix, iz in 3..7).
		if (ix < 3 || ix > 7 || iz < 3 || iz > 7) return false;
		int shape = Math.floorMod(hash(seed, cx, cz, FEATURE + 1), 4);
		return switch (shape) {
			case 0 -> ix >= 4 && ix <= 6 && iz >= 4 && iz <= 6; // 3x3 square pit
			case 1 -> ix >= 3 && ix <= 6 && iz >= 3 && iz <= 6; // 4x4 wide pit
			case 2 -> iz >= 4 && iz <= 6;                       // 5x3 E-W trench pit
			default -> ix >= 4 && ix <= 6;                      // 3x5 N-S trench pit
		};
	}

	private static boolean isPuddleCell(long seed, int cx, int cz) {
		if (cx == 0 && cz == 0) return false;
		if (zoneForCell(seed, cx, cz) != Zone.DARKENED) return false;
		if (ZonePoiPlan.isDarkenedPoiCell(seed, cx, cz)) return false;
		return Math.floorMod(hash(seed, cx, cz, FEATURE), 3) == 0;
	}

	/** Whether (x, z) is a sunken ankle-deep puddle inside a DARKENED zone. */
	static boolean isPuddle(long seed, int x, int z) {
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		if (!isPuddleCell(seed, cx, cz)) return false;
		int ix = Math.floorMod(x, CELL), iz = Math.floorMod(z, CELL);
		if (ix < 3 || ix > 7 || iz < 3 || iz > 7) return false;
		int shape = Math.floorMod(hash(seed, cx, cz, FEATURE + 1), 4);
		return switch (shape) {
			case 0 -> ix <= 6 && iz <= 6;
			case 1 -> ix >= 4 && ix <= 6 && iz >= 4 && iz <= 6;
			case 2 -> ix >= 4 && ix <= 6;
			default -> iz >= 4 && iz <= 6;
		};
	}

	/** Lethal chasm abyss void (Pitfalls now drop to an escapable lower floor instead of instant death). */
	static boolean isAbyss(long seed, int x, int z) {
		return zoneAt(seed, x, z) == Zone.CHASM && ChasmPlan.isAbyssBlock(seed, x, z);
	}

	/** Whether a cell has flat standard FLOOR_Y = 32 across its whole floor (safe for Exits). */
	static boolean isStandardFloorCell(long seed, int cx, int cz) {
		if (StaircasePlan.isProtectedCell(seed, cx, cz)) return false;
		Zone zone = zoneForCell(seed, cx, cz);
		if (zone == Zone.CHASM) return ChasmPlan.isFlatRimCell(seed, cx, cz);
		if (zone == Zone.MEGATHALL) return MegahallPlan.isRimCell(cx, cz);
		return true;
	}

	/** Whether (cx, cz) belongs to a merged 2x2 high-ceiling atrium hall (20x20 blocks) in CLASSIC or ARCHES. */
	static boolean isAtriumCell(long seed, int cx, int cz) {
		int ax = Math.floorDiv(cx, 2), az = Math.floorDiv(cz, 2);
		if (ax == 0 && az == 0) return false;
		if (Math.floorMod(hash(seed, ax, az, ATRIUM), 10) != 0) return false;
		int baseCx = ax * 2, baseCz = az * 2;
		Zone zone = zoneForCell(seed, baseCx, baseCz);
		if (zone != Zone.CLASSIC && zone != Zone.ARCHES) return false;
		if (Math.floorDiv(baseCx, SECTOR) == 0 && Math.floorDiv(baseCz, SECTOR) == 0) return false;
		for (int dx = 0; dx < 2; dx++) {
			for (int dz = 0; dz < 2; dz++) {
				if (ManilaRoomPlan.isRoomCell(seed, baseCx + dx, baseCz + dz)
						|| StaircasePlan.isProtectedCell(seed, baseCx + dx, baseCz + dz)) {
					return false;
				}
			}
		}
		return zoneForCell(seed, baseCx + 1, baseCz) == zone
				&& zoneForCell(seed, baseCx, baseCz + 1) == zone
				&& zoneForCell(seed, baseCx + 1, baseCz + 1) == zone;
	}

	/** Standing floor block Y at (x, z), or Integer.MIN_VALUE if solid wall or pit/abyss opening. */
	static int walkableFloorY(long seed, int x, int z, EpochSource epochs) {
		if (isWall(seed, x, z, false, epochs)) return Integer.MIN_VALUE;
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		Zone zone = zoneForCell(seed, cx, cz);
		if (zone == Zone.CHASM) return ChasmPlan.walkableFloorY(seed, x, z);
		if (zone == Zone.MEGATHALL) return MegahallPlan.walkableFloorY(seed, x, z);
		StaircasePlan.Complex staircase = StaircasePlan.complexAtBlock(seed, x, z);
		if (staircase != null) return StaircasePlan.groundWalkableY(staircase, x, z);
		if (isPit(seed, x, z)) return Integer.MIN_VALUE;
		if (zone == Zone.PITS && PitfallsPlan.isEscapeShaftOpening(cx, cz, Math.floorMod(x, CELL), Math.floorMod(z, CELL))) {
			return Integer.MIN_VALUE;
		}
		int ix = Math.floorMod(x, CELL), iz = Math.floorMod(z, CELL);
		if (ix >= 3 && ix <= 7 && iz >= 3 && iz <= 7 && isSunkenCell(seed, cx, cz)) {
			return Level0ChunkGenerator.FLOOR_Y - 1;
		}
		return Level0ChunkGenerator.FLOOR_Y;
	}

	/** Ceiling Y for a cell (5-block standard headroom y=38, tall MEGATHALL bays, CHASM caverns, and 2x2 atriums). */
	static int cellCeilingY(long seed, int cx, int cz) {
		Zone zone = zoneForCell(seed, cx, cz);
		if (zone == Zone.CHASM) return ChasmPlan.cellCeilingY(seed, cx, cz);
		if (zone == Zone.MEGATHALL) return MegahallPlan.cellCeilingY(seed, cx, cz);
		if (isAtriumCell(seed, cx, cz)) {
			int ax = Math.floorDiv(cx, 2), az = Math.floorDiv(cz, 2);
			return Math.floorMod(hash(seed, ax, az, ATRIUM + 1), 2) == 0 ? 44 : 48;
		}
		return Level0ChunkGenerator.CEILING_Y;
	}

	static boolean isStairwellCell(long seed, int cx, int cz) {
		return stairwellDir(seed, cx, cz) >= 0;
	}

	static int stairwellDir(long seed, int cx, int cz) {
		StaircasePlan.Complex complex = StaircasePlan.complexForCell(seed, cx, cz);
		if (complex == null) return -1;
		if (complex.isEntryCell(cx, cz)) return complex.rot();
		if (complex.isExitStairCell(cx, cz)) return (complex.rot() + 2) & 3;
		return -1;
	}

	/** Whether a cell features a 1-block sunken carpeted basin in its 5x5 core (ix,iz in 3..7). */
	static boolean isSunkenCell(long seed, int cx, int cz) {
		if (cx == 0 && cz == 0) return false;
		if (ManilaRoomPlan.isRoomCell(seed, cx, cz) || StaircasePlan.isComplexCell(seed, cx, cz)
				|| ZonePoiPlan.isStandardPoiCell(seed, cx, cz)) return false;
		Zone zone = zoneForCell(seed, cx, cz);
		if (zone != Zone.CLASSIC && zone != Zone.ARCHES && zone != Zone.CUBICLES) return false;
		if (Math.floorMod(hash(seed, cx, cz, FLOOR), 14) != 0) return false;
		if (isAtriumCell(seed, cx, cz)) return false;
		if (Exits.isProtectedCell(seed, cx, cz)) return false;
		return !isStairwellCell(seed, cx, cz);
	}

	static int sunkenEntryDir(long seed, int cx, int cz) {
		return Math.floorMod(hash(seed, cx, cz, FLOOR + 1), 4);
	}

	static CeilingStyle ceilingStyle(long seed, int cx, int cz) {
		if ((cx == 0 && cz == 0) || isStairwellCell(seed, cx, cz) || isAtriumCell(seed, cx, cz)
				|| ManilaRoomPlan.isRoomCell(seed, cx, cz) || StaircasePlan.isComplexCell(seed, cx, cz)
				|| ZonePoiPlan.isStandardPoiCell(seed, cx, cz)
				|| Exits.isProtectedCell(seed, cx, cz)) {
			return CeilingStyle.FLAT;
		}
		Zone zone = zoneForCell(seed, cx, cz);
		int roll = Math.floorMod(hash(seed, cx, cz, CEILING), 100);
		return switch (zone) {
			case CORRIDORS -> {
				if (roll < 26) yield CeilingStyle.LOW_SOFFIT;
				if (roll < 44) yield CeilingStyle.BEAMED_X;
				if (roll < 62) yield CeilingStyle.BEAMED_Z;
				if (roll < 76) yield CeilingStyle.DAMAGED_TILES;
				yield CeilingStyle.FLAT;
			}
			case DARKENED -> roll < 22 ? CeilingStyle.LOW_SOFFIT : (roll < 42 ? CeilingStyle.DAMAGED_TILES : CeilingStyle.FLAT);
			case CUBICLES -> {
				if (roll < 22) yield CeilingStyle.COFFERED;
				if (roll < 36) yield CeilingStyle.BEAMED_X;
				if (roll < 50) yield CeilingStyle.BEAMED_Z;
				if (roll < 64) yield CeilingStyle.DAMAGED_TILES;
				yield CeilingStyle.FLAT;
			}
			case ARCHES, PILLARS -> roll < 30 ? CeilingStyle.COFFERED : CeilingStyle.FLAT;
			case CLASSIC -> {
				if (roll < 18) yield CeilingStyle.COFFERED;
				if (roll < 28) yield CeilingStyle.BEAMED_X;
				if (roll < 38) yield CeilingStyle.BEAMED_Z;
				if (roll < 46) yield CeilingStyle.LOW_SOFFIT;
				if (roll < 60) yield CeilingStyle.DAMAGED_TILES;
				yield CeilingStyle.FLAT;
			}
			case PITS, RED_ROOM, MEGATHALL, CHASM -> CeilingStyle.FLAT;
		};
	}

	static FloorAccent floorAccent(long seed, int cx, int cz) {
		if ((cx == 0 && cz == 0) || isStairwellCell(seed, cx, cz) || isSunkenCell(seed, cx, cz)
				|| ManilaRoomPlan.isRoomCell(seed, cx, cz)) {
			return FloorAccent.DEFAULT;
		}
		Zone zone = zoneForCell(seed, cx, cz);
		if (zone != Zone.CLASSIC && zone != Zone.CUBICLES && zone != Zone.CORRIDORS) {
			return FloorAccent.DEFAULT;
		}
		int roll = Math.floorMod(hash(seed, cx, cz, FLOOR + 2), 100);
		if (zone == Zone.CUBICLES) {
			if (roll < 35) return FloorAccent.DRY_RUG;
			if (roll < 50) return FloorAccent.THICK_RUG;
			return FloorAccent.DEFAULT;
		}
		if (roll < 18) return FloorAccent.DRY_RUG;
		if (roll < 30) return FloorAccent.THICK_RUG;
		return FloorAccent.DEFAULT;
	}

	static boolean useAccentWall(long seed, int cx, int cz, EpochSource epochs) {
		Zone zone = zoneForCell(seed, cx, cz);
		if (zone != Zone.CLASSIC && zone != Zone.CUBICLES) return false;
		int epoch = effectiveEpoch(zone, cx, cz, epochs);
		return Math.floorMod(mixEpoch(hash(seed, cx, cz, ROOM + 3), epoch), 3) == 0;
	}

	static boolean isWall(long seed, int x, int z) {
		return isWall(seed, x, z, false);
	}

	static boolean isWall(long seed, int x, int z, boolean topLayer) {
		return isWall(seed, x, z, topLayer, EpochSource.ZERO);
	}

	static boolean isWall(long seed, int x, int z, boolean topLayer, EpochSource epochs) {
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		int ix = Math.floorMod(x, CELL), iz = Math.floorMod(z, CELL);
		Zone zone = zoneForCell(seed, cx, cz);
		if (zone == Zone.CHASM) return ChasmPlan.isWallAt(seed, x, z, topLayer);
		if (zone == Zone.MEGATHALL) return MegahallPlan.isWallAt(seed, x, z, topLayer);
		ManilaRoomPlan.Room manila = ManilaRoomPlan.roomForCell(seed, cx, cz);
		if (manila != null) {
			if (manila.containsBlock(x, z)) return manila.isWall(x, z, topLayer);
			if (manila.isNeighborDoorClear(x, z)) return topLayer;
		}
		int epoch = effectiveEpoch(zone, cx, cz, epochs);

		if (ix != 0 && iz != 0) {
			StaircasePlan.Complex staircase = StaircasePlan.complexAtBlock(seed, x, z);
			if (staircase != null) {
				return StaircasePlan.isGroundWall(staircase, x, z);
			}
			// Keep 3-block wide perimeter corridors (ix,iz in 1..3 and 7..9) free of cramped slits!
			if (ix < 4 || ix > 6 || iz < 4 || iz > 6) return false;
			if (ix == CENTER && iz == CENTER) return false;
			if (zone == Zone.PILLARS && epoch > 0 && isShiftedPillar(seed, cx, cz, ix, iz, epoch)) return true;
			if ((cx == 0 && cz == 0) || isPitCell(seed, cx, cz) || isPuddleCell(seed, cx, cz)
					|| isSunkenCell(seed, cx, cz) || zone == Zone.PITS) {
				return false;
			}
			if (isAtriumCell(seed, cx, cz)) {
				return false;
			}
			return isInteriorLayoutWall(seed, cx, cz, ix, iz, zone, epoch, topLayer)
					&& !Exits.isProtectedCell(seed, cx, cz);
		}

		// Kane Pixels 3x3 Cruciform Columns on PILLARS cell corners (arms at offset 1 & CELL-1, leaving 7-block open spans!).
		if (zone == Zone.PILLARS) {
			if (ix == 0 && iz == 0) return true;
			if (ix == 0 && (iz == 1 || iz == CELL - 1) && zoneForCell(seed, cx - 1, cz) == Zone.PILLARS) return true;
			if (iz == 0 && (ix == 1 || ix == CELL - 1) && zoneForCell(seed, cx, cz - 1) == Zone.PILLARS) return true;
		}

		if (iz != 0) return westEdge(seed, cx, cz, zone, epoch).blocks(iz, topLayer);
		if (ix != 0) return northEdge(seed, cx, cz, zone, epoch).blocks(ix, topLayer);

		if (zone == Zone.PILLARS || zone == Zone.PITS) return true;
		if (isAtriumCell(seed, cx, cz) && Math.floorMod(cx, 2) != 0 && Math.floorMod(cz, 2) != 0) {
			return false;
		}

		Zone northNeighbor = zoneForCell(seed, cx, cz - 1);
		Zone westNeighbor = zoneForCell(seed, cx - 1, cz);
		return !westEdge(seed, cx, cz, zone, epoch).open()
				|| !westEdge(seed, cx, cz - 1, northNeighbor, effectiveEpoch(northNeighbor, cx, cz - 1, epochs)).open()
				|| !northEdge(seed, cx, cz, zone, epoch).open()
				|| !northEdge(seed, cx - 1, cz, westNeighbor, effectiveEpoch(westNeighbor, cx - 1, cz, epochs)).open()
				|| Math.floorMod(mixEpoch(hash(seed, cx, cz, PILLAR), epoch), 3) == 0;
	}

	static RoomLayout roomLayout(long seed, int cx, int cz, Zone zone, int epoch) {
		long h = mixEpoch(hash(seed, cx, cz, ROOM), epoch);
		int roll = Math.floorMod(h, 100);
		return switch (zone) {
			case CLASSIC -> {
				if (roll < 36) yield RoomLayout.OPEN;
				if (roll < 50) yield RoomLayout.PARTITION_HALF;
				if (roll < 62) yield RoomLayout.PARTITION_FULL;
				if (roll < 72) yield RoomLayout.FOUR_PILLARS;
				if (roll < 80) yield RoomLayout.L_ALCOVE_HALF;
				if (roll < 86) yield RoomLayout.L_ALCOVE_FULL;
				if (roll < 92) yield RoomLayout.CUBICLE_BOOTH_HALF;
				if (roll < 95) yield RoomLayout.SINGLE_PILLAR;
				if (roll < 98) yield RoomLayout.OFFSET_MONOLITH;
				yield RoomLayout.CORRIDOR_SPLIT_HALF;
			}
			case CUBICLES -> {
				if (roll < 12) yield RoomLayout.OPEN;
				if (roll < 40) yield RoomLayout.CUBICLE_BOOTH_HALF;
				if (roll < 62) yield RoomLayout.PARTITION_HALF;
				if (roll < 78) yield RoomLayout.L_ALCOVE_HALF;
				if (roll < 90) yield RoomLayout.CORRIDOR_SPLIT_HALF;
				if (roll < 95) yield RoomLayout.CUBICLE_BOOTH_FULL;
				yield RoomLayout.PARTITION_FULL;
			}
			case CORRIDORS -> {
				if (roll < 24) yield RoomLayout.OPEN;
				if (roll < 50) yield RoomLayout.CORRIDOR_SPLIT_FULL;
				if (roll < 68) yield RoomLayout.PARTITION_FULL;
				if (roll < 82) yield RoomLayout.L_ALCOVE_FULL;
				if (roll < 92) yield RoomLayout.OFFSET_MONOLITH;
				yield RoomLayout.CUBICLE_BOOTH_FULL;
			}
			case ARCHES -> {
				if (roll < 45) yield RoomLayout.OPEN;
				if (roll < 75) yield RoomLayout.ARCH_RIBS;
				if (roll < 90) yield RoomLayout.FOUR_PILLARS;
				yield RoomLayout.DIAGONAL_PILLARS;
			}
			case DARKENED -> {
				if (roll < 48) yield RoomLayout.OPEN;
				if (roll < 68) yield RoomLayout.FOUR_PILLARS;
				if (roll < 82) yield RoomLayout.OFFSET_MONOLITH;
				if (roll < 92) yield RoomLayout.PARTITION_FULL;
				yield RoomLayout.L_ALCOVE_FULL;
			}
			case RED_ROOM -> {
				if (roll < 50) yield RoomLayout.OPEN;
				if (roll < 68) yield RoomLayout.FOUR_PILLARS;
				if (roll < 82) yield RoomLayout.PARTITION_FULL;
				if (roll < 92) yield RoomLayout.CORRIDOR_SPLIT_FULL;
				yield RoomLayout.OFFSET_MONOLITH;
			}
			case PILLARS -> {
				if (roll < 70) yield RoomLayout.OPEN;
				if (roll < 85) yield RoomLayout.SINGLE_PILLAR;
				if (roll < 95) yield RoomLayout.DIAGONAL_PILLARS;
				yield RoomLayout.OFFSET_MONOLITH;
			}
			case PITS, MEGATHALL, CHASM -> RoomLayout.OPEN;
		};
	}

	/**
	 * Interior columns and partitions strictly occupy ix,iz in 4..6 (never blocking center (5,5)),
	 * guaranteeing at least 3-block-wide open aisles on every side of the 10x10 cell.
	 */
	private static boolean isInteriorLayoutWall(long seed, int cx, int cz, int ix, int iz, Zone zone, int epoch,
			boolean topLayer) {
		if (ZonePoiPlan.isStandardPoiCell(seed, cx, cz)) return false;
		RoomLayout layout = roomLayout(seed, cx, cz, zone, epoch);
		if (layout == RoomLayout.OPEN) return false;
		if (topLayer && isHalfHeightLayout(layout)) return false;

		long h = mixEpoch(hash(seed, cx, cz, ROOM + 1), epoch);
		int variant = Math.floorMod(h, 8);
		int rot = variant & 3;
		return switch (layout) {
			case OPEN -> false;
			case FOUR_PILLARS -> (ix == 4 || ix == 6) && (iz == 4 || iz == 6);
			case DIAGONAL_PILLARS -> (variant & 1) == 0
					? ((ix == 4 && iz == 4) || (ix == 6 && iz == 6))
					: ((ix == 6 && iz == 4) || (ix == 4 && iz == 6));
			case SINGLE_PILLAR -> switch (rot) {
				case 0 -> ix == 4 && iz == 4;
				case 1 -> ix == 6 && iz == 4;
				case 2 -> ix == 4 && iz == 6;
				default -> ix == 6 && iz == 6;
			};
			case OFFSET_MONOLITH -> switch (rot) {
				case 0 -> ix == 6 && iz >= 5;
				case 1 -> ix == 4 && iz <= 5;
				case 2 -> iz == 6 && ix >= 5;
				default -> iz == 4 && ix <= 5;
			};
			case PARTITION_FULL, PARTITION_HALF -> switch (rot) {
				case 0 -> ix == 6;
				case 1 -> ix == 4;
				case 2 -> iz == 6;
				default -> iz == 4;
			};
			case L_ALCOVE_FULL, L_ALCOVE_HALF -> switch (rot) {
				case 0 -> ix == 6 || iz == 6;
				case 1 -> ix == 4 || iz == 6;
				case 2 -> ix == 6 || iz == 4;
				default -> ix == 4 || iz == 4;
			};
			case CUBICLE_BOOTH_FULL, CUBICLE_BOOTH_HALF -> switch (rot) {
				case 0 -> ix == 4 || iz == 4 || iz == 6; // opens east
				case 1 -> ix == 6 || iz == 4 || iz == 6; // opens west
				case 2 -> iz == 4 || ix == 4 || ix == 6; // opens south
				default -> iz == 6 || ix == 4 || ix == 6; // opens north
			};
			case CORRIDOR_SPLIT_FULL, CORRIDOR_SPLIT_HALF -> (variant & 1) == 0
					? (ix == 4 || ix == 6)
					: (iz == 4 || iz == 6);
			case ARCH_RIBS -> {
				if ((ix == 4 || ix == 6) && (iz == 4 || iz == 6)) yield true;
				if (!topLayer) yield false;
				yield (variant & 1) == 0 ? (iz == 4 || iz == 6) : (ix == 4 || ix == 6);
			}
		};
	}

	private static boolean isHalfHeightLayout(RoomLayout layout) {
		return layout == RoomLayout.PARTITION_HALF
				|| layout == RoomLayout.L_ALCOVE_HALF
				|| layout == RoomLayout.CUBICLE_BOOTH_HALF
				|| layout == RoomLayout.CORRIDOR_SPLIT_HALF;
	}

	private static boolean isShiftedPillar(long seed, int cx, int cz, int ix, int iz, int epoch) {
		long h = mixEpoch(hash(seed, cx, cz, SHIFT_PILLAR), epoch);
		if (Math.floorMod(h, 3) == 0) return false;
		int px = (h & 1) == 0 ? 4 : 6;
		int pz = (h & 2) == 0 ? 4 : 6;
		return ix == px && iz == pz;
	}

	static boolean isLamp(int x, int z) {
		int ix = Math.floorMod(x, CELL);
		return (ix == CENTER || ix == CENTER + 1) && Math.floorMod(z, CELL) == CENTER;
	}

	static boolean isLamp(long seed, int x, int z, EpochSource epochs) {
		ManilaRoomPlan.Room manila = ManilaRoomPlan.roomAtBlock(seed, x, z);
		if (manila != null) {
			int dx = x - manila.baseX(), dz = z - manila.baseZ();
			return (dx == 9 || dx == 10) && (dz == 9 || dz == 10);
		}
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		int ix = Math.floorMod(x, CELL), iz = Math.floorMod(z, CELL);
		if (cx == 0 && cz == 0) return isLamp(x, z);
		if (ix < 2 || ix > 8 || iz < 2 || iz > 8) return false;

		Zone zone = zoneForCell(seed, cx, cz);
		if (zone == Zone.MEGATHALL) return MegahallPlan.isLamp(seed, x, z);
		if (zone == Zone.CHASM) return isLamp(x, z);
		if (ZonePoiPlan.isRedRoomPoiCell(seed, cx, cz)) {
			return (ix == CENTER || ix == CENTER + 1) && (iz == CENTER || iz == CENTER + 1);
		}
		ZonePoiPlan.MysteryExit mystery = ZonePoiPlan.mysteryExitAtCell(seed, cx, cz);
		if (mystery != null) {
			return mystery.kind() == ZonePoiPlan.MysteryKind.HUB
					? ix == CENTER && (iz == CENTER - 1 || iz == CENTER + 1)
					: ix == CENTER && iz == CENTER + 1;
		}
		ZonePoiPlan.Vignette vignette = ZonePoiPlan.vignetteAtCell(seed, cx, cz);
		if (vignette != null && vignette.type() != ZonePoiPlan.VignetteType.NOCLIP_ALCOVE) {
			if (vignette.type() == ZonePoiPlan.VignetteType.PEEK_CHAMBER) {
				return ix == CENTER && iz == CENTER;
			}
			return (ix == CENTER || ix == CENTER + 1) && iz == CENTER;
		}
		int epoch = effectiveEpoch(zone, cx, cz, epochs);
		long h = mixEpoch(hash(seed, cx, cz, LAMP + 1), epoch);
		int roll = Math.floorMod(h, 100);

		int pattern = switch (zone) {
			case CUBICLES -> roll < 35 ? 2 : (roll < 70 ? 3 : (roll < 85 ? 0 : 1));
			case ARCHES, PILLARS -> roll < 38 ? 2 : (roll < 64 ? 0 : (roll < 90 ? 1 : 4));
			case CORRIDORS -> roll < 35 ? 1 : (roll < 60 ? 5 : (roll < 80 ? 0 : (roll < 92 ? 4 : 6)));
			default -> {
				if (roll < 30) yield 0;
				if (roll < 52) yield 1;
				if (roll < 68) yield 2;
				if (roll < 82) yield 3;
				if (roll < 92) yield 4;
				if (roll < 97) yield 5;
				yield 6;
			}
		};

		return switch (pattern) {
			case 0 -> (ix == CENTER || ix == CENTER + 1) && iz == CENTER;
			case 1 -> ix == CENTER && (iz == CENTER || iz == CENTER + 1);
			case 2 -> (ix == CENTER || ix == CENTER + 1) && (iz == CENTER || iz == CENTER + 1);
			case 3 -> ((ix == 3 || ix == 4) && iz == 3) || ((ix == 6 || ix == 7) && iz == 7);
			case 4 -> (h & 256L) == 0
					? (iz == CENTER && ix >= 4 && ix <= 6)
					: (ix == CENTER && iz >= 4 && iz <= 6);
			case 5 -> ix == CENTER && iz == CENTER;
			default -> false;
		};
	}

	static boolean lampWorks(long seed, int x, int z) {
		return lampWorks(seed, x, z, EpochSource.ZERO);
	}

	static boolean lampWorks(long seed, int x, int z, EpochSource epochs) {
		if (ManilaRoomPlan.roomAtBlock(seed, x, z) != null) return true;
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		if (ZonePoiPlan.isRedRoomPoiCell(seed, cx, cz)) return true;
		ZonePoiPlan.MysteryExit mystery = ZonePoiPlan.mysteryExitAtCell(seed, cx, cz);
		if (mystery != null) {
			return mystery.kind() == ZonePoiPlan.MysteryKind.FUN || Math.floorMod(z, CELL) == CENTER + 1;
		}
		ZonePoiPlan.Vignette vignette = ZonePoiPlan.vignetteAtCell(seed, cx, cz);
		if (vignette != null) {
			if (vignette.type() == ZonePoiPlan.VignetteType.WANDERER_SITE
					|| vignette.type() == ZonePoiPlan.VignetteType.BREAKER_NICHE) {
				return false;
			}
			if (vignette.type() == ZonePoiPlan.VignetteType.PEEK_CHAMBER
					|| vignette.type() == ZonePoiPlan.VignetteType.MEG_ISOLATION_POST) return true;
			if (vignette.type() == ZonePoiPlan.VignetteType.GLASS_OFFICE) {
				return Math.floorMod(x, CELL) == CENTER + 1 && Math.floorMod(z, CELL) == CENTER;
			}
		}
		Zone zone = zoneForCell(seed, cx, cz);
		int epoch = effectiveEpoch(zone, cx, cz, epochs);
		int roll = Math.floorMod(mixEpoch(hash(seed, cx, cz, LAMP), epoch), 100);
		return switch (zone) {
			case DARKENED -> false;
			case RED_ROOM -> roll < 65;
			case PITS -> roll < 45;
			case CHASM -> isLamp(x, z) && roll < 50;
			case MEGATHALL -> MegahallPlan.isLamp(seed, x, z) && roll < 55;
			case PILLARS, ARCHES -> roll < 55;
			case CUBICLES -> roll < 60;
			case CORRIDORS -> roll < 48;
			case CLASSIC -> roll < 52;
		};
	}

	private static boolean isOpenQuadEdge(long seed, int qx, int qz, Zone zoneA, Zone zoneB) {
		if (zoneA != zoneB) return false;
		if (zoneA != Zone.CLASSIC && zoneA != Zone.CUBICLES) return false;
		return Math.floorMod(hash(seed, qx, qz, MERGE), 7) == 0;
	}

	private static Edge westEdge(long seed, int cx, int cz, Zone zone, int epoch) {
		if (Math.floorMod(cx, 2) != 0 && isAtriumCell(seed, cx, cz)) {
			return new Edge(1, CELL - 1, zone == Zone.ARCHES ? Edge.DoorStyle.ARCHED : Edge.DoorStyle.FULL);
		}
		if (Math.floorMod(cx, 2) == 1 && isOpenQuadEdge(seed, Math.floorDiv(cx, 2), Math.floorDiv(cz, 2),
				zone, zoneForCell(seed, cx - 1, cz))) {
			return new Edge(1, CELL - 1, Edge.DoorStyle.FULL);
		}
		int lx = Math.floorMod(cx, SECTOR), lz = Math.floorMod(cz, SECTOR);
		boolean required = lz == 0 || (lx != 0 && pathGoesWest(seed, cx, cz));
		return Edge.random(mixEpoch(hash(seed, cx, cz, WEST), epoch), required, zone, epoch > 0);
	}

	private static Edge northEdge(long seed, int cx, int cz, Zone zone, int epoch) {
		if (Math.floorMod(cz, 2) != 0 && isAtriumCell(seed, cx, cz)) {
			return new Edge(1, CELL - 1, zone == Zone.ARCHES ? Edge.DoorStyle.ARCHED : Edge.DoorStyle.FULL);
		}
		if (Math.floorMod(cz, 2) == 1 && isOpenQuadEdge(seed, Math.floorDiv(cx, 2), Math.floorDiv(cz, 2),
				zone, zoneForCell(seed, cx, cz - 1))) {
			return new Edge(1, CELL - 1, Edge.DoorStyle.FULL);
		}
		int lx = Math.floorMod(cx, SECTOR), lz = Math.floorMod(cz, SECTOR);
		boolean required = lx == 0 || (lz != 0 && !pathGoesWest(seed, cx, cz));
		return Edge.random(mixEpoch(hash(seed, cx, cz, NORTH), epoch), required, zone, epoch > 0);
	}

	private static boolean pathGoesWest(long seed, int cx, int cz) {
		ManilaRoomPlan.Room manila = ManilaRoomPlan.roomForCell(seed, cx, cz);
		if (manila != null) {
			if (cx == manila.cx0() + 2 && cz == manila.cz0() + 1) return false;
			if (cx == manila.cx0() + 1 && cz == manila.cz0() + 2) return true;
		}
		return (hash(seed, cx, cz, TREE) & 1) == 0;
	}

	private static long mixEpoch(long baseHash, int epoch) {
		return epoch == 0 ? baseHash : mix(baseHash ^ epoch * 0x9E3779B97F4A7C15L);
	}

	static long hash(long seed, int x, int z, int salt) {
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

	private static final ThreadLocal<StageCache> STAGE_CACHE = ThreadLocal.withInitial(StageCache::new);

	private static final class StageCache {
		private static final int MASK = 127;
		private final long[] seeds = new long[MASK + 1];
		private final int[] sxs = new int[MASK + 1];
		private final int[] szs = new int[MASK + 1];
		private final Zone[][] values = new Zone[MASK + 1][];
		private final boolean[] valid = new boolean[MASK + 1];

		Zone[] getOrCompute(long seed, int stx, int stz) {
			int idx = (int) ((seed ^ (stx * 0x9E3779B97F4A7C15L) ^ (stz * 0xC2B2AE3D27D4EB4FL)) >>> 32) & MASK;
			if (valid[idx] && seeds[idx] == seed && sxs[idx] == stx && szs[idx] == stz) {
				return values[idx];
			}
			Zone[] computed = computeStageZones(seed, stx, stz);
			seeds[idx] = seed;
			sxs[idx] = stx;
			szs[idx] = stz;
			values[idx] = computed;
			valid[idx] = true;
			return computed;
		}
	}

	/** The passable span of one cell edge; a width of 0 is a solid wall. All doorways are >= 3 blocks wide. */
	private record Edge(int start, int width, DoorStyle style) {
		enum DoorStyle {
			FULL,
			FRAMED,
			ARCHED
		}

		static Edge random(long hash, boolean required, Zone zone, boolean shifted) {
			int roll = Math.floorMod(hash, 100);
			DoorStyle framedOrFull = (hash & 512L) == 0 ? DoorStyle.FRAMED : DoorStyle.FULL;
			return switch (zone) {
				case PILLARS -> {
					int openReq = shifted ? 72 : 95;
					int openOpt = shifted ? 58 : 90;
					if (roll < (required ? openReq : openOpt)) yield new Edge(1, CELL - 1, DoorStyle.FULL);
					if (!required && shifted && roll >= 85) yield new Edge(1, 0, DoorStyle.FULL);
					if (!required && !shifted) yield new Edge(1, 0, DoorStyle.FULL);
					yield doorway(hash, 4, 6, DoorStyle.FULL);
				}
				case PITS -> {
					if (roll < (required ? 95 : 90)) yield new Edge(1, CELL - 1, DoorStyle.FULL);
					if (!required) yield new Edge(1, 0, DoorStyle.FULL);
					yield doorway(hash, 4, 6, DoorStyle.FULL);
				}
				case ARCHES -> {
					if (roll < (required ? 18 : 12)) yield new Edge(1, CELL - 1, DoorStyle.ARCHED);
					if (!required && roll >= 58) yield new Edge(1, 0, DoorStyle.FULL);
					yield doorway(hash, 4, 6, DoorStyle.ARCHED);
				}
				case DARKENED -> {
					if (roll < (required ? 32 : 18)) yield new Edge(1, CELL - 1, DoorStyle.FULL);
					if (!required && roll >= 38) yield new Edge(1, 0, DoorStyle.FULL);
					yield doorway(hash, 3, 4, framedOrFull);
				}
				case RED_ROOM -> {
					if (roll < (required ? 15 : 8)) yield new Edge(1, CELL - 1, DoorStyle.FULL);
					if (!required && roll >= 20) yield new Edge(1, 0, DoorStyle.FULL);
					yield doorway(hash, 3, 4, (hash & 256L) == 0 ? DoorStyle.FRAMED : DoorStyle.FULL);
				}
				case CUBICLES -> {
					if (roll < (required ? 55 : 42)) yield new Edge(1, CELL - 1, DoorStyle.FULL);
					if (!required && roll >= 78) yield new Edge(1, 0, DoorStyle.FULL);
					yield doorway(hash, 4, 6, DoorStyle.FULL);
				}
				case CORRIDORS -> {
					if (roll < (required ? 16 : 8)) yield new Edge(1, CELL - 1, DoorStyle.FULL);
					if (!required && roll >= 52) yield new Edge(1, 0, DoorStyle.FULL);
					yield doorway(hash, 3, 4, framedOrFull);
				}
				case CLASSIC -> {
					if (roll < (required ? 44 : 26)) yield new Edge(1, CELL - 1, DoorStyle.FULL);
					if (!required && roll >= 50) yield new Edge(1, 0, DoorStyle.FULL);
					yield doorway(hash, 3, 5, framedOrFull);
				}
				case MEGATHALL, CHASM -> new Edge(1, CELL - 1, DoorStyle.FULL);
			};
		}

		private static Edge doorway(long hash, int minWidth, int maxWidth, DoorStyle style) {
			int span = maxWidth - minWidth + 1;
			int width = minWidth + (span > 1 ? Math.floorMod(hash >>> 32, span) : 0);
			int start = 1 + Math.floorMod(hash >>> 40, CELL - width);
			return new Edge(start, width, style);
		}

		boolean open() {
			return width == CELL - 1;
		}

		boolean blocks(int offset, boolean topLayer) {
			if (width == 0) return true;
			if (topLayer && style == DoorStyle.FRAMED && !open()) {
				return true;
			}
			if (style == DoorStyle.ARCHED && topLayer && width >= 4) {
				return offset <= start || offset >= start + width - 1;
			}
			return offset < start || offset >= start + width;
		}
	}
}
