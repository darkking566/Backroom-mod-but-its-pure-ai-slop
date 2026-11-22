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

	private static final int TREE = 1, WEST = 2, NORTH = 3, PILLAR = 4, LAMP = 5, ZONE = 6, FEATURE = 7, WARNING = 8;

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

	/** Whether a wall block should show peeling yellow wallpaper exposing dark red underneath. */
	static boolean isRedWarningWall(long seed, int x, int z) {
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		if (!nearRedRoom(seed, cx, cz)) return false;
		return (hash(seed, x, z, WARNING) & 1) == 0;
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
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		int ix = Math.floorMod(x, CELL), iz = Math.floorMod(z, CELL);

		if (ix != 0 && iz != 0) return false;
		if (iz != 0) return westEdge(seed, cx, cz).blocks(iz, topLayer);
		if (ix != 0) return northEdge(seed, cx, cz).blocks(ix, topLayer);

		Zone zone = zoneForCell(seed, cx, cz);
		if (zone == Zone.PILLARS || zone == Zone.PITS) return true;

		return !westEdge(seed, cx, cz).open() || !westEdge(seed, cx, cz - 1).open()
				|| !northEdge(seed, cx, cz).open() || !northEdge(seed, cx - 1, cz).open()
				|| Math.floorMod(hash(seed, cx, cz, PILLAR), 3) == 0;
	}

	static boolean isLamp(int x, int z) {
		int ix = Math.floorMod(x, CELL);
		return (ix == CENTER || ix == CENTER + 1) && Math.floorMod(z, CELL) == CENTER;
	}

	static boolean lampWorks(long seed, int x, int z) {
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		Zone zone = zoneForCell(seed, cx, cz);
		return switch (zone) {
			case DARKENED -> false;
			case RED_ROOM -> Math.floorMod(hash(seed, cx, cz, LAMP), 100) < 65;
			case PITS -> Math.floorMod(hash(seed, cx, cz, LAMP), 5) >= 2;
			case PILLARS -> Math.floorMod(hash(seed, cx, cz, LAMP), 10) != 0;
			case ARCHES -> Math.floorMod(hash(seed, cx, cz, LAMP), 7) != 0;
			case CLASSIC -> Math.floorMod(hash(seed, cx, cz, LAMP), 5) != 0;
		};
	}

	private static Edge westEdge(long seed, int cx, int cz) {
		int lx = Math.floorMod(cx, SECTOR), lz = Math.floorMod(cz, SECTOR);
		boolean required = lz == 0 || (lx != 0 && pathGoesWest(seed, cx, cz));
		return Edge.random(hash(seed, cx, cz, WEST), required, zoneForCell(seed, cx, cz));
	}

	private static Edge northEdge(long seed, int cx, int cz) {
		int lx = Math.floorMod(cx, SECTOR), lz = Math.floorMod(cz, SECTOR);
		boolean required = lx == 0 || (lz != 0 && !pathGoesWest(seed, cx, cz));
		return Edge.random(hash(seed, cx, cz, NORTH), required, zoneForCell(seed, cx, cz));
	}

	private static boolean pathGoesWest(long seed, int cx, int cz) {
		return (hash(seed, cx, cz, TREE) & 1) == 0;
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
		static Edge random(long hash, boolean required, Zone zone) {
			int roll = Math.floorMod(hash, 100);
			return switch (zone) {
				case PILLARS, PITS -> {
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
