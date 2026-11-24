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
	static final int RO = 480, RI = 176, HULL = 3, SKIN = 2, PROMENADE = 10;
	static final int PITCH = 8, MODULE = 16, GROUP = 3, GROUP_DECKS = 6;
	/** The lake in the bottom of the middle is this deep at the centre. */
	static final int LAKE = 18;
	/** The four spokes run along the corridors that start at 0 on each axis: x in 0..3 and z in 0..3. */
	static final int SPOKE = 4;

	private static final long RO2 = (long) RO * RO;
	private static final long HULL2 = (long) (RO - HULL) * (RO - HULL);
	private static final long OUTER2 = (long) (RO - 1) * (RO - 1);
	private static final long RI2 = (long) RI * RI;
	private static final long SKIN2 = (long) (RI + SKIN) * (RI + SKIN);
	private static final long BULK_IN2 = (long) (RI + SKIN + PROMENADE) * (RI + SKIN + PROMENADE);
	private static final long BULK_OUT2 = (long) (RI + SKIN + PROMENADE + 1) * (RI + SKIN + PROMENADE + 1);

	enum Kind {
		AIR(true), HULL, HULL_SEAM, SKIN_PLATE, WINDOW, BULKHEAD,
		FLOOR, FLOOR_CORRIDOR, FLOOR_STRIPE, FLOOR_QUARTERS, FLOOR_KITCHEN, FLOOR_GYM, FLOOR_GARDEN, FLOOR_WORN, FLOOR_HAZARD, FLOOR_FROST, FLOOR_SCORCH,
		CEILING, LIGHT, DEAD_LIGHT, CEILING_FROST, CEILING_SCORCH,
		WALL, WALL_QUARTERS, WALL_KITCHEN, WALL_GYM, WALL_DAMAGED, WALL_FROST, WALL_SCORCH,
		DOOR(true), STAIR, STAIR_CORE, RAIL,
		BED, LOCKER, COUNTER, STOVE, TREADMILL, WEIGHTS, PLANTER, CONSOLE, BROKEN_CONSOLE, SERVER, CRATE, CABLES(true), TABLE, BENCH,
		PORTAL, PORTAL_FRAME, STATIC_TEAR,
		LEAVES, LOG, WATER(true), ORB,
		CATWALK, TRUSS, TETHER, GIRDER, GIRDER_LIGHT, SINGULARITY_CORE, SINGULARITY_SHELL, PLASMA, RAIL_LAMP;

		/** Whether a player can walk through it (a closed door counts: it opens). */
		final boolean passable;

		Kind() {
			this(false);
		}

		Kind(boolean passable) {
			this.passable = passable;
		}
	}

	enum Room { BEDROOM, KITCHEN, GYM, GREENHOUSE, CONSOLES, MACHINES, STORAGE, EMPTY, WRECK, AIRLOCK }

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
		if (d2 > RO2) return of(Kind.AIR);
		if (d2 >= HULL2) return hull(x, y, z, d2);
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
		return (long) nx * nx + (long) nz * nz > RO2;
	}

	// The outer hull

	private static int hull(int x, int y, int z, long d2) {
		boolean seam = (x & 15) == 0 || (y & 15) == 0 || (z & 15) == 0;
		if (d2 >= OUTER2) return of(seam ? Kind.HULL_SEAM : Kind.HULL);
		return of(((x + y + z) & 7) == 0 ? Kind.HULL_SEAM : Kind.HULL);
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
		int zone = zone(seed, mx, mz, k);
		boolean damaged = zone != 0;
		boolean corridor = u < 4 || v < 4;
		Room room = corridor ? null : room(seed, mx, mz, k);
		if (t == 7) return ceiling(seed, x, z, u, v, corridor, zone, mx, mz, k);
		if (t == 0) return floor(seed, x, z, u, v, corridor, zone, room);
		if (corridor) {
			// Cables hang from the ceiling in the dark stretches.
			if (damaged && t >= 5 && hash(seed, x, z, k) % 9 == 0) return of(Kind.CABLES);
			return of(Kind.AIR);
		}
		boolean wallW = u == 4, wallE = u == 15, wallN = v == 4, wallS = v == 15;
		if (wallW || wallE || wallN || wallS) return wall(seed, mx, mz, k, u, v, t, wallW, wallE, wallN, wallS, room, zone);
		return furnish(seed, room, mx, mz, k, u, v, t, damaged);
	}

	private static int ceiling(long seed, int x, int z, int u, int v, boolean corridor, int zone, int mx, int mz, int k) {
		boolean damaged = zone != 0;
		boolean lamp;
		if (corridor) lamp = v < 4 ? (x & 7) == 4 && v == 1 : (z & 7) == 4 && u == 1;
		else lamp = u == 9 && v == 9 || u == 10 && v == 10;
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

	private static int floor(long seed, int x, int z, int u, int v, boolean corridor, int zone, Room room) {
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
		return switch (room) {
			case BEDROOM -> of(Kind.FLOOR_QUARTERS);
			case KITCHEN -> of(Kind.FLOOR_KITCHEN);
			case GYM -> of(Kind.FLOOR_GYM);
			case GREENHOUSE -> of(Kind.FLOOR_GARDEN);
			case WRECK -> of(Kind.FLOOR_WORN);
			case AIRLOCK -> u >= 7 && u <= 12 && v >= 7 && v <= 12 ? of(Kind.FLOOR_HAZARD) : of(Kind.FLOOR);
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

	private static int wall(long seed, int mx, int mz, int k, int u, int v, int t, boolean w, boolean e, boolean n, boolean s,
			Room room, int zone) {
		int mask = doors(seed, mx, mz, k);
		boolean alongZ = w || e; // the wall runs along z, so the door is at v 9 and 10
		int along = alongZ ? v : u;
		boolean corner = (w || e) && (n || s);
		if (!corner && along >= 9 && along <= 10) {
			int side = w ? 0 : n ? 1 : e ? 2 : 3;
			if ((mask >> side & 1) != 0) {
				if (t <= 2) return of(Kind.DOOR, alongZ ? 1 : 0, (along == 9 ? 0 : 1) | (t == 1 ? 0 : 2));
				return of(wallKind(room, zone, seed, u, v, k));
			}
		}
		return of(wallKind(room, zone, seed, u, v, k));
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

	/**
	 * The inside of a room. A cross of free floor runs through the middle (where the doors are), and each of the four
	 * quadrants (4x4) is filled by the room's kind. a and b count blocks away from the quadrant's own corner walls.
	 */
	private static int furnish(long seed, Room room, int mx, int mz, int k, int u, int v, int t, boolean damaged) {
		boolean crossU = u == 9 || u == 10, crossV = v == 9 || v == 10;
		if (room == Room.AIRLOCK && t <= 3 && v == 9 && (u == 8 || u == 10 || u == 9)) {
			if (u == 9) return t <= 2 ? of(Kind.PORTAL, 0, t - 1) : of(Kind.PORTAL_FRAME);
			return of(Kind.PORTAL_FRAME);
		}
		if (room == Room.WRECK && t <= 2 && u == 9 && v == 10 && hash(seed, mx, mz, k + 77) % 5 == 0) return of(Kind.STATIC_TEAR);
		if (crossU || crossV) return of(Kind.AIR);
		boolean west = u < 9, north = v < 9;
		int a = west ? u - 5 : 14 - u; // 0 at the east/west wall, up to 3 next to the cross
		int b = north ? v - 5 : 14 - v;
		// Which way things face: towards the middle of the room.
		int faceV = north ? 2 : 0, faceU = west ? 1 : 3;
		int roll = hash(seed, u + mx * 16, v + mz * 16, k);
		return switch (room) {
			case BEDROOM -> bedroom(a, b, t, faceV, roll);
			case KITCHEN -> kitchen(a, b, t, faceV);
			case GYM -> gym(a, b, t, faceV, roll);
			case GREENHOUSE -> greenhouse(a, b, t, roll);
			case CONSOLES -> consoles(a, b, t, faceV);
			case MACHINES -> machines(a, b, t, faceU);
			case STORAGE -> storage(a, b, t, faceV, roll);
			case EMPTY -> t == 1 && a == 0 && b == 0 && roll % 3 == 0 ? of(Kind.CRATE) : of(Kind.AIR);
			case WRECK -> wreck(a, b, t, faceV, roll);
			case AIRLOCK -> t == 1 && a == 0 && b == 0 ? of(Kind.CRATE) : of(Kind.AIR);
		};
	}

	private static int bedroom(int a, int b, int t, int face, int roll) {
		if (t == 1 && b == 0 && a <= 2) return of(Kind.BED, face);
		if (t == 1 && b == 0 && a == 3) return of(Kind.LOCKER, face);
		if (t == 2 && b == 0 && a == 3) return of(Kind.LOCKER, face, 1);
		if (t == 1 && a == 2 && b == 3 && roll % 2 == 0) return of(Kind.TABLE);
		return of(Kind.AIR);
	}

	private static int kitchen(int a, int b, int t, int face) {
		if (t == 1 && b == 0 && a <= 2) return of(Kind.COUNTER, face);
		if (t == 1 && b == 0 && a == 3) return of(Kind.STOVE, face);
		if (t == 1 && a == 1 && b == 2) return of(Kind.TABLE);
		if (t == 1 && a == 3 && b == 3) return of(Kind.LOCKER, face);
		if (t == 2 && a == 3 && b == 3) return of(Kind.LOCKER, face, 1);
		return of(Kind.AIR);
	}

	private static int gym(int a, int b, int t, int face, int roll) {
		if (t == 1 && b == 0 && a <= 2) return of(Kind.TREADMILL, face);
		if (t == 1 && b == 3 && a <= 1) return of(Kind.WEIGHTS, face);
		if (t == 1 && a == 3 && b == 3 && roll % 2 == 0) return of(Kind.BENCH);
		return of(Kind.AIR);
	}

	private static int greenhouse(int a, int b, int t, int roll) {
		if (a <= 1) {
			if (t == 1) return of(Kind.PLANTER);
			if (t == 2 && roll % 3 != 0) return of(Kind.LEAVES);
		}
		if (t == 6 && a == 3 && b == 3) return of(Kind.ORB);
		return of(Kind.AIR);
	}

	private static int consoles(int a, int b, int t, int face) {
		if (t == 1 && b == 0) return of(Kind.CONSOLE, face);
		if (t == 1 && a == 0 && b == 1) return of(Kind.CONSOLE, face);
		return of(Kind.AIR);
	}

	private static int machines(int a, int b, int t, int face) {
		if (t <= 3 && a <= 1) return of(Kind.SERVER, face);
		return of(Kind.AIR);
	}

	private static int storage(int a, int b, int t, int face, int roll) {
		if (t == 1 && b == 0) return roll % 2 == 0 ? of(Kind.LOCKER, face) : of(Kind.CRATE);
		if (t == 2 && b == 0 && roll % 2 == 0) return of(Kind.LOCKER, face, 1);
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

	private static Room computeRoom(long seed, int mx, int mz, int k, int zone) {
		double cx = mx * MODULE + 9.5, cy = k * PITCH + 4, cz = mz * MODULE + 9.5;
		double distance = Math.sqrt(cx * cx + cy * cy + cz * cz);
		int h = hash(seed, mx, mz, k * 7 + 3);
		if (distance >= RO - 40 && distance <= RO - 14 && h % 100 < 38) return Room.AIRLOCK;
		if (zone != 0 && h % 100 < 60) return Room.WRECK;
		int roll = h / 100 % 100;
		if (roll < 20) return Room.BEDROOM;
		if (roll < 30) return Room.KITCHEN;
		if (roll < 40) return Room.GYM;
		if (roll < 52) return Room.GREENHOUSE;
		if (roll < 64) return Room.CONSOLES;
		if (roll < 76) return Room.MACHINES;
		if (roll < 88) return Room.STORAGE;
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
		int zone, doors;
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
		info.zone = computeZone(seed, mx, mz, k);
		info.room = computeRoom(seed, mx, mz, k, info.zone);
		info.doors = computeDoors(seed, mx, mz, k);
		info.atrium = atrium(seed, Math.floorDiv(mx, GROUP), Math.floorDiv(mz, GROUP), Math.floorDiv(k, GROUP_DECKS));
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
