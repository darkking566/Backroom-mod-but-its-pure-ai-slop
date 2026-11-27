package dev.noclip.backrooms.frontrooms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The M.E.G. research institute at the middle of the Frontrooms (docs/企劃書/1-遊戲玩法/README.md, "研究所改版"), as
 * plain data. Five floor plans (B2, B1 and three floors above ground) 96 blocks wide (x) and 72 deep (z), drawn by
 * tools/make_institute_plans.py into {@link InstitutePlans}, are turned here into pieces of the building by
 * {@link #cell}, together with what no plan shows: the stairs between floors, the atrium three floors high with the
 * specimen chamber in it, the facade, and what stands on the roof. Nothing here touches Minecraft's blocks, so tests
 * can walk the plans.
 * <p>
 * The look follows the research facility at the end of the film (A24's Backrooms, 2026): white walls over a mustard
 * wainscot, dark observation windows, a hospital wing with mint walls; outside, precast concrete with ribbon windows.
 * <p>
 * Plan legend: {@code E} outside wall, {@code #} inside wall, {@code .} floor, {@code ,} floor of a work area (players
 * may build there), {@code ;} floor of a dark room, {@code +} floor of the medical wing (its walls are mint), {@code _}
 * the atrium's floor, {@code ' '} the atrium's void, {@code c} the specimen chamber, {@code v} railing, {@code s} stairs,
 * {@code P} pillar, {@code w} glass, {@code W} observation window, {@code o} a doorway in glass, {@code g} the main gate,
 * {@code 1 3 5} blast doors that open once the team has met that many quotas, {@code a} where people arrive,
 * {@code k y u n q} where the commander, buyer, supplier, medic and quartermaster stand, {@code B S C M Q} their
 * counters, {@code L} lockers, {@code r} weapon lockers, {@code b f} the head and foot of a bed, {@code D} B1 transport
 * pads, {@code d} the ground-floor pad back to Level 0, {@code h} the sealed hatch down to B2, {@code l} its ladder,
 * {@code H} archive shelves, {@code F} archive files, and workstations: {@code T} crafting table, {@code K} salvage
 * bench, {@code V} workshop bench, {@code A} almond distiller, {@code R} anomaly altar, {@code X} scrap recycler,
 * {@code N} spiderweb crafting table, {@code Y} distillery barrel, {@code U} research terminal, {@code Z} crystal
 * replicator.
 */
public final class InstituteLayout {
	public static final int WIDTH = 96, DEPTH = 72, MIN_X = -48, MIN_Z = -36, MAX_X = MIN_X + WIDTH - 1, MAX_Z = MIN_Z + DEPTH - 1;
	/** Floor and ceiling heights: B2, B1 and the three floors above ground; the roof; the top of the atrium's lantern. */
	public static final int B2_FLOOR = 46, B2_CEILING = 51, B1_FLOOR = 54, B1_CEILING = 61, FLOOR = 64, CEILING = 69,
			F2_FLOOR = 70, F2_CEILING = 75, F3_FLOOR = 76, F3_CEILING = 81, ROOF = 82, LANTERN_TOP = 98;
	/** Everything the institute builds lies between these heights, inclusive. */
	public static final int BOTTOM = B2_FLOOR - 1, TOP = LANTERN_TOP + 1;
	/** The atrium (open from the ground floor up to the skylight) and the specimen chamber in its middle. */
	public static final int ATRIUM_MIN = -12, ATRIUM_MAX = 11, SPECIMEN_MIN = -4, SPECIMEN_MAX = 3;
	private static final String WORKSTATIONS = "TKVARXNYUZ";
	private static final String OPEN = ".,;+_akyunqgoDdhsl";
	private static final String PEOPLE = "kyunq";

	/** What a piece of the building is; the generator picks the block. */
	public enum Kind {
		/** Not the institute's: the ground and the sky around it. */
		NONE,
		/** Concrete that no one sees: under walls, between floors, around the basements. */
		SOLID,
		WALL, WAINSCOT, MINT_WALL, MINT_WAINSCOT, PILLAR, GLASS, OBSERVATION, FLOOR, LOBBY_FLOOR, WORK_FLOOR, CEILING, LIGHT,
		EMERGENCY_LIGHT, ROOF, AIR, DOOR, DOOR_LAMP, STAIR, LADDER, HATCH, COUNTER, LOCKER, WEAPON_LOCKER, BED_HEAD, BED_FOOT,
		STATION, PAD, SHELF, FILE, RAIL,
		/** Outside: precast concrete, ribbon windows, dark steel, the lettering over the entrance. */
		PRECAST, RIBBON, STEEL, SIGN,
		/** The specimen chamber: wired glass, hazard bands, a piece of Level 0 hung inside, the field coils over it. */
		CONTAINMENT, BAND, LEVEL0_CARPET, LEVEL0_WALL, LEVEL0_CEILING, LEVEL0_LIGHT, BARS, COIL,
		/** On the roof: the helipad's asphalt and its paint. */
		ASPHALT, PAINT,
		/** One of the institute's objects (InstituteFurnishing): the block that draws it, and the rest of its blocks. */
		PROP, PROP_PART
	}

	/**
	 * One block of the building. {@code facing} is a 2D data value (0 south, 1 west, 2 north, 3 east) for things that
	 * face a way; {@code requires} is how many quotas open a blast door; {@code upper} its top half; {@code code} the
	 * plan character (which workstation).
	 */
	public record Cell(Kind kind, int facing, int requires, boolean upper, char code) {
		static final Cell NONE = of(Kind.NONE), SOLID = of(Kind.SOLID), AIR = of(Kind.AIR);

		static Cell of(Kind kind) {
			return new Cell(kind, 0, 0, false, ' ');
		}
	}

	/** A blast door column: where its lower half is, what opens it, and whether its wall runs along x. */
	public record Door(int x, int y, int z, int requires, boolean alongX) {
	}

	/** A spot on a floor (x and z). */
	public record Spot(int x, int z) {
	}

	/**
	 * A flight of stairs from one floor up to the next: its footprint, which way it climbs ({@code dx}, {@code dz}),
	 * where its foot is ({@code footX}, {@code footZ}: the first step's column), and the floors it joins. Steps are one
	 * block a column; the last one is level with the upper floor. Above the steps it is open up to the upper floor's
	 * ceiling.
	 */
	public record Flight(int minX, int minZ, int maxX, int maxZ, int dx, int dz, int foot, int low, int high, int ceiling) {
		public boolean covers(int x, int z) {
			return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
		}

		/** Which step a column is (0 at the foot). */
		public int step(int x, int z) {
			return dx != 0 ? (x - foot) * dx : (z - foot) * dz;
		}

		public int steps() {
			return high - low;
		}

		/** The 2D data value of the way up. */
		public int facing() {
			return dx > 0 ? 3 : dx < 0 ? 1 : dz > 0 ? 0 : 2;
		}
	}

	private static final String[][] PLANS = {InstitutePlans.B2, InstitutePlans.B1, InstitutePlans.F1, InstitutePlans.F2, InstitutePlans.F3};
	private static final int[] FLOORS = {B2_FLOOR, B1_FLOOR, FLOOR, F2_FLOOR, F3_FLOOR};
	private static final int[] CEILINGS = {B2_CEILING, B1_CEILING, CEILING, F2_CEILING, F3_CEILING};
	private static final List<Flight> FLIGHTS = List.of(
			// Down to B1 from the deep wing: the foot is at the west end.
			new Flight(-9, -30, 0, -27, 1, 0, -9, B1_FLOOR, FLOOR, CEILING),
			// Up to the second floor on both sides of the atrium hall, then on to the third.
			new Flight(-24, 5, -22, 10, 0, -1, 10, FLOOR, F2_FLOOR, F2_CEILING),
			new Flight(21, 5, 23, 10, 0, -1, 10, FLOOR, F2_FLOOR, F2_CEILING),
			new Flight(-24, -6, -22, -1, 0, -1, -1, F2_FLOOR, F3_FLOOR, F3_CEILING),
			new Flight(21, -6, 23, -1, 0, -1, -1, F2_FLOOR, F3_FLOOR, F3_CEILING));
	private static final Map<Character, Spot> SPOTS = new HashMap<>();
	private static final List<Door> DOORS = new ArrayList<>();
	private static final List<Spot> PADS = new ArrayList<>();
	private static final List<Spot> LOBBY_PADS = new ArrayList<>();
	private static final List<Spot> FILES = new ArrayList<>();
	private static Spot hatch;
	/** Columns next to the medical wing, whose walls are mint. */
	private static final boolean[][] MINT = new boolean[PLANS.length][WIDTH * DEPTH];

	static {
		for (String[] plan : PLANS) {
			if (plan.length != DEPTH) throw new IllegalStateException("a plan is " + plan.length + " rows deep");
			for (String row : plan) if (row.length() != WIDTH) throw new IllegalStateException("a plan row is " + row.length() + " wide");
		}
		String[] ground = PLANS[2];
		for (int r = 0; r < DEPTH; r++) {
			for (int c = 0; c < WIDTH; c++) {
				char ch = ground[r].charAt(c);
				if (PEOPLE.indexOf(ch) >= 0 || ch == 'a') SPOTS.put(ch, new Spot(c + MIN_X, r + MIN_Z));
				if (ch == '1' || ch == '3' || ch == '5') {
					boolean alongX = isDoor(ground, c - 1, r) || isDoor(ground, c + 1, r);
					DOORS.add(new Door(c + MIN_X, FLOOR + 1, r + MIN_Z, ch - '0', alongX));
				}
				if (ch == 'd') LOBBY_PADS.add(new Spot(c + MIN_X, r + MIN_Z));
				if (PLANS[1][r].charAt(c) == 'D') PADS.add(new Spot(c + MIN_X, r + MIN_Z));
				if (PLANS[1][r].charAt(c) == 'h') hatch = new Spot(c + MIN_X, r + MIN_Z);
				if (PLANS[0][r].charAt(c) == 'F') FILES.add(new Spot(c + MIN_X, r + MIN_Z));
			}
		}
		for (int f = 0; f < PLANS.length; f++) {
			for (int r = 0; r < DEPTH; r++) {
				for (int c = 0; c < WIDTH; c++) {
					if (PLANS[f][r].charAt(c) != '+') continue;
					for (int dr = -1; dr <= 1; dr++) {
						for (int dc = -1; dc <= 1; dc++) {
							int rr = r + dr, cc = c + dc;
							if (rr >= 0 && rr < DEPTH && cc >= 0 && cc < WIDTH) MINT[f][rr * WIDTH + cc] = true;
						}
					}
				}
			}
		}
	}

	private InstituteLayout() {
	}

	private static boolean isDoor(String[] plan, int c, int r) {
		if (c < 0 || c >= WIDTH || r < 0 || r >= DEPTH) return false;
		char ch = plan[r].charAt(c);
		return ch == '1' || ch == '3' || ch == '5';
	}

	/** Whether (x, z) is under the building. */
	public static boolean inside(int x, int z) {
		return x >= MIN_X && x <= MAX_X && z >= MIN_Z && z <= MAX_Z;
	}

	/** Whether (x, z) is in the atrium, open from the ground floor to the skylight. */
	public static boolean atrium(int x, int z) {
		return x >= ATRIUM_MIN && x <= ATRIUM_MAX && z >= ATRIUM_MIN && z <= ATRIUM_MAX;
	}

	/** The ground floor's grand rooms: the atrium hall and the entrance hall, floored in terrazzo. */
	public static boolean lobby(int x, int z) {
		return x >= -24 && x <= 23 && z >= -24 && z <= 23 || x >= -10 && x <= 9 && z >= 24 && z <= 34;
	}

	/** The plan character of a floor (0 B2, 1 B1, 2 ground, 3 and 4 the floors above) at (x, z); '#' outside the plans. */
	public static char plan(int floor, int x, int z) {
		if (!inside(x, z)) return '#';
		return PLANS[floor][z - MIN_Z].charAt(x - MIN_X);
	}

	public static int floors() {
		return PLANS.length;
	}

	public static int floorY(int floor) {
		return FLOORS[floor];
	}

	public static int ceilingY(int floor) {
		return CEILINGS[floor];
	}

	/** Whether someone can stand in a plan cell. */
	public static boolean open(char ch) {
		return OPEN.indexOf(ch) >= 0;
	}

	/** Where someone stands on the ground floor: {@code a} arrivals, {@code k y u n q} the institute's people. */
	public static Spot spot(char who) {
		return SPOTS.get(who);
	}

	public static List<Door> doors() {
		return DOORS;
	}

	public static List<Spot> pads() {
		return PADS;
	}

	/** Ground-floor lobby return pads that send players straight back to Level 0 (open from clearance 0). */
	public static List<Spot> lobbyPads() {
		return LOBBY_PADS;
	}

	public static List<Spot> files() {
		return FILES;
	}

	public static List<Flight> flights() {
		return FLIGHTS;
	}

	/** The sealed hatch in B1's floor. */
	public static Spot hatch() {
		return hatch;
	}

	/** Whether a player may build at this block: the air of a work area on the ground floor. */
	public static boolean workArea(int x, int y, int z) {
		return inside(x, z) && y > FLOOR && y < CEILING && plan(2, x, z) == ',';
	}

	/** Whether a block belongs to the building (which nobody may break). */
	public static boolean building(int x, int y, int z) {
		return cell(x, y, z).kind() != Kind.NONE && !workArea(x, y, z);
	}

	private static int floorOf(int y) {
		for (int f = FLOORS.length - 1; f >= 0; f--) if (y >= FLOORS[f]) return f;
		return -1;
	}

	/** The piece of the building at a block, or {@link Cell#NONE} where the building is not. */
	public static Cell cell(int x, int y, int z) {
		if (y < BOTTOM || y > TOP || !inside(x, z)) return Cell.NONE;
		if (y >= ROOF) return roof(x, y, z);
		Cell prop = prop(x, y, z);
		if (prop != null) return prop;
		if (atrium(x, z) && y > FLOOR) return specimen(x, y, z);
		for (Flight flight : FLIGHTS) {
			if (flight.covers(x, z) && y >= flight.low() && y < flight.ceiling()) return stair(flight, x, y, z);
		}
		int floor = floorOf(y);
		if (floor < 0) return Cell.SOLID;
		String[] plan = PLANS[floor];
		int c = x - MIN_X, r = z - MIN_Z;
		char ch = plan[r].charAt(c);
		int base = FLOORS[floor], ceiling = CEILINGS[floor];
		if (ch == 'E') return facade(floor, x, y, z);
		if (ch == 'g') return y == base ? Cell.of(Kind.LOBBY_FLOOR) : y - base <= 3 ? Cell.AIR : Cell.of(Kind.PRECAST);
		// The slab between B2 and B1, and between B1 and the ground floor; the ladder goes through the first.
		if (y > ceiling) return floor == 0 && ch == 'l' ? ladder() : Cell.SOLID;
		boolean mint = MINT[floor][r * WIDTH + c];
		if (y == base) {
			return switch (ch) {
				case '#', 'P' -> Cell.SOLID;
				case ',' -> Cell.of(Kind.WORK_FLOOR);
				case 'h' -> Cell.of(Kind.HATCH);
				case 'D', 'd' -> Cell.of(Kind.PAD);
				default -> Cell.of(floor == 2 && lobby(x, z) ? Kind.LOBBY_FLOOR : Kind.FLOOR);
			};
		}
		if (y == ceiling) {
			if (ch == 'l') return ladder();
			if (ch == '#' || ch == 'P') return Cell.SOLID;
			if (ch == ';') {
				// Dark rooms: no light but, in the archive, a red emergency lamp here and there.
				return floor == 0 && Math.floorMod(x, 6) == 0 && Math.floorMod(z, 6) == 0 ? Cell.of(Kind.EMERGENCY_LIGHT) : Cell.of(Kind.CEILING);
			}
			// Troffers two blocks long, in rows.
			if (open(ch) && Math.floorMod(x, 4) <= 1 && Math.floorMod(z, 4) == 0) return Cell.of(Kind.LIGHT);
			return Cell.of(Kind.CEILING);
		}
		int h = y - base, top = ceiling - base - 1;
		Kind wall = mint ? Kind.MINT_WALL : Kind.WALL, wainscot = mint ? Kind.MINT_WAINSCOT : Kind.WAINSCOT;
		return switch (ch) {
			case '#' -> floor >= 2 || touchesOpen(plan, c, r) ? Cell.of(h == 1 ? wainscot : wall) : Cell.SOLID;
			case 'P' -> Cell.of(Kind.PILLAR);
			case 'w' -> Cell.of(h == 1 ? wainscot : h <= 3 ? Kind.GLASS : wall);
			case 'W' -> Cell.of(h == 1 ? wainscot : h < top ? Kind.OBSERVATION : wall);
			case 'o' -> h <= 3 ? Cell.AIR : Cell.of(Kind.GLASS);
			case 'v' -> h == 1 ? Cell.of(Kind.RAIL) : Cell.AIR;
			case 'c' -> Cell.of(Kind.STEEL);
			case '1', '3', '5' -> {
				int requires = ch - '0';
				// facing 0: the wall (and the door's plate) runs along x; 1: along z.
				int along = isDoor(plan, c - 1, r) || isDoor(plan, c + 1, r) ? 0 : 1;
				if (h <= 2) yield new Cell(Kind.DOOR, along, requires, h == 2, ch);
				yield h == 3 ? new Cell(Kind.DOOR_LAMP, along, requires, false, ch) : Cell.of(wall);
			}
			case 'B', 'S', 'C', 'M', 'Q' -> h == 1 ? Cell.of(Kind.COUNTER) : Cell.AIR;
			case 'L' -> h <= 2 ? new Cell(Kind.LOCKER, facingOpen(plan, c, r), 0, h == 2, ch) : Cell.AIR;
			case 'r' -> h <= 2 ? new Cell(Kind.WEAPON_LOCKER, facingOpen(plan, c, r), 0, h == 2, ch) : Cell.AIR;
			case 'b', 'f' -> h == 1 ? new Cell(ch == 'b' ? Kind.BED_HEAD : Kind.BED_FOOT, bedFacing(plan, c, r, ch), 0, false, ch) : Cell.AIR;
			case 'H' -> h <= 3 ? Cell.of(Kind.SHELF) : Cell.AIR;
			case 'F' -> h == 1 ? new Cell(Kind.FILE, 0, 0, false, ch) : Cell.AIR;
			case 'l' -> ladder();
			default -> station(plan, c, r, ch, h);
		};
	}

	/** An object of the institute filling this block, or null. {@code requires} carries which object it is. */
	private static Cell prop(int x, int y, int z) {
		int floor = floorOf(y);
		if (floor < 0 || y <= FLOORS[floor] || y >= CEILINGS[floor]) return null;
		InstituteFurnishing.Occupant occupant = InstituteFurnishing.at(floor, x, y - FLOORS[floor] - 1, z);
		if (occupant == null) return null;
		InstituteFurnishing.Place place = InstituteFurnishing.places().get(occupant.index());
		return new Cell(occupant.master() ? Kind.PROP : Kind.PROP_PART, place.facing(), occupant.index(), false, 'p');
	}

	/** Whether an object stands in the way on a floor at (x, z) (things hung on walls do not). */
	public static boolean furnished(int floor, int x, int z) {
		InstituteFurnishing.Occupant occupant = InstituteFurnishing.at(floor, x, 0, z);
		return occupant != null && !InstituteFurnishing.places().get(occupant.index()).type().mount().equals("wall");
	}

	private static Cell station(String[] plan, int c, int r, char ch, int h) {
		if (WORKSTATIONS.indexOf(ch) < 0) return Cell.AIR;
		return h == 1 ? new Cell(Kind.STATION, facingOpen(plan, c, r), 0, false, ch) : Cell.AIR;
	}

	/** A step, the solid under it, or the open stairwell over it. */
	private static Cell stair(Flight flight, int x, int y, int z) {
		int step = flight.step(x, z);
		int surface = flight.low() + 1 + step;
		if (y < surface) return Cell.SOLID;
		if (y == surface) return step == flight.steps() - 1 ? Cell.of(Kind.FLOOR) : new Cell(Kind.STAIR, flight.facing(), 0, false, 's');
		return Cell.AIR;
	}

	/** The outside walls: precast concrete, ribbon windows between pilasters, glass all the way up at the entrance. */
	private static Cell facade(int floor, int x, int y, int z) {
		if (floor < 2) return Cell.SOLID;
		int h = y - FLOORS[floor];
		boolean sideX = x == MIN_X || x == MAX_X, sideZ = z == MIN_Z || z == MAX_Z;
		if (sideX && sideZ) return Cell.of(Kind.PRECAST);
		int along = sideX ? z : x;
		// The south front: the lettering over the entrance, and the entrance hall's glass.
		if (z == MAX_Z && x >= -14 && x <= 13 && floor >= 3) return letter(x, y) ? Cell.of(Kind.SIGN) : Cell.of(Kind.PRECAST);
		if (z == MAX_Z && x >= -10 && x <= 9 && floor == 2) {
			if (h == 0 || h >= 5) return Cell.of(Kind.PRECAST);
			return Cell.of(Math.floorMod(x, 4) == 0 ? Kind.STEEL : Kind.RIBBON);
		}
		if (h <= 1 || h >= 4 || Math.floorMod(along, 8) == 0) return Cell.of(Kind.PRECAST);
		return Cell.of(Kind.RIBBON);
	}

	/** "M.E.G." in letters five blocks high over the entrance (x -11..9, y 73..77). */
	private static final String[] LETTERS = {
			"#...#.....####.....####..",
			"##.##.....#.......#......",
			"#.#.#.....###.....#.##...",
			"#...#.....#.......#..#...",
			"#...#.#...####.#...###.#.",
	};

	private static boolean letter(int x, int y) {
		int row = 77 - y, col = x + 11;
		if (row < 0 || row >= LETTERS.length || col < 0 || col >= LETTERS[0].length()) return false;
		return LETTERS[row].charAt(col) == '#';
	}

	/** The roof, its parapet, the atrium's glass lantern, and what stands on the roof. */
	private static Cell roof(int x, int y, int z) {
		boolean edge = x == MIN_X || x == MAX_X || z == MIN_Z || z == MAX_Z;
		int lx0 = ATRIUM_MIN - 1, lx1 = ATRIUM_MAX + 1;
		boolean inLantern = x >= lx0 && x <= lx1 && z >= lx0 && z <= lx1;
		boolean lanternRing = inLantern && (x == lx0 || x == lx1 || z == lx0 || z == lx1);
		if (atrium(x, z) && y < LANTERN_TOP) return specimen(x, y, z);
		if (inLantern && y == LANTERN_TOP) {
			return lanternRing || Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0 ? Cell.of(Kind.STEEL) : Cell.of(Kind.CONTAINMENT);
		}
		if (lanternRing && y < LANTERN_TOP) {
			boolean corner = (x == lx0 || x == lx1) && (z == lx0 || z == lx1);
			int along = x == lx0 || x == lx1 ? z : x;
			if (y == ROOF || corner || y == LANTERN_TOP - 1 || Math.floorMod(along, 4) == 0) return Cell.of(Kind.STEEL);
			return Cell.of(Kind.RIBBON);
		}
		if (y == ROOF) return Cell.of(edge ? Kind.PRECAST : Kind.ROOF);
		if (edge) return y == ROOF + 1 ? Cell.of(Kind.PRECAST) : y == ROOF + 2 ? Cell.of(Kind.STEEL) : Cell.NONE;
		return rooftop(x, y, z);
	}

	/** A helipad on the west wing, a radar dome on the east, air handlers, and two masts with red lamps. */
	private static Cell rooftop(int x, int y, int z) {
		int dy = y - ROOF;
		double pad = Math.hypot(x + 36.5, z + 17.5);
		if (pad <= 8.5 && dy == 1) {
			boolean h = Math.abs(x + 36.5) <= 3 && (Math.abs(Math.abs(x + 36.5) - 3) < 0.6 && Math.abs(z + 17.5) <= 4
					|| Math.abs(z + 17.5) < 0.6);
			if (h) return Cell.of(Kind.PAINT);
			if (pad >= 6.6 && pad <= 7.6) return Cell.of(Kind.BAND);
			return Cell.of(Kind.ASPHALT);
		}
		if (pad > 8.5 && pad <= 9.5 && dy == 1 && Math.floorMod(x + z, 4) == 0) return Cell.of(Kind.LIGHT);
		double dome = Math.sqrt(Math.pow(x - 35.5, 2) + Math.pow(z + 17.5, 2) + Math.pow(y - (ROOF + 3.5), 2));
		if (dy >= 1 && Math.hypot(x - 35.5, z + 17.5) <= 6.5) {
			if (dy == 1) return Cell.of(Kind.STEEL);
			if (dome >= 5.4 && dome <= 6.5) return Cell.of(Kind.PRECAST);
			return dome < 5.4 ? Cell.AIR : Cell.NONE;
		}
		for (int[] box : new int[][] {{-40, 8, -32, 12}, {28, 8, 36, 12}, {28, 20, 36, 24}}) {
			if (x >= box[0] && x <= box[2] && z >= box[1] && z <= box[3]) {
				if (dy == 1 || dy == 2) return Cell.of(Kind.STEEL);
				if (dy == 3) return Cell.of((x + z) % 2 == 0 ? Kind.CEILING : Kind.STEEL);
			}
		}
		for (int[] mast : new int[][] {{-46, 33}, {45, 33}}) {
			if (x == mast[0] && z == mast[1]) {
				if (dy >= 1 && dy <= 14) return Cell.of(Kind.BARS);
				if (dy == 15) return Cell.of(Kind.EMERGENCY_LIGHT);
			}
		}
		return Cell.NONE;
	}

	/**
	 * The atrium's air, and the specimen chamber standing in it: a glass column on a steel plinth with hazard bands,
	 * a piece of Level 0 (carpet, a corner of yellow wallpaper, a ceiling with its lights) hung in it on rods, field
	 * coils over it, and rods up to the skylight.
	 */
	private static Cell specimen(int x, int y, int z) {
		boolean in = x >= SPECIMEN_MIN && x <= SPECIMEN_MAX && z >= SPECIMEN_MIN && z <= SPECIMEN_MAX;
		boolean rod = (x == -2 || x == 1) && (z == -2 || z == 1);
		if (!in) return Cell.AIR;
		boolean ring = x == SPECIMEN_MIN || x == SPECIMEN_MAX || z == SPECIMEN_MIN || z == SPECIMEN_MAX;
		boolean corner = (x == SPECIMEN_MIN || x == SPECIMEN_MAX) && (z == SPECIMEN_MIN || z == SPECIMEN_MAX);
		boolean chunk = x >= -2 && x <= 1 && z >= -2 && z <= 1;
		if (y <= FLOOR + 1) return Cell.of(Kind.STEEL);
		if (y == FLOOR + 2) return Cell.of(ring ? Kind.BAND : Kind.STEEL);
		if (y <= 89) {
			if (ring) return Cell.of(corner ? Kind.STEEL : Kind.CONTAINMENT);
			if (!chunk) return Cell.AIR;
			if (y == 74) return Cell.of(Kind.LEVEL0_CARPET);
			if (y >= 75 && y <= 78) return z == -2 || x == -2 ? Cell.of(Kind.LEVEL0_WALL) : Cell.AIR;
			if (y == 79) return z == 0 && (x == 0 || x == -1) ? Cell.of(Kind.LEVEL0_LIGHT) : Cell.of(Kind.LEVEL0_CEILING);
			return y > 79 && rod ? Cell.of(Kind.BARS) : Cell.AIR;
		}
		if (y == 90) {
			if (ring) return Cell.of(Kind.STEEL);
			if ((x == 0 || x == -1) && (z == 0 || z == -1)) return Cell.of(Kind.COIL);
			return rod ? Cell.of(Kind.BARS) : Cell.AIR;
		}
		if (y == 91) return Cell.of(ring ? Kind.BAND : Kind.STEEL);
		if (y == 92) return Cell.of(Kind.STEEL);
		if (y == 93) return chunk ? Cell.of(Kind.STEEL) : Cell.AIR;
		return rod ? Cell.of(Kind.BARS) : Cell.AIR;
	}

	/** The ladder to B2 hangs on the pillar north of it, facing south. */
	private static Cell ladder() {
		return new Cell(Kind.LADDER, 0, 0, false, 'l');
	}

	private static boolean touchesOpen(String[] plan, int c, int r) {
		for (int dr = -1; dr <= 1; dr++) {
			for (int dc = -1; dc <= 1; dc++) {
				int cc = c + dc, rr = r + dr;
				if (cc < 0 || cc >= WIDTH || rr < 0 || rr >= DEPTH) continue;
				if (plan[rr].charAt(cc) != '#') return true;
			}
		}
		return false;
	}

	/** Which way a thing against a wall faces: away from the wall behind it, else toward the first open side. */
	private static int facingOpen(String[] plan, int c, int r) {
		int[][] steps = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
		int first = -1;
		for (int d = 0; d < 4; d++) {
			if (!openAt(plan, c + steps[d][0], r + steps[d][1])) continue;
			if (first < 0) first = d;
			int[] back = steps[(d + 2) % 4];
			char behind = charAt(plan, c + back[0], r + back[1]);
			if (behind == '#' || behind == 'E') return d;
		}
		return Math.max(first, 0);
	}

	private static boolean openAt(String[] plan, int c, int r) {
		return c >= 0 && c < WIDTH && r >= 0 && r < DEPTH && open(plan[r].charAt(c));
	}

	private static char charAt(String[] plan, int c, int r) {
		return c >= 0 && c < WIDTH && r >= 0 && r < DEPTH ? plan[r].charAt(c) : 'E';
	}

	/** A bed faces from its foot to its head. */
	private static int bedFacing(String[] plan, int c, int r, char part) {
		char other = part == 'b' ? 'f' : 'b';
		int[][] steps = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
		for (int d = 0; d < 4; d++) {
			int cc = c + steps[d][0], rr = r + steps[d][1];
			if (cc < 0 || cc >= WIDTH || rr < 0 || rr >= DEPTH || plan[rr].charAt(cc) != other) continue;
			// Seen from the head the foot lies direction d, so the bed faces the opposite way; from the foot, toward d.
			return part == 'b' ? (d + 2) % 4 : d;
		}
		return 2;
	}
}
