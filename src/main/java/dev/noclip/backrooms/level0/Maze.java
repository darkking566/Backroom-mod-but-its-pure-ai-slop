package dev.noclip.backrooms.level0;

/**
 * The Level 0 floor plan as a pure function of seed and block position, so chunks can be generated in any order.
 *
 * Every cell is guaranteed a path: inside a sector it leads west or north to the sector's edge,
 * and those edges join up into an endless lattice. Each sector also chooses a structural variant
 * (classic rooms, pillar halls, arched rooms, or darkened zones) while keeping connectivity intact.
 */
final class Maze {
	static final int CELL = 7;
	static final int CENTER = 3;
	static final int SECTOR = 8;
	static final int LARGE_ZONE_SPAN = 3;

	private static final int TREE = 1, WEST = 2, NORTH = 3, PILLAR = 4, LAMP = 5, ZONE = 6, FEATURE = 7, WARNING = 8,
			SHIFT_PILLAR = 9, STAIRWELL = 10, ROOM = 11, CEILING = 12, FLOOR = 13, MERGE = 14, ATRIUM = 15;

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
		LOW_SOFFIT
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
		// Keep the origin sector classic so first-time landings always look familiar.
		if (sx == 0 && sz == 0) return Zone.CLASSIC;

		Zone large = largeZoneAtSector(seed, sx, sz);
		if (large != null) return large;

		// Macro 2x2-sector zones (112x112 blocks): Pillar Halls, Megahalls, and Abyssal Chasms.
		int mx = Math.floorDiv(sx, 2), mz = Math.floorDiv(sz, 2);
		if (!macroHasLargeZone(seed, mx, mz)) {
			int macroRoll = Math.floorMod(hash(seed, mx, mz, ZONE + 1), 100);
			if (macroRoll < 14) return Zone.PILLARS;
			if (!nearOriginEdgeShiftCheck(mx, mz)) {
				if (macroRoll < 20) return Zone.MEGATHALL;
				if (macroRoll < 26) return Zone.CHASM;
			}
		}

		int roll = Math.floorMod(hash(seed, sx, sz, ZONE), 100);
		if (roll < 40) return Zone.CLASSIC;
		if (roll < 56) return Zone.ARCHES;
		if (roll < 72) return Zone.PITS;
		if (roll < 86) return Zone.CUBICLES;
		return Zone.CORRIDORS;
	}

	private static Zone largeZoneAtSector(long seed, int sx, int sz) {
		// Darkened zones and Red Rooms span 3x3 sectors (168x168 blocks) at lower occurrence frequency.
		int lx = Math.floorDiv(sx, LARGE_ZONE_SPAN), lz = Math.floorDiv(sz, LARGE_ZONE_SPAN);
		if (lx == 0 && lz == 0) return null;
		int roll = Math.floorMod(hash(seed, lx, lz, ZONE + 2), 100);
		if (roll < 3) return Zone.RED_ROOM;
		if (roll < 9) return Zone.DARKENED;
		return null;
	}

	private static boolean macroHasLargeZone(long seed, int mx, int mz) {
		int baseSx = mx * 2, baseSz = mz * 2;
		for (int dx = 0; dx < 2; dx++) {
			for (int dz = 0; dz < 2; dz++) {
				if (largeZoneAtSector(seed, baseSx + dx, baseSz + dz) != null) return true;
			}
		}
		return false;
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

	/** Whether a non-red cell sits within 2 cells of a RED_ROOM sector border (transition warning band). */
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

	static Zone baseZoneForSector(long seed, int sx, int sz) {
		return zoneForCell(seed, sx * SECTOR, sz * SECTOR);
	}

	private static Zone zoneForSector(long seed, int sx, int sz) {
		return zoneForCell(seed, sx * SECTOR, sz * SECTOR);
	}

	private static boolean nearOriginEdgeShiftCheck(int mx, int mz) {
		return (mx == 0 && mz == 0) || (mx == 0 && mz == -1) || (mx == -1 && mz == -1);
	}

	/** Relative frequency of Edge Shift in this zone (0 means completely stable). */
	static int shiftWeight(Zone zone) {
		return switch (zone) {
			case ARCHES, MEGATHALL, CHASM -> 0;
			case RED_ROOM -> 1;
			case PITS -> 2;
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
			case PITS -> 500;
			case RED_ROOM -> 700;
			case ARCHES, MEGATHALL, CHASM -> Integer.MAX_VALUE;
		};
	}

	private static int effectiveEpoch(Zone zone, int cx, int cz, EpochSource epochs) {
		return (zone == Zone.ARCHES || zone == Zone.MEGATHALL || zone == Zone.CHASM) ? 0 : epochs.epochAt(cx, cz);
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

	private static boolean isPitCell(long seed, int cx, int cz) {
		if (cx == 0 && cz == 0) return false;
		if (zoneForCell(seed, cx, cz) != Zone.PITS) return false;
		return Math.floorMod(hash(seed, cx, cz, FEATURE), 5) != 0;
	}

	/** Whether (x, z) opens into a bottomless dark pit in a PITS zone. */
	static boolean isPit(long seed, int x, int z) {
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		if (!isPitCell(seed, cx, cz)) return false;
		int ix = Math.floorMod(x, CELL), iz = Math.floorMod(z, CELL);
		// Keep a 1-2 block walkway around every pit so all cells and Exits stay safely passable.
		if (ix < 2 || ix > 5 || iz < 2 || iz > 5) return false;
		int shape = Math.floorMod(hash(seed, cx, cz, FEATURE + 1), 5);
		return switch (shape) {
			case 0 -> ix <= 4 && iz <= 4; // 3x3 square
			case 1 -> true; // 4x4 wide abyss
			case 2 -> ix == 3 || ix == 4; // 2x4 N-S trench
			case 3 -> iz == 3 || iz == 4; // 4x2 E-W trench
			default -> ix >= 4 && iz >= 4; // 2x2 offset pit
		};
	}

	private static boolean isPuddleCell(long seed, int cx, int cz) {
		if (cx == 0 && cz == 0) return false;
		if (zoneForCell(seed, cx, cz) != Zone.DARKENED) return false;
		return Math.floorMod(hash(seed, cx, cz, FEATURE), 3) == 0;
	}

	/** Whether (x, z) is a sunken ankle-deep puddle inside a DARKENED zone. */
	static boolean isPuddle(long seed, int x, int z) {
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		if (!isPuddleCell(seed, cx, cz)) return false;
		int ix = Math.floorMod(x, CELL), iz = Math.floorMod(z, CELL);
		// Stay inside ix, iz in 2..5 so normal carpet borders contain the water cleanly.
		if (ix < 2 || ix > 5 || iz < 2 || iz > 5) return false;
		int shape = Math.floorMod(hash(seed, cx, cz, FEATURE + 1), 4);
		return switch (shape) {
			case 0 -> true; // 4x4 pool
			case 1 -> ix <= 4 && iz <= 4; // 3x3 pool
			case 2 -> ix == 3 || ix == 4; // 2x4 channel
			default -> ix <= 3 && iz >= 3 && iz <= 4; // 2x2 corner puddle
		};
	}

	static boolean isAbyss(long seed, int x, int z) {
		if (isPit(seed, x, z)) return true;
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

	/** Whether (cx, cz) belongs to a merged 3x3 high-ceiling atrium hall in CLASSIC or ARCHES. */
	static boolean isAtriumCell(long seed, int cx, int cz) {
		int ax = Math.floorDiv(cx, 3), az = Math.floorDiv(cz, 3);
		if (ax == 0 && az == 0) return false;
		if (Math.floorMod(hash(seed, ax, az, ATRIUM), 18) != 0) return false;
		int baseCx = ax * 3, baseCz = az * 3;
		Zone zone = zoneForCell(seed, baseCx, baseCz);
		if (zone != Zone.CLASSIC && zone != Zone.ARCHES) return false;
		if (Math.floorDiv(baseCx, SECTOR) == 0 && Math.floorDiv(baseCz, SECTOR) == 0) return false;
		for (int dx = 0; dx < 3; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				if (ManilaRoomPlan.isRoomCell(seed, baseCx + dx, baseCz + dz)
						|| StaircasePlan.isProtectedCell(seed, baseCx + dx, baseCz + dz)) {
					return false;
				}
			}
		}
		return zoneForCell(seed, baseCx + 2, baseCz) == zone
				&& zoneForCell(seed, baseCx, baseCz + 2) == zone
				&& zoneForCell(seed, baseCx + 2, baseCz + 2) == zone;
	}

	/** Standing floor block Y at (x, z), or Integer.MIN_VALUE if solid wall or abyss pit. */
	static int walkableFloorY(long seed, int x, int z, EpochSource epochs) {
		if (isWall(seed, x, z, false, epochs)) return Integer.MIN_VALUE;
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		Zone zone = zoneForCell(seed, cx, cz);
		if (zone == Zone.CHASM) return ChasmPlan.walkableFloorY(seed, x, z);
		if (zone == Zone.MEGATHALL) return MegahallPlan.walkableFloorY(seed, x, z);
		StaircasePlan.Complex staircase = StaircasePlan.complexAtBlock(seed, x, z);
		if (staircase != null) return StaircasePlan.groundWalkableY(staircase, x, z);
		if (isPit(seed, x, z)) return Integer.MIN_VALUE;
		int ix = Math.floorMod(x, CELL), iz = Math.floorMod(z, CELL);
		if (ix >= 2 && ix <= 5 && iz >= 2 && iz <= 5 && isSunkenCell(seed, cx, cz)) {
			return Level0ChunkGenerator.FLOOR_Y - 1;
		}
		return Level0ChunkGenerator.FLOOR_Y;
	}

	/** Ceiling Y for a cell (supports tall MEGATHALL bays, CHASM caverns/sub-levels, and 3x3 atriums). */
	static int cellCeilingY(long seed, int cx, int cz) {
		Zone zone = zoneForCell(seed, cx, cz);
		if (zone == Zone.CHASM) return ChasmPlan.cellCeilingY(seed, cx, cz);
		if (zone == Zone.MEGATHALL) return MegahallPlan.cellCeilingY(seed, cx, cz);
		if (isAtriumCell(seed, cx, cz)) {
			int ax = Math.floorDiv(cx, 3), az = Math.floorDiv(cz, 3);
			return Math.floorMod(hash(seed, ax, az, ATRIUM + 1), 2) == 0 ? 44 : 48;
		}
		return Level0ChunkGenerator.CEILING_Y;
	}

	/** Whether a cell hosts a multi-level staircase entrance (up to attic/loft/overlook/loop or down to basement). */
	static boolean isStairwellCell(long seed, int cx, int cz) {
		return stairwellDir(seed, cx, cz) >= 0;
	}

	/**
	 * Orientation of a cell's staircase entrance (0=east, 1=west, 2=south, 3=north), or -1 if none.
	 */
	static int stairwellDir(long seed, int cx, int cz) {
		StaircasePlan.Complex complex = StaircasePlan.complexForCell(seed, cx, cz);
		if (complex == null) return -1;
		if (complex.isEntryCell(cx, cz)) return complex.rot();
		if (complex.isExitStairCell(cx, cz)) return (complex.rot() + 2) & 3;
		return -1;
	}

	/** Whether a cell features a 1-block sunken carpeted basin in its 4x4 core. */
	static boolean isSunkenCell(long seed, int cx, int cz) {
		if (cx == 0 && cz == 0) return false;
		if (ManilaRoomPlan.isRoomCell(seed, cx, cz) || StaircasePlan.isComplexCell(seed, cx, cz)) return false;
		Zone zone = zoneForCell(seed, cx, cz);
		if (zone != Zone.CLASSIC && zone != Zone.ARCHES && zone != Zone.CUBICLES) return false;
		if (Math.floorMod(hash(seed, cx, cz, FLOOR), 22) != 0) return false;
		if (isAtriumCell(seed, cx, cz)) return false;
		if (Exits.isProtectedCell(seed, cx, cz)) return false;
		return !isStairwellCell(seed, cx, cz);
	}

	/** Side (0=west, 1=east, 2=north, 3=south) where steps lead down into a sunken room. */
	static int sunkenEntryDir(long seed, int cx, int cz) {
		return Math.floorMod(hash(seed, cx, cz, FLOOR + 1), 4);
	}

	static CeilingStyle ceilingStyle(long seed, int cx, int cz) {
		if ((cx == 0 && cz == 0) || isStairwellCell(seed, cx, cz) || isAtriumCell(seed, cx, cz)
				|| ManilaRoomPlan.isRoomCell(seed, cx, cz) || StaircasePlan.isComplexCell(seed, cx, cz)
				|| Exits.isProtectedCell(seed, cx, cz)) {
			return CeilingStyle.FLAT;
		}
		Zone zone = zoneForCell(seed, cx, cz);
		int roll = Math.floorMod(hash(seed, cx, cz, CEILING), 100);
		return switch (zone) {
			case CORRIDORS -> {
				if (roll < 35) yield CeilingStyle.LOW_SOFFIT;
				if (roll < 55) yield CeilingStyle.BEAMED_X;
				if (roll < 75) yield CeilingStyle.BEAMED_Z;
				yield CeilingStyle.FLAT;
			}
			case DARKENED -> roll < 22 ? CeilingStyle.LOW_SOFFIT : CeilingStyle.FLAT;
			case CUBICLES -> {
				if (roll < 24) yield CeilingStyle.COFFERED;
				if (roll < 38) yield CeilingStyle.BEAMED_X;
				if (roll < 52) yield CeilingStyle.BEAMED_Z;
				yield CeilingStyle.FLAT;
			}
			case ARCHES, PILLARS -> roll < 28 ? CeilingStyle.COFFERED : CeilingStyle.FLAT;
			case CLASSIC -> {
				if (roll < 20) yield CeilingStyle.COFFERED;
				if (roll < 30) yield CeilingStyle.BEAMED_X;
				if (roll < 40) yield CeilingStyle.BEAMED_Z;
				if (roll < 48) yield CeilingStyle.LOW_SOFFIT;
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
		if (roll < 16) return FloorAccent.DRY_RUG;
		if (roll < 26) return FloorAccent.THICK_RUG;
		return FloorAccent.DEFAULT;
	}

	/** Whether interior columns/monoliths in this cell use contrasting pale/rough architectural blocks. */
	static boolean useAccentWall(long seed, int cx, int cz, EpochSource epochs) {
		Zone zone = zoneForCell(seed, cx, cz);
		if (zone != Zone.CLASSIC && zone != Zone.CUBICLES) return false;
		int epoch = effectiveEpoch(zone, cx, cz, epochs);
		return Math.floorMod(mixEpoch(hash(seed, cx, cz, ROOM + 3), epoch), 3) == 0;
	}

	/** Whether the walkable layers (floor+1 and floor+2) are solid wall at (x, z). */
	static boolean isWall(long seed, int x, int z) {
		return isWall(seed, x, z, false);
	}

	/**
	 * Whether a block at (x, z) is solid wall. When {@code topLayer} is true (the layer directly
	 * below the ceiling), arched openings narrow, framed doorways close overhead, and half-walls open.
	 */
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
			if (ix < 2 || ix > 5 || iz < 2 || iz > 5) return false;
			if (ix == CENTER && iz == CENTER) return false;
			if (zone == Zone.PILLARS && epoch > 0 && isShiftedPillar(seed, cx, cz, ix, iz, epoch)) return true;
			if ((cx == 0 && cz == 0) || isPitCell(seed, cx, cz) || isPuddleCell(seed, cx, cz)
					|| isSunkenCell(seed, cx, cz)) {
				return false;
			}
			if (isAtriumCell(seed, cx, cz)) {
				int ax = Math.floorMod(cx, 3), az = Math.floorMod(cz, 3);
				return (ax == 0 || ax == 2) && (az == 0 || az == 2) && ix == 4 && iz == 4;
			}
			return isInteriorLayoutWall(seed, cx, cz, ix, iz, zone, epoch, topLayer)
					&& !Exits.isProtectedCell(seed, cx, cz);
		}
		if (iz != 0) return westEdge(seed, cx, cz, zone, epoch).blocks(iz, topLayer);
		if (ix != 0) return northEdge(seed, cx, cz, zone, epoch).blocks(ix, topLayer);

		if (zone == Zone.PILLARS || zone == Zone.PITS) return true;
		if (isAtriumCell(seed, cx, cz) && Math.floorMod(cx, 3) != 0 && Math.floorMod(cz, 3) != 0) {
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
				if (roll < 34) yield RoomLayout.OPEN;
				if (roll < 48) yield RoomLayout.PARTITION_HALF;
				if (roll < 60) yield RoomLayout.PARTITION_FULL;
				if (roll < 70) yield RoomLayout.FOUR_PILLARS;
				if (roll < 78) yield RoomLayout.L_ALCOVE_HALF;
				if (roll < 84) yield RoomLayout.L_ALCOVE_FULL;
				if (roll < 90) yield RoomLayout.CUBICLE_BOOTH_HALF;
				if (roll < 94) yield RoomLayout.SINGLE_PILLAR;
				if (roll < 97) yield RoomLayout.OFFSET_MONOLITH;
				yield RoomLayout.CORRIDOR_SPLIT_HALF;
			}
			case CUBICLES -> {
				if (roll < 10) yield RoomLayout.OPEN;
				if (roll < 38) yield RoomLayout.CUBICLE_BOOTH_HALF;
				if (roll < 60) yield RoomLayout.PARTITION_HALF;
				if (roll < 76) yield RoomLayout.L_ALCOVE_HALF;
				if (roll < 88) yield RoomLayout.CORRIDOR_SPLIT_HALF;
				if (roll < 94) yield RoomLayout.CUBICLE_BOOTH_FULL;
				yield RoomLayout.PARTITION_FULL;
			}
			case CORRIDORS -> {
				if (roll < 20) yield RoomLayout.OPEN;
				if (roll < 48) yield RoomLayout.CORRIDOR_SPLIT_FULL;
				if (roll < 66) yield RoomLayout.PARTITION_FULL;
				if (roll < 80) yield RoomLayout.L_ALCOVE_FULL;
				if (roll < 90) yield RoomLayout.OFFSET_MONOLITH;
				yield RoomLayout.CUBICLE_BOOTH_FULL;
			}
			case ARCHES -> {
				if (roll < 45) yield RoomLayout.OPEN;
				if (roll < 75) yield RoomLayout.ARCH_RIBS;
				if (roll < 90) yield RoomLayout.FOUR_PILLARS;
				yield RoomLayout.DIAGONAL_PILLARS;
			}
			case DARKENED -> {
				if (roll < 45) yield RoomLayout.OPEN;
				if (roll < 65) yield RoomLayout.FOUR_PILLARS;
				if (roll < 80) yield RoomLayout.OFFSET_MONOLITH;
				if (roll < 90) yield RoomLayout.PARTITION_FULL;
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
				if (roll < 65) yield RoomLayout.OPEN;
				if (roll < 82) yield RoomLayout.SINGLE_PILLAR;
				if (roll < 94) yield RoomLayout.DIAGONAL_PILLARS;
				yield RoomLayout.OFFSET_MONOLITH;
			}
			case PITS -> {
				if (roll < 60) yield RoomLayout.OPEN;
				if (roll < 80) yield RoomLayout.FOUR_PILLARS;
				yield RoomLayout.PARTITION_FULL;
			}
			case MEGATHALL, CHASM -> RoomLayout.OPEN;
		};
	}

	private static boolean isInteriorLayoutWall(long seed, int cx, int cz, int ix, int iz, Zone zone, int epoch,
			boolean topLayer) {
		RoomLayout layout = roomLayout(seed, cx, cz, zone, epoch);
		if (layout == RoomLayout.OPEN) return false;
		if (topLayer && isHalfHeightLayout(layout)) return false;

		long h = mixEpoch(hash(seed, cx, cz, ROOM + 1), epoch);
		int variant = Math.floorMod(h, 8);
		int rot = variant & 3;
		return switch (layout) {
			case OPEN -> false;
			case FOUR_PILLARS -> (ix == 2 || ix == 5) && (iz == 2 || iz == 5);
			case DIAGONAL_PILLARS -> (variant & 1) == 0
					? ((ix == 2 && iz == 2) || (ix == 5 && iz == 5))
					: ((ix == 5 && iz == 2) || (ix == 2 && iz == 5));
			case SINGLE_PILLAR -> switch (variant) {
				case 0 -> ix == 2 && iz == 2;
				case 1 -> ix == 5 && iz == 2;
				case 2 -> ix == 2 && iz == 5;
				case 3 -> ix == 5 && iz == 5;
				case 4 -> ix == 4 && iz == 2;
				case 5 -> ix == 2 && iz == 4;
				case 6 -> ix == 5 && iz == 4;
				default -> ix == 4 && iz == 5;
			};
			case OFFSET_MONOLITH -> switch (variant % 3) {
				case 0 -> ix >= 4 && iz >= 4;
				case 1 -> ix >= 4 && iz <= 3;
				default -> ix <= 3 && iz >= 4;
			};
			case PARTITION_FULL, PARTITION_HALF -> switch (variant) {
				case 0 -> ix == 5;
				case 1 -> ix == 2;
				case 2 -> iz == 5;
				case 3 -> iz == 2;
				case 4 -> ix == 4 && iz <= 4;
				case 5 -> ix == 4 && iz >= 3;
				case 6 -> iz == 4 && ix <= 4;
				default -> iz == 4 && ix >= 3;
			};
			case L_ALCOVE_FULL, L_ALCOVE_HALF -> switch (rot) {
				case 0 -> ix == 5 || (iz == 5 && ix >= 3);
				case 1 -> ix == 2 || (iz == 5 && ix <= 4);
				case 2 -> ix == 5 || (iz == 2 && ix >= 3);
				default -> ix == 2 || (iz == 2 && ix <= 4);
			};
			case CUBICLE_BOOTH_FULL, CUBICLE_BOOTH_HALF -> switch (rot) {
				case 0 -> (ix == 2) || ((iz == 2 || iz == 5) && ix <= 4); // opens east
				case 1 -> (ix == 5) || ((iz == 2 || iz == 5) && ix >= 3); // opens west
				case 2 -> (iz == 2) || ((ix == 2 || ix == 5) && iz <= 4); // opens south
				default -> (iz == 5) || ((ix == 2 || ix == 5) && iz >= 3); // opens north
			};
			case CORRIDOR_SPLIT_FULL, CORRIDOR_SPLIT_HALF -> (variant & 1) == 0
					? (ix == 2 || ix == 5)
					: (iz == 2 || iz == 5);
			case ARCH_RIBS -> {
				if ((ix == 2 || ix == 5) && (iz == 2 || iz == 5)) yield true;
				if (!topLayer) yield false;
				yield (variant & 1) == 0 ? (iz == 2 || iz == 5) : (ix == 2 || ix == 5);
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
		int px = 2 + Math.floorMod(h >>> 8, 3);
		int pz = 2 + Math.floorMod(h >>> 16, 3);
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
			return (dx == 6 || dx == 7) && (dz == 6 || dz == 7);
		}
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		int ix = Math.floorMod(x, CELL), iz = Math.floorMod(z, CELL);
		if (cx == 0 && cz == 0) return isLamp(x, z);
		if (ix < 2 || ix > 5 || iz < 2 || iz > 5) return false;

		Zone zone = zoneForCell(seed, cx, cz);
		if (zone == Zone.MEGATHALL) return MegahallPlan.isLamp(seed, x, z);
		if (zone == Zone.CHASM) return isLamp(x, z);
		int epoch = effectiveEpoch(zone, cx, cz, epochs);
		long h = mixEpoch(hash(seed, cx, cz, LAMP + 1), epoch);
		int roll = Math.floorMod(h, 100);

		int pattern = switch (zone) {
			case CUBICLES -> roll < 35 ? 2 : (roll < 70 ? 3 : (roll < 85 ? 0 : 1));
			case ARCHES, PILLARS -> roll < 38 ? 2 : (roll < 64 ? 0 : (roll < 90 ? 1 : 4));
			case CORRIDORS -> roll < 35 ? 1 : (roll < 60 ? 5 : (roll < 80 ? 0 : (roll < 92 ? 4 : 6)));
			default -> {
				if (roll < 30) yield 0; // E-W 2x1
				if (roll < 52) yield 1; // N-S 1x2
				if (roll < 68) yield 2; // 2x2 panel
				if (roll < 82) yield 3; // twin offset strips
				if (roll < 92) yield 4; // 3x1 long strip
				if (roll < 97) yield 5; // single bulb
				yield 6; // unlit alcove
			}
		};

		return switch (pattern) {
			case 0 -> (ix == CENTER || ix == CENTER + 1) && iz == CENTER;
			case 1 -> ix == CENTER && (iz == CENTER || iz == CENTER + 1);
			case 2 -> (ix == CENTER || ix == CENTER + 1) && (iz == CENTER || iz == CENTER + 1);
			case 3 -> ((ix == 2 || ix == 3) && iz == 2) || ((ix == 4 || ix == 5) && iz == 5);
			case 4 -> (h & 256L) == 0
					? (iz == CENTER && ix >= 2 && ix <= 4)
					: (ix == CENTER && iz >= 2 && iz <= 4);
			case 5 -> ix == CENTER && iz == CENTER;
			default -> false;
		};
	}

	static boolean lampWorks(long seed, int x, int z) {
		return lampWorks(seed, x, z, EpochSource.ZERO);
	}

	/**
	 * Whether a lamp in this cell is lit; the rest hang dark (broken). About half are lit, so the lamps make pools of
	 * light with dark stretches between them (the user asked for fewer lit lamps, 2026-11-24; before, 70 to 100% were).
	 */
	static boolean lampWorks(long seed, int x, int z, EpochSource epochs) {
		if (ManilaRoomPlan.roomAtBlock(seed, x, z) != null) return true;
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		Zone zone = zoneForCell(seed, cx, cz);
		int epoch = effectiveEpoch(zone, cx, cz, epochs);
		int roll = Math.floorMod(mixEpoch(hash(seed, cx, cz, LAMP), epoch), 100);
		return switch (zone) {
			case DARKENED -> false;
			case RED_ROOM -> roll < 65;
			case PITS -> roll < 40;
			case CHASM -> isLamp(x, z) && roll < 50;
			case MEGATHALL -> MegahallPlan.isLamp(seed, x, z) && roll < 50;
			case PILLARS, ARCHES -> roll < 55;
			case CUBICLES -> roll < 60;
			case CORRIDORS -> roll < 45;
			case CLASSIC -> roll < 50;
		};
	}

	private static boolean isOpenQuadEdge(long seed, int qx, int qz, Zone zoneA, Zone zoneB) {
		if (zoneA != zoneB) return false;
		if (zoneA != Zone.CLASSIC && zoneA != Zone.CUBICLES) return false;
		return Math.floorMod(hash(seed, qx, qz, MERGE), 9) == 0;
	}

	private static Edge westEdge(long seed, int cx, int cz, Zone zone, int epoch) {
		if (Math.floorMod(cx, 3) != 0 && isAtriumCell(seed, cx, cz)) {
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
		if (Math.floorMod(cz, 3) != 0 && isAtriumCell(seed, cx, cz)) {
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

	private static long hash(long seed, int x, int z, int salt) {
		long h = mix(seed + salt);
		h = mix(h ^ x);
		return mix(h ^ z);
	}

	// SplitMix64 finalizer
	private static long mix(long v) {
		v += 0x9E3779B97F4A7C15L;
		v = (v ^ (v >>> 30)) * 0xBF58476D1CE4E5B9L;
		v = (v ^ (v >>> 27)) * 0x94D049BB133111EBL;
		return v ^ (v >>> 31);
	}

	/** The passable span of one cell edge; a width of 0 is a solid wall. */
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
					int openReq = shifted ? 70 : 95;
					int openOpt = shifted ? 55 : 90;
					if (roll < (required ? openReq : openOpt)) yield new Edge(1, CELL - 1, DoorStyle.FULL);
					if (!required && shifted && roll >= 85) yield new Edge(1, 0, DoorStyle.FULL);
					if (!required && !shifted) yield new Edge(1, 0, DoorStyle.FULL);
					yield doorway(hash, 3, 4, DoorStyle.FULL);
				}
				case PITS -> {
					if (roll < (required ? 95 : 90)) yield new Edge(1, CELL - 1, DoorStyle.FULL);
					if (!required) yield new Edge(1, 0, DoorStyle.FULL);
					yield doorway(hash, 3, 4, DoorStyle.FULL);
				}
				case ARCHES -> {
					if (roll < (required ? 15 : 10)) yield new Edge(1, CELL - 1, DoorStyle.ARCHED);
					if (!required && roll >= 55) yield new Edge(1, 0, DoorStyle.FULL);
					yield doorway(hash, 3, 4, DoorStyle.ARCHED);
				}
				case DARKENED -> {
					if (roll < (required ? 30 : 15)) yield new Edge(1, CELL - 1, DoorStyle.FULL);
					if (!required && roll >= 35) yield new Edge(1, 0, DoorStyle.FULL);
					yield doorway(hash, 2, 3, framedOrFull);
				}
				case RED_ROOM -> {
					if (roll < (required ? 10 : 5)) yield new Edge(1, CELL - 1, DoorStyle.FULL);
					if (!required && roll >= 15) yield new Edge(1, 0, DoorStyle.FULL);
					yield doorway(hash, 2, 2, (hash & 256L) == 0 ? DoorStyle.FRAMED : DoorStyle.FULL);
				}
				case CUBICLES -> {
					if (roll < (required ? 55 : 40)) yield new Edge(1, CELL - 1, DoorStyle.FULL);
					if (!required && roll >= 75) yield new Edge(1, 0, DoorStyle.FULL);
					yield doorway(hash, 2, 4, DoorStyle.FULL);
				}
				case CORRIDORS -> {
					if (roll < (required ? 12 : 6)) yield new Edge(1, CELL - 1, DoorStyle.FULL);
					if (!required && roll >= 50) yield new Edge(1, 0, DoorStyle.FULL);
					yield doorway(hash, 1, 2, framedOrFull);
				}
				case CLASSIC -> {
					if (roll < (required ? 42 : 24)) yield new Edge(1, CELL - 1, DoorStyle.FULL);
					if (!required && roll >= 48) yield new Edge(1, 0, DoorStyle.FULL);
					yield doorway(hash, 2, 3, framedOrFull);
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
			if (style == DoorStyle.ARCHED && topLayer && width >= 3) {
				return offset <= start || offset >= start + width - 1;
			}
			return offset < start || offset >= start + width;
		}
	}
}
