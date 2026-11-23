package dev.noclip.backrooms;

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

	private static final int TREE = 1, WEST = 2, NORTH = 3, PILLAR = 4, LAMP = 5, ZONE = 6, FEATURE = 7, WARNING = 8,
			SHIFT_PILLAR = 9;

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
		RED_ROOM
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

		int roll = Math.floorMod(hash(seed, sx, sz, ZONE), 100);
		if (roll < 42) return Zone.CLASSIC;
		if (roll < 59) return Zone.PILLARS;
		if (roll < 72) return Zone.ARCHES;
		if (roll < 84) return Zone.DARKENED;
		if (roll < 95) return Zone.PITS;
		return Zone.RED_ROOM;
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

	private static Zone zoneForSector(long seed, int sx, int sz) {
		return zoneForCell(seed, sx * SECTOR, sz * SECTOR);
	}

	/** Relative frequency of Edge Shift in this zone (0 means completely stable). */
	static int shiftWeight(Zone zone) {
		return switch (zone) {
			case ARCHES -> 0;
			case RED_ROOM -> 1;
			case PITS -> 2;
			case CLASSIC -> 4;
			case PILLARS -> 7;
			case DARKENED -> 9;
		};
	}

	/** Minimum ticks before the same cell can shift again. */
	static int shiftCooldownTicks(Zone zone) {
		return switch (zone) {
			case DARKENED -> 200;
			case PILLARS -> 280;
			case CLASSIC -> 400;
			case PITS -> 500;
			case RED_ROOM -> 700;
			case ARCHES -> Integer.MAX_VALUE;
		};
	}

	private static int effectiveEpoch(Zone zone, int cx, int cz, EpochSource epochs) {
		return zone == Zone.ARCHES ? 0 : epochs.epochAt(cx, cz);
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

	/** Whether (x, z) opens into a bottomless dark pit in a PITS zone. */
	static boolean isPit(long seed, int x, int z) {
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		if (zoneForCell(seed, cx, cz) != Zone.PITS) return false;
		int ix = Math.floorMod(x, CELL), iz = Math.floorMod(z, CELL);
		// Keep a 1-2 block walkway around every 3x3 pit so all cells stay safely passable.
		if (ix < 2 || ix > 4 || iz < 2 || iz > 4) return false;
		return Math.floorMod(hash(seed, cx, cz, FEATURE), 5) != 0;
	}

	/** Whether (x, z) is a sunken ankle-deep puddle inside a DARKENED zone. */
	static boolean isPuddle(long seed, int x, int z) {
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		if (zoneForCell(seed, cx, cz) != Zone.DARKENED) return false;
		int ix = Math.floorMod(x, CELL), iz = Math.floorMod(z, CELL);
		// Stay inside ix, iz in 2..5 so normal carpet borders contain the water cleanly.
		if (ix < 2 || ix > 5 || iz < 2 || iz > 5) return false;
		return Math.floorMod(hash(seed, cx, cz, FEATURE), 3) == 0;
	}

	/** Whether the walkable layers (floor+1 and floor+2) are solid wall at (x, z). */
	static boolean isWall(long seed, int x, int z) {
		return isWall(seed, x, z, false);
	}

	/**
	 * Whether a block at (x, z) is solid wall. When {@code topLayer} is true (the layer directly
	 * below the ceiling), arched openings narrow by one block on each side to form an arch silhouette.
	 */
	static boolean isWall(long seed, int x, int z, boolean topLayer) {
		return isWall(seed, x, z, topLayer, EpochSource.ZERO);
	}

	static boolean isWall(long seed, int x, int z, boolean topLayer, EpochSource epochs) {
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		int ix = Math.floorMod(x, CELL), iz = Math.floorMod(z, CELL);
		Zone zone = zoneForCell(seed, cx, cz);
		int epoch = effectiveEpoch(zone, cx, cz, epochs);

		if (ix != 0 && iz != 0) {
			return zone == Zone.PILLARS && epoch > 0 && isShiftedPillar(seed, cx, cz, ix, iz, epoch);
		}
		if (iz != 0) return westEdge(seed, cx, cz, zone, epoch).blocks(iz, topLayer);
		if (ix != 0) return northEdge(seed, cx, cz, zone, epoch).blocks(ix, topLayer);

		if (zone == Zone.PILLARS || zone == Zone.PITS) return true;

		Zone northNeighbor = zoneForCell(seed, cx, cz - 1);
		Zone westNeighbor = zoneForCell(seed, cx - 1, cz);
		return !westEdge(seed, cx, cz, zone, epoch).open()
				|| !westEdge(seed, cx, cz - 1, northNeighbor, effectiveEpoch(northNeighbor, cx, cz - 1, epochs)).open()
				|| !northEdge(seed, cx, cz, zone, epoch).open()
				|| !northEdge(seed, cx - 1, cz, westNeighbor, effectiveEpoch(westNeighbor, cx - 1, cz, epochs)).open()
				|| Math.floorMod(mixEpoch(hash(seed, cx, cz, PILLAR), epoch), 3) == 0;
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

	static boolean lampWorks(long seed, int x, int z) {
		return lampWorks(seed, x, z, EpochSource.ZERO);
	}

	static boolean lampWorks(long seed, int x, int z, EpochSource epochs) {
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		Zone zone = zoneForCell(seed, cx, cz);
		int epoch = effectiveEpoch(zone, cx, cz, epochs);
		long h = mixEpoch(hash(seed, cx, cz, LAMP), epoch);
		return switch (zone) {
			case DARKENED -> false;
			case RED_ROOM -> Math.floorMod(h, 100) < 65;
			case PITS -> Math.floorMod(h, 5) >= 2;
			case PILLARS -> Math.floorMod(h, 10) != 0;
			case ARCHES -> Math.floorMod(h, 7) != 0;
			case CLASSIC -> Math.floorMod(h, 5) != 0;
		};
	}

	private static Edge westEdge(long seed, int cx, int cz, Zone zone, int epoch) {
		int lx = Math.floorMod(cx, SECTOR), lz = Math.floorMod(cz, SECTOR);
		boolean required = lz == 0 || (lx != 0 && pathGoesWest(seed, cx, cz));
		return Edge.random(mixEpoch(hash(seed, cx, cz, WEST), epoch), required, zone, epoch > 0);
	}

	private static Edge northEdge(long seed, int cx, int cz, Zone zone, int epoch) {
		int lx = Math.floorMod(cx, SECTOR), lz = Math.floorMod(cz, SECTOR);
		boolean required = lx == 0 || (lz != 0 && !pathGoesWest(seed, cx, cz));
		return Edge.random(mixEpoch(hash(seed, cx, cz, NORTH), epoch), required, zone, epoch > 0);
	}

	private static boolean pathGoesWest(long seed, int cx, int cz) {
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
	private record Edge(int start, int width, boolean arched) {
		static Edge random(long hash, boolean required, Zone zone, boolean shifted) {
			int roll = Math.floorMod(hash, 100);
			return switch (zone) {
				case PILLARS -> {
					int openReq = shifted ? 70 : 95;
					int openOpt = shifted ? 55 : 90;
					if (roll < (required ? openReq : openOpt)) yield new Edge(1, CELL - 1, false);
					if (!required && shifted && roll >= 85) yield new Edge(1, 0, false);
					if (!required && !shifted) yield new Edge(1, 0, false);
					yield doorway(hash, 3, 4, false);
				}
				case PITS -> {
					if (roll < (required ? 95 : 90)) yield new Edge(1, CELL - 1, false);
					if (!required) yield new Edge(1, 0, false);
					yield doorway(hash, 3, 4, false);
				}
				case ARCHES -> {
					if (roll < (required ? 15 : 10)) yield new Edge(1, CELL - 1, true);
					if (!required && roll >= 55) yield new Edge(1, 0, false);
					yield doorway(hash, 4, 4, true);
				}
				case DARKENED -> {
					if (roll < (required ? 30 : 15)) yield new Edge(1, CELL - 1, false);
					if (!required && roll >= 35) yield new Edge(1, 0, false);
					yield doorway(hash, 2, 3, false);
				}
				case RED_ROOM -> {
					if (roll < (required ? 10 : 5)) yield new Edge(1, CELL - 1, false);
					if (!required && roll >= 15) yield new Edge(1, 0, false);
					yield doorway(hash, 2, 2, false);
				}
				case CLASSIC -> {
					if (roll < (required ? 45 : 25)) yield new Edge(1, CELL - 1, false);
					if (!required && roll >= 45) yield new Edge(1, 0, false);
					yield doorway(hash, 2, 3, false);
				}
			};
		}

		private static Edge doorway(long hash, int minWidth, int maxWidth, boolean arched) {
			int span = maxWidth - minWidth + 1;
			int width = minWidth + (span > 1 ? Math.floorMod(hash >>> 32, span) : 0);
			int start = 1 + Math.floorMod(hash >>> 40, CELL - width);
			return new Edge(start, width, arched);
		}

		boolean open() {
			return width == CELL - 1;
		}

		boolean blocks(int offset, boolean topLayer) {
			if (width == 0) return true;
			if (arched && topLayer && width >= 4) {
				return offset <= start || offset >= start + width - 1;
			}
			return offset < start || offset >= start + width;
		}
	}
}
