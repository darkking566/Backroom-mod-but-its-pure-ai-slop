package dev.noclip.backrooms.level3;

import static dev.noclip.backrooms.level3.Level3Plan.CELL;
import static dev.noclip.backrooms.level3.Level3Plan.EAST;
import static dev.noclip.backrooms.level3.Level3Plan.N;
import static dev.noclip.backrooms.level3.Level3Plan.NORTH;
import static dev.noclip.backrooms.level3.Level3Plan.SIZE;
import static dev.noclip.backrooms.level3.Level3Plan.SOUTH;
import static dev.noclip.backrooms.level3.Level3Plan.WEST;

import dev.noclip.backrooms.level3.Level3Plan.Edge;
import dev.noclip.backrooms.level3.Level3Plan.Hall;

/**
 * Level 3's floor plan as a pure function of the seed and a column. The hallways' dimensions are wildly
 * inconsistent (Wikidot): each maze cell has its own profile, from wide halls to passages a block wide and two
 * high, or low enough that you have to crouch; where two cells meet, the passage takes the smaller of each.
 */
public final class Level3Layout {
	static final int PROFILES = 303, WALLS = 304, LIGHTS = 305, HAZARDS = 306, DECOR = 307, PURPLE = 308;
	/** Lights are wired in blocks of 3x3 cells; a whole block is lit, flickering, or dead. */
	static final int CIRCUIT = 3;

	/** A cell's passage: how wide, and how much headroom (1 means a crouch-height ceiling, 1.5 blocks). */
	public enum Profile {
		WIDE(4, 4), NORMAL(2, 3), NARROW(1, 2), LOW(2, 1);

		public final int width, clearance;

		Profile(int width, int clearance) {
			this.width = width;
			this.clearance = clearance;
		}
	}

	public enum Kind {
		WALL, FLOOR, BARS, GATE, EXIT, HALL, HALL_WALL, HALL_GAP;

		/** Whether a player can get through. Gates count: their locks can be picked. */
		public boolean walkable() {
			return this == FLOOR || this == GATE || this == HALL || this == HALL_GAP;
		}
	}

	/**
	 * One column. clearance is the headroom over the floor (1 for crouch height); dir is the way an exit faces,
	 * or the side of the cell a bar or gate crosses. Hall tiles carry their hall and the position inside it.
	 */
	public record Tile(Kind kind, int dir, int clearance, Level3Exit exit, Hall hall, int hx, int hz) {
		static final Tile WALL = new Tile(Kind.WALL, 0, 0, null, null, 0, 0);

		static Tile floor(int clearance) {
			return new Tile(Kind.FLOOR, 0, clearance, null, null, 0, 0);
		}
	}

	enum Finish {
		RED_BRICK, GLAZED_TILE, CINDER_BLOCK, BROWN_BRICK
	}

	enum Light {
		NONE, FLUORESCENT, DIM_FLUORESCENT, FLICKERING, BROKEN, BULB, DEAD_BULB
	}

	enum Circuit {
		ON, DIM, FLICKERING, DEAD
	}

	enum Hazard {
		NONE,
		/** A fallen live wire across the floor. */
		WIRE,
		/** A puddle of the black sludge. */
		SLUDGE
	}

	private Level3Layout() {
	}

	static Level3Plan plan(long seed, int x, int z) {
		return Level3Plan.of(seed, Math.floorDiv(x, SIZE), Math.floorDiv(z, SIZE));
	}

	static Profile profile(long seed, int gi, int gj) {
		if (GammaPlan.front(seed, gi, gj) || GammaPlan.sideFront(seed, gi, gj)) return Profile.WIDE;
		int roll = Math.floorMod(hash(seed, gi, gj, PROFILES), 100);
		return roll < 22 ? Profile.WIDE : roll < 65 ? Profile.NORMAL : roll < 85 ? Profile.NARROW : Profile.LOW;
	}

	private static int low(int width) {
		return width == 4 ? 1 : 2;
	}

	private static int high(int width) {
		return width == 4 ? 4 : width == 2 ? 3 : 2;
	}

	private static boolean inWidth(int width, int offset) {
		return offset >= low(width) && offset <= high(width);
	}

	public static Tile tile(long seed, int x, int z) {
		int rx = Math.floorDiv(x, SIZE), rz = Math.floorDiv(z, SIZE);
		int lx = Math.floorMod(x, SIZE), lz = Math.floorMod(z, SIZE);
		Level3Plan plan = Level3Plan.of(seed, rx, rz);
		int i = lx / CELL, j = lz / CELL, ox = lx % CELL, oz = lz % CELL;
		int cell = Level3Plan.cell(i, j);
		int hall = plan.hallOf[cell];
		if (hall >= 0) {
			Hall h = plan.halls.get(hall);
			return h.kind() == Level3Plan.HallKind.GAMMA ? gammaTile(h, lx, lz) : hallTile(plan, h, lx, lz);
		}
		Profile p = plan.profile(i, j);

		boolean edgeX = ox == 0 || ox == CELL - 1, edgeZ = oz == 0 || oz == CELL - 1;
		if (edgeX && edgeZ) return Tile.WALL;
		if (edgeX || edgeZ) {
			int d = oz == 0 ? NORTH : ox == CELL - 1 ? EAST : oz == CELL - 1 ? SOUTH : WEST;
			int along = d == NORTH || d == SOUTH ? ox : oz;
			Edge edge = plan.edge(cell, d);
			if (edge == Edge.NONE) return wall(plan, cell, p, ox, oz);
			int[] dims = edgeDims(seed, plan, rx, rz, i, j, d, edge, p);
			if (!inWidth(dims[0], along)) return wall(plan, cell, p, ox, oz);
			return switch (edge) {
				case BARS -> new Tile(Kind.BARS, d, dims[1], null, null, 0, 0);
				case GATE -> new Tile(along == low(dims[0]) ? Kind.GATE : Kind.BARS, d, dims[1], null, null, 0, 0);
				default -> Tile.floor(dims[1]);
			};
		}
		boolean centre = inWidth(p.width, ox) && inWidth(p.width, oz);
		boolean arm = plan.passage(cell, NORTH) && inWidth(p.width, ox) && oz < low(p.width)
				|| plan.passage(cell, SOUTH) && inWidth(p.width, ox) && oz > high(p.width)
				|| plan.passage(cell, WEST) && inWidth(p.width, oz) && ox < low(p.width)
				|| plan.passage(cell, EAST) && inWidth(p.width, oz) && ox > high(p.width);
		return centre || arm ? Tile.floor(p.clearance) : wall(plan, cell, p, ox, oz);
	}

	/** The width and headroom where a cell meets the next one: the smaller of the two. */
	private static int[] edgeDims(long seed, Level3Plan plan, int rx, int rz, int i, int j, int d, Edge edge, Profile p) {
		if (edge == Edge.HALL) return new int[] {Math.min(2, p.width), Math.min(3, p.clearance)};
		Profile q = profile(seed, rx * N + i + Level3Plan.DX[d], rz * N + j + Level3Plan.DZ[d]);
		return new int[] {Math.min(p.width, q.width), Math.min(p.clearance, q.clearance)};
	}

	/** Wall, unless an elevator is set into it here: in the wall beside the passage, in the middle of the cell. */
	private static Tile wall(Level3Plan plan, int cell, Profile p, int ox, int oz) {
		for (int d = 0; d < 4; d++) {
			Level3Exit exit = plan.exits[cell * 4 + d];
			if (exit == null) continue;
			int tx = d == WEST ? low(p.width) - 1 : d == EAST ? high(p.width) + 1 : 2;
			int tz = d == NORTH ? low(p.width) - 1 : d == SOUTH ? high(p.width) + 1 : 2;
			if (ox == tx && oz == tz) return new Tile(Kind.EXIT, Level3Plan.opposite(d), p.clearance, exit, null, 0, 0);
		}
		return Tile.WALL;
	}

	/** Gamma's outer wall, its gate, three locked side doors, and inside: GammaLayout says what is there at each height. */
	private static Tile gammaTile(Hall hall, int lx, int lz) {
		int u = lx - hall.x0(), v = lz - hall.z0();
		if (v == 0 && u >= GammaLayout.GATE_U && u <= GammaLayout.GATE_U + 1) {
			return new Tile(Kind.HALL_GAP, NORTH, GammaLayout.GATE_CLEARANCE, null, hall, u, v);
		}
		if (u == 0 && v == GammaLayout.SIDE_V) {
			return new Tile(Kind.HALL_GAP, WEST, 2, null, hall, u, v);
		}
		if (u == GammaLayout.W - 1 && v == GammaLayout.SIDE_V) {
			return new Tile(Kind.HALL_GAP, EAST, 2, null, hall, u, v);
		}
		if (v == GammaLayout.D - 1 && u == GammaLayout.GATE_U) {
			return new Tile(Kind.HALL_GAP, SOUTH, 2, null, hall, u, v);
		}
		boolean edge = u == 0 || v == 0 || u == GammaLayout.W - 1 || v == GammaLayout.D - 1;
		return new Tile(edge ? Kind.HALL_WALL : Kind.HALL, 0, edge ? 0 : Level3Halls.HEIGHT, null, hall, u, v);
	}

	private static Tile hallTile(Level3Plan plan, Hall hall, int lx, int lz) {
		boolean west = lx == hall.x0(), east = lx == hall.x1(), north = lz == hall.z0(), south = lz == hall.z1();
		if ((west || east) && (north || south)) return new Tile(Kind.HALL_WALL, 0, 0, null, hall, 0, 0);
		if (west || east || north || south) {
			int out = west ? WEST : east ? EAST : north ? NORTH : SOUTH;
			int mx = lx + Level3Plan.DX[out], mz = lz + Level3Plan.DZ[out];
			int maze = Level3Plan.cell(mx / CELL, mz / CELL);
			if (plan.hallOf[maze] < 0 && plan.edge(maze, Level3Plan.opposite(out)) == Edge.HALL) {
				Profile p = plan.profile(mx / CELL, mz / CELL);
				int along = out == WEST || out == EAST ? mz % CELL : mx % CELL;
				if (inWidth(Math.min(2, p.width), along)) {
					return new Tile(Kind.HALL_GAP, out, Math.min(3, p.clearance), null, hall, 0, 0);
				}
			}
			// Base Gamma's door is in the middle of its north wall (or south, if a way in is there).
			if (hall.kind() == Level3Plan.HallKind.BASE && (west || east) == false && lx == (hall.x0() + hall.x1()) / 2) {
				int doorSide = gapOnWall(plan, hall, NORTH) ? SOUTH : NORTH;
				if (out == doorSide) return new Tile(Kind.EXIT, Level3Plan.opposite(out), 3, Level3Exit.BASE_GAMMA_DOOR, hall, 0, 0);
			}
			return new Tile(Kind.HALL_WALL, out, 0, null, hall, 0, 0);
		}
		return new Tile(Kind.HALL, 0, Level3Halls.HEIGHT, null, hall, lx - hall.x0() - 1, lz - hall.z0() - 1);
	}

	private static boolean gapOnWall(Level3Plan plan, Hall hall, int side) {
		int lz = side == NORTH ? hall.z0() : hall.z1(), lx = (hall.x0() + hall.x1()) / 2;
		int mz = lz + Level3Plan.DZ[side];
		int maze = Level3Plan.cell(lx / CELL, mz / CELL);
		return plan.hallOf[maze] < 0 && plan.edge(maze, Level3Plan.opposite(side)) == Edge.HALL;
	}

	/** Whether a hall position is within two blocks of a way in, which must stay clear. */
	static boolean nearGap(long seed, Hall hall, int x, int z) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) + Math.abs(dz) > 2) continue;
				Tile t = tile(seed, x + dx, z + dz);
				if (t.kind() == Kind.HALL_GAP || t.kind() == Kind.EXIT) return true;
			}
		}
		return false;
	}

	static Finish finish(long seed, int x, int z) {
		int px = Math.floorDiv(x, CELL * 4), pz = Math.floorDiv(z, CELL * 4);
		int roll = Math.floorMod(hash(seed, px, pz, WALLS), 100);
		return roll < 45 ? Finish.RED_BRICK : roll < 65 ? Finish.GLAZED_TILE : roll < 85 ? Finish.CINDER_BLOCK : Finish.BROWN_BRICK;
	}

	/** A few wall blocks are "purpified": an entity half-clipped into them turned them purple. */
	static boolean purpified(long seed, int x, int y, int z) {
		return Math.floorMod(hash(seed, x * 7 + y, z, PURPLE), 1000) < 4;
	}

	static Circuit circuit(long seed, int x, int z) {
		int ci = Math.floorDiv(Math.floorDiv(x, CELL), CIRCUIT), cj = Math.floorDiv(Math.floorDiv(z, CELL), CIRCUIT);
		int roll = Math.floorMod(hash(seed, ci, cj, LIGHTS), 100);
		return roll < 42 ? Circuit.ON : roll < 57 ? Circuit.DIM : roll < 70 ? Circuit.FLICKERING : Circuit.DEAD;
	}

	/** The light over a tunnel column: one in the middle of each cell, a bulb in the narrow ones, none in the low ones. */
	static Light light(long seed, int x, int z) {
		int lx = Math.floorMod(x, SIZE), lz = Math.floorMod(z, SIZE);
		if (lx % CELL != 2 || lz % CELL != 2) return Light.NONE;
		Level3Plan plan = plan(seed, x, z);
		int i = lx / CELL, j = lz / CELL;
		if (plan.hallOf[Level3Plan.cell(i, j)] >= 0) return Light.NONE;
		Profile p = plan.profile(i, j);
		Circuit circuit = circuit(seed, x, z);
		return switch (p) {
			case LOW -> Light.NONE;
			case NARROW -> circuit == Circuit.DEAD ? Light.DEAD_BULB : Light.BULB;
			default -> switch (circuit) {
				case ON -> Light.FLUORESCENT;
				case DIM -> Light.DIM_FLUORESCENT;
				case FLICKERING -> Light.FLICKERING;
				case DEAD -> Light.BROKEN;
			};
		};
	}

	/**
	 * Dark, narrow stretches where wanderers feel watched: the paranoia hotspots. A narrow or crouch-height
	 * cell on a dead circuit.
	 */
	public static boolean hotspot(long seed, int x, int z) {
		Level3Plan plan = plan(seed, x, z);
		int lx = Math.floorMod(x, SIZE), lz = Math.floorMod(z, SIZE);
		int cell = Level3Plan.cell(lx / CELL, lz / CELL);
		if (plan.hallOf[cell] >= 0) return false;
		Profile p = plan.profile(lx / CELL, lz / CELL);
		return (p == Profile.NARROW || p == Profile.LOW) && circuit(seed, x, z) == Circuit.DEAD;
	}

	/** Fallen live wires and puddles of sludge, only where there is room to step round them. */
	static Hazard hazard(long seed, int x, int z, Tile tile) {
		if (tile.kind() != Kind.FLOOR || tile.clearance() < 3) return Hazard.NONE;
		Level3Plan plan = plan(seed, x, z);
		int lx = Math.floorMod(x, SIZE), lz = Math.floorMod(z, SIZE);
		if (plan.profile(lx / CELL, lz / CELL) != Profile.WIDE) return Hazard.NONE;
		int roll = Math.floorMod(hash(seed, x, z, HAZARDS), 1000);
		return roll < 25 ? Hazard.WIRE : roll < 45 ? Hazard.SLUDGE : Hazard.NONE;
	}

	/** An M.E.G. sign on a passage wall: an arrow towards Base Gamma, or a warning that a paranoia hotspot lies ahead. */
	record Sign(boolean warning, int facing, boolean right) {
	}

	/**
	 * The M.E.G. sign on the wall beside this column, at eye height, or null. Signs hang in roomy straight passages,
	 * on their north or west wall: arrows towards Base Gamma one block in from the cell's end, and warnings at the
	 * end that opens into a dark, narrow stretch (Wikidot: "M.E.G. has installed warning signage at all known hotspots").
	 */
	static Sign sign(long seed, int x, int z) {
		int lx = Math.floorMod(x, SIZE), lz = Math.floorMod(z, SIZE);
		Level3Plan plan = plan(seed, x, z);
		int i = lx / CELL, j = lz / CELL, ox = lx % CELL, oz = lz % CELL;
		int cell = Level3Plan.cell(i, j);
		if (plan.hallOf[cell] >= 0) return null;
		int axis = plan.straightAxis(cell);
		Profile p = plan.profile(i, j);
		if (axis < 0 || p.clearance < 3) return null;
		int wall = axis == 0 ? NORTH : WEST;
		int along = axis == 0 ? ox : oz, across = axis == 0 ? oz : ox;
		if (across != low(p.width)) return null;
		if (along == 1 && plan.baseTo[cell] >= 0) return new Sign(false, Level3Plan.opposite(wall), plan.baseTo[cell] == ((wall + 1) & 3));
		// Outside Gamma's region: distant direction+distance signs (denser within 2 regions, sparser further out).
		if (along == 1 && plan.baseTo[cell] < 0) {
			int[] gate = GammaPlan.nearestGate(seed, x, z);
			if (gate != null) {
				double dist = Math.hypot(gate[0] - x, gate[1] - z);
				boolean show = dist <= 2.0 * SIZE || (dist <= 4.5 * SIZE && Math.floorMod(hash(seed, x, z, DECOR + 19), 100) < 35);
				if (show) {
					int want = axis == 0 ? (gate[0] >= x ? EAST : WEST) : (gate[1] >= z ? SOUTH : NORTH);
					return new Sign(false, Level3Plan.opposite(wall), want == ((wall + 1) & 3));
				}
			}
		}
		int ahead = along == CELL - 2 ? (axis == 0 ? EAST : SOUTH) : along == 1 ? (axis == 0 ? WEST : NORTH) : -1;
		if (ahead < 0) return null;
		int gx = Math.floorDiv(x, CELL) + Level3Plan.DX[ahead], gz = Math.floorDiv(z, CELL) + Level3Plan.DZ[ahead];
		if (hotspot(seed, gx * CELL + 2, gz * CELL + 2)) return new Sign(true, Level3Plan.opposite(wall), false);
		return null;
	}

	/** 0..99, for decorations that are a matter of chance. */
	static int roll(long seed, int x, int y, int z) {
		return Math.floorMod(hash(seed, x * 31 + y, z, DECOR), 100);
	}

	/** The middle of the nearest roomy tunnel cell to (x, z), as {x, z}: somewhere to stand on arriving. */
	public static int[] landing(long seed, int x, int z) {
		int gi0 = Math.floorDiv(x, CELL), gj0 = Math.floorDiv(z, CELL);
		for (int ring = 0; ring < 40; ring++) {
			for (int gi = gi0 - ring; gi <= gi0 + ring; gi++) {
				for (int gj = gj0 - ring; gj <= gj0 + ring; gj++) {
					if (Math.max(Math.abs(gi - gi0), Math.abs(gj - gj0)) != ring) continue;
					int cx = gi * CELL + 2, cz = gj * CELL + 2;
					Tile t = tile(seed, cx, cz);
					if (t.kind() == Kind.FLOOR && t.clearance() >= 3 && hazard(seed, cx, cz, t) == Hazard.NONE) return new int[] {cx, cz};
				}
			}
		}
		return new int[] {gi0 * CELL + 2, gj0 * CELL + 2};
	}

	static int hash(long seed, int x, int z, int salt) {
		long h = mix(seed + salt);
		h = mix(h ^ x);
		return (int) mix(h ^ z);
	}

	// SplitMix64 finalizer
	private static long mix(long v) {
		v += 0x9E3779B97F4A7C15L;
		v = (v ^ (v >>> 30)) * 0xBF58476D1CE4E5B9L;
		v = (v ^ (v >>> 27)) * 0x94D049BB133111EBL;
		return v ^ (v >>> 31);
	}
}
