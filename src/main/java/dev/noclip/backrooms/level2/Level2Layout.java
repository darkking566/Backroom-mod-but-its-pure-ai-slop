package dev.noclip.backrooms.level2;

import static dev.noclip.backrooms.level2.Level2Plan.CELL;
import static dev.noclip.backrooms.level2.Level2Plan.EAST;
import static dev.noclip.backrooms.level2.Level2Plan.N;
import static dev.noclip.backrooms.level2.Level2Plan.NORTH;
import static dev.noclip.backrooms.level2.Level2Plan.SIZE;
import static dev.noclip.backrooms.level2.Level2Plan.SOUTH;
import static dev.noclip.backrooms.level2.Level2Plan.WEST;

import dev.noclip.backrooms.level2.Level2Plan.Feature;
import dev.noclip.backrooms.level2.Level2Plan.Room;
import dev.noclip.backrooms.level2.Level2Plan.RoomKind;

/**
 * Level 2's floor plan as a pure function of the seed and a column: what stands at (x, z), and what light,
 * wall finish and heat it has. Tunnels are three blocks wide inside their cells; along straight runs one of the
 * three is a bank of pipes floor to ceiling, which leaves two to walk in. Turns are cut off at the outer corner.
 */
public final class Level2Layout {
	static final int BANK = 203, WALLS = 204, LIGHTS = 205, FLOORS = 206, PALETTE = 207, ROOM_PROPS = 208, BANK_PARTS = 209, OFFICE = 210;
	/** Lights are wired in series in blocks of 3x3 cells; one broken light takes its whole block down. */
	static final int CIRCUIT = 3;
	/** Cells around the door to 811 whose pipes carry blue pulsing lights. */
	static final int BLADE_TRAIL = 6;

	enum Kind {
		WALL, FLOOR, BANK, MACHINE, DOOR, LOCKED_DOOR, EXIT, WINDOW, ROOM, VOID, VOID_WALL, CRATE, DRUM, SCRAP, CABINET, TOOLBOX, CABLE_REEL, LUMBER_STACK;

		/** Whether a player can stand here. Doors count: they open. */
		public boolean walkable() {
			return this == FLOOR || this == ROOM || this == VOID || this == DOOR;
		}
	}

	/**
	 * What stands in one column. dir is the direction a door, exit, window, machine or pipe bank faces: away from
	 * the wall it is set into or bracketed to, towards where you stand.
	 */
	public record Tile(Kind kind, int dir, Level2Exit exit) {
		static final Tile WALL = new Tile(Kind.WALL, 0, null);
		static final Tile FLOOR = new Tile(Kind.FLOOR, 0, null);
		static final Tile ROOM = new Tile(Kind.ROOM, 0, null);
		static final Tile VOID = new Tile(Kind.VOID, 0, null);
		static final Tile VOID_WALL = new Tile(Kind.VOID_WALL, 0, null);
	}

	enum Finish {
		/** Withered, chalky concrete: most of the level. */
		CONCRETE,
		/** The same with peeling and water stains. */
		STAINED,
		MUDDY_BRICK, BLACK_BRICK,
		/** White-painted brick. */
		PAINTED
	}

	enum Light {
		NONE, WHITE, WARM, DEAD, VIOLET, BULB, DEAD_BULB
	}

	enum Ground {
		PLAIN, DEBRIS, DAMP
	}

	enum Palette {
		/** Fat pipes in cream insulation, rusty steel above. */
		INSULATED,
		/** Fat pale green pipes. */
		GREEN
	}

	private Level2Layout() {
	}

	static Level2Plan plan(long seed, int x, int z) {
		return Level2Plan.of(seed, Math.floorDiv(x, SIZE), Math.floorDiv(z, SIZE));
	}

	public static Tile tile(long seed, int x, int z) {
		int rx = Math.floorDiv(x, SIZE), rz = Math.floorDiv(z, SIZE);
		int lx = Math.floorMod(x, SIZE), lz = Math.floorMod(z, SIZE);
		Level2Plan plan = Level2Plan.of(seed, rx, rz);
		int i = lx / CELL, j = lz / CELL, ox = lx % CELL, oz = lz % CELL;
		int cell = Level2Plan.cell(i, j);
		int room = plan.roomOf[cell];
		if (room >= 0) return roomTile(seed, plan, plan.rooms.get(room), lx, lz, x, z);

		boolean edgeX = ox == 0 || ox == CELL - 1, edgeZ = oz == 0 || oz == CELL - 1;
		if (edgeX && edgeZ) return Tile.WALL;
		if (edgeX || edgeZ) {
			int d = oz == 0 ? NORTH : ox == CELL - 1 ? EAST : oz == CELL - 1 ? SOUTH : WEST;
			int along = d == NORTH || d == SOUTH ? ox : oz;
			if (plan.isOpen(cell, d)) return passage(seed, plan, rx, rz, i, j, d, along);
			if (along != 2) return Tile.WALL;
			int facing = Level2Plan.opposite(d);
			return switch (plan.feature(cell, d)) {
				case DOOR -> new Tile(Kind.DOOR, facing, null);
				case LOCKED_DOOR -> new Tile(Kind.LOCKED_DOOR, facing, null);
				case EXIT -> new Tile(Kind.EXIT, facing, plan.exits[cell * 4 + d]);
				case WINDOW -> new Tile(Kind.WINDOW, facing, null);
				case NONE -> Tile.WALL;
			};
		}
		int bank = bankSide(seed, plan, rx, rz, i, j);
		if (bank >= 0 && bankColumn(bank, ox, oz)) return new Tile(Kind.BANK, Level2Plan.opposite(bank), null);
		if (outerCorner(plan.open[cell], ox, oz)) return Tile.WALL;
		Tile debris = deadEndDebris(seed, plan, i, j, ox, oz, x, z);
		if (debris != null) return debris;
		return Tile.FLOOR;
	}

	/** An open side of a cell: tunnel floor, or the pipe bank running on into the next cell (or ending here in a machine). */
	private static Tile passage(long seed, Level2Plan plan, int rx, int rz, int i, int j, int d, int along) {
		int bank = bankSide(seed, plan, rx, rz, i, j);
		if (bank < 0) return Tile.FLOOR;
		int axis = plan.straightAxis(Level2Plan.cell(i, j));
		boolean inBank = axis == 0 ? (bank == NORTH ? along == 1 : along == 3) : (bank == WEST ? along == 1 : along == 3);
		if (!inBank) return Tile.FLOOR;
		// The next cell over, which may be in the next region.
		int gi = rx * N + i + Level2Plan.DX[d], gj = rz * N + j + Level2Plan.DZ[d];
		int nrx = Math.floorDiv(gi, N), nrz = Math.floorDiv(gj, N);
		Level2Plan next = nrx == rx && nrz == rz ? plan : Level2Plan.of(seed, nrx, nrz);
		int ni = Math.floorMod(gi, N), nj = Math.floorMod(gj, N);
		boolean continues = next.roomOf[Level2Plan.cell(ni, nj)] < 0 && next.straightAxis(Level2Plan.cell(ni, nj)) == axis
				&& bankSide(seed, next, nrx, nrz, ni, nj) == bank;
		return new Tile(continues ? Kind.BANK : Kind.MACHINE, Level2Plan.opposite(bank), null);
	}

	private static boolean bankColumn(int bank, int ox, int oz) {
		return switch (bank) {
			case NORTH -> oz == 1;
			case SOUTH -> oz == 3;
			case WEST -> ox == 1;
			default -> ox == 3;
		};
	}

	/**
	 * Which wall of a straight cell carries the pipe bank, or -1. Whole rows (columns) of cells prefer the same
	 * side, so banks run on from cell to cell; a wall with a door or exit in it is kept clear.
	 */
	static int bankSide(long seed, Level2Plan plan, int rx, int rz, int i, int j) {
		int cell = Level2Plan.cell(i, j);
		int axis = plan.straightAxis(cell);
		if (axis < 0 || isRailCell(plan, i, j) || isOfficePatch(seed, rx * SIZE + i * CELL, rz * SIZE + j * CELL)) return -1;
		int line = axis == 0 ? rz * N + j : rx * N + i;
		int a = axis == 0 ? NORTH : WEST, b = Level2Plan.opposite(a);
		int preferred = (hash(seed, line, axis, BANK) & 1) == 0 ? a : b;
		if (plan.feature(cell, preferred) == Feature.NONE) return preferred;
		int other = Level2Plan.opposite(preferred);
		return plan.feature(cell, other) == Feature.NONE ? other : -1;
	}

	/** In a turn, the inside corner farthest from both openings is walled off, so the bend reads as a diagonal. */
	private static boolean outerCorner(int open, int ox, int oz) {
		if (Integer.bitCount(open) != 2) return false;
		boolean n = (open & (1 << NORTH)) != 0, e = (open & (1 << EAST)) != 0, s = (open & (1 << SOUTH)) != 0, w = (open & (1 << WEST)) != 0;
		if (n == s || e == w) return false;
		return ox == (e ? 1 : 3) && oz == (n ? 3 : 1);
	}

	private static Tile roomTile(long seed, Level2Plan plan, Room room, int lx, int lz, int x, int z) {
		boolean dark = room.kind() == RoomKind.VOID;
		boolean ring = lx == room.x0() || lx == room.x1() || lz == room.z0() || lz == room.z1();
		if (ring) {
			if (plan.doorways.contains(lz * SIZE + lx)) return dark ? Tile.VOID : Tile.ROOM;
			return dark ? Tile.VOID_WALL : Tile.WALL;
		}
		if (dark) return Tile.VOID;
		if (nearDoorway(plan, lx, lz)) return Tile.ROOM;
		boolean byWall = lx == room.x0() + 1 || lx == room.x1() - 1 || lz == room.z0() + 1 || lz == room.z1() - 1;
		int roll = (int) Math.floorMod(hash(seed, x, z, ROOM_PROPS), 100);
		return switch (room.kind()) {
			case STORAGE -> !byWall ? Tile.ROOM
					: roll < 26 ? new Tile(Kind.CRATE, 0, null)
					: roll < 42 ? new Tile(Kind.DRUM, 0, null)
					: roll < 54 ? new Tile(Kind.SCRAP, 0, null)
					: roll < 66 ? new Tile(Kind.TOOLBOX, 0, null)
					: roll < 76 ? new Tile(Kind.CABLE_REEL, 0, null)
					: roll < 86 ? new Tile(Kind.LUMBER_STACK, 0, null) : Tile.ROOM;
			case MACHINES -> (lz - room.z0()) % 3 == 2 && lx > room.x0() + 1 && lx < room.x1() - 1
					? new Tile(Kind.CABINET, (lz - room.z0()) % 6 == 2 ? SOUTH : NORTH, null) : Tile.ROOM;
			case EL3A -> !byWall ? Tile.ROOM
					: roll < 20 ? new Tile(Kind.CRATE, 0, null)
					: roll < 35 ? new Tile(Kind.CABINET, lx == room.x0() + 1 ? EAST : lx == room.x1() - 1 ? WEST : lz == room.z0() + 1 ? SOUTH : NORTH, null)
					: roll < 45 ? new Tile(Kind.TOOLBOX, 0, null)
					: roll < 52 ? new Tile(Kind.CABLE_REEL, 0, null)
					: Tile.ROOM;
			case MACCHINA -> !byWall ? Tile.ROOM
					: roll < 28 ? new Tile(Kind.CABINET, lx == room.x0() + 1 ? EAST : lx == room.x1() - 1 ? WEST : lz == room.z0() + 1 ? SOUTH : NORTH, null)
					: roll < 50 ? new Tile(Kind.SCRAP, 0, null) : Tile.ROOM;
			default -> Tile.ROOM;
		};
	}

	/** Occasional toolbox, cable reel, lumber stack, or scrap tucked into a bare dead-end corner. */
	private static Tile deadEndDebris(long seed, Level2Plan plan, int i, int j, int ox, int oz, int x, int z) {
		int cell = Level2Plan.cell(i, j);
		if (plan.openings(cell) != 1 || isOfficePatch(seed, x, z)) return null;
		for (int d = 0; d < 4; d++) {
			if (plan.feature(cell, d) != Feature.NONE) return null;
		}
		int openDir = Integer.numberOfTrailingZeros(plan.open[cell] & 0xF);
		int backX = openDir == WEST ? 3 : 1, backZ = openDir == NORTH ? 3 : 1;
		if (ox != backX || oz != backZ) return null;
		int roll = Math.floorMod(hash(seed, x, z, ROOM_PROPS), 100);
		if (roll < 16) return new Tile(Kind.TOOLBOX, 0, null);
		if (roll < 28) return new Tile(Kind.CABLE_REEL, 0, null);
		if (roll < 40) return new Tile(Kind.LUMBER_STACK, 0, null);
		if (roll < 52) return new Tile(Kind.SCRAP, 0, null);
		return null;
	}

	/** Two blocks in from a doorway stay clear, so nothing blocks the way in. */
	private static boolean nearDoorway(Level2Plan plan, int lx, int lz) {
		for (int doorway : plan.doorways) {
			if (Math.abs(doorway % SIZE - lx) + Math.abs(doorway / SIZE - lz) <= 2) return true;
		}
		return false;
	}

	/** The finish of the tunnel walls around here, in patches of 4x4 cells. */
	static Finish finish(long seed, int x, int z) {
		if (isOfficePatch(seed, x, z)) return Finish.PAINTED;
		int px = Math.floorDiv(x, CELL * 4), pz = Math.floorDiv(z, CELL * 4);
		int roll = Math.floorMod(hash(seed, px, pz, WALLS), 100);
		Finish finish = roll < 52 ? Finish.CONCRETE : roll < 72 ? Finish.MUDDY_BRICK : roll < 84 ? Finish.PAINTED : Finish.BLACK_BRICK;
		if (finish == Finish.CONCRETE && Math.floorMod(hash(seed, x, z, WALLS), 100) < 22) return Finish.STAINED;
		return finish;
	}

	/** The light hanging over a column, if any: one in the middle of each tunnel cell and each lit room. */
	static Light light(long seed, int x, int z) {
		int lx = Math.floorMod(x, SIZE), lz = Math.floorMod(z, SIZE);
		Level2Plan plan = plan(seed, x, z);
		int room = plan.roomOf[Level2Plan.cell(lx / CELL, lz / CELL)];
		boolean brick = switch (finish(seed, x, z)) {
			case MUDDY_BRICK, BLACK_BRICK -> true;
			default -> false;
		};
		if (room >= 0) {
			Room r = plan.rooms.get(room);
			if (lx != (r.x0() + r.x1()) / 2 || lz != (r.z0() + r.z1()) / 2 || r.kind() == RoomKind.VOID) return Light.NONE;
			if (!r.lit()) return brick ? Light.DEAD_BULB : Light.DEAD;
			return brick ? Light.BULB : Light.WHITE;
		}
		if (lx % CELL != 2 || lz % CELL != 2) return Light.NONE;
		int gi = Math.floorDiv(x, CELL), gj = Math.floorDiv(z, CELL);
		int ci = Math.floorDiv(gi, CIRCUIT), cj = Math.floorDiv(gj, CIRCUIT);
		int roll = Math.floorMod(hash(seed, ci, cj, LIGHTS), 100);
		boolean hot = hot(seed, x, z);
		if (roll < 30) {
			// A dead circuit; some have a violet emergency light in the middle.
			boolean middle = Math.floorMod(gi, CIRCUIT) == 1 && Math.floorMod(gj, CIRCUIT) == 1;
			if (middle && roll < 12) return Light.VIOLET;
			return brick ? Light.DEAD_BULB : Light.DEAD;
		}
		if (brick) return Light.BULB;
		return hot || roll >= 80 ? Light.WARM : Light.WHITE;
	}

	/** Which way a light hangs: along the tunnel (0 for X, 1 for Z); across X in junctions and rooms. */
	static int lightAxis(long seed, int x, int z) {
		Level2Plan plan = plan(seed, x, z);
		int cell = Level2Plan.cell(Math.floorMod(x, SIZE) / CELL, Math.floorMod(z, SIZE) / CELL);
		return plan.roomOf[cell] < 0 && plan.straightAxis(cell) == 1 ? 1 : 0;
	}

	/** The block box (min x, min z, max x, max z) of the lighting circuit a column belongs to. */
	static int[] circuit(int x, int z) {
		int ci = Math.floorDiv(Math.floorDiv(x, CELL), CIRCUIT), cj = Math.floorDiv(Math.floorDiv(z, CELL), CIRCUIT);
		int span = CELL * CIRCUIT;
		return new int[] {ci * span, cj * span, ci * span + span - 1, cj * span + span - 1};
	}

	static Ground ground(long seed, int x, int z) {
		if (isOfficePatch(seed, x, z)) return Ground.PLAIN;
		int roll = Math.floorMod(hash(seed, x, z, FLOORS), 100);
		return roll < 14 ? Ground.DEBRIS : roll < 19 ? Ground.DAMP : Ground.PLAIN;
	}

	/** The pipes along a run of tunnel: the same for 20 blocks or so, so a run looks like one installation. */
	static Palette palette(long seed, int x, int z, int axis) {
		int along = axis == 0 ? Math.floorDiv(x, CELL * 4) : Math.floorDiv(z, CELL * 4);
		int across = axis == 0 ? Math.floorDiv(z, CELL) : Math.floorDiv(x, CELL);
		return Math.floorMod(hash(seed, along, across, PALETTE), 100) < 66 ? Palette.INSULATED : Palette.GREEN;
	}

	/** 0..99, for picking which pipe a bank block shows at one height. */
	static int bankRoll(long seed, int x, int y, int z) {
		return Math.floorMod(hash(seed, x * 31 + y, z, BANK_PARTS), 100);
	}

	/** Whether a column is inside a hot zone (Office Area EL3A is always climate-controlled). */
	public static boolean hot(long seed, int x, int z) {
		if (roomKindAt(seed, x, z) == RoomKind.EL3A) return false;
		int rx = Math.floorDiv(x, SIZE), rz = Math.floorDiv(z, SIZE);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Level2Plan plan = Level2Plan.of(seed, rx + dx, rz + dz);
				if (plan.hotX < 0) continue;
				int hx = (rx + dx) * SIZE + plan.hotX, hz = (rz + dz) * SIZE + plan.hotZ;
				if (Math.abs(x - hx) + Math.abs(z - hz) <= Level2Plan.HOT_RADIUS) return true;
			}
		}
		return false;
	}

	/** Whether a column is on the trail of blue pipe lights leading to the door to 811. */
	static boolean bladeTrail(long seed, int x, int z) {
		int gi = Math.floorDiv(x, CELL), gj = Math.floorDiv(z, CELL);
		int rx = Math.floorDiv(x, SIZE), rz = Math.floorDiv(z, SIZE);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Level2Plan plan = Level2Plan.of(seed, rx + dx, rz + dz);
				if (plan.bladeCell < 0) continue;
				int bi = (rx + dx) * N + plan.bladeCell % N, bj = (rz + dz) * N + plan.bladeCell / N;
				if (Math.abs(gi - bi) + Math.abs(gj - bj) <= BLADE_TRAIL) return true;
			}
		}
		return false;
	}

	/** A wanderer's spray-painted mark on a tunnel wall: which door it points to, facing out from the wall, arrow right or left. */
	record Mark(Level2Exit exit, int facing, boolean right) {
	}

	/**
	 * The mark on the wall beside this column, at eye height, or null: in straight cells near a standard exit,
	 * on the wall without pipes, one block in from the cell's end (clear of any door in the middle of the wall).
	 */
	static Mark mark(long seed, int x, int z) {
		int lx = Math.floorMod(x, SIZE), lz = Math.floorMod(z, SIZE);
		int rx = Math.floorDiv(x, SIZE), rz = Math.floorDiv(z, SIZE);
		Level2Plan plan = Level2Plan.of(seed, rx, rz);
		int i = lx / CELL, j = lz / CELL, ox = lx % CELL, oz = lz % CELL;
		int cell = Level2Plan.cell(i, j);
		if (plan.roomOf[cell] >= 0 || plan.markTo[cell] < 0) return null;
		int axis = plan.straightAxis(cell);
		if (axis < 0) return null;
		int bank = bankSide(seed, plan, rx, rz, i, j);
		int wall = bank >= 0 ? Level2Plan.opposite(bank) : axis == 0 ? NORTH : WEST;
		int along = axis == 0 ? ox : oz, across = axis == 0 ? oz : ox;
		int beside = wall == NORTH || wall == WEST ? 1 : 3;
		if (along != 1 || across != beside) return null;
		boolean right = plan.markTo[cell] == ((wall + 1) & 3);
		return new Mark(plan.markFor[cell], Level2Plan.opposite(wall), right);
	}

	/** The middle of the nearest tunnel cell to (x, z), as {x, z}: somewhere to stand on arriving. */
	public static int[] landing(long seed, int x, int z) {
		int gi0 = Math.floorDiv(x, CELL), gj0 = Math.floorDiv(z, CELL);
		for (int ring = 0; ring < 40; ring++) {
			for (int gi = gi0 - ring; gi <= gi0 + ring; gi++) {
				for (int gj = gj0 - ring; gj <= gj0 + ring; gj++) {
					if (Math.max(Math.abs(gi - gi0), Math.abs(gj - gj0)) != ring) continue;
					int cx = gi * CELL + 2, cz = gj * CELL + 2;
					if (tile(seed, cx, cz).kind() == Kind.FLOOR && !hot(seed, cx, cz) && !cold(seed, cx, cz)
							&& !gas(seed, cx, cz) && floodedStage(seed, cx, cz) == 0) {
						return new int[] {cx, cz};
					}
				}
			}
		}
		return new int[] {gi0 * CELL + 2, gj0 * CELL + 2};
	}

	/** Office-style corridors: bare painted walls, plain floors, no industrial pipes or machinery. */
	static boolean isOfficePatch(long seed, int x, int z) {
		int px = Math.floorDiv(x, CELL * 4), pz = Math.floorDiv(z, CELL * 4);
		return Math.floorMod(hash(seed, px, pz, OFFICE), 100) < 14;
	}

	/** Whether a column is inside an abnormally cold stretch. */
	public static boolean cold(long seed, int x, int z) {
		if (isSafeZone(seed, x, z)) return false;
		int rx = Math.floorDiv(x, SIZE), rz = Math.floorDiv(z, SIZE);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Level2Plan plan = Level2Plan.of(seed, rx + dx, rz + dz);
				if (plan.coldX < 0) continue;
				int cx = (rx + dx) * SIZE + plan.coldX, cz = (rz + dz) * SIZE + plan.coldZ;
				if (Math.abs(x - cx) + Math.abs(z - cz) <= Level2Plan.COLD_RADIUS) return true;
			}
		}
		return false;
	}

	/** Whether a column is inside a burst-pipe gas leak zone. */
	public static boolean gas(long seed, int x, int z) {
		if (isSafeZone(seed, x, z)) return false;
		int rx = Math.floorDiv(x, SIZE), rz = Math.floorDiv(z, SIZE);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Level2Plan plan = Level2Plan.of(seed, rx + dx, rz + dz);
				if (plan.gasX < 0) continue;
				int gx = (rx + dx) * SIZE + plan.gasX, gz = (rz + dz) * SIZE + plan.gasZ;
				if (Math.abs(x - gx) + Math.abs(z - gz) <= Level2Plan.GAS_RADIUS) return true;
			}
		}
		return false;
	}

	static RoomKind roomKindAt(long seed, int x, int z) {
		int lx = Math.floorMod(x, SIZE), lz = Math.floorMod(z, SIZE);
		Level2Plan plan = plan(seed, x, z);
		int room = plan.roomOf[Level2Plan.cell(lx / CELL, lz / CELL)];
		return room >= 0 ? plan.rooms.get(room).kind() : null;
	}

	static boolean isSafeZone(long seed, int x, int z) {
		return roomKindAt(seed, x, z) == RoomKind.EL3A;
	}

	/** Straight tunnel cells in an EL3A region that form a run of >= 2 straight cells carry B.N.T.G. narrow-gauge rails. */
	private static boolean isRailCell(Level2Plan plan, int i, int j) {
		if (!plan.hasEl3a) return false;
		int axis = plan.straightAxis(Level2Plan.cell(i, j));
		if (axis < 0) return false;
		int dx = axis == 0 ? 1 : 0, dz = axis == 1 ? 1 : 0;
		for (int step : new int[] {-1, 1}) {
			int ni = i + dx * step, nj = j + dz * step;
			if (ni >= 0 && nj >= 0 && ni < N && nj < N) {
				int nc = Level2Plan.cell(ni, nj);
				if (plan.roomOf[nc] < 0 && plan.straightAxis(nc) == axis) return true;
			}
		}
		return false;
	}

	/** Returns 0 (east-west) or 1 (north-south) if a rail track sits at (x, z), or -1. */
	static int railAxis(long seed, int x, int z) {
		int lx = Math.floorMod(x, SIZE), lz = Math.floorMod(z, SIZE);
		Level2Plan plan = plan(seed, x, z);
		int i = lx / CELL, j = lz / CELL;
		int cell = Level2Plan.cell(i, j);
		if (plan.roomOf[cell] >= 0 || !isRailCell(plan, i, j)) return -1;
		int axis = plan.straightAxis(cell);
		int ox = lx % CELL, oz = lz % CELL;
		if ((axis == 0 && oz == 2) || (axis == 1 && ox == 2)) return axis;
		return -1;
	}

	enum WallProp { BREAKER_BOX, PRESSURE_GAUGE, HAZARD_SIGN }
	record WallDetail(WallProp prop, int dir) {}
	enum CeilingProp { VENT, SNAPPED_CABLES }

	private static final int[] WALL_DX = {0, 1, 0, -1};
	private static final int[] WALL_DZ = {-1, 0, 1, 0};

	static WallDetail wallDetail(long seed, int x, int z) {
		if (roomKindAt(seed, x, z) != null || isOfficePatch(seed, x, z)) return null;
		int wallDir = -1;
		for (int d = 0; d < 4; d++) {
			if (tile(seed, x + WALL_DX[d], z + WALL_DZ[d]).kind() == Kind.WALL) {
				wallDir = Level2Plan.opposite(d);
				break;
			}
		}
		if (wallDir < 0) return null;
		int r = Math.floorMod(hash(seed, x, z, 0x41D1), 100);
		if (hot(seed, x, z) || cold(seed, x, z) || gas(seed, x, z)) {
			if (r < 6) return new WallDetail(WallProp.HAZARD_SIGN, wallDir);
			if (r < 9) return new WallDetail(WallProp.PRESSURE_GAUGE, wallDir);
			return null;
		}
		if (r < 2) return new WallDetail(WallProp.BREAKER_BOX, wallDir);
		if (r < 4) return new WallDetail(WallProp.PRESSURE_GAUGE, wallDir);
		if (r < 6) return new WallDetail(WallProp.HAZARD_SIGN, wallDir);
		return null;
	}

	static boolean isDrainGrate(long seed, int x, int z) {
		if (roomKindAt(seed, x, z) != null || isOfficePatch(seed, x, z)) return false;
		if (ground(seed, x, z) != Ground.DAMP) return false;
		return Math.floorMod(hash(seed, x, z, 0x41D2), 100) < 10;
	}

	static int floorCableDuctAxis(long seed, int x, int z) {
		if (roomKindAt(seed, x, z) != null || isOfficePatch(seed, x, z) || railAxis(seed, x, z) >= 0) return -1;
		int lx = Math.floorMod(x, SIZE), lz = Math.floorMod(z, SIZE);
		Level2Plan plan = plan(seed, x, z);
		int i = lx / CELL, j = lz / CELL;
		int cell = Level2Plan.cell(i, j);
		int axis = plan.straightAxis(cell);
		if (axis < 0) return -1;
		int cellHash = Math.floorMod(hash(seed, Math.floorDiv(x, CELL), Math.floorDiv(z, CELL), 0x41D3), 100);
		if (cellHash >= 10) return -1;
		int side = (cellHash & 1) == 0 ? 1 : 3;
		int across = axis == 0 ? lz % CELL : lx % CELL;
		return across == side ? axis : -1;
	}

	static CeilingProp ceilingDetail(long seed, int x, int z) {
		if (roomKindAt(seed, x, z) != null || isOfficePatch(seed, x, z)) return null;
		int r = Math.floorMod(hash(seed, x, z, 0x41D4), 100);
		if (r < 3) return CeilingProp.VENT;
		int cableCutoff = (ground(seed, x, z) == Ground.DEBRIS || hot(seed, x, z) || gas(seed, x, z)) ? 8 : 6;
		return r < cableCutoff ? CeilingProp.SNAPPED_CABLES : null;
	}

	/** 0 = dry, 1 = puddled concrete floor, 2 = shallow ankle-deep water pool. */
	static int floodedStage(long seed, int x, int z) {
		if (roomKindAt(seed, x, z) != null || isOfficePatch(seed, x, z)
				|| hot(seed, x, z) || cold(seed, x, z) || railAxis(seed, x, z) >= 0) {
			return 0;
		}
		int px = Math.floorDiv(x, CELL * 2), pz = Math.floorDiv(z, CELL * 2);
		if (Math.floorMod(hash(seed, px, pz, 0x41D5), 100) >= 9) return 0;
		int ox = Math.floorMod(x, CELL), oz = Math.floorMod(z, CELL);
		boolean inner = ox >= 1 && ox <= 3 && oz >= 1 && oz <= 3;
		if (inner && (ox == 2 || oz == 2) && !isDrainGrate(seed, x, z)
				&& Math.floorMod(hash(seed, x, z, 0x41D6), 100) < 55) {
			return 2;
		}
		return 1;
	}

	/** 0 = none, 1 = bridge X, 2 = bridge Z, 3 = leaking X, 4 = leaking Z. */
	static int overheadPipe(long seed, int x, int z) {
		if (roomKindAt(seed, x, z) != null || isOfficePatch(seed, x, z)) return 0;
		int lx = Math.floorMod(x, SIZE), lz = Math.floorMod(z, SIZE);
		Level2Plan plan = plan(seed, x, z);
		int cell = Level2Plan.cell(lx / CELL, lz / CELL);
		int corridorAxis = plan.straightAxis(cell);
		if (corridorAxis < 0) return 0;
		int along = corridorAxis == 0 ? lx % CELL : lz % CELL;
		int across = corridorAxis == 0 ? lz % CELL : lx % CELL;
		if (floodedStage(seed, x, z) > 0 && (across == 1 || across == 3)) {
			int side = Math.floorMod(hash(seed, Math.floorDiv(x, CELL), Math.floorDiv(z, CELL), 0x41D8), 2) == 0 ? 1 : 3;
			if (across == side) return corridorAxis == 0 ? 3 : 4;
		}
		int cellRoll = Math.floorMod(hash(seed, Math.floorDiv(x, CELL), Math.floorDiv(z, CELL), 0x41D7), 100);
		int bridgeRate = gas(seed, x, z) ? 35 : 22;
		if (cellRoll < bridgeRate && along == 0 && across >= 1 && across <= 3) {
			int bridgeAxis = 1 - corridorAxis;
			boolean leaking = (cellRoll & 3) == 0 || floodedStage(seed, x, z) > 0;
			return (leaking ? 3 : 1) + bridgeAxis;
		}
		return 0;
	}

	/** Rare anomalous wall section that lets players walk/noclip into it toward Level 2.1, backed by solid wall. */
	static boolean isNoclipWall(long seed, int x, int z) {
		if (roomKindAt(seed, x, z) != null || isOfficePatch(seed, x, z)) return false;
		if (tile(seed, x, z).kind() != Kind.WALL) return false;
		boolean backed = false;
		for (int d = 0; d < 4; d++) {
			if (tile(seed, x + WALL_DX[d], z + WALL_DZ[d]).kind() == Kind.FLOOR
					&& tile(seed, x - WALL_DX[d], z - WALL_DZ[d]).kind() == Kind.WALL) {
				backed = true;
				break;
			}
		}
		return backed && Math.floorMod(hash(seed, x, z, 0x41D9), 260) == 0;
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
