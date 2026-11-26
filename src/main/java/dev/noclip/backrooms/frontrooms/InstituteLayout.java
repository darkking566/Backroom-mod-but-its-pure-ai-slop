package dev.noclip.backrooms.frontrooms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The M.E.G. research institute at the middle of the Frontrooms (docs/project/1-gameplay/README.md, "前室"), as plain
 * data: three floor plans 64 blocks wide (x) and 48 deep (z), one character a column, turned into pieces of the building
 * by {@link #cell}. Nothing here touches Minecraft's blocks, so tests can walk the plans.
 * <p>
 * Legend: {@code #} wall, {@code .} floor, {@code ,} floor of a work area (players may build there), {@code w} glass,
 * {@code o} a doorway in glass, {@code g} the main gate, {@code 1 3 5} blast doors that open once the team has met that
 * many quotas, {@code a} where people arrive, {@code k y u n q} where the commander, buyer, supplier, medic and
 * quartermaster stand, {@code B S C M Q} their counters, {@code L} lockers, {@code r} weapon lockers, {@code b f} the head
 * and foot of a bed, {@code s} stairs down to B1, {@code h} the sealed hatch down to B2, {@code l} its ladder, {@code D}
 * transport pads, {@code P} pillars, {@code H} archive shelves, {@code F} archive files, and workstations: {@code T}
 * crafting table, {@code K} salvage bench, {@code W} workshop bench, {@code A} almond distiller, {@code R} anomaly altar,
 * {@code X} scrap recycler, {@code N} spiderweb crafting table, {@code Y} distillery barrel, {@code U} research terminal,
 * {@code Z} crystal replicator.
 */
public final class InstituteLayout {
	public static final int WIDTH = 64, DEPTH = 48, MIN_X = -32, MIN_Z = -24, MAX_X = MIN_X + WIDTH - 1, MAX_Z = MIN_Z + DEPTH - 1;
	/** Floor and ceiling heights: B2, B1 and the ground floor; the roof; the parapet on top of the outer walls. */
	public static final int B2_FLOOR = 48, B2_CEILING = 54, B1_FLOOR = 56, B1_CEILING = 62, FLOOR = 64, CEILING = 70, ROOF = 71, PARAPET = 72;
	/** Everything the institute builds lies between these heights, inclusive. */
	public static final int BOTTOM = B2_FLOOR - 1, TOP = PARAPET;
	/** The paved forecourt outside the main gate. */
	public static final int YARD_MIN_X = -6, YARD_MAX_X = 5, YARD_MIN_Z = MAX_Z + 1, YARD_MAX_Z = MAX_Z + 8;
	private static final String WORKSTATIONS = "TKWARXNYUZ";
	private static final String OPEN = ".,akyunqgoDhsl";
	private static final String PEOPLE = "kyunq";

	private static final String[] B2_PLAN = {
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"######################################.H.H.H.H.H.H.H.###########",
			"######################################...............###########",
			"######################################...............###########",
			"######################################...............###########",
			"######################################...F...F...F...###########",
			"######################################.........#.....###########",
			"######################################.........l.....###########",
			"######################################...............###########",
			"######################################...............###########",
			"######################################......F........###########",
			"######################################...............###########",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
	};
	private static final String[] B1_PLAN = {
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################sss#############################",
			"################################sss#############################",
			"################################sss#############################",
			"################################sss#############################",
			"################################sss#############################",
			"################################sss#############################",
			"################################sss#############################",
			"################################sss#############################",
			"################################...#############################",
			"################################...#############################",
			"####################........................####################",
			"####################.U.Z....................####################",
			"####################........................####################",
			"####################........................####################",
			"####################....P..............P....####################",
			"####################........................####################",
			"####################.....DD....DD....DD.....####################",
			"####################.....DD....DD....DD.....####################",
			"####################........................#....###############",
			"####################........................#....###############",
			"####################....P..............P.........###############",
			"####################.............................###############",
			"####################........................#..h.###############",
			"####################........................#....###############",
			"####################........................####################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
			"################################################################",
	};
	private static final String[] F1_PLAN = {
			"################################################################",
			"################################################################",
			"#......#......#......######################.........#,,X,N,W,K,#",
			"#.b....#.b....#.b....########.......#######....q....#,,,,,,,,,,#",
			"#.f....#.f....#.f....########...sss.#######..QQQQQQ.#,,,,,,,,,,#",
			"#......#......#......########...sss.#######.........#,,,,,,,,,,#",
			"#......#......#......########...sss.#######.........#,,,,,,,,,,#",
			"#......#......#......########...sss.#######.........#,,,,,,,,,,#",
			"#......#......#......########...sss.#######.........#,,,,,,,,,,#",
			"#......#......#......########...sss.#######.........#,,,,,,,,,,#",
			"#......#......#......########...sss.#######..........,,,,,,,,,,#",
			"#......#......#......########...sss.#######..........,,,,,,,,,,#",
			"#wwowww#wwowww#wwowww########...###.#######..........,,,,,,,,,,#",
			"#...........................#...###.#######.........#,,,,,,,,,,#",
			"#...........................#...###.#######.........#,,,,,,,,,,#",
			"#...........................#...###.#######.........#,,,,,,,,,,#",
			"########################...###55###########.........#,,,,,,,,,,#",
			"######################....................#.........#,,,,,,,,,,#",
			"######################.........k..........3.........#,,,,,,,,,,#",
			"######################......CCCCCCCC......3.........#,,,,,,,,,,#",
			"######################...P............P...#.r.r.r.r.#,,,,,,,,,,#",
			"######################....................#.........#,,,,,,,,,,#",
			"######################....................######################",
			"######################....................######################",
			"######################....................1........#############",
			"#,T,K,W,A,R,,#########...P............P...1........#############",
			"#,,,,,,,,,,,,.............................#####....#############",
			"#,,,,,,,,,,,,.............................#####....#############",
			"w,,,,,,,,,,,,.............................#####....#############",
			"w,,,,,,,,,,,,#############............#########....#############",
			"#,,,,,,,,,,,,#########....................#####.........n......#",
			"#,,,,,,,,,,,,#########....................#####........MMM.....#",
			"w,,,,,,,,,,,,#####...B....................S...#................w",
			"w,,,,,,,,,,,,#####...B....................S...#.............AY.w",
			"#,,,,,,,,,,,,#####..yB.........a..........Su..#................#",
			"#,,,,,,,,,,,,#####...B....................S...#................#",
			"w,,,,,,,,,,,,#####...B....................S...#................w",
			"w,,,,,,,,,,,,#####...B....................S...#................w",
			"#,,,,,,,,,,,,#########....................#####wwwowww.........#",
			"#,,,,,,,,,,,,#########....................#####................#",
			"w,,,,,,,,,,,,#L......#....................#####................w",
			"w,,,,,,,,,,,,#L......#....................#####................w",
			"#,,,,,,,,,,,,#L...........................#####................#",
			"#,,,,,,,,,,,,.L...........................#####................#",
			"w,,,,,,,,,,,,.L...........................#####................w",
			"w,,,,,,,,,,,,#L......#....................#####..f..f..f..f..f.w",
			"#,,,,,,,,,,,,#LLLLLLL#....................#####..b..b..b..b..b.#",
			"#######################wwwwww#gggg#wwwwww#######################",
	};

	/** What a piece of the building is; the generator picks the block. */
	public enum Kind {
		/** Not the institute's: the ground and the sky around it. */
		NONE,
		/** Concrete that no one sees: under walls, between floors, around B2. */
		SOLID,
		WALL, WAINSCOT, PILLAR, GLASS, FLOOR, WORK_FLOOR, CEILING, LIGHT, EMERGENCY_LIGHT, ROOF, AIR,
		DOOR, DOOR_LAMP, STAIR, LADDER, HATCH, COUNTER, LOCKER, WEAPON_LOCKER, BED_HEAD, BED_FOOT, STATION, PAD, SHELF, FILE
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

	private static final String[][] PLANS = {B2_PLAN, B1_PLAN, F1_PLAN};
	private static final int[] FLOORS = {B2_FLOOR, B1_FLOOR, FLOOR};
	private static final int[] CEILINGS = {B2_CEILING, B1_CEILING, CEILING};
	private static final Map<Character, Spot> SPOTS = new HashMap<>();
	private static final List<Door> DOORS = new ArrayList<>();
	private static final List<Spot> PADS = new ArrayList<>();
	private static final List<Spot> FILES = new ArrayList<>();
	private static Spot hatch;
	/** The row of the top step of the stairs down, per column; -1 where there are none. */
	private static final int[] STAIR_TOP = new int[WIDTH];

	static {
		for (String[] plan : PLANS) {
			if (plan.length != DEPTH) throw new IllegalStateException("a plan is " + plan.length + " rows deep");
			for (String row : plan) if (row.length() != WIDTH) throw new IllegalStateException("a plan row is " + row.length() + " wide");
		}
		java.util.Arrays.fill(STAIR_TOP, -1);
		for (int r = 0; r < DEPTH; r++) {
			for (int c = 0; c < WIDTH; c++) {
				char ch = F1_PLAN[r].charAt(c);
				if (PEOPLE.indexOf(ch) >= 0 || ch == 'a') SPOTS.put(ch, new Spot(c + MIN_X, r + MIN_Z));
				if (ch == 's' && STAIR_TOP[c] < 0) STAIR_TOP[c] = r;
				if (ch == '1' || ch == '3' || ch == '5') {
					boolean alongX = isDoor(F1_PLAN, c - 1, r) || isDoor(F1_PLAN, c + 1, r);
					DOORS.add(new Door(c + MIN_X, FLOOR + 1, r + MIN_Z, ch - '0', alongX));
				}
				if (B1_PLAN[r].charAt(c) == 'D') PADS.add(new Spot(c + MIN_X, r + MIN_Z));
				if (B1_PLAN[r].charAt(c) == 'h') hatch = new Spot(c + MIN_X, r + MIN_Z);
				if (B2_PLAN[r].charAt(c) == 'F') FILES.add(new Spot(c + MIN_X, r + MIN_Z));
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

	/** Whether (x, z) is the forecourt outside the gate. */
	public static boolean yard(int x, int z) {
		return x >= YARD_MIN_X && x <= YARD_MAX_X && z >= YARD_MIN_Z && z <= YARD_MAX_Z;
	}

	/** The plan character of a floor (0 B2, 1 B1, 2 ground) at (x, z); '#' outside the plans. */
	public static char plan(int floor, int x, int z) {
		if (!inside(x, z)) return '#';
		return PLANS[floor][z - MIN_Z].charAt(x - MIN_X);
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

	public static List<Spot> files() {
		return FILES;
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

	/** The piece of the building at a block, or {@link Cell#NONE} where the building is not. */
	public static Cell cell(int x, int y, int z) {
		if (y < BOTTOM || y > TOP) return Cell.NONE;
		if (!inside(x, z)) {
			return y == FLOOR && yard(x, z) ? Cell.of(Kind.FLOOR) : Cell.NONE;
		}
		int c = x - MIN_X, r = z - MIN_Z;
		// The stairs to B1 cut through B1's ceiling and the ground floor's floor.
		if (STAIR_TOP[c] >= 0 && F1_PLAN[r].charAt(c) == 's' && y >= B1_FLOOR && y < CEILING) {
			int surface = FLOOR - 1 - (r - STAIR_TOP[c]);
			if (y < surface) return Cell.SOLID;
			// Going up is going north, toward the top step.
			return y == surface ? new Cell(Kind.STAIR, 2, 0, false, 's') : Cell.AIR;
		}
		if (y >= ROOF) {
			if (y == ROOF) return Cell.of(Kind.ROOF);
			boolean edge = c == 0 || c == WIDTH - 1 || r == 0 || r == DEPTH - 1;
			return edge ? Cell.of(Kind.WALL) : Cell.NONE;
		}
		int floor = y >= FLOOR ? 2 : y >= B1_FLOOR ? 1 : y >= B2_FLOOR ? 0 : -1;
		if (floor < 0) return Cell.SOLID;
		String[] plan = PLANS[floor];
		char ch = plan[r].charAt(c);
		int base = FLOORS[floor], ceiling = CEILINGS[floor];
		// The slab between B2 and B1, and between B1 and the ground floor; the ladder goes through the first.
		if (y > ceiling) return floor == 0 && ch == 'l' ? ladder() : Cell.SOLID;
		if (y == base) {
			return switch (ch) {
				case '#', 'P' -> Cell.SOLID;
				case ',' -> Cell.of(Kind.WORK_FLOOR);
				case 'h' -> Cell.of(Kind.HATCH);
				case 'D' -> Cell.of(Kind.PAD);
				default -> Cell.of(Kind.FLOOR);
			};
		}
		if (y == ceiling) {
			if (ch == 'l') return ladder();
			if (ch == '#' || ch == 'P') return Cell.SOLID;
			if (Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0 && open(ch)) {
				return Cell.of(floor == 0 ? Kind.EMERGENCY_LIGHT : Kind.LIGHT);
			}
			return Cell.of(Kind.CEILING);
		}
		int h = y - base;
		return switch (ch) {
			case '#' -> floor == 2 || touchesOpen(plan, c, r) ? Cell.of(h == 1 ? Kind.WAINSCOT : Kind.WALL) : Cell.SOLID;
			case 'P' -> Cell.of(Kind.PILLAR);
			case 'w' -> Cell.of(h >= 2 && h <= 4 ? Kind.GLASS : h == 1 ? Kind.WAINSCOT : Kind.WALL);
			case 'o' -> h <= 2 ? Cell.AIR : Cell.of(h <= 4 ? Kind.GLASS : Kind.WALL);
			case 'g' -> h <= 4 ? Cell.AIR : Cell.of(Kind.WALL);
			case '1', '3', '5' -> {
				int requires = ch - '0';
				// facing 0: the wall (and the door's plate) runs along x; 1: along z.
				int along = isDoor(plan, c - 1, r) || isDoor(plan, c + 1, r) ? 0 : 1;
				if (h <= 2) yield new Cell(Kind.DOOR, along, requires, h == 2, ch);
				yield h == 3 ? new Cell(Kind.DOOR_LAMP, along, requires, false, ch) : Cell.of(Kind.WALL);
			}
			case 'B', 'S', 'C', 'M', 'Q' -> h == 1 ? Cell.of(Kind.COUNTER) : Cell.AIR;
			case 'L' -> h <= 2 ? new Cell(Kind.LOCKER, facingOpen(plan, c, r), 0, h == 2, ch) : Cell.AIR;
			case 'r' -> h <= 2 ? new Cell(Kind.WEAPON_LOCKER, facingOpen(plan, c, r), 0, h == 2, ch) : Cell.AIR;
			case 'b', 'f' -> h == 1 ? new Cell(ch == 'b' ? Kind.BED_HEAD : Kind.BED_FOOT, bedFacing(plan, c, r, ch), 0, false, ch) : Cell.AIR;
			case 'H' -> h <= 3 ? Cell.of(Kind.SHELF) : Cell.AIR;
			case 'F' -> h == 1 ? new Cell(Kind.FILE, 0, 0, false, ch) : Cell.AIR;
			case 'l' -> ladder();
			default -> WORKSTATIONS.indexOf(ch) >= 0
					? h == 1 ? new Cell(Kind.STATION, facingOpen(plan, c, r), 0, false, ch) : Cell.AIR
					: Cell.AIR;
		};
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

	/** Which way a thing against a wall faces: toward the first open neighbour (south, west, north, east). */
	private static int facingOpen(String[] plan, int c, int r) {
		int[][] steps = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
		for (int d = 0; d < 4; d++) {
			int cc = c + steps[d][0], rr = r + steps[d][1];
			if (cc < 0 || cc >= WIDTH || rr < 0 || rr >= DEPTH) continue;
			if (open(plan[rr].charAt(cc))) return d;
		}
		return 0;
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
