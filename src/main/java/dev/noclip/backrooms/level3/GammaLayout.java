package dev.noclip.backrooms.level3;

import static dev.noclip.backrooms.level3.Level3Plan.EAST;
import static dev.noclip.backrooms.level3.Level3Plan.NORTH;
import static dev.noclip.backrooms.level3.Level3Plan.SOUTH;
import static dev.noclip.backrooms.level3.Level3Plan.WEST;

import java.util.List;

/**
 * Base Gamma, block by block: a fixed template of 60x48 columns over the whole height of Level 3, looked up by the
 * position inside it (u east, v south, y the world height). After the CN wiki's Level 3 article and its Gamma
 * section, from the top down:
 *
 * <ul>
 * <li>y 40-45, the administration: server rooms behind a locked door, seen through its windows;
 * <li>y 33-38, level with the maze: the gate, the checkpoint, then the residential part (rooms with a cot and a
 * locker each, along corridors and an indoor street), and the door back to Level 11;
 * <li>y 10-31, the public part: a mall of three floors (9, 16, 23) round an atrium lit by simulated daylight, with
 * stalls, food carts, an arcade, a haunted house, the old wooden quarter, a hot spring and the elevator; the
 * storage facility opens off its ground floor on the east side;
 * <li>y 2-5, the tunnels, behind a locked gate: Mason's shop.
 * </ul>
 *
 * A stairwell in the south-east corner joins every floor. Pure: no Minecraft blocks, so tests can walk it.
 */
final class GammaLayout {
	static final int W = GammaPlan.W * Level3Plan.CELL, D = GammaPlan.H * Level3Plan.CELL, Y = Level3ChunkGenerator.HEIGHT;
	/** The gate is two blocks wide (u 26, 27) in the north wall, three high over the maze floor. */
	static final int GATE_U = 26, GATE_CLEARANCE = 3;
	/** The west/east side doors sit at v = 14 (matching maze cell J+2 offset 2); south side door sits at u = GATE_U, v = D-1. */
	static final int SIDE_V = 14;
	static final int TUNNEL = 1, MALL_1 = 9, MALL_2 = 16, MALL_3 = 23, RESIDENTIAL = 32, ADMIN = 39, ROOF = 46;
	/** The floors, bottom to top; the stairwell joins each to the next. */
	static final int[] FLOORS = {TUNNEL, MALL_1, MALL_2, MALL_3, RESIDENTIAL, ADMIN};
	/** The atrium, open from the mall's ground floor to the daylight ceiling. */
	static final int ATRIUM_U0 = 14, ATRIUM_U1 = 33, ATRIUM_V0 = 14, ATRIUM_V1 = 33;
	/** The hot spring in the middle of the atrium floor. */
	static final int SPRING_U0 = 21, SPRING_U1 = 26, SPRING_V0 = 21, SPRING_V1 = 26;

	enum Thing {
		AIR, BEDROCK, BRICK, TITANIUM, WALL, FLOOR, MALL_FLOOR, CEILING, LIGHT, BULB, DAYLIGHT, RAIL, STAIRS, GLASS, WATER, RIM,
		CHECKPOINT, LOCKER, COT, TABLE, SUPPLY_CRATE, CRATE, CAMERA, MEG_SIGN, LECTERN, IRON_DOOR, SIDE_DOOR, CAGE_GATE, EXIT_DOOR,
		ELEVATOR, PLANKS, PATCH, COBWEB, CANOPY_RED, CANOPY_YELLOW, CANOPY_BLUE, SERVER, ARCADE, SCREEN, JUKEBOX, SMOKER,
		BARBED_WIRE, ELECTRIC_FENCE, RELAY_BOX;

		/** Whether a player can stand in it (or climb through it). */
		boolean open() {
			return this == AIR || this == WATER || this == COBWEB || this == CHECKPOINT || this == IRON_DOOR || this == SIDE_DOOR
					|| this == CAGE_GATE || this == BARBED_WIRE;
		}

		/** Whether a player can stand on it. */
		boolean footing() {
			return !open() && this != RAIL && this != CANOPY_RED && this != CANOPY_YELLOW && this != CANOPY_BLUE && this != ELECTRIC_FENCE;
		}
	}

	/** One block: what, which way it faces (a plan direction; rails and gates use 0 for along X, 1 for along Z), which part. */
	record Bit(Thing thing, int dir, int part) {
	}

	/** Someone who lives or works in Gamma, where they stand (feet), and what they do (a core.trade role id, or null). */
	record Spot(String id, int u, int y, int v, String look, String name, String role) {
	}

	static final List<Spot> PEOPLE = List.of(
			new Spot("gate_west", 23, RESIDENTIAL + 1, 6, "meg_patrol", "M.E.G. Checkpoint Guard", null),
			new Spot("gate_east", 30, RESIDENTIAL + 1, 6, "meg_patrol", "M.E.G. Checkpoint Guard", null),
			new Spot("resident_north", 25, RESIDENTIAL + 1, 18, "colonist", "Gamma Resident", null),
			new Spot("resident_south", 30, RESIDENTIAL + 1, 36, "colonist", "Gamma Resident", null),
			new Spot("quartermaster", 17, MALL_1 + 1, 5, "meg_patrol", "M.E.G. Quartermaster", "quartermaster"),
			new Spot("supplier", 23, MALL_1 + 1, 5, "medic", "Gamma Pharmacy", "supplier"),
			new Spot("buyer", 29, MALL_1 + 1, 5, "trader", "Gamma Buyer", "buyer"),
			new Spot("performer", 24, MALL_2 + 1, 9, "speednoclipper", "Street Performer", null),
			new Spot("cook", 5, MALL_2 + 1, 19, "tom", "Food Cart", null),
			new Spot("pawnbroker", 54, MALL_1 + 1, 42, "trader", "B.N.T.G. Pawnbroker", "buyer"),
			new Spot("black_market", 52, MALL_1 + 1, 36, "speednoclipper", "Black Market Broker", "black_market"),
			new Spot("archivist", 37, ADMIN + 1, 44, "colonist", "M.E.G. Archivist", null),
			new Spot("mason", 5, TUNNEL + 1, 40, "aiko", "Mason", "mason"));

	private static final short[] TEMPLATE = new short[W * D * Y];

	private GammaLayout() {
	}

	static Bit at(int u, int y, int v) {
		if (u < 0 || v < 0 || u >= W || v >= D || y < 0 || y >= Y) return new Bit(Thing.BEDROCK, 0, 0);
		short packed = TEMPLATE[index(u, y, v)];
		return new Bit(Thing.values()[packed & 0xFF], (packed >> 8) & 3, (packed >> 10) & 3);
	}

	static Thing thing(int u, int y, int v) {
		return at(u, y, v).thing();
	}

	private static int index(int u, int y, int v) {
		return (y * D + v) * W + u;
	}

	private static void set(int u, int y, int v, Thing thing, int dir, int part) {
		TEMPLATE[index(u, y, v)] = (short) (thing.ordinal() | dir << 8 | part << 10);
	}

	private static void set(int u, int y, int v, Thing thing) {
		set(u, y, v, thing, 0, 0);
	}

	private static void box(int u0, int u1, int y0, int y1, int v0, int v1, Thing thing, int dir) {
		for (int u = u0; u <= u1; u++) {
			for (int y = y0; y <= y1; y++) {
				for (int v = v0; v <= v1; v++) set(u, y, v, thing, dir, 0);
			}
		}
	}

	private static void box(int u0, int u1, int y0, int y1, int v0, int v1, Thing thing) {
		box(u0, u1, y0, y1, v0, v1, thing, 0);
	}

	/** Something two blocks high: part 0 at y, part 1 over it. */
	private static void tall(int u, int y, int v, Thing thing, int dir) {
		set(u, y, v, thing, dir, 0);
		set(u, y + 1, v, thing, dir, 1);
	}

	private static void build() {
		// Solid all through, faced with brick outside; bedrock under and over, like the rest of the level.
		box(0, W - 1, 1, Y - 2, 0, D - 1, Thing.WALL);
		box(0, W - 1, 0, 0, 0, D - 1, Thing.BEDROCK);
		box(0, W - 1, Y - 1, Y - 1, 0, D - 1, Thing.BEDROCK);
		for (int y = 1; y < Y - 1; y++) {
			box(0, W - 1, y, y, 0, 0, Thing.BRICK);
			box(0, W - 1, y, y, D - 1, D - 1, Thing.BRICK);
			box(0, 0, y, y, 0, D - 1, Thing.BRICK);
			box(W - 1, W - 1, y, y, 0, D - 1, Thing.BRICK);
			// Inner titanium defense lining behind the outer brick wall (D2).
			box(1, W - 2, y, y, 1, 1, Thing.TITANIUM);
			box(1, W - 2, y, y, D - 2, D - 2, Thing.TITANIUM);
			box(1, 1, y, y, 1, D - 2, Thing.TITANIUM);
			box(W - 2, W - 2, y, y, 1, D - 2, Thing.TITANIUM);
		}
		mall();
		storage();
		residential();
		admin();
		tunnels();
		stairwell();
	}

	private static boolean inAtrium(int u, int v) {
		return u >= ATRIUM_U0 && u <= ATRIUM_U1 && v >= ATRIUM_V0 && v <= ATRIUM_V1;
	}

	/** Ceiling lights on a 4-block grid, under a ceiling at y; none hanging in mid-air over the atrium. */
	private static void lights(int u0, int u1, int v0, int v1, int y, Thing light, boolean avoidAtrium) {
		for (int u = u0; u <= u1; u++) {
			for (int v = v0; v <= v1; v++) {
				if (u % 4 != 1 || v % 4 != 3 || avoidAtrium && inAtrium(u, v)) continue;
				if (thing(u, y, v) == Thing.AIR && thing(u, y + 1, v) != Thing.AIR) set(u, y, v, light);
			}
		}
	}

	// The public part: three floors round an atrium under simulated daylight.
	private static void mall() {
		box(2, 45, MALL_1 + 1, RESIDENTIAL - 1, 2, 45, Thing.AIR);
		for (int floor : new int[] {MALL_1, MALL_2, MALL_3}) box(2, 45, floor, floor, 2, 45, Thing.MALL_FLOOR);
		box(2, 45, RESIDENTIAL, RESIDENTIAL, 2, 45, Thing.FLOOR);
		for (int floor : new int[] {MALL_2, MALL_3}) {
			box(ATRIUM_U0, ATRIUM_U1, floor, floor, ATRIUM_V0, ATRIUM_V1, Thing.AIR);
			box(ATRIUM_U0 - 1, ATRIUM_U1 + 1, floor + 1, floor + 1, ATRIUM_V0 - 1, ATRIUM_V0 - 1, Thing.RAIL, 0);
			box(ATRIUM_U0 - 1, ATRIUM_U1 + 1, floor + 1, floor + 1, ATRIUM_V1 + 1, ATRIUM_V1 + 1, Thing.RAIL, 0);
			box(ATRIUM_U0 - 1, ATRIUM_U0 - 1, floor + 1, floor + 1, ATRIUM_V0, ATRIUM_V1, Thing.RAIL, 1);
			box(ATRIUM_U1 + 1, ATRIUM_U1 + 1, floor + 1, floor + 1, ATRIUM_V0, ATRIUM_V1, Thing.RAIL, 1);
		}
		box(ATRIUM_U0, ATRIUM_U1, RESIDENTIAL - 1, RESIDENTIAL - 1, ATRIUM_V0, ATRIUM_V1, Thing.DAYLIGHT);
		lights(2, 45, 2, 45, MALL_2 - 1, Thing.LIGHT, true);
		lights(2, 45, 2, 45, MALL_3 - 1, Thing.LIGHT, true);
		lights(2, 45, 2, 45, RESIDENTIAL - 1, Thing.LIGHT, true);

		// Ground floor: the hot spring in the middle, three shops along the north side, the elevator in the west wall.
		box(SPRING_U0 - 1, SPRING_U1 + 1, MALL_1, MALL_1, SPRING_V0 - 1, SPRING_V1 + 1, Thing.RIM);
		box(SPRING_U0, SPRING_U1, MALL_1, MALL_1, SPRING_V0, SPRING_V1, Thing.WATER);
		box(SPRING_U0 - 1, SPRING_U1 + 1, MALL_1 - 1, MALL_1 - 1, SPRING_V0 - 1, SPRING_V1 + 1, Thing.RIM);
		Thing[] canopies = {Thing.CANOPY_BLUE, Thing.CANOPY_RED, Thing.CANOPY_YELLOW};
		for (int s = 0; s < 3; s++) {
			int u0 = 15 + 6 * s;
			box(u0, u0 + 4, MALL_1 + 1, MALL_1 + 1, 7, 7, Thing.TABLE);
			box(u0, u0 + 4, MALL_1 + 4, MALL_1 + 4, 3, 8, canopies[s]);
			set(u0, MALL_1 + 1, 3, Thing.SUPPLY_CRATE);
			set(u0 + 4, MALL_1 + 1, 3, Thing.CRATE);
		}
		tall(1, MALL_1 + 1, 24, Thing.ELEVATOR, EAST);
		set(1, MALL_1 + 3, 24, Thing.MEG_SIGN);
		// Relay drop-off box beside the mall shops.
		set(13, MALL_1 + 1, 5, Thing.RELAY_BOX, SOUTH, 0);

		// First floor: the old wooden quarter in the north-west corner, food carts, the haunted house, a busker.
		box(2, 12, MALL_2, MALL_2, 2, 12, Thing.PLANKS);
		box(13, 13, MALL_2 + 1, MALL_2 + 4, 2, 12, Thing.PLANKS);
		box(2, 13, MALL_2 + 1, MALL_2 + 4, 13, 13, Thing.PLANKS);
		box(7, 8, MALL_2 + 1, MALL_2 + 2, 13, 13, Thing.AIR);
		// Where a clump broke through the wood, patched with other boards; above it, the holes the death moths left.
		box(13, 13, MALL_2 + 1, MALL_2 + 3, 5, 8, Thing.PATCH);
		for (int u = 3; u <= 11; u += 2) set(u, MALL_2 + 4, 13, Thing.RAIL, 0, 0);
		set(5, MALL_2 + 1, 5, Thing.TABLE);
		set(9, MALL_2 + 1, 9, Thing.CRATE);
		for (int c = 0; c < 3; c++) {
			int v = 17 + 6 * c;
			box(3, 6, MALL_2 + 1, MALL_2 + 1, v + 1, v + 1, Thing.TABLE);
			set(3, MALL_2 + 1, v, Thing.SMOKER, SOUTH, 0);
			box(3, 6, MALL_2 + 3, MALL_2 + 3, v - 1, v + 2, c == 1 ? Thing.CANOPY_YELLOW : Thing.CANOPY_RED);
		}
		box(2, 9, MALL_2 + 1, MALL_2 + 5, 36, 36, Thing.WALL);
		box(9, 9, MALL_2 + 1, MALL_2 + 5, 36, 45, Thing.WALL);
		box(9, 9, MALL_2 + 1, MALL_2 + 2, 40, 40, Thing.COBWEB);
		for (int[] web : new int[][] {{3, 38}, {6, 41}, {4, 44}, {8, 38}, {2, 41}}) set(web[0], MALL_2 + 2, web[1], Thing.COBWEB);
		box(2, 8, MALL_2 - 1 + 6, MALL_2 - 1 + 6, 37, 45, Thing.WALL);
		set(9, MALL_2 + 3, 42, Thing.MEG_SIGN);

		// Second floor: the arcade along the north side.
		for (int u = 4; u <= 42; u += 3) {
			if (u >= 34 && u <= 36) continue;
			set(u, MALL_3 + 1, 3, Thing.ARCADE, SOUTH, 0);
			set(u, MALL_3 + 2, 3, Thing.SCREEN, SOUTH, 0);
		}
		set(24, MALL_3 + 1, 8, Thing.JUKEBOX);
	}

	// The storage facility, off the mall's ground floor: rows of stores, a pawnshop counter, a small museum.
	private static void storage() {
		box(49, 58, MALL_1 + 1, MALL_1 + 6, 2, 45, Thing.AIR);
		box(49, 58, MALL_1, MALL_1, 2, 45, Thing.FLOOR);
		box(46, 48, MALL_1 + 1, MALL_1 + 3, 20, 23, Thing.AIR);
		box(46, 48, MALL_1, MALL_1, 20, 23, Thing.FLOOR);
		for (int v = 12; v <= 34; v += 4) {
			if (v >= 18 && v <= 25) continue;
			box(50, 52, MALL_1 + 1, MALL_1 + 2, v, v + 1, Thing.CRATE);
			box(55, 57, MALL_1 + 1, MALL_1 + 2, v, v + 1, Thing.SUPPLY_CRATE);
		}
		box(50, 57, MALL_1 + 1, MALL_1 + 1, 40, 40, Thing.TABLE);
		// The museum: the original Ultimate Backrooms Cookbook in a glass case.
		box(52, 54, MALL_1 + 1, MALL_1 + 2, 5, 7, Thing.GLASS);
		set(53, MALL_1 + 1, 6, Thing.LECTERN, SOUTH, 0);
		set(53, MALL_1 + 2, 6, Thing.AIR);
		set(53, MALL_1 + 3, 6, Thing.GLASS);
		lights(49, 58, 2, 45, MALL_1 + 6, Thing.LIGHT, false);
	}

	/** The rows of rooms: each starts with a wall at v, and its room is v + 1 to v + 5. */
	private static final int[] ROWS = {13, 19, 25, 31, 37, 43};

	// Level with the maze: gate, checkpoint, then the residential part.
	private static void residential() {
		int y0 = RESIDENTIAL + 1, y1 = ADMIN - 1;
		box(2, 45, y0, y1, 2, 45, Thing.AIR);
		box(2, 45, ADMIN, ADMIN, 2, 45, Thing.CEILING);
		// The gate, and the checkpoint behind it: lockers for weapons, a metal detector you are let through.
		box(GATE_U, GATE_U + 1, y0, y0 + GATE_CLEARANCE - 1, 0, 1, Thing.AIR);
		box(2, 45, y0, y1, 9, 9, Thing.WALL);
		tall(GATE_U, y0, 9, Thing.CHECKPOINT, NORTH);
		tall(GATE_U + 1, y0, 9, Thing.CHECKPOINT, NORTH);
		box(19, 23, y0 + 2, y0 + 2, 9, 9, Thing.GLASS);
		box(30, 34, y0 + 2, y0 + 2, 9, 9, Thing.GLASS);
		for (int u = 4; u <= 14; u++) {
			if (u == 9) continue;
			set(u, y0, 2, Thing.LOCKER, SOUTH, 0);
			set(u, y0 + 1, 2, Thing.LOCKER, SOUTH, 0);
		}
		for (int u = 37; u <= 44; u++) {
			set(u, y0, 2, Thing.LOCKER, SOUTH, 0);
			set(u, y0 + 1, 2, Thing.LOCKER, SOUTH, 0);
		}
		set(GATE_U - 1, y1 - 1, 2, Thing.CAMERA, SOUTH, 0);
		set(GATE_U + 2, y1 - 1, 2, Thing.CAMERA, SOUTH, 0);
		// D2 perimeter defenses: electric fence flanking the entry hall & razor wire atop the checkpoint parapet.
		for (int u = 16; u <= 20; u++) set(u, y0, 2, Thing.ELECTRIC_FENCE, 0, 0);
		for (int u = 32; u <= 35; u++) set(u, y0, 2, Thing.ELECTRIC_FENCE, 0, 0);
		for (int u = 19; u <= 23; u++) set(u, y1, 8, Thing.BARBED_WIRE);
		for (int u = 30; u <= 34; u++) set(u, y1, 8, Thing.BARBED_WIRE);
		// The M.E.G.'s sign is set into the wall itself.
		set(GATE_U - 2, y0 + 1, 9, Thing.MEG_SIGN);
		set(GATE_U + 3, y0 + 1, 9, Thing.MEG_SIGN);

		// Rooms: two columns off a corridor in the west, one off the street in the east.
		box(9, 9, y0, y1, 13, 45, Thing.WALL);
		box(14, 14, y0, y1, 13, 45, Thing.WALL);
		box(22, 22, y0, y1, 13, 45, Thing.WALL);
		box(33, 33, y0, y1, 13, 37, Thing.WALL);
		for (int row : ROWS) {
			box(2, 9, y0, y1, row, row, Thing.WALL);
			box(14, 22, y0, y1, row, row, Thing.WALL);
			if (row <= 37) box(33, 45, y0, y1, row, row, Thing.WALL);
		}
		box(2, 9, y0, y1, 44, 45, Thing.WALL);
		box(14, 22, y0, y1, 44, 45, Thing.WALL);
		for (int row : ROWS) {
			if (row == 43) continue;
			room(9, 2, WEST, row);
			room(14, 21, EAST, row);
			if (row < 37) room(33, 45, EAST, row);
		}
		// The street: lit like a road, the door back to Level 11 at its south end.
		lights(10, 13, 10, 45, y1, Thing.LIGHT, false);
		lights(23, 32, 10, 45, y1, Thing.LIGHT, false);
		lights(2, 45, 2, 12, y1, Thing.LIGHT, false);
		tall(27, y0, D - 2, Thing.EXIT_DOOR, NORTH);
		set(27, y0 + 3, D - 2, Thing.MEG_SIGN);
		// Three locked side doors (west, east, south): openable only from inside Gamma.
		box(1, 1, y0, y0 + 1, SIDE_V, SIDE_V, Thing.AIR);
		tall(0, y0, SIDE_V, Thing.SIDE_DOOR, WEST);
		box(26, 26, y0, y0 + 1, D - 2, D - 2, Thing.AIR);
		tall(GATE_U, y0, D - 1, Thing.SIDE_DOOR, SOUTH);
		// Short east service corridor through the storage roof slab to the east side door.
		box(46, W - 2, RESIDENTIAL, RESIDENTIAL, SIDE_V, SIDE_V + 1, Thing.FLOOR);
		box(46, W - 2, y0, y0 + 2, SIDE_V, SIDE_V + 1, Thing.AIR);
		set(50, y0 + 2, SIDE_V, Thing.BULB);
		set(55, y0 + 2, SIDE_V, Thing.BULB);
		tall(W - 1, y0, SIDE_V, Thing.SIDE_DOOR, EAST);
	}

	/** A room behind a wall at u = door, its far end at u = back (west of the door if dir is WEST, else east). */
	private static void room(int door, int back, int dir, int row) {
		int y0 = RESIDENTIAL + 1;
		box(door, door, y0, y0 + 1, row + 3, row + 3, Thing.AIR);
		int facing = dir == WEST ? EAST : WEST;
		set(back, y0, row + 1, Thing.COT, facing, 0);
		set(back, y0, row + 5, Thing.LOCKER, facing, 0);
		int middle = (door + back) / 2;
		set(middle, ADMIN - 1, row + 3, Thing.BULB);
	}

	// The administration on top: no way in, only a look through the windows at the servers.
	private static void admin() {
		int y0 = ADMIN + 1, y1 = ROOF - 1;
		box(2, 45, y0, y1, 2, 45, Thing.AIR);
		box(33, 33, y0, y1, 37, 45, Thing.WALL);
		box(33, 45, y0, y1, 37, 37, Thing.WALL);
		tall(33, y0, 43, Thing.IRON_DOOR, WEST);
		box(33, 33, y0 + 1, y0 + 2, 39, 40, Thing.GLASS);
		box(37, 43, y0 + 1, y0 + 2, 37, 37, Thing.GLASS);
		for (int v = 4; v <= 34; v += 3) {
			for (int u = 4; u <= 44; u++) {
				if (u % 6 == 0 || u >= 31 && u <= 34 && v >= 35) continue;
				tall(u, y0, v, Thing.SERVER, SOUTH);
			}
		}
		lights(2, 45, 2, 45, y1, Thing.LIGHT, false);
		lights(34, 45, 38, 45, y1, Thing.BULB, false);
	}

	// The tunnels under the mall, locked away: a corridor to Mason's shop.
	private static void tunnels() {
		int y0 = TUNNEL + 1, y1 = TUNNEL + 4;
		box(2, 33, TUNNEL, TUNNEL, 36, 45, Thing.FLOOR);
		box(13, 33, y0, y1, 39, 41, Thing.AIR);
		box(3, 12, y0, y1, 37, 44, Thing.AIR);
		box(7, 7, y0, y0, 38, 43, Thing.TABLE);
		set(3, y0, 37, Thing.SUPPLY_CRATE);
		set(3, y0, 44, Thing.SUPPLY_CRATE);
		for (int u = 15; u <= 33; u += 5) set(u, y1, 40, Thing.BULB);
		set(5, y1, 40, Thing.BULB);
		set(10, y1, 40, Thing.BULB);
	}

	/** The stairwell's two lanes, for runs going east and going west. */
	private static final int LANE_EAST = 39, LANE_WEST = 42, STAIR_U0 = 35, STAIR_U1 = 44;

	// South-east corner, every floor: switchback runs of stairs, a railing round each opening, a locked gate down.
	private static void stairwell() {
		box(34, 45, TUNNEL, TUNNEL, 38, 45, Thing.FLOOR);
		box(34, 45, TUNNEL + 1, MALL_1 - 1, 38, 45, Thing.AIR);
		box(34, 45, MALL_1, MALL_1, 38, 45, Thing.MALL_FLOOR);
		box(34, 45, RESIDENTIAL + 1, ADMIN - 1, 38, 45, Thing.AIR);
		for (int f = 0; f + 1 < FLOORS.length; f++) {
			int from = FLOORS[f], to = FLOORS[f + 1], rise = to - from;
			boolean east = f % 2 == 0;
			int lane = east ? LANE_EAST : LANE_WEST, step = east ? 1 : -1, start = east ? STAIR_U0 : STAIR_U1;
			for (int a = 0; a < rise; a++) {
				int u = start + step * a, top = from + 1 + a;
				for (int v = lane; v <= lane + 1; v++) {
					for (int y = from + 1; y < top; y++) set(u, y, v, Thing.WALL);
					set(u, top, v, Thing.STAIRS, east ? EAST : WEST, 0);
					if (a < rise - 1) set(u, to, v, Thing.AIR);
					for (int y = top + 1; y <= top + 3 && y < to; y++) set(u, y, v, Thing.AIR);
				}
			}
			// The railing round the opening this run comes up through, and along the run's top step.
			for (int a = -1; a < rise; a++) {
				int u = start + step * a;
				set(u, to + 1, lane - 1, Thing.RAIL, 0, 0);
				set(u, to + 1, lane + 2, Thing.RAIL, 0, 0);
			}
			for (int v = lane; v <= lane + 1; v++) set(start - step, to + 1, v, Thing.RAIL, 1, 0);
		}
		// The way down to the tunnels is kept locked (the gate can be picked).
		for (int v = LANE_EAST; v <= LANE_EAST + 1; v++) tall(STAIR_U0 + MALL_1 - TUNNEL, MALL_1 + 1, v, Thing.CAGE_GATE, 1);
		for (int v = LANE_EAST - 1; v <= LANE_EAST + 2; v += 3) set(STAIR_U0 + MALL_1 - TUNNEL, MALL_1 + 1, v, Thing.RAIL, 0, 0);
		lights(34, 45, 38, 45, MALL_2 - 1, Thing.BULB, false);
		lights(34, 45, 38, 45, ADMIN - 1, Thing.BULB, false);
	}

	// Last, so every table above is set before the template is built.
	static {
		build();
	}
}
