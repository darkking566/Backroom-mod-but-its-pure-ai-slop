package dev.noclip.backrooms.level1;

import java.util.SplittableRandom;

/**
 * The Level 1 floor plan as a pure function of seed and block position, so chunks can be generated in any order.
 *
 * The level is cut into 64x64 districts. Most are parking or warehouse halls whose pillar grid runs on without a
 * seam from one district to the next; about a third are blocks of tight white corridors with small rooms.
 * A district owns the line of blocks along its west and north edges. A line only carries a wall where corridors
 * meet something, or where a hall happens to be divided, and every such wall has openings, so each district
 * reaches all four neighbours and the whole level is one connected space.
 */
public final class Level1Layout {
	public static final int DISTRICT = 64;
	static final int CELL = 4;
	static final int CELLS = DISTRICT / CELL;
	/** Pillars stand every BAY_X blocks along X, in rows every BAY_Z blocks along Z. */
	static final int BAY_X = 9;
	static final int BAY_Z = 16;
	private static final int KIND = 1, EDGE = 2, LAMP = 3, LEAK = 4, PUDDLE = 5, BASE = 8;
	/** The share of hall districts that hold a settlement. */
	private static final int BASE_PERCENT = 7;
	static final int PLAN = 7;

	public enum Kind {
		PARKING,
		WAREHOUSE,
		CORRIDORS
	}

	/** The wiki's sectors of the Habitable Zone: broad regions that dress the same halls in other materials. */
	public enum Sector {
		TALON, GILD, GOTHIC, TAILCHASE, GARDEN, LEGEND
	}

	private static final int SECTOR = 9, SECTOR_DISTRICTS = 4;

	/** The sector at (x, z): square regions four districts wide; the one around the origin is always Talon. */
	public static Sector sector(long seed, int x, int z) {
		int size = DISTRICT * SECTOR_DISTRICTS, rx = Math.floorDiv(x, size), rz = Math.floorDiv(z, size);
		if (rx == 0 && rz == 0) return Sector.TALON;
		int roll = Math.floorMod(hash(seed, rx, rz, SECTOR), 100);
		if (roll < 40) return Sector.TALON;
		if (roll < 58) return Sector.GILD;
		if (roll < 70) return Sector.GOTHIC;
		if (roll < 80) return Sector.GARDEN;
		if (roll < 90) return Sector.LEGEND;
		return Sector.TAILCHASE;
	}

	/**
	 * The way to Level 2: "head toward where the pipes and gauges get thicker". Some regions have a pipe zone around their
	 * centre, copper pillars crowding in toward the middle. Returns how far from that centre (0 at it, 1 at the edge), or -1.
	 */
	public static double pipeZone(long seed, int x, int z) {
		int size = DISTRICT * SECTOR_DISTRICTS, rx = Math.floorDiv(x, size), rz = Math.floorDiv(z, size);
		if ((rx == 0 && rz == 0) || Math.floorMod(hash(seed, rx, rz, SECTOR + 1), 100) >= 15) return -1;
		double distance = Math.hypot(x - (rx * size + size / 2.0), z - (rz * size + size / 2.0)) / 100.0;
		return distance < 1 ? distance : -1;
	}

	/** The doorways of the rooms in a corridor district, each with the lock that hangs in it. */
	static java.util.List<CorridorPlan.Doorway> doorways(long seed, int dx, int dz) {
		return kind(seed, dx, dz) == Kind.CORRIDORS ? CorridorPlan.of(seed, dx, dz).doorways() : java.util.List.of();
	}

	/**
	 * How tangled the "glitch trail" is at (x, z): some corridor districts are a trail that grows more and more broken
	 * toward its middle (0 at the centre, 1 at the edge), which is the way on to Level 38; -1 elsewhere.
	 */
	public static double glitchZone(long seed, int x, int z) {
		int dx = Math.floorDiv(x, DISTRICT), dz = Math.floorDiv(z, DISTRICT);
		if (kind(seed, dx, dz) != Kind.CORRIDORS || Math.floorMod(hash(seed, dx, dz, SECTOR + 2), 100) >= 12) return -1;
		double distance = Math.hypot(x - (dx * DISTRICT + DISTRICT / 2.0), z - (dz * DISTRICT + DISTRICT / 2.0)) / 40.0;
		return distance < 1 ? distance : -1;
	}

	/** The settlements players have found in Level 1. */
	public enum Base {
		NONE,
		/** The M.E.G. outpost, in a parking hall. */
		FIRST_LIGHT,
		/** The colonists' camp, in a warehouse. */
		BLINK,
		/** B.N.T.G.'s Trader's Keep: barter counters and a vault, in the Gild Sector. */
		TRADERS_KEEP,
		/** Hippocrates-1, the Ariane Circle's clinic, in the Gothic Sector. */
		HIPPOCRATES,
		/** Tom's Restaurant: the biggest place to eat and meet. */
		TOMS,
		/** A Speednoclipper sign-up point: a booth and a couple of tents. */
		SIGNUP,
		/** Cornucopia, the self-appointed justice seekers' fort, in the Legend Sector. */
		CORNUCOPIA
	}

	/** What fills a column between the floor and the ceiling. */
	public enum Tile {
		// Halls: a five-block ceiling.
		FLOOR, LINE, DAMP, PUDDLE, PILLAR, SHELF, HALL_WALL,
		// Corridors: a three-block ceiling.
		CORRIDOR, DESK, ROOM_SHELF, BED, TABLE, CHAIR, FLOOR_PAINTING, WALL, BRICK, PADDED, GALLERY, DOOR, BOOKSHELF, PARTITION, BIN,
		// The wall around a corridor block, and the low doorways through it.
		PERIMETER, OPENING, OPENING_SIGN,
		// Exits to other levels (see HallPlan): a service room, a floor hatch, a lone door frame, a stairwell.
		EXIT_CORE, HATCH, FRAME, STAIRWELL,
		// Props in the halls (see HallPlan.Prop): booth walls, a room's floor, a machine, a crate.
		BOOTH, ROOM, MACHINE, CRATE,
		// Settlements (see BasePlan): the ring of barriers, and what stands inside.
		BARRIER, FURNITURE;

		public boolean walkable() {
			return switch (this) {
				case FLOOR, LINE, DAMP, PUDDLE, CORRIDOR, OPENING, OPENING_SIGN, HATCH, FRAME, ROOM, FLOOR_PAINTING -> true;
				default -> false;
			};
		}
	}

	/** What hangs from the ceiling of a column: at the top of a hall, or just under a corridor ceiling. */
	public enum Ceiling {
		NONE, BEAM, LAMP, DEAD_LAMP, PIPE, LEAK, BULB, DEAD_BULB, EXIT_BULB
	}

	/** The wall along the west or north edge of a district: a bit per open slot. */
	record Edge(int open, boolean corridor, boolean signs) {
		boolean isOpen(int slot) {
			return (open >>> slot & 1) != 0;
		}
	}

	/** The bottom block of an exit door, in a wall that runs along Z. */
	public record Door(int x, int z) {
	}

	private Level1Layout() {
	}

	public static Kind kind(long seed, int dx, int dz) {
		// Everyone who comes in near the origin lands in a parking hall.
		if (dx == 0 && dz == 0) return Kind.PARKING;
		int roll = Math.floorMod(hash(seed, dx, dz, KIND), 100);
		if (roll < 48) return Kind.PARKING;
		if (roll < 70) return Kind.WAREHOUSE;
		return Kind.CORRIDORS;
	}

	public static Base base(long seed, int dx, int dz) {
		if (dx == 0 && dz == 0) return Base.NONE;
		Kind kind = kind(seed, dx, dz);
		if (kind == Kind.CORRIDORS) return Base.NONE;
		int roll = Math.floorMod(hash(seed, dx, dz, BASE), 100);
		if (roll >= BASE_PERCENT + 6) return Base.NONE;
		Sector sector = sector(seed, dx * DISTRICT + DISTRICT / 2, dz * DISTRICT + DISTRICT / 2);
		if (sector == Sector.GARDEN || sector == Sector.TAILCHASE) return Base.NONE;
		// The extra rolls past the old settlements: a restaurant, then a sign-up point, in any habitable sector.
		if (roll >= BASE_PERCENT) return roll < BASE_PERCENT + 3 ? Base.TOMS : Base.SIGNUP;
		return switch (sector) {
			case GILD -> Base.TRADERS_KEEP;
			case GOTHIC -> Base.HIPPOCRATES;
			case LEGEND -> Base.CORNUCOPIA;
			default -> kind == Kind.PARKING ? Base.FIRST_LIGHT : Base.BLINK;
		};
	}

	/** The settlement piece at (x, z), or null. */
	static BasePlan.Piece basePiece(long seed, int x, int z) {
		int dx = Math.floorDiv(x, DISTRICT), dz = Math.floorDiv(z, DISTRICT);
		Base base = base(seed, dx, dz);
		if (base == Base.NONE) return null;
		return BasePlan.piece(base, Math.floorMod(x, DISTRICT), Math.floorMod(z, DISTRICT));
	}

	public static Kind kindAt(long seed, int x, int z) {
		return kind(seed, Math.floorDiv(x, DISTRICT), Math.floorDiv(z, DISTRICT));
	}

	public static Tile tile(long seed, int x, int z) {
		int dx = Math.floorDiv(x, DISTRICT), dz = Math.floorDiv(z, DISTRICT);
		int lx = Math.floorMod(x, DISTRICT), lz = Math.floorMod(z, DISTRICT);
		if (lx == 0 || lz == 0) {
			Tile line = lineTile(seed, dx, dz, lx, lz);
			if (line != null) return line;
		}
		Kind kind = kind(seed, dx, dz);
		if (kind == Kind.CORRIDORS) return CorridorPlan.of(seed, dx, dz).tile(lx, lz);
		return hallTile(seed, kind, x, z);
	}

	public static Ceiling ceiling(long seed, int x, int z) {
		int dx = Math.floorDiv(x, DISTRICT), dz = Math.floorDiv(z, DISTRICT);
		Kind kind = kind(seed, dx, dz);
		if (kind == Kind.CORRIDORS) {
			int lx = Math.floorMod(x, DISTRICT), lz = Math.floorMod(z, DISTRICT);
			if (lx % CELL != CELL / 2 || lz % CELL != CELL / 2) return Ceiling.NONE;
			return CorridorPlan.of(seed, dx, dz).bulb(lx / CELL, lz / CELL);
		}
		int bx = Math.floorMod(x, BAY_X), bz = Math.floorMod(z, BAY_Z);
		if (bz < 2) return Ceiling.BEAM;
		if (bz == 8) return leaky(seed, x, z) ? Ceiling.LEAK : Ceiling.PIPE;
		if (bx == 5 && (bz == 4 || bz == 12)) {
			int works = kind == Kind.PARKING ? 88 : 80;
			return Math.floorMod(hash(seed, x, z, LAMP), 100) < works ? Ceiling.LAMP : Ceiling.DEAD_LAMP;
		}
		return Ceiling.NONE;
	}

	private static Tile hallTile(long seed, Kind kind, int x, int z) {
		HallPlan.Cell cell = exitCell(seed, x, z);
		if (cell != null) {
			Tile exit = exitTile(cell);
			if (exit != null) return exit;
		}
		HallPlan.PropCell prop = propCell(seed, x, z);
		if (prop != null) {
			Tile tile = propTile(prop);
			if (tile != null) return tile;
		}
		int bx = Math.floorMod(x, BAY_X), bz = Math.floorMod(z, BAY_Z);
		if (bx < 2 && bz < 2) return Tile.PILLAR;
		Base base = base(seed, Math.floorDiv(x, DISTRICT), Math.floorDiv(z, DISTRICT));
		boolean settled = base != Base.NONE && BasePlan.inside(Math.floorMod(x, DISTRICT), Math.floorMod(z, DISTRICT));
		if (settled) {
			BasePlan.Piece piece = BasePlan.piece(base, Math.floorMod(x, DISTRICT), Math.floorMod(z, DISTRICT));
			if (piece != null) {
				switch (piece) {
					case BARRIER_X, BARRIER_Z, SIGN_X, SIGN_Z -> {
						return Tile.BARRIER;
					}
					case TENT_FLOOR -> {
						return Tile.ROOM;
					}
					default -> {
						return Tile.FURNITURE;
					}
				}
			}
		}
		// Racks leave the aisle and the pillar line free, with a gap at every pillar; the camp has cleared them away.
		if (kind == Kind.WAREHOUSE && !settled && bx >= 2 && bx <= 7 && (bz == 4 || bz == 5 || bz == 11 || bz == 12)) return Tile.SHELF;
		int wet = wetness(seed, x, z);
		if (wet == 2) return Tile.PUDDLE;
		if (wet == 1) return Tile.DAMP;
		// Stall lines run along Z through the two rows of parking spaces between pillar lines.
		boolean stalls = bz >= 2 && bz <= 6 || bz >= 11;
		if (kind == Kind.PARKING && stalls && (bx == 1 || bx == 4 || bx == 7)) return Tile.LINE;
		return Tile.FLOOR;
	}

	/** The exit footprint at (x, z) in a hall, or null. */
	static HallPlan.Cell exitCell(long seed, int x, int z) {
		int dx = Math.floorDiv(x, DISTRICT), dz = Math.floorDiv(z, DISTRICT);
		if (kind(seed, dx, dz) == Kind.CORRIDORS) return null;
		return HallPlan.of(seed, dx, dz).cell(x, z);
	}

	/** The prop footprint at (x, z) in a hall, or null. */
	static HallPlan.PropCell propCell(long seed, int x, int z) {
		int dx = Math.floorDiv(x, DISTRICT), dz = Math.floorDiv(z, DISTRICT);
		if (kind(seed, dx, dz) == Kind.CORRIDORS) return null;
		return HallPlan.of(seed, dx, dz).propCell(x, z);
	}

	/** The toll booths, generator rooms and eyed crates in a district. */
	public static java.util.List<HallPlan.PlacedProp> props(long seed, int dx, int dz) {
		return HallPlan.of(seed, dx, dz).props();
	}

	/** The tile of a prop footprint, or null where the ordinary hall floor shows (inside a booth, under the barrier arm). */
	private static Tile propTile(HallPlan.PropCell cell) {
		int bx = cell.bx(), bz = cell.bz();
		HallPlan.PlacedProp prop = cell.prop();
		return switch (prop.prop()) {
			case TOLL_BOOTH -> {
				if (bx == 5) yield Tile.BOOTH; // the barrier's housing
				if (bx > 5 || bx == 3 && bz >= 8) yield null; // under the arm; the booth's inside and its doorway
				yield Tile.BOOTH;
			}
			case GENERATOR_ROOM -> {
				int x = prop.x0() + bx, z = prop.z0() + bz;
				int[] door = prop.door();
				if (x == door[0] && z == door[1]) yield Tile.ROOM;
				if (bx == 3 || bx == 7 || bz == 6 || bz == 10) yield Tile.EXIT_CORE;
				if (bx == 5 && bz == 8) yield Tile.MACHINE;
				int[] drum = generatorDrum(prop);
				yield bx == drum[0] && bz == drum[1] ? Tile.MACHINE : Tile.ROOM;
			}
			case EYED_CRATE -> Tile.CRATE;
		};
	}

	/** The fuel drum stands in one inside corner of a generator room, in bay coordinates. */
	static int[] generatorDrum(HallPlan.PlacedProp prop) {
		int[][] corners = {{4, 7}, {6, 7}, {6, 9}, {4, 9}};
		return corners[prop.variant()];
	}

	/** The exits in a district; none in corridor blocks, whose way out is the metal door home. */
	public static java.util.List<HallPlan.Placed> exits(long seed, int dx, int dz) {
		return HallPlan.of(seed, dx, dz).placed();
	}

	/** The tile of an exit footprint, or null where the hall shows through (around a stairwell). */
	private static Tile exitTile(HallPlan.Cell cell) {
		int bx = cell.bx(), bz = cell.bz();
		boolean shaft = bx >= 4 && bx <= 5;
		return switch (cell.placed().exit().shape) {
			case CORE_DOOR, CORE_PAINTING, CORE_OUTLETS -> Tile.EXIT_CORE;
			case PILLAR -> Tile.PILLAR;
			case HATCH -> Tile.HATCH;
			case FRAME -> Tile.FRAME;
			case STAIRS_DOWN -> shaft && bz >= 3 && bz <= 6 ? Tile.STAIRWELL : null;
			case STAIRS_UP -> shaft && bz >= 3 && bz <= 9 ? Tile.STAIRWELL : null;
		};
	}

	/** 2 under a leak, 1 around it, 0 dry. Leaks only hang over the aisle, so only nearby rows can be wet. */
	private static int wetness(long seed, int x, int z) {
		int bz = Math.floorMod(z, BAY_Z);
		if (bz < 5 || bz > 11) return 0;
		int pipeZ = z - bz + 8;
		int wet = 0;
		for (int px = x - 3; px <= x + 3; px++) {
			if (!leaksAt(seed, px, pipeZ)) continue;
			long h = hash(seed, px, pipeZ, PUDDLE);
			double radius = 0.9 + Math.floorMod(h, 10) / 10.0;
			double stretch = 1.0 + Math.floorMod(h >>> 8, 7) / 10.0; // puddles spread along the aisle
			double rough = Math.floorMod(hash(seed, x, z, PUDDLE + 1), 5) / 10.0;
			double distance = Math.hypot((x - px) / stretch, z - pipeZ) + rough;
			if (distance <= radius) return 2;
			if (distance <= radius + 1.0) wet = 1;
		}
		return wet;
	}

	private static boolean leaky(long seed, int x, int z) {
		return Math.floorMod(hash(seed, x, z, LEAK), 29) == 0;
	}

	/** A leaking pipe really hangs here: not over corridors and not inside a wall. */
	private static boolean leaksAt(long seed, int x, int z) {
		if (!leaky(seed, x, z)) return false;
		int dx = Math.floorDiv(x, DISTRICT), dz = Math.floorDiv(z, DISTRICT);
		if (kind(seed, dx, dz) == Kind.CORRIDORS) return false;
		int lx = Math.floorMod(x, DISTRICT), lz = Math.floorMod(z, DISTRICT);
		return lx != 0 && lz != 0 || lineTile(seed, dx, dz, lx, lz) == null;
	}

	/** The tile on a district's own west or north line, or null where that line has no wall. */
	private static Tile lineTile(long seed, int dx, int dz, int lx, int lz) {
		if (lx == 0 && lz == 0) return corner(seed, dx, dz);
		boolean west = lx == 0;
		Edge edge = edge(seed, dx, dz, west);
		if (edge == null) return null;
		int along = west ? lz : lx;
		int slot = along / CELL, offset = along % CELL;
		Tile wall = edge.corridor() ? Tile.PERIMETER : Tile.HALL_WALL;
		if (offset == 0) {
			// Between two open slots of a hall wall the post goes too, for a wider gap.
			return !edge.corridor() && edge.isOpen(slot - 1) && edge.isOpen(slot) ? null : wall;
		}
		if (!edge.isOpen(slot)) return wall;
		if (!edge.corridor()) return null;
		return edge.signs() && offset == CELL / 2 ? Tile.OPENING_SIGN : Tile.OPENING;
	}

	private static Tile corner(long seed, int dx, int dz) {
		Edge[] around = {edge(seed, dx, dz, true), edge(seed, dx, dz, false),
				edge(seed, dx - 1, dz, false), edge(seed, dx, dz - 1, true)};
		Tile tile = null;
		for (Edge edge : around) {
			if (edge == null) continue;
			if (edge.corridor()) return Tile.PERIMETER;
			tile = Tile.HALL_WALL;
		}
		return tile;
	}

	/** The wall on the west (or north) line of a district, or null if there is none. */
	static Edge edge(long seed, int dx, int dz, boolean west) {
		Kind here = kind(seed, dx, dz);
		Kind there = west ? kind(seed, dx - 1, dz) : kind(seed, dx, dz - 1);
		SplittableRandom random = new SplittableRandom(hash(seed, dx, dz, west ? EDGE : EDGE + 100));
		boolean hereCorridors = here == Kind.CORRIDORS, thereCorridors = there == Kind.CORRIDORS;
		if (!hereCorridors && !thereCorridors) {
			// Most halls run straight into the next one.
			if (random.nextInt(100) >= 25) return null;
			int open = 0;
			for (int slot = 0; slot < CELLS; slot++) {
				if (random.nextInt(100) < 45) open |= 1 << slot;
			}
			while (Integer.bitCount(open) < 3) open |= 1 << random.nextInt(CELLS);
			return new Edge(open, false, false);
		}
		int open = 0;
		while (Integer.bitCount(open) < 2) open |= 1 << random.nextInt(CELLS);
		// Green lights mark the way from a hall into the corridors.
		return new Edge(open, true, hereCorridors != thereCorridors);
	}

	/** The way out of a district, or null for halls: every block of corridors has one door. */
	public static Door door(long seed, int dx, int dz) {
		if (kind(seed, dx, dz) != Kind.CORRIDORS) return null;
		CorridorPlan plan = CorridorPlan.of(seed, dx, dz);
		return new Door(dx * DISTRICT + plan.doorX(), dz * DISTRICT + plan.doorZ());
	}

	/**
	 * Where someone falling in near (x, z) lands: on the aisle of the nearest parking hall,
	 * since new arrivals from Level 0 come out in the parking structure.
	 */
	public static int[] landing(long seed, int x, int z) {
		int dx0 = Math.floorDiv(x, DISTRICT), dz0 = Math.floorDiv(z, DISTRICT);
		for (int ring = 0; ring < 8; ring++) {
			int[] best = null;
			long bestDistance = Long.MAX_VALUE;
			for (int dx = dx0 - ring; dx <= dx0 + ring; dx++) {
				for (int dz = dz0 - ring; dz <= dz0 + ring; dz++) {
					if (Math.max(Math.abs(dx - dx0), Math.abs(dz - dz0)) != ring) continue;
					if (kind(seed, dx, dz) != Kind.PARKING) continue;
					int[] spot = aisleSpot(seed, dx, dz, x, z);
					long distance = (long) (spot[0] - x) * (spot[0] - x) + (long) (spot[1] - z) * (spot[1] - z);
					if (distance < bestDistance) {
						bestDistance = distance;
						best = spot;
					}
				}
			}
			if (best != null) return best;
		}
		return aisleSpot(seed, 0, 0, x, z);
	}

	/** The open point on an aisle inside district (dx, dz) nearest to (x, z), stepping past any exit standing there. */
	static int[] aisleSpot(long seed, int dx, int dz, int x, int z) {
		int[] spot = aisleSpot(dx, dz, x, z);
		int minX = dx * DISTRICT + 6, maxX = dx * DISTRICT + DISTRICT - 7;
		for (int i = 0; i < 12; i++) {
			int sx = spot[0] + (i + 1) / 2 * (i % 2 == 0 ? 1 : -1) * BAY_X;
			if (sx >= minX && sx <= maxX && tile(seed, sx, spot[1]).walkable()) return new int[] {sx, spot[1]};
		}
		return spot;
	}

	/** The point on an aisle inside district (dx, dz) nearest to (x, z), never on its edge lines. */
	static int[] aisleSpot(int dx, int dz, int x, int z) {
		int minX = dx * DISTRICT + 6, maxX = dx * DISTRICT + DISTRICT - 7;
		int minZ = dz * DISTRICT + 6, maxZ = dz * DISTRICT + DISTRICT - 7;
		int sx = Math.clamp(x, minX, maxX), sz = Math.clamp(z, minZ, maxZ);
		sx += 5 - Math.floorMod(sx, BAY_X);
		sz += 9 - Math.floorMod(sz, BAY_Z);
		if (sx < minX) sx += BAY_X;
		if (sx > maxX) sx -= BAY_X;
		if (sz < minZ) sz += BAY_Z;
		if (sz > maxZ) sz -= BAY_Z;
		return new int[] {sx, sz};
	}

	static long hash(long seed, int x, int z, int salt) {
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
}
