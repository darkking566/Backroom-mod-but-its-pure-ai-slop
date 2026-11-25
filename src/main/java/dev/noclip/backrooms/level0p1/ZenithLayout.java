package dev.noclip.backrooms.level0p1;

/**
 * The shape of Level 0.1, "Zenith Station": a hollow sphere of steel and glass, a thick shell of decks around a vast
 * empty middle, with something that is not quite a star hanging in the centre.
 *
 * Everything is a pure function of (seed, x, y, z) that answers with a packed "cell": which kind of thing is in the
 * block, plus a direction and a variant. Nothing here touches Minecraft, so the whole station can be unit-tested
 * (can you walk from the airlock to the middle?) and the chunk generator only has to map kinds to blocks.
 *
 * The sphere is centred on (0, 0, 0). From outside in:
 * <pre>
 *   outer hull   RO-3 .. RO     plating and ribs, nothing gets out
 *   the shell    RI+PROMENADE+1 .. RO-3   decks every 8 blocks (floor at y%8==0, ceiling at y%8==7), laid out as a grid of
 *                16x16 modules: a 4-wide corridor along two sides of each, a 10x10 room in the rest; the odd 48x48x48
 *                atrium (hydroponic hall) hollowed out of it; a spiral stair in a corner of every 48x48 block
 *   bulkhead     one block thick, with wide arches
 *   promenade    10 wide, between the bulkhead and the inner skin, a curved street with windows on the middle
 *   inner skin   2 thick, mostly glass
 *   the middle   radius RI: the singularity, its plasma disc, catwalks out to it along four spokes, rings of girders,
 *                a lake of coolant at the bottom to land in
 * </pre>
 */
final class ZenithLayout {
	/** How far the sea of Level 0 rooms around the station is worked out (beyond this: nothing; the fog hides the edge). */
	static final int SEA = 260, SEA_GAP = 60;
	static final int RO = 480, RI = 176, HULL = 3, SKIN = 2, PROMENADE = 10;
	static final int PITCH = 8, MODULE = 16, GROUP = 3, GROUP_DECKS = 6;
	/** The lake in the bottom of the middle is this deep at the centre. */
	static final int LAKE = 18;
	/** The four spokes run along the corridors that start at 0 on each axis: x in 0..3 and z in 0..3. */
	static final int SPOKE = 4;

	private static final long RO2 = (long) RO * RO;
	private static final long RGAP2 = (long) (RO + SEA_GAP) * (RO + SEA_GAP);
	private static final long ROUT2 = (long) (RO + SEA) * (RO + SEA);
	private static final long HULL2 = (long) (RO - HULL) * (RO - HULL);
	private static final long OUTER2 = (long) (RO - 1) * (RO - 1);
	private static final long RI2 = (long) RI * RI;
	private static final long SKIN2 = (long) (RI + SKIN) * (RI + SKIN);
	private static final long BULK_IN2 = (long) (RI + SKIN + PROMENADE) * (RI + SKIN + PROMENADE);
	private static final long BULK_OUT2 = (long) (RI + SKIN + PROMENADE + 1) * (RI + SKIN + PROMENADE + 1);

	enum Kind {
		AIR(true), HULL, HULL_SEAM, SKIN_PLATE, WINDOW, WINDOW_CLEAR, BULKHEAD,
		FLOOR, FLOOR_CORRIDOR, FLOOR_STRIPE, FLOOR_QUARTERS, FLOOR_KITCHEN, FLOOR_GYM, FLOOR_GARDEN, FLOOR_WORN, FLOOR_HAZARD, FLOOR_FROST, FLOOR_SCORCH,
		CEILING, LIGHT, DEAD_LIGHT, CEILING_FROST, CEILING_SCORCH,
		WALL, WALL_QUARTERS, WALL_KITCHEN, WALL_GYM, WALL_DAMAGED, WALL_FROST, WALL_SCORCH,
		DOOR(true), STAIR, STAIR_CORE, RAIL,
		BED, LOCKER, COUNTER, STOVE, TREADMILL, WEIGHTS, PLANTER, CONSOLE, BROKEN_CONSOLE, SERVER, CRATE, CABLES(true), TABLE, BENCH,
		PORTAL, PORTAL_FRAME, STATIC_TEAR,
		LEAVES, LOG, WATER(true), ORB,
		CATWALK, TRUSS, TETHER, GIRDER, GIRDER_LIGHT, SINGULARITY_CORE, SINGULARITY_SHELL, PLASMA, RAIL_LAMP,
		DESK, WARDROBE, BUNK_BED, FRAME, ITEMS, SINK, FRIDGE, POTS, DISHES, BENCH_PRESS, BAG, MIRROR, DISPENSER, MATS, RACK, GROW_LIGHT, TANK, VINES,
		SPEAKER(false), ICICLES(true), DRIFT(true), SLAG(true), VENT(true),
		SCREEN, COIL, NOTES(true), COAT,
		SEA_WALL, SEA_CARPET, SEA_LIGHT;

		/** Whether a player can walk through it (a closed door counts: it opens). */
		final boolean passable;

		Kind() {
			this(false);
		}

		Kind(boolean passable) {
			this.passable = passable;
		}
	}

	enum Room { BEDROOM, KITCHEN, GYM, GREENHOUSE, CONSOLES, MACHINES, STORAGE, EMPTY, WRECK, AIRLOCK, LAB }

	enum Shape { STANDARD, NARROW_HALL, L_CORNER, CUBICLES, SUNKEN_PIT, RAISED_DAIS, ALCOVE_BAYS, DOUBLE_LOWER, DOUBLE_UPPER }

	// Directions: 0 north (-z), 1 east (+x), 2 south (+z), 3 west (-x)
	private static final int[][] RING = {{0, 0}, {1, 0}, {2, 0}, {3, 0}, {3, 1}, {3, 2}, {3, 3}, {2, 3}, {1, 3}, {0, 3}, {0, 2}, {0, 1}};
	private static final int[] RING_DIR = {1, 1, 1, 2, 2, 2, 3, 3, 3, 0, 0, 0};

	private static final Kind[] KINDS = Kind.values();

	private ZenithLayout() {
	}

	// Packed cells: kind in the low byte, then direction, then a variant.

	static int of(Kind kind) {
		return kind.ordinal();
	}

	static int of(Kind kind, int dir) {
		return kind.ordinal() | dir << 8;
	}

	static int of(Kind kind, int dir, int extra) {
		return kind.ordinal() | dir << 8 | extra << 12;
	}

	static Kind kind(int cell) {
		return KINDS[cell & 0xFF];
	}

	static int dir(int cell) {
		return cell >> 8 & 0xF;
	}

	static int extra(int cell) {
		return cell >> 12;
	}

	static int hash(long seed, int a, int b, int c) {
		long h = seed ^ a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
		h ^= h >>> 32;
		h *= 0xD6E8FEB86659FD93L;
		h ^= h >>> 29;
		h *= 0xD6E8FEB86659FD93L;
		h ^= h >>> 32;
		return (int) (h & 0x7FFFFFFF);
	}

	/** What is at this block. */
	static int cell(long seed, int x, int y, int z) {
		long d2 = (long) x * x + (long) y * y + (long) z * z;
		if (d2 > RO2) return d2 > RGAP2 && d2 <= ROUT2 ? sea(seed, x, y, z) : of(Kind.AIR);
		if (d2 >= HULL2) return hull(seed, x, y, z, d2);
		if (d2 < RI2) return cavity(seed, x, y, z, d2);
		if (d2 < SKIN2) return skin(seed, x, y, z);
		int k = Math.floorDiv(y, PITCH), t = Math.floorMod(y, PITCH);
		if (d2 < BULK_IN2) return promenade(seed, x, y, z, k, t);
		if (d2 < BULK_OUT2) return bulkhead(seed, x, y, z, k, t);
		return modules(seed, x, y, z, k, t);
	}

	/** Whether the whole column of this chunk-sized box is certainly empty, so the generator can skip it. */
	static boolean emptyBox(int minX, int maxX, int minZ, int maxZ) {
		int nx = minX > 0 ? minX : maxX < 0 ? -maxX : 0;
		int nz = minZ > 0 ? minZ : maxZ < 0 ? -maxZ : 0;
		return (long) nx * nx + (long) nz * nz > ROUT2;
	}

	// The outer hull

	private static int hull(long seed, int x, int y, int z, long d2) {
		// Only the outer face of a window has a framed pane; the hull's inner layers are clear glass, so it reads as one window, not three.
		if (window(seed, x, y, z, d2)) return of(d2 >= OUTER2 ? Kind.WINDOW : Kind.WINDOW_CLEAR);
		boolean seam = (x & 15) == 0 || (y & 15) == 0 || (z & 15) == 0;
		if (d2 >= OUTER2) return of(seam ? Kind.HULL_SEAM : Kind.HULL);
		return of(((x + y + z) & 7) == 0 ? Kind.HULL_SEAM : Kind.HULL);
	}

	/** Outer windows: in 4-block patches of the hull, wherever a corridor or room is right behind it, so someone can stand at the glass. */
	private static boolean window(long seed, int x, int y, int z, long d2) {
		if (hash(seed, x >> 2, y >> 2, z >> 2 ^ 0x51) % 3 != 0) return false;
		double scale = (RO - HULL - 3.0) / Math.sqrt(d2);
		int px = (int) Math.round(x * scale), py = (int) Math.round(y * scale), pz = (int) Math.round(z * scale);
		return passable(cell(seed, px, py, pz)) && passable(cell(seed, px, py + 1, pz));
	}

	/**
	 * What lies outside, as the Wiki's story has it: a sea of dim Level 0 rooms in the dark, stacked on one another far
	 * beyond anything a person has seen. Rooms 11 across and 3 high, each floor the ceiling of the room below; holes and
	 * cliffs where whole stretches are missing, doorways here and there, and every so often a lamp still burning.
	 */
	private static int sea(long seed, int x, int y, int z) {
		if (hash(seed, Math.floorDiv(x, 60), Math.floorDiv(y, 24), Math.floorDiv(z, 60)) % 100 < 22) return of(Kind.AIR);
		int cx = Math.floorDiv(x, 12), cy = Math.floorDiv(y, 4), cz = Math.floorDiv(z, 12);
		if (hash(seed, cx, cy, cz + 999) % 10 == 0) return of(Kind.AIR);
		int lx = Math.floorMod(x, 12), lz = Math.floorMod(z, 12), t = Math.floorMod(y, 4);
		if (t == 0) return lx == 5 && lz == 5 && hash(seed, cx, cy, cz + 7) % 5 == 0 ? of(Kind.SEA_LIGHT) : of(Kind.SEA_CARPET);
		if (lx != 0 && lz != 0) return of(Kind.AIR);
		boolean corner = lx == 0 && lz == 0;
		int along = lx == 0 ? lz : lx;
		if (!corner && t <= 2 && along >= 5 && along <= 6 && hash(seed, cx, cy, cz * 2 + (lx == 0 ? 3 : 4)) % 3 == 0) return of(Kind.AIR);
		return of(Kind.SEA_WALL);
	}

	// The inner skin: glass in a frame of ribs, one wedge of glass per sector of the sphere

	private static int skin(long seed, int x, int y, int z) {
		int t = Math.floorMod(y, PITCH);
		// The doorways at the ends of the four spokes, on the equator deck only.
		if (spokeBand(x, y, z)) return y == 0 ? of(Kind.CATWALK) : y <= 5 ? of(Kind.AIR) : of(Kind.SKIN_PLATE);
		if (t == 0 || t == 7) return of(Kind.SKIN_PLATE);
		double sector = Math.atan2(z, x) / (Math.PI * 2) * 96;
		double within = sector - Math.floor(sector);
		if (within < 0.07) return of(Kind.SKIN_PLATE);
		int pane = hash(seed, (int) Math.floor(sector), Math.floorDiv(y, PITCH), 71);
		return pane % 7 == 0 ? of(Kind.SKIN_PLATE) : of(Kind.WINDOW);
	}

	/** Inside a spoke's footprint, in the band of floors around the equator. */
	private static boolean spokeBand(int x, int y, int z) {
		if (y < 0 || y > 7) return false;
		return x >= 0 && x < SPOKE || z >= 0 && z < SPOKE;
	}

	// The promenade: a wide street curving round the inside of the skin

	private static int promenade(long seed, int x, int y, int z, int k, int t) {
		if (t == 0) return of(Kind.FLOOR_CORRIDOR);
		if (t == 7) return (x & 7) == 4 && (z & 7) == 4 ? of(zone(seed, x, y, z) != 0 ? Kind.DEAD_LIGHT : Kind.LIGHT) : of(Kind.CEILING);
		if (t == 1 && hash(seed, x >> 3, z >> 3, k) % 29 == 0 && (x & 7) < 3 && (z & 7) < 2) return of(Kind.BENCH);
		return of(Kind.AIR);
	}

	private static int bulkhead(long seed, int x, int y, int z, int k, int t) {
		if (t == 0) return of(Kind.FLOOR_CORRIDOR);
		if (t == 7) return of(Kind.CEILING);
		if (t <= 4 && (spokeBand(x, y, z) || hash(seed, x >> 3, z >> 3, k) % 3 == 0)) return of(Kind.AIR);
		return of(Kind.BULKHEAD);
	}

	// The shell: modules, atria, stairs

	private static int modules(long seed, int x, int y, int z, int k, int t) {
		int mx = Math.floorDiv(x, MODULE), mz = Math.floorDiv(z, MODULE);
		int u = Math.floorMod(x, MODULE), v = Math.floorMod(z, MODULE);
		if (Math.floorMod(mx, GROUP) == 0 && Math.floorMod(mz, GROUP) == 0 && u < 4 && v < 4) return stairwell(seed, mx, mz, k, u, v, t);
		int ax = Math.floorDiv(mx, GROUP), az = Math.floorDiv(mz, GROUP), kg = Math.floorDiv(k, GROUP_DECKS);
		if (info(seed, mx, mz, k).atrium) {
			int inside = atriumCell(seed, x, z, k, t, kg);
			if (inside >= 0) return inside;
		}
		return deck(seed, x, y, z, k, t, mx, mz, u, v);
	}

	/** One stairwell in the corner of each 48x48 block: a spiral that climbs one deck per turn round a lit pillar. */
	private static int stairwell(long seed, int mx, int mz, int k, int u, int v, int t) {
		boolean damaged = zone(seed, mx, mz, k) != 0;
		if (u >= 1 && u <= 2 && v >= 1 && v <= 2) return of(Kind.STAIR_CORE, 0, damaged ? 1 : 0);
		int i = 0;
		while (RING[i][0] != u || RING[i][1] != v) i++;
		if (i <= 7) {
			int step = (i + 1) & 7;
			if (t == step) return of(Kind.STAIR, RING_DIR[i]);
			// The two blocks above a step are clear, even through the floor and ceiling layers.
			if (t == ((step + 1) & 7) || t == ((step + 2) & 7)) return of(Kind.AIR);
		}
		if (t == 0) return of(Kind.FLOOR_CORRIDOR);
		if (t == 7) return of(Kind.CEILING);
		return of(Kind.AIR);
	}

	/** Roughly one block of 48x48x48 in ten is a hydroponic hall instead of rooms. */
	static boolean atrium(long seed, int ax, int az, int kg) {
		return hash(seed, ax, az, kg * 31 + 7) % 100 < 10;
	}

	/** The inside of an atrium; -1 for the parts that are ordinary deck (the corridors round its west and north edges). */
	private static int atriumCell(long seed, int x, int z, int k, int t, int kg) {
		int lx = Math.floorMod(x, MODULE * GROUP), lz = Math.floorMod(z, MODULE * GROUP);
		int kd = k - kg * GROUP_DECKS;
		if (lx < 4 || lz < 4) return -1;
		boolean ledge = lx <= 46 && lz <= 46 && (lx >= 45 || lz >= 45);
		if (ledge) {
			if (t == 0) return of(Kind.FLOOR_CORRIDOR);
			return t == 7 ? of(Kind.CEILING) : of(Kind.AIR);
		}
		boolean open = lx >= 5 && lx <= 43 && lz >= 5 && lz <= 43;
		if (!open) {
			boolean ring = lx <= 44 && lz <= 44 && (lx == 4 || lx == 44 || lz == 4 || lz == 44);
			if (!ring) return -1;
			if (t == 0) return of(Kind.FLOOR_CORRIDOR);
			if (t == 7) return of(Kind.CEILING);
			// Gaps in the rail where a bridge meets the ring.
			boolean bridgeEnd = kd == 3 && (lx == 4 || lx == 44 ? lz >= 22 && lz <= 25 : lx >= 22 && lx <= 25);
			return t == 1 && !bridgeEnd ? of(Kind.RAIL) : of(Kind.AIR);
		}
		boolean zoneDamaged = zone(seed, Math.floorDiv(x, MODULE), Math.floorDiv(z, MODULE), k) != 0;
		if (kd == 0) return garden(seed, lx, lz, t);
		if (t == 0) return bridge(lx, lz, kd, t);
		if (t == 7) {
			if (kd != GROUP_DECKS - 1) return of(Kind.AIR);
			return lx % 8 == 5 && lz % 8 == 5 ? of(zoneDamaged ? Kind.DEAD_LIGHT : Kind.LIGHT) : of(Kind.CEILING);
		}
		if (t == 1) {
			int rail = bridgeRail(lx, lz, kd);
			if (rail >= 0) return rail;
		}
		// Glowing orbs hung in the air, so the hall is lit all the way up.
		if (t == 4 && kd % 2 == 1 && lx % 12 == 6 && lz % 12 == 6) return of(Kind.ORB);
		return of(Kind.AIR);
	}

	private static int garden(long seed, int lx, int lz, int t) {
		if (t == 0) {
			int pond = hash(seed, lx / 7, lz / 7, 5);
			int px = lx % 7 - 3, pz = lz % 7 - 3;
			if (pond % 6 == 0 && px * px + pz * pz <= 6) return of(Kind.WATER);
			return of(Kind.FLOOR_GARDEN);
		}
		if (t == 7) return of(Kind.AIR);
		// Trees on a loose grid, one in each 8x8 patch (most patches).
		int gi = Math.floorDiv(lx, 8), gj = Math.floorDiv(lz, 8);
		int roll = hash(seed, gi, gj, 9);
		if (roll % 4 != 0) {
			int cx = gi * 8 + 2 + roll / 7 % 4, cz = gj * 8 + 2 + roll / 29 % 4;
			int pond = hash(seed, cx / 7, cz / 7, 5);
			int px = cx % 7 - 3, pz = cz % 7 - 3;
			if (!(pond % 6 == 0 && px * px + pz * pz <= 8)) {
				int dx = lx - cx, dz = lz - cz;
				if (dx == 0 && dz == 0 && t <= 4) return of(Kind.LOG);
				if (t == 1 && lx % 10 == 5 && lz % 10 == 5) return of(Kind.ORB);
				int height = 4 + roll / 101 % 2;
				int dy = t - height;
				if (dx * dx + dz * dz + dy * dy * 2 <= 7 && t >= 3) return of(Kind.LEAVES);
			}
		}
		return of(Kind.AIR);
	}

	private static int bridge(int lx, int lz, int kd, int t) {
		if (kd == 3 && (lx >= 22 && lx <= 25 || lz >= 22 && lz <= 25)) return of(Kind.CATWALK);
		if (kd == 1 && lz >= 12 && lz <= 14) return of(Kind.CATWALK);
		return of(Kind.AIR);
	}

	private static int bridgeRail(int lx, int lz, int kd) {
		boolean crossX = lx >= 22 && lx <= 25, crossZ = lz >= 22 && lz <= 25;
		if (kd == 3) {
			if (crossX && !crossZ) return lx == 22 || lx == 25 ? of(Kind.RAIL) : -1;
			if (crossZ && !crossX) return lz == 22 || lz == 25 ? of(Kind.RAIL) : -1;
		}
		if (kd == 1 && lz >= 12 && lz <= 14) return lz == 12 || lz == 14 ? of(Kind.RAIL) : -1;
		return -1;
	}

	// One deck of modules

	private static int deck(long seed, int x, int y, int z, int k, int t, int mx, int mz, int u, int v) {
		Info info = info(seed, mx, mz, k);
		int zone = info.zone;
		boolean damaged = zone != 0;
		boolean inRoomU = u >= 4 || (info.swallowW && (v >= 4 || info.swallowN));
		boolean inRoomV = v >= 4 || (info.swallowN && (u >= 4 || info.swallowW));
		boolean corridor = !(inRoomU && inRoomV);
		if (t == 7) return ceiling(seed, x, z, u, v, corridor, zone, mx, mz, k, info);
		if (corridor) {
			if (t == 0) return floor(seed, x, z, u, v, true, zone, null, false);
			// Cables hang from the ceiling in the dark stretches.
			if (damaged && t >= 5 && hash(seed, x, z, k) % 9 == 0) return of(Kind.CABLES);
			// Rime hangs from the cold stretches' ceilings and drifts on their floors; the hot ones have slag and venting floor grates.
			if (zone == 1 && t == 6 && hash(seed, x, z, k + 9) % 7 == 0) return of(Kind.ICICLES);
			if (zone == 1 && t == 1 && hash(seed, x, z, k + 10) % 9 == 0) return of(Kind.DRIFT);
			if (zone == 2 && t == 1) {
				int h = Math.floorMod(hash(seed, x, z, k + 10), 14);
				if (h == 0) return of(Kind.SLAG);
				if (h == 1) return of(Kind.VENT);
			}
			return of(Kind.AIR);
		}
		int W = (info.maxMx - info.minMx) * MODULE + 12;
		int H = (info.maxMz - info.minMz) * MODULE + 12;
		int rx = x - (info.minMx * MODULE + 4);
		int rz = z - (info.minMz * MODULE + 4);
		if (info.shape == Shape.DOUBLE_UPPER) return doubleUpperCell(seed, x, z, rx, rz, W, H, t, info);
		if (t == 0) {
			int f = roomFloor(seed, x, z, rx, rz, W, H, info);
			// The pit's bottom is the deck below's ceiling: furniture stands right on it, one block down.
			if (info.shape == Shape.SUNKEN_PIT && kind(f) == Kind.AIR && !isCrossSlot(rx, W) && !isCrossSlot(rz, H))
				return pitFurniture(seed, info.room, mx, mz, k, rx, rz, W, H, 0);
			return f;
		}
		boolean wallW = rx == 0, wallE = rx == W - 1, wallN = rz == 0, wallS = rz == H - 1;
		if (wallW || wallE || wallN || wallS) return wall(seed, info, k, rx, rz, W, H, t, wallW, wallE, wallN, wallS);
		return roomInterior(seed, x, z, mx, mz, k, rx, rz, W, H, t, info);
	}

	private static int ceiling(long seed, int x, int z, int u, int v, boolean corridor, int zone, int mx, int mz, int k, Info info) {
		// If the deck directly above has a sunken pit, this ceiling layer becomes that pit's floor while staying solid below.
		Info above = info(seed, mx, mz, k + 1);
		if (!above.atrium && above.shape == Shape.SUNKEN_PIT && isInRoomFootprint(above, u, v)) {
			int Wa = (above.maxMx - above.minMx) * MODULE + 12;
			int Ha = (above.maxMz - above.minMz) * MODULE + 12;
			int rxa = x - (above.minMx * MODULE + 4);
			int rza = z - (above.minMz * MODULE + 4);
			if (rxa >= 3 && rxa <= Wa - 4 && rza >= 3 && rza <= Ha - 4) {
				return floor(seed, x, z, u, v, false, above.zone, above.room, false);
			}
		}
		if (!corridor && info.shape == Shape.DOUBLE_LOWER) {
			int W = (info.maxMx - info.minMx) * MODULE + 12;
			int H = (info.maxMz - info.minMz) * MODULE + 12;
			int rx = x - (info.minMx * MODULE + 4);
			int rz = z - (info.minMz * MODULE + 4);
			if (rx == 0 || rx == W - 1 || rz == 0 || rz == H - 1) return of(wallKind(info.room, zone, seed, rx, rz, k));
			if (rx == 2 && rz == 1) return of(Kind.STAIR, 1);
			return of(Kind.AIR);
		}
		boolean damaged = zone != 0;
		boolean lamp;
		if (corridor) lamp = v < 4 ? (x & 7) == 4 && v == 1 : (z & 7) == 4 && u == 1;
		else lamp = u == 9 && v == 9 || u == 10 && v == 10;
		// The announcement speakers: one every 16 blocks along a working corridor.
		if (corridor && zone == 0 && !lamp && (v < 4 ? (x & 15) == 0 && v == 2 : (z & 15) == 0 && u == 2)) return of(Kind.SPEAKER);
		if (!lamp) {
			// In the cold and the hot stretches most plates are furred with rime or blackened.
			if (zone == 1 && hash(seed, x, z, k + 5) % 4 != 0) return of(Kind.CEILING_FROST);
			if (zone == 2 && hash(seed, x, z, k + 5) % 4 != 0) return of(Kind.CEILING_SCORCH);
			return of(Kind.CEILING);
		}
		// A few lamps still work in the dark stretches.
		boolean alive = !damaged || hash(seed, x, z, k) % 12 == 0;
		return of(alive ? Kind.LIGHT : Kind.DEAD_LIGHT);
	}

	private static boolean isInRoomFootprint(Info info, int u, int v) {
		boolean inRoomU = u >= 4 || (info.swallowW && (v >= 4 || info.swallowN));
		boolean inRoomV = v >= 4 || (info.swallowN && (u >= 4 || info.swallowW));
		return inRoomU && inRoomV;
	}

	private static int roomFloor(long seed, int x, int z, int rx, int rz, int W, int H, Info info) {
		if (info.shape == Shape.SUNKEN_PIT && rx >= 3 && rx <= W - 4 && rz >= 3 && rz <= H - 4) {
			boolean crossX = isCrossSlot(rx, W), crossZ = isCrossSlot(rz, H);
			if (rz == 3 && crossX) return of(Kind.STAIR, 0);
			if (rz == H - 4 && crossX) return of(Kind.STAIR, 2);
			if (rx == 3 && crossZ) return of(Kind.STAIR, 3);
			if (rx == W - 4 && crossZ) return of(Kind.STAIR, 1);
			// The rim keeps its floor, so its rails stand on something; only the inside is sunk.
			if (rx >= 4 && rx <= W - 5 && rz >= 4 && rz <= H - 5) return of(Kind.AIR);
		}
		boolean hazard = info.room == Room.AIRLOCK && rx >= 3 && rx <= 8 && rz >= 3 && rz <= 8;
		return floor(seed, x, z, rx + 4, rz + 4, false, info.zone, info.room, hazard);
	}

	private static int floor(long seed, int x, int z, int u, int v, boolean corridor, int zone, Room room, boolean hazard) {
		if (zone != 0) {
			// A damaged stretch: worn plates, and in the cold and the hot ones plates under ice or burnt, over half the floor.
			int h = hash(seed, x, z, 3) % 4;
			if (h == 0) return of(Kind.FLOOR_WORN);
			if (h <= 2 && zone == 1) return of(Kind.FLOOR_FROST);
			if (h <= 2 && zone == 2) return of(Kind.FLOOR_SCORCH);
		}
		if (corridor) {
			boolean stripe = u < 4 && v >= 4 ? u == 1 || u == 2 : v < 4 && u >= 4 && (v == 1 || v == 2);
			return of(stripe ? Kind.FLOOR_STRIPE : Kind.FLOOR_CORRIDOR);
		}
		if (hazard) return of(Kind.FLOOR_HAZARD);
		if (room == null) return of(Kind.FLOOR);
		return switch (room) {
			case BEDROOM -> of(Kind.FLOOR_QUARTERS);
			case KITCHEN -> of(Kind.FLOOR_KITCHEN);
			case GYM -> of(Kind.FLOOR_GYM);
			case GREENHOUSE -> of(Kind.FLOOR_GARDEN);
			case WRECK -> of(Kind.FLOOR_WORN);
			default -> of(Kind.FLOOR);
		};
	}

	/** Which of a room's four walls (west, north, east, south) have a door: bit 0 to 3; always at least one. */
	static int doors(long seed, int mx, int mz, int k) {
		return info(seed, mx, mz, k).doors;
	}

	private static int computeDoors(long seed, int mx, int mz, int k) {
		int h = hash(seed, mx, mz, k * 13 + 1);
		int mask = h & 15;
		if (mask == 0) mask = 1 << (h >> 4 & 3);
		// A second door most of the time, so rooms are not dead ends.
		if (Integer.bitCount(mask) == 1 && (h >> 8) % 5 != 0) mask |= 1 << (h >> 12 & 3);
		return mask;
	}

	private static boolean isCrossSlot(int coord, int size) {
		return coord == 5 || coord == 6 || (size > 12 && (coord == 13 || coord == 14 || coord == 21 || coord == 22));
	}

	private static int vestibuleSide(Info info) {
		if (info.shape != Shape.STANDARD || info.room == Room.AIRLOCK || info.variant % 5 != 0) return -1;
		for (int s = 0; s < 4; s++) {
			int side = (info.variant + s) & 3;
			if ((info.doors >> side & 1) != 0) return side;
		}
		return -1;
	}

	private static int wall(long seed, Info info, int k, int rx, int rz, int W, int H, int t, boolean w, boolean e, boolean n, boolean s) {
		boolean alongZ = w || e;
		int along = alongZ ? rz : rx;
		int span = alongZ ? H : W;
		boolean corner = (w || e) && (n || s);
		if (!corner && isCrossSlot(along, span)) {
			int side = w ? 0 : n ? 1 : e ? 2 : 3;
			// Swallowed internal corridors in merged rooms always pass through via doors at 13..14.
			boolean corridorThru = span > 12 && (along == 13 || along == 14);
			boolean open = corridorThru || (info.doors >> side & 1) != 0;
			if (open) {
				boolean recessed = (along == 5 || along == 6) && vestibuleSide(info) == side;
				if (recessed) return t <= 2 ? of(Kind.AIR) : of(wallKind(info.room, info.zone, seed, rx, rz, k));
				if (t <= 2) return of(Kind.DOOR, alongZ ? 1 : 0, ((along & 1) == 1 ? 0 : 1) | (t == 1 ? 0 : 2));
			}
		}
		return of(wallKind(info.room, info.zone, seed, rx, rz, k));
	}

	private static Kind wallKind(Room room, int zone, long seed, int u, int v, int k) {
		if (zone != 0) {
			// Every wall in a damaged stretch shows what is wrong with it: some torn open, the rest frosted (zone 1) or scorched (2).
			int h = hash(seed, u, v, k) % 3;
			if (h == 0) return Kind.WALL_DAMAGED;
			if (zone == 1) return Kind.WALL_FROST;
			if (zone == 2) return Kind.WALL_SCORCH;
		}
		return switch (room) {
			case BEDROOM -> Kind.WALL_QUARTERS;
			case KITCHEN -> Kind.WALL_KITCHEN;
			case GYM -> Kind.WALL_GYM;
			case WRECK -> Kind.WALL_DAMAGED;
			default -> Kind.WALL;
		};
	}

	/** Upper deck (t = 0..6 of k+1) of a two-story chamber: mezzanine balcony, railings, stair top, and hanging orbs. */
	private static int doubleUpperCell(long seed, int x, int z, int rx, int rz, int W, int H, int t, Info info) {
		boolean wallW = rx == 0, wallE = rx == W - 1, wallN = rz == 0, wallS = rz == H - 1;
		boolean onOuter = wallW || wallE || wallN || wallS;
		if (t == 0) {
			if (onOuter) {
				boolean alongZ = wallW || wallE;
				int along = alongZ ? rz : rx;
				int span = alongZ ? H : W;
				boolean corner = (wallW || wallE) && (wallN || wallS);
				if (!corner && isCrossSlot(along, span)) return floor(seed, x, z, rx + 4, rz + 4, false, info.zone, info.room, false);
				return of(wallKind(info.room, info.zone, seed, rx, rz, info.k));
			}
			if (rx == 3 && rz == 1) return of(Kind.STAIR, 1);
			if (rx <= 2 && rz <= 2) return of(Kind.AIR);
			boolean balcony = rx <= 2 || rx >= W - 3 || rz <= 2 || rz >= H - 3;
			if (balcony) return floor(seed, x, z, rx + 4, rz + 4, false, info.zone, info.room, false);
			if (W == 28 && H == 28 && rx >= 12 && rx <= 15 && rz >= 3 && rz <= 24) return of(Kind.CATWALK);
			return of(Kind.AIR);
		}
		if (onOuter) return wall(seed, info, info.k, rx, rz, W, H, t, wallW, wallE, wallN, wallS);
		if (t == 1) {
			if ((rx == 1 || rx == 2) && rz == 3 || (rx == 3 && rz == 2)) return of(Kind.RAIL);
			boolean bridgeRim = W == 28 && H == 28 && (rx == 12 || rx == 15) && rz >= 3 && rz <= 24;
			boolean bridgeOpening = W == 28 && H == 28 && (rx == 13 || rx == 14) && (rz == 2 || rz == 25);
			boolean innerRim = (rx == 2 || rx == W - 3 || rz == 2 || rz == H - 3)
					&& rx >= 2 && rx <= W - 3 && rz >= 2 && rz <= H - 3
					&& !(rx <= 3 && rz <= 2) && !bridgeOpening;
			if (innerRim || bridgeRim) return of(Kind.RAIL);
		}
		if (t == 4 && rx > 3 && rx < W - 4 && rz > 3 && rz < H - 4 && (rx - 5) % 8 == 0 && (rz - 5) % 8 == 0) {
			if (info.zone == 0 || hash(seed, x, z, info.k) % 4 == 0) return of(Kind.ORB);
		}
		return of(Kind.AIR);
	}

	/** Step height (1..6) for the NW corner stair inside a two-story room, or 0 if not on the stair. */
	private static int doubleStairStep(int rx, int rz) {
		if (rx == 3 && rz == 4) return 1;
		if (rx == 2 && rz == 4) return 2;
		if (rx == 1 && rz == 4) return 3;
		if (rx == 1 && rz == 3) return 4;
		if (rx == 1 && rz == 2) return 5;
		if (rx == 1 && rz == 1) return 6;
		return 0;
	}

	private static int doubleStairDir(int rx, int rz) {
		if (rz == 4 && rx >= 2) return 3;
		if (rx == 1 && rz >= 2) return 0;
		return 1;
	}

	/** Maps (rx, rz) into rotated coordinates (px, pz) of size (PW, PH) for directional shapes like RAISED_DAIS. */
	private static int rotX(int rx, int rz, int W, int H, int rot) {
		return switch (rot & 3) {
			case 1 -> rz;
			case 2 -> W - 1 - rx;
			case 3 -> H - 1 - rz;
			default -> rx;
		};
	}

	private static int rotZ(int rx, int rz, int W, int H, int rot) {
		return switch (rot & 3) {
			case 1 -> W - 1 - rx;
			case 2 -> H - 1 - rz;
			case 3 -> rx;
			default -> rz;
		};
	}

	private static int rotDir(int dir, int rot) {
		return (dir + (rot & 3)) & 3;
	}

	/**
	 * The inside of a room (standard 10x10 or merged 26x10 / 10x26 / 26x26), applying depth, height, and partition shapes
	 * before furnishing each 10x10 bay.
	 */
	private static int roomInterior(long seed, int x, int z, int mx, int mz, int k, int rx, int rz, int W, int H, int t, Info info) {
		Room room = info.room;
		Shape shape = info.shape;
		int wallBlock = of(wallKind(room, info.zone, seed, rx, rz, k));

		if (room == Room.AIRLOCK && t <= 3 && rz == 5 && (rx == 4 || rx == 5 || rx == 6)) {
			if (rx == 5) return t <= 2 ? of(Kind.PORTAL, 0, t - 1) : of(Kind.PORTAL_FRAME);
			return of(Kind.PORTAL_FRAME);
		}
		if (room == Room.WRECK && t <= 2 && rx == 5 && rz == 6 && hash(seed, info.minMx, info.minMz, k + 77) % 5 == 0) {
			return of(Kind.STATIC_TEAR);
		}

		// Recessed door vestibule on one standard room wall.
		int vest = vestibuleSide(info);
		if (vest >= 0) {
			if (vest == 0 && rx == 1 && (rz == 4 || rz == 7)) return wallBlock;
			if (vest == 0 && rx == 1 && (rz == 5 || rz == 6)) return t <= 2 ? of(Kind.DOOR, 1, ((rz & 1) == 1 ? 0 : 1) | (t == 1 ? 0 : 2)) : wallBlock;
			if (vest == 1 && rz == 1 && (rx == 4 || rx == 7)) return wallBlock;
			if (vest == 1 && rz == 1 && (rx == 5 || rx == 6)) return t <= 2 ? of(Kind.DOOR, 0, ((rx & 1) == 1 ? 0 : 1) | (t == 1 ? 0 : 2)) : wallBlock;
			if (vest == 2 && rx == W - 2 && (rz == 4 || rz == 7)) return wallBlock;
			if (vest == 2 && rx == W - 2 && (rz == 5 || rz == 6)) return t <= 2 ? of(Kind.DOOR, 1, ((rz & 1) == 1 ? 0 : 1) | (t == 1 ? 0 : 2)) : wallBlock;
			if (vest == 3 && rz == H - 2 && (rx == 4 || rx == 7)) return wallBlock;
			if (vest == 3 && rz == H - 2 && (rx == 5 || rx == 6)) return t <= 2 ? of(Kind.DOOR, 0, ((rx & 1) == 1 ? 0 : 1) | (t == 1 ? 0 : 2)) : wallBlock;
		}

		if (shape == Shape.DOUBLE_LOWER) {
			int step = doubleStairStep(rx, rz);
			if (step > 0) return t == step ? of(Kind.STAIR, doubleStairDir(rx, rz)) : of(Kind.AIR);
			if (rx <= 4 && rz <= 4 && (rx == 4 || rz == 4)) return of(Kind.AIR);
		}

		if (shape == Shape.L_CORNER) {
			int corner = info.variant & 3;
			int limX = W > 12 ? 11 : 4, limZ = H > 12 ? 11 : 4;
			boolean blocked = switch (corner) {
				case 0 -> rx <= limX && rz <= limZ;
				case 1 -> rx >= W - 1 - limX && rz <= limZ;
				case 2 -> rx <= limX && rz >= H - 1 - limZ;
				default -> rx >= W - 1 - limX && rz >= H - 1 - limZ;
			};
			if (blocked) return wallBlock;
		}

		if (shape == Shape.NARROW_HALL) {
			// The corners between the hall's walls and the room's would be closed 2x2 cells with no way in: fill them.
			if ((rz <= 2 || rz >= H - 3) && (rx <= 2 || rx >= W - 3)) return wallBlock;
			boolean alongX = W > H || (W == H && (info.variant & 1) == 0);
			if (alongX) {
				if ((rz == 3 || rz == H - 4) && !isCrossSlot(rx, W)) return wallBlock;
				if ((rz <= 2 || rz >= H - 3) && (rx == 3 || rx == W - 4 || (W > 12 && (rx == 11 || rx == 16)))) return wallBlock;
			} else {
				if ((rx == 3 || rx == W - 4) && !isCrossSlot(rz, H)) return wallBlock;
				if ((rx <= 2 || rx >= W - 3) && (rz == 3 || rz == H - 4 || (H > 12 && (rz == 11 || rz == 16)))) return wallBlock;
			}
		}

		if (shape == Shape.CUBICLES) {
			boolean halfWall = (info.variant & 2) != 0;
			boolean wallX = (rx == 4 || rx == W - 5 || (W > 12 && (rx == 11 || rx == 16))) && !isCrossSlot(rz, H);
			boolean wallZ = (rz == 4 || rz == H - 5 || (H > 12 && (rz == 11 || rz == 16))) && !isCrossSlot(rx, W);
			boolean subDoorX = rz == 2 || rz == H - 3 || (H > 12 && (rz == 18 || rz == 19));
			boolean subDoorZ = rx == 2 || rx == W - 3 || (W > 12 && (rx == 18 || rx == 19));
			boolean divider = (wallX && !subDoorX) || (wallZ && !subDoorZ);
			// Keep the cell either side of each small doorway clear, or a rack or cabinet stands in it.
			if (subDoorX && (cubicleCol(rx - 1, W) || cubicleCol(rx + 1, W)) || subDoorZ && (cubicleCol(rz - 1, H) || cubicleCol(rz + 1, H))) return of(Kind.AIR);
			if (divider && (!halfWall || t <= 3)) return wallBlock;
			if (divider) return of(Kind.AIR);
		}

		if (shape == Shape.ALCOVE_BAYS) {
			boolean pilaster = ((rx == 1 || rx == W - 2) && (rz == 3 || rz == H - 4 || (H > 12 && (rz == 11 || rz == 16))))
					|| ((rz == 1 || rz == H - 2) && (rx == 3 || rx == W - 4 || (W > 12 && (rx == 11 || rx == 16))));
			boolean centerCol = W == 28 && H == 28 && (rx == 11 || rx == 16) && (rz == 11 || rz == 16);
			boolean soffit = t == 6 && (rx == 3 || rx == W - 4 || rz == 3 || rz == H - 4
					|| (W > 12 && (rx == 11 || rx == 16)) || (H > 12 && (rz == 11 || rz == 16)));
			if (pilaster || centerCol || soffit) return wallBlock;
		}

		if (shape == Shape.SUNKEN_PIT) {
			boolean onRim = (rx == 3 || rx == W - 4) && rz >= 3 && rz <= H - 4
					|| (rz == 3 || rz == H - 4) && rx >= 3 && rx <= W - 4;
			if (onRim && t == 1 && !isCrossSlot(rx, W) && !isCrossSlot(rz, H)) return of(Kind.RAIL);
			if (onRim) return of(Kind.AIR);
			if (rx >= 4 && rx <= W - 5 && rz >= 4 && rz <= H - 5) {
				if (isCrossSlot(rx, W) || isCrossSlot(rz, H)) return of(Kind.AIR);
				return pitFurniture(seed, room, mx, mz, k, rx, rz, W, H, t);
			}
		}

		if (shape == Shape.RAISED_DAIS) {
			int rot = W != H ? (W > H ? ((info.variant & 1) == 0 ? 0 : 2) : ((info.variant & 1) == 0 ? 1 : 3)) : (info.variant & 3);
			int px = rotX(rx, rz, W, H, rot);
			int pz = rotZ(rx, rz, W, H, rot);
			int PW = (rot & 1) == 0 ? W : H;
			boolean inLane = isCrossSlot(px, PW);
			if (pz >= 1 && pz <= 3) {
				if (!inLane) {
					if (t == 1) return floor(seed, x, z, rx + 4, rz + 4, false, info.zone, room, false);
					if (t == 6) return of(wallKind(room, info.zone, seed, rx, rz, k));
					return furnishCoords(seed, room, mx, mz, k, rx, rz, W, H, t - 1);
				}
				if (t == 6) return of(wallKind(room, info.zone, seed, rx, rz, k));
				return of(Kind.AIR);
			}
			if (pz == 4 && !inLane && t == 1) return of(Kind.STAIR, rotDir(0, rot));
			if (pz == 4 && !inLane) return of(Kind.AIR);
			if (pz >= 1 && pz <= 3 && inLane) return of(Kind.AIR);
		}

		if (W == 28 && H == 28 && (rx == 11 || rx == 16) && (rz == 11 || rz == 16)) return wallBlock;
		int cell = furnishCoords(seed, room, mx, mz, k, rx, rz, W, H, t);
		// A two-storey room has no ceiling of its own: what would hang from it is left out.
		if (shape == Shape.DOUBLE_LOWER && hangsFromCeiling(kind(cell))) return of(Kind.AIR);
		return cell;
	}

	private static boolean cubicleCol(int c, int size) {
		return c == 4 || c == size - 5 || (size > 12 && (c == 11 || c == 16));
	}

	static boolean hangsFromCeiling(Kind kind) {
		return kind == Kind.GROW_LIGHT || kind == Kind.VINES || kind == Kind.CABLES || kind == Kind.ICICLES;
	}

	static boolean hangsOnWall(Kind kind) {
		return kind == Kind.FRAME || kind == Kind.MIRROR || kind == Kind.POTS || kind == Kind.SCREEN || kind == Kind.COAT;
	}

	/** Inside a sunken pit floor furniture rests one block lower; what hangs from the ceiling stays at the ceiling. */
	private static int pitFurniture(long seed, Room room, int mx, int mz, int k, int rx, int rz, int W, int H, int t) {
		int up = t >= 1 ? furnishCoords(seed, room, mx, mz, k, rx, rz, W, H, t) : of(Kind.AIR);
		if (hangsFromCeiling(kind(up))) return up;
		int down = t <= 5 ? furnishCoords(seed, room, mx, mz, k, rx, rz, W, H, t + 1) : of(Kind.AIR);
		return hangsFromCeiling(kind(down)) ? of(Kind.AIR) : down;
	}

	/** A bay's furniture, minus anything hung on a wall that is not there (a merged hall opens up the bays' inner walls). */
	private static int furnishCoords(long seed, Room room, int mx, int mz, int k, int rx, int rz, int W, int H, int t) {
		// In a merged hall the bays' inner walls are gone: what stood against them would stand in the middle of the hall.
		if (W > 12 && (rx == 10 || rx == 17) || H > 12 && (rz == 10 || rz == 17)) return of(Kind.AIR);
		int cell = furnishBay(seed, room, mx, mz, k, rx, rz, W, H, t);
		if (!hangsOnWall(kind(cell))) return cell;
		int d = dir(cell); // the wall is behind, opposite the way it faces
		int bx = rx - (d == 1 ? 1 : d == 3 ? -1 : 0), bz = rz - (d == 2 ? 1 : d == 0 ? -1 : 0);
		return bx <= 0 || bx >= W - 1 || bz <= 0 || bz >= H - 1 ? cell : of(Kind.AIR);
	}

	private static int furnishBay(long seed, Room room, int mx, int mz, int k, int rx, int rz, int W, int H, int t) {
		if (isCrossSlot(rx, W) || isCrossSlot(rz, H)) return of(Kind.AIR);
		int bx = rx <= 10 ? rx : (rx >= 17 ? rx - 16 : -1);
		int bz = rz <= 10 ? rz : (rz >= 17 ? rz - 16 : -1);
		if (bx < 0 || bz < 0) {
			// In the swallowed corridor strip of a merged hall, place occasional wall-side crates, tables, or planters.
			boolean againstWall = rx == 1 || rx == W - 2 || rz == 1 || rz == H - 2;
			if (!againstWall || t != 1) return of(Kind.AIR);
			int r = hash(seed, rx + mx * 16, rz + mz * 16, k + 19);
			if (r % 3 != 0) return of(Kind.AIR);
			return switch (room) {
				case GREENHOUSE -> of(Kind.PLANTER);
				case MACHINES, CONSOLES -> of(Kind.SERVER, rz == 1 ? 2 : rz == H - 2 ? 0 : rx == 1 ? 1 : 3);
				case KITCHEN, BEDROOM -> of(Kind.TABLE);
				default -> of(Kind.CRATE);
			};
		}
		boolean west = bx < 5, north = bz < 5;
		int a = west ? bx - 1 : 10 - bx; // 0 at the outer/bay edge, 3 next to the bay cross
		int b = north ? bz - 1 : 10 - bz;
		int faceV = north ? 2 : 0, faceU = west ? 1 : 3;
		int roll = hash(seed, rx + mx * 16, rz + mz * 16, k);
		return switch (room) {
			case BEDROOM -> bedroom(a, b, t, faceV, roll, hash(seed, mx, mz, k + 31));
			case KITCHEN -> kitchen(a, b, t, faceV, roll);
			case GYM -> gym(a, b, t, faceV, faceU, roll);
			case GREENHOUSE -> greenhouse(a, b, t, faceV, roll);
			case CONSOLES -> consoles(a, b, t, faceV, faceU);
			case LAB -> lab(a, b, t, faceV, faceU, roll);
			case MACHINES -> machines(a, b, t, faceU);
			case STORAGE -> storage(a, b, t, faceV, roll);
			case EMPTY -> t == 1 && a == 0 && b == 0 && roll % 3 == 0 ? of(Kind.CRATE) : of(Kind.AIR);
			case WRECK -> wreck(a, b, t, faceV, roll);
			case AIRLOCK -> airlock(a, b, t, faceV, faceU);
		};
	}

	private static int bedroom(int a, int b, int t, int face, int roll, int room) {
		// The beds: plain ones, or bunks in a third of the rooms.
		if (t == 1 && b == 0 && a <= 2) return of(room % 3 == 0 ? Kind.BUNK_BED : Kind.BED, face);
		// A picture over the middle bed.
		if (t == 2 && b == 0 && a == 1 && room % 4 != 3) return of(Kind.FRAME, face);
		// A wardrobe standing open, or the plain locker.
		// One block tall: the models are whole pieces, and a second one on top reads as two stacked.
		if (b == 0 && a == 3 && t == 1) return room % 2 == 0 ? of(Kind.WARDROBE, face) : of(Kind.LOCKER, face);
		// A desk with a lamp, or the plain table with someone's things on it.
		if (a == 2 && b == 3 && roll % 2 == 0) {
			if (t == 1) return room % 2 == 0 ? of(Kind.DESK, (face + 2) % 4) : of(Kind.TABLE); // drawers toward the wall side
			if (t == 2 && room % 2 != 0) return of(Kind.ITEMS);
		}
		return of(Kind.AIR);
	}

	private static int kitchen(int a, int b, int t, int face, int roll) {
		if (t == 1 && b == 0 && a <= 2) return a == 1 ? of(Kind.SINK, face) : of(Kind.COUNTER, face);
		if (t == 1 && b == 0 && a == 3) return of(Kind.STOVE, face);
		if (t == 2 && b == 0 && (a == 0 || a == 2)) return of(Kind.POTS, face);
		if (a == 1 && b == 2) {
			if (t == 1) return of(Kind.TABLE);
			if (t == 2) return of(Kind.DISHES);
		}
		if (a == 3 && b == 3 && t == 1) return roll % 2 == 0 ? of(Kind.FRIDGE, face) : of(Kind.LOCKER, face);
		return of(Kind.AIR);
	}

	private static int gym(int a, int b, int t, int face, int faceU, int roll) {
		if (t == 1 && b == 0 && a <= 2) return of(Kind.TREADMILL, face);
		if (t == 2 && b == 0 && a == 1) return of(Kind.MIRROR, face);
		if (t == 1 && b == 3 && a == 0) return of(Kind.WEIGHTS, faceU);
		if (t == 1 && b == 3 && a == 1) return of(Kind.BENCH_PRESS, face);
		if (t == 1 && a == 3 && b == 0) return of(Kind.BAG);
		if (t == 1 && a == 0 && b == 1) return of(Kind.DISPENSER, faceU);
		if (t == 1 && a == 2 && b == 3 && roll % 2 == 0) return of(Kind.MATS);
		if (t == 1 && a == 3 && b == 3 && roll % 2 == 0) return of(Kind.BENCH, faceU); // long side toward the wall side
		return of(Kind.AIR);
	}

	private static int greenhouse(int a, int b, int t, int face, int roll) {
		// Tiered racks along the wall in every other row, the single planters and bushes in the rest.
		if (a == 0 && b % 2 == 0 && t <= 2) return of(Kind.RACK);
		if (a <= 1) {
			if (t == 1) return of(Kind.PLANTER);
			if (t == 2 && roll % 3 != 0) return of(Kind.LEAVES);
		}
		if (a == 3 && b == 0 && t == 1) return of(Kind.TANK, face);
		if (t == 6 && a == 3 && b == 3) return of(Kind.ORB);
		if (t == 6 && a == 1 && (b == 1 || b == 3)) return of(Kind.GROW_LIGHT);
		if (t == 5 && a == 1 && (b == 1 || b == 3) && roll % 2 == 0) return of(Kind.VINES);
		return of(Kind.AIR);
	}

	private static int consoles(int a, int b, int t, int face, int faceU) {
		if (t == 1 && b == 0) return of(Kind.CONSOLE, face);
		if (t == 1 && a == 0 && b == 1) return of(Kind.CONSOLE, faceU);
		// A wall screen above the consoles, stopped at zero.
		if (t == 2 && b == 0 && a == 2) return of(Kind.SCREEN, face);
		return of(Kind.AIR);
	}

	/** The wormhole lab: an emitter coil in one corner, benches along the walls with a stopped countdown over them, server racks in another. */
	private static int lab(int a, int b, int t, int face, int faceU, int roll) {
		boolean coilCorner = face == 2 && faceU == 1, serverCorner = face == 2 && faceU == 3;
		if (coilCorner) {
			if (a == 1 && b == 1 && t <= 3) return of(Kind.COIL);
			if (t == 1 && a == 1 && b == 2 && roll % 2 == 0) return of(Kind.NOTES);
			return of(Kind.AIR);
		}
		if (serverCorner) return t <= 3 && b == 0 && a <= 2 ? of(Kind.SERVER, face) : of(Kind.AIR);
		if (t == 1 && b == 0 && a >= 2) return of(Kind.TABLE);
		if (t == 2 && b == 0 && a == 3) return of(Kind.ITEMS);
		if (t == 2 && b == 0 && a == 1) return of(Kind.SCREEN, face);
		if (t == 1 && b == 0 && a == 0) return of(Kind.CONSOLE, face);
		return of(Kind.AIR);
	}

	/** Beside the yellow doorway, in one corner of each airlock: Rhea's notes and her lab coat, as if she had stood here. */
	private static int airlock(int a, int b, int t, int face, int faceU) {
		if (t == 1 && a == 0 && b == 0) return of(Kind.CRATE);
		if (face == 2 && faceU == 1) {
			if (t == 1 && a == 1 && b == 1) return of(Kind.NOTES);
			if (t == 2 && a == 3 && b == 0) return of(Kind.COAT, face);
		}
		return of(Kind.AIR);
	}

	private static int machines(int a, int b, int t, int face) {
		if (t <= 3 && a <= 1) return of(Kind.SERVER, face);
		return of(Kind.AIR);
	}

	private static int storage(int a, int b, int t, int face, int roll) {
		if (t == 1 && b == 0) return roll % 2 == 0 ? of(Kind.LOCKER, face) : of(Kind.CRATE);
		if (t == 1 && roll % 4 == 0) return of(Kind.CRATE);
		if (t == 2 && roll % 11 == 0) return of(Kind.CRATE);
		return of(Kind.AIR);
	}

	private static int wreck(int a, int b, int t, int face, int roll) {
		if (t == 1 && b == 0 && a <= 1) return of(Kind.BROKEN_CONSOLE, face);
		if (t == 1 && roll % 6 == 0) return of(Kind.CRATE);
		if (t >= 4 && roll % 4 == 0) return of(Kind.CABLES);
		return of(Kind.AIR);
	}

	/** What a room is for. The rooms nearest the outer hull may be airlocks, which hold a way out to Level 0. */
	static Room room(long seed, int mx, int mz, int k) {
		return info(seed, mx, mz, k).room;
	}

	static Shape shape(long seed, int mx, int mz, int k) {
		return info(seed, mx, mz, k).shape;
	}

	static boolean isMerged(long seed, int mx, int mz, int k) {
		Info i = info(seed, mx, mz, k);
		return i.maxMx > i.minMx || i.maxMz > i.minMz;
	}

	static int[] roomBounds(long seed, int mx, int mz, int k) {
		Info i = info(seed, mx, mz, k);
		return new int[] {i.minMx, i.minMz, i.maxMx, i.maxMz};
	}

	private static boolean isAirlockCandidate(long seed, int mx, int mz, int k) {
		double cx = mx * MODULE + 9.5, cy = k * PITCH + 4, cz = mz * MODULE + 9.5;
		double distance = Math.sqrt(cx * cx + cy * cy + cz * cz);
		int h = hash(seed, mx, mz, k * 7 + 3);
		return distance >= RO - 40 && distance <= RO - 14 && h % 100 < 38;
	}

	/** Group-local merge rect {minMx, minMz, maxMx, maxMz}. Never crosses 48x48 group boundaries or swallows airlocks. */
	private static int[] mergeRect(long seed, int mx, int mz, int k) {
		int ax = Math.floorDiv(mx, GROUP), az = Math.floorDiv(mz, GROUP);
		if (atrium(seed, ax, az, Math.floorDiv(k, GROUP_DECKS))) return new int[] {mx, mz, mx, mz};
		int gx = Math.floorMod(mx, GROUP), gz = Math.floorMod(mz, GROUP);
		int p = hash(seed, ax, az, k * 19 + 5) % 100;
		int gx0 = gx, gz0 = gz, gx1 = gx, gz1 = gz;
		if (p >= 55 && p < 67) {
			if (gx >= 1 && gz >= 1) { gx0 = 1; gz0 = 1; gx1 = 2; gz1 = 2; }
		} else if (p >= 67 && p < 78) {
			if (gx >= 1 && (gz == 0 || gz == 2)) { gx0 = 1; gx1 = 2; }
		} else if (p >= 78 && p < 89) {
			if (gz >= 1 && (gx == 0 || gx == 2)) { gz0 = 1; gz1 = 2; }
		} else if (p >= 89) {
			if (gx >= 1 && gz == 0) { gx0 = 1; gx1 = 2; }
			else if (gx == 0 && gz >= 1) { gz0 = 1; gz1 = 2; }
		}
		if (gx0 == gx1 && gz0 == gz1) return new int[] {mx, mz, mx, mz};
		int minMx = ax * GROUP + gx0, minMz = az * GROUP + gz0;
		int maxMx = ax * GROUP + gx1, maxMz = az * GROUP + gz1;
		for (int cx = minMx; cx <= maxMx; cx++) {
			for (int cz = minMz; cz <= maxMz; cz++) {
				if (isAirlockCandidate(seed, cx, cz, k)) return new int[] {mx, mz, mx, mz};
			}
		}
		return new int[] {minMx, minMz, maxMx, maxMz};
	}

	private static boolean isDoubleLowerRect(long seed, int minMx, int minMz, int maxMx, int maxMz, int k) {
		if ((k & 1) != 0 || Math.floorMod(k, GROUP_DECKS) >= GROUP_DECKS - 1) return false;
		int ax = Math.floorDiv(minMx, GROUP), az = Math.floorDiv(minMz, GROUP);
		if (atrium(seed, ax, az, Math.floorDiv(k, GROUP_DECKS))) return false;
		int[] upper = mergeRect(seed, minMx, minMz, k + 1);
		if (upper[0] != minMx || upper[1] != minMz || upper[2] != maxMx || upper[3] != maxMz) return false;
		for (int cx = minMx; cx <= maxMx; cx++) {
			for (int cz = minMz; cz <= maxMz; cz++) {
				if (isAirlockCandidate(seed, cx, cz, k) || isAirlockCandidate(seed, cx, cz, k + 1)) return false;
			}
		}
		int h = hash(seed, minMx, minMz, k * 23 + 11) % 100;
		boolean quad = maxMx > minMx && maxMz > minMz;
		return h < (quad ? 25 : 10);
	}

	private static boolean isDoubleUpperModule(long seed, int mx, int mz, int k) {
		if ((k & 1) == 0) return false;
		int[] lower = mergeRect(seed, mx, mz, k - 1);
		return isDoubleLowerRect(seed, lower[0], lower[1], lower[2], lower[3], k - 1);
	}

	private static Shape computeShape(long seed, int minMx, int minMz, int maxMx, int maxMz, int k, Room room) {
		if (room == Room.AIRLOCK) return Shape.STANDARD;
		if (isDoubleLowerRect(seed, minMx, minMz, maxMx, maxMz, k)) return Shape.DOUBLE_LOWER;
		if ((k & 1) != 0) {
			int[] lower = mergeRect(seed, minMx, minMz, k - 1);
			if (lower[0] == minMx && lower[1] == minMz && lower[2] == maxMx && lower[3] == maxMz
					&& isDoubleLowerRect(seed, lower[0], lower[1], lower[2], lower[3], k - 1)) {
				return Shape.DOUBLE_UPPER;
			}
		}
		int s = hash(seed, minMx, minMz, k * 29 + 17) % 100;
		if (s < 42) return Shape.STANDARD;
		if (s < 53) return Shape.NARROW_HALL;
		if (s < 63) return Shape.L_CORNER;
		if (s < 73) return Shape.CUBICLES;
		if (s < 82) {
			boolean canSink = !atrium(seed, Math.floorDiv(minMx, GROUP), Math.floorDiv(minMz, GROUP), Math.floorDiv(k - 1, GROUP_DECKS));
			for (int cx = minMx; cx <= maxMx && canSink; cx++) {
				for (int cz = minMz; cz <= maxMz && canSink; cz++) {
					if (isDoubleUpperModule(seed, cx, cz, k - 1)) canSink = false;
				}
			}
			return canSink ? Shape.SUNKEN_PIT : Shape.ALCOVE_BAYS;
		}
		if (s < 91) return Shape.RAISED_DAIS;
		return Shape.ALCOVE_BAYS;
	}

	private static Room computeRoom(long seed, int mx, int mz, int k, int zone) {
		if (isAirlockCandidate(seed, mx, mz, k)) return Room.AIRLOCK;
		int h = hash(seed, mx, mz, k * 7 + 3);
		if (zone != 0 && h % 100 < 60) return Room.WRECK;
		int roll = h / 100 % 100;
		if (roll < 20) return Room.BEDROOM;
		if (roll < 30) return Room.KITCHEN;
		if (roll < 40) return Room.GYM;
		if (roll < 52) return Room.GREENHOUSE;
		if (roll < 64) return Room.CONSOLES;
		if (roll < 76) return Room.MACHINES;
		if (roll < 88) return Room.STORAGE;
		if (roll < 92) return Room.LAB;
		return roll < 96 ? Room.EMPTY : Room.WRECK;
	}

	/** 0 normal; 1 a dead, freezing stretch; 2 a dead, scorching one; 3 merely dark. Zones are 48x48 blocks and 3 decks. */
	static int zone(long seed, int mx, int mz, int k) {
		return info(seed, mx, mz, k).zone;
	}

	private static int computeZone(long seed, int mx, int mz, int k) {
		int h = hash(seed, Math.floorDiv(mx, GROUP), Math.floorDiv(mz, GROUP), Math.floorDiv(k, 3) + 1000) % 100;
		if (h < 8) return 1;
		if (h < 15) return 2;
		return h < 22 ? 3 : 0;
	}

	static int zoneAt(long seed, int x, int y, int z) {
		return zone(seed, Math.floorDiv(x, MODULE), Math.floorDiv(z, MODULE), Math.floorDiv(y, PITCH));
	}

	// The middle

	private static int cavity(long seed, int x, int y, int z, long d2) {
		if (d2 < 49) return of(Kind.SINGULARITY_CORE);
		if (d2 < 144) return of(Kind.SINGULARITY_SHELL);
		// A one-block skin round the dark ball, white-hot along the equator where the disc touches it, so the ball has a bright edge.
		if (d2 < 169) return Math.abs(y) <= 2 ? of(Kind.PLASMA, 0, 0) : of(Kind.SINGULARITY_SHELL);
		long rho2 = (long) x * x + (long) z * z;
		if (y >= -1 && y <= 2 && rho2 >= 256 && rho2 <= 1296) {
			// The plasma disc: hotter and whiter towards the inside, in rings.
			int ring = (int) ((Math.sqrt(rho2) - 16) / 6);
			return of(Kind.PLASMA, 0, Math.min(ring, 3));
		}
		// The ring walkway round the singularity, and the four spokes out to the skin.
		boolean onRing = rho2 >= 40 * 40 && rho2 <= 46 * 46;
		if (y == 0 && (onRing || spokeOut(x, z))) return of(Kind.CATWALK);
		if (y == 1) {
			boolean spokeAxis = x >= 0 && x < SPOKE || z >= 0 && z < SPOKE;
			if (onRing && !spokeAxis && (rho2 < 41 * 41 || rho2 > 45 * 45)) return (x + z & 7) == 0 ? of(Kind.RAIL_LAMP) : of(Kind.RAIL);
			if (spokeOut(x, z) && (x == 0 || x == SPOKE - 1 || z == 0 || z == SPOKE - 1)) {
				int along = x >= 0 && x < SPOKE ? z : x;
				return Math.floorMod(along, 8) == 0 ? of(Kind.RAIL_LAMP) : of(Kind.RAIL);
			}
		}
		if (gateFrame(x, y, z) != 0) return of(Kind.TRUSS);
		int girder = girders(x, y, z);
		if (girder != 0) return of(girder == 2 ? Kind.GIRDER_LIGHT : Kind.GIRDER);
		if (rho2 <= 6 && Math.abs(y) >= 12) return of(Math.abs(y) % 10 == 0 ? Kind.GIRDER_LIGHT : Kind.TETHER);
		if (y <= -RI + LAKE) return of(Kind.WATER);
		return of(Kind.AIR);
	}

	/** Whether a block at y == 0 is on one of the four spokes, outside the ring walkway. */
	private static boolean spokeOut(int x, int z) {
		boolean alongZ = x >= 0 && x < SPOKE && Math.abs(z) > 46;
		boolean alongX = z >= 0 && z < SPOKE && Math.abs(x) > 46;
		return alongZ || alongX;
	}

	/** Arches over the spokes: two posts and a lintel every 24 blocks. Returns non-zero inside the frame. */
	private static int gateFrame(int x, int y, int z) {
		if (y < 1 || y > 7) return 0;
		boolean zSpoke = Math.abs(z) > 50 && Math.floorMod(z, 24) == 0 && x >= -1 && x <= SPOKE;
		if (zSpoke && (x == -1 || x == SPOKE || y == 7)) return 1;
		boolean xSpoke = Math.abs(x) > 50 && Math.floorMod(x, 24) == 0 && z >= -1 && z <= SPOKE;
		if (xSpoke && (z == -1 || z == SPOKE || y == 7)) return 1;
		return 0;
	}

	private static final int[][] GIRDER_RINGS = {{70, 100}, {-70, 100}, {130, 95}, {-130, 95}};

	/** Rings of girders hung in the middle, well clear of the spokes: 1 a girder, 2 one with a lamp in it. */
	private static int girders(int x, int y, int z) {
		for (int[] ring : GIRDER_RINGS) {
			int dy = y - ring[0];
			if (dy < -3 || dy > 3) continue;
			double rho = Math.sqrt((double) x * x + (double) z * z);
			double dr = rho - ring[1];
			if (dr * dr + dy * dy > 6.5) continue;
			double angle = Math.atan2(z, x);
			int bead = (int) Math.floor(angle * ring[1] / 12.0);
			boolean lamp = dy == 0 && Math.abs(dr) < 1.2 && Math.abs(angle * ring[1] / 12.0 - bead - 0.5) < 0.12;
			return lamp ? 2 : 1;
		}
		return 0;
	}

	/** What is the same for every block of one module on one deck, worked out once (a chunk asks thousands of times). */
	private static final class Info {
		long seed;
		int mx, mz, k;
		boolean valid;
		Room room;
		Shape shape;
		int zone, doors, variant;
		int minMx, minMz, maxMx, maxMz;
		boolean swallowW, swallowN;
		boolean atrium;
	}

	private static final ThreadLocal<Info[]> CACHE = ThreadLocal.withInitial(() -> new Info[2048]);

	private static Info info(long seed, int mx, int mz, int k) {
		Info[] cache = CACHE.get();
		int slot = ((mx * 73856093) ^ (mz * 19349663) ^ (k * 83492791) ^ (int) seed) & 2047;
		Info info = cache[slot];
		if (info == null) info = cache[slot] = new Info();
		if (info.valid && info.mx == mx && info.mz == mz && info.k == k && info.seed == seed) return info;
		info.seed = seed;
		info.mx = mx;
		info.mz = mz;
		info.k = k;
		info.atrium = atrium(seed, Math.floorDiv(mx, GROUP), Math.floorDiv(mz, GROUP), Math.floorDiv(k, GROUP_DECKS));
		info.zone = computeZone(seed, mx, mz, k);
		int[] rect = mergeRect(seed, mx, mz, k);
		info.minMx = rect[0];
		info.minMz = rect[1];
		info.maxMx = rect[2];
		info.maxMz = rect[3];
		info.swallowW = mx > info.minMx;
		info.swallowN = mz > info.minMz;
		Room baseRoom = computeRoom(seed, info.minMx, info.minMz, k, info.zone);
		info.shape = info.atrium ? Shape.STANDARD : computeShape(seed, info.minMx, info.minMz, info.maxMx, info.maxMz, k, baseRoom);
		if (info.shape == Shape.DOUBLE_UPPER) {
			int lowerZone = computeZone(seed, info.minMx, info.minMz, k - 1);
			info.room = computeRoom(seed, info.minMx, info.minMz, k - 1, lowerZone);
			info.doors = computeDoors(seed, info.minMx, info.minMz, k - 1);
			info.variant = hash(seed, info.minMx, info.minMz, (k - 1) * 37 + 13);
		} else {
			info.room = baseRoom;
			info.doors = computeDoors(seed, info.minMx, info.minMz, k);
			info.variant = hash(seed, info.minMx, info.minMz, k * 37 + 13);
		}
		info.valid = true;
		return info;
	}

	// Walking about: landing places

	/** Where a block of kind stands for walking: whether feet can pass through it. */
	static boolean passable(int cell) {
		return kind(cell).passable;
	}

	/**
	 * A place to arrive: in an airlock room near the outer hull at the equator, looking for the one nearest to the
	 * given point's direction. Returns {x, y, z} of the floor-top (feet) position.
	 */
	static int[] landing(long seed, int fromX, int fromZ) {
		double angle = (hash(seed, fromX >> 6, fromZ >> 6, 99) % 3600) / 3600.0 * Math.PI * 2;
		int targetX = (int) Math.round(Math.cos(angle) * (RO - 28)), targetZ = (int) Math.round(Math.sin(angle) * (RO - 28));
		int mx0 = Math.floorDiv(targetX, MODULE), mz0 = Math.floorDiv(targetZ, MODULE);
		for (int ring = 0; ring < 24; ring++) {
			for (int dk : new int[] {0, -1, 1}) {
				for (int i = -ring; i <= ring; i++) {
					for (int j = -ring; j <= ring; j++) {
						if (Math.max(Math.abs(i), Math.abs(j)) != ring) continue;
						int mx = mx0 + i, mz = mz0 + j;
						if (room(seed, mx, mz, dk) != Room.AIRLOCK) continue;
						if (atrium(seed, Math.floorDiv(mx, GROUP), Math.floorDiv(mz, GROUP), Math.floorDiv(dk, GROUP_DECKS))) continue;
						return new int[] {mx * MODULE + 7, dk * PITCH + 1, mz * MODULE + 7};
					}
				}
			}
		}
		return new int[] {1, 1, RO - 60};
	}
}
