package dev.noclip.backrooms.frontrooms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The staff town in the Frontrooms (docs/企劃書/1-遊戲玩法/README.md, "前室的夜晚：398"), as plain numbers: one street
 * west of the avenue, four houses on each side, a corner store, a park with a picnic, swings and a clock. By day it is
 * the "Earth" of 狐霖's Frontrooms, a 1990s American street; but every house inside is furnished from the film's 1991
 * furniture store, on Level 0's carpet under Level 0's wallpaper. Nothing here touches Minecraft's blocks.
 * <p>
 * Houses are drawn for the north side (front door looking south, to the street) and mirrored across the street's
 * middle line for the south side.
 */
public final class FrontroomsTown {
	/** What a block of the town is; the generator picks the block. */
	public enum Kind {
		AIR, SIDING, TRIM, FOUNDATION, CARPET, WALLPAPER, PLASTER, WINDOW, DOOR, GARAGE, GARAGE_TOP, SHINGLES, ROOF_STAIR,
		FLAT_ROOF, STORE_WALL, STORE_FLOOR, STORE_CEILING, STORE_LIGHT, COUNTER, ASPHALT, SIDEWALK, POLE, SODIUM, LOG, LEAVES,
		PROP, PROP_PART
	}

	/** One block: its kind, a 2D data facing (0 south, 1 west, 2 north, 3 east), and a variant (siding colour, door half, object). */
	public record Piece(Kind kind, int facing, int variant) {
		static Piece of(Kind kind) {
			return new Piece(kind, 0, 0);
		}
	}

	/** An object of the town or a house: its id (a film prop or a town prop), size, mount, and where it stands. */
	public record Type(String id, int width, int height, int depth, String mount) {
		boolean multi() {
			return mount.equals("multi");
		}
	}

	public record Place(String id, int x, int y, int z, int facing) {
	}

	private record Occupant(int index, boolean master) {
	}

	static final int G = FrontroomsTerrain.GROUND;
	/** The town's ground, kept flat; the street's two kerbs; its middle line (houses mirror across it). */
	public static final int MIN_X = -114, MAX_X = -18, MIN_Z = 56, MAX_Z = 108;
	static final int STREET_MIN_Z = 79, STREET_MAX_Z = 82, MIRROR = STREET_MIN_Z + STREET_MAX_Z;
	/** The west ends of the four lots on each side; the corner store; the park. */
	static final int[] LOTS = {-110, -94, -78, -62};
	static final int STORE_X0 = -46, STORE_X1 = -30, PARK_X0 = -46, PARK_X1 = -20, PARK_Z0 = 85, PARK_Z1 = 106;
	/** The house: walls from z 60 (back) to 69 (front) on the north side, 13 wide; the roof's ridge is at y G + 10. */
	static final int BACK = 60, FRONT = 69, WIDTH = 13;
	/** Where the shopkeeper stands behind the counter, and where the picnic's people stand round their table. */
	public static final int[] SHOPKEEPER = {-33, 62};
	public static final int[][] PICNIC = {{-44, 88}, {-41, 87}, {-42, 90}};

	static final Map<String, Type> TYPES = new HashMap<>();
	private static final List<Place> PLACES = new ArrayList<>();
	private static final Map<Long, Occupant> FILLED = new HashMap<>();
	private static final String[] SOFAS = {"film_sofa_tan", "film_sofa_brown", "film_sofa_tropical", "film_sofa_quilted"};
	private static final String[] RECLINERS = {"film_recliner_leather", "film_recliner_mauve", "film_recliner_tan", "film_recliner_bluegrey"};

	static {
		// The town's own objects (tools/film/town_list.py) and the film's furniture the houses use (film_list.py).
		for (Type type : new Type[] {
				new Type("town_mailbox", 1, 1, 1, "floor"), new Type("town_picnic_table", 2, 1, 2, "multi"),
				new Type("town_park_bench", 2, 1, 1, "multi"), new Type("town_swing_set", 3, 2, 1, "multi"),
				new Type("town_street_clock", 1, 3, 1, "multi"), new Type("town_fire_hydrant", 1, 1, 1, "small"),
				new Type("town_trash_can", 1, 1, 1, "small"), new Type("town_store_shelf", 1, 2, 1, "multi"),
				new Type("town_store_sign", 5, 1, 1, "wall"),
				new Type("film_sofa_tan", 3, 1, 1, "multi"), new Type("film_sofa_brown", 3, 1, 1, "multi"),
				new Type("film_sofa_tropical", 3, 1, 1, "multi"), new Type("film_sofa_quilted", 3, 1, 1, "multi"),
				new Type("film_recliner_leather", 1, 1, 1, "floor"), new Type("film_recliner_mauve", 1, 1, 1, "floor"),
				new Type("film_recliner_tan", 1, 1, 1, "floor"), new Type("film_recliner_bluegrey", 1, 1, 1, "floor"),
				new Type("film_coffee_table_glass", 1, 1, 1, "small"), new Type("film_tv_vcr", 1, 1, 1, "floor"),
				new Type("film_showroom_bed", 2, 2, 2, "multi"), new Type("film_nightstand", 1, 1, 1, "floor"),
				new Type("film_dresser", 2, 2, 1, "multi"), new Type("film_chandelier", 1, 1, 1, "ceiling"),
				new Type("film_torchiere", 1, 2, 1, "multi")}) {
			TYPES.put(type.id(), type);
		}
		furnish();
	}

	private FrontroomsTown() {
	}

	// Furnishing ---------------------------------------------------------------------------------------------------

	private static void furnish() {
		for (int lot = 0; lot < LOTS.length * 2; lot++) {
			int x0 = LOTS[lot % LOTS.length];
			boolean south = lot >= LOTS.length;
			// Inside: a sofa and a recliner before the television, a bed with its nightstand, a dresser, the lights.
			house(south, "town_mailbox", x0 + 6, G + 1, 76, 0);
			house(south, SOFAS[lot % SOFAS.length], x0 + 4, G + 1, 62, 0);
			house(south, "film_coffee_table_glass", x0 + 3, G + 1, 64, 0);
			house(south, "film_tv_vcr", x0 + 3, G + 1, 66, 2);
			house(south, RECLINERS[lot % RECLINERS.length], x0 + 5, G + 1, 64, 0);
			house(south, "film_showroom_bed", x0 + 9, G + 1, 63, 0);
			house(south, "film_nightstand", x0 + 10, G + 1, 62, 0);
			house(south, "film_dresser", x0 + 9, G + 1, 67, 2);
			house(south, "film_torchiere", x0 + 7, G + 1, 62, 0);
			house(south, "film_chandelier", x0 + 5, G + 4, 65, 0);
		}
		// The corner store: shelves along the wall and back to back in the middle, its sign over the door.
		for (int z = 62; z <= 67; z++) place("town_store_shelf", -45, G + 1, z, 3);
		for (int z = 63; z <= 66; z++) {
			place("town_store_shelf", -41, G + 1, z, 1);
			place("town_store_shelf", -40, G + 1, z, 3);
		}
		place("town_store_sign", -36, G + 5, FRONT + 1, 0);
		// The park.
		place("town_street_clock", -36, G + 1, 94, 0);
		place("town_picnic_table", -42, G + 1, 88, 0);
		place("town_picnic_table", -26, G + 1, 102, 0);
		place("town_swing_set", -24, G + 1, 89, 0);
		place("town_park_bench", -40, G + 1, 93, 0);
		place("town_park_bench", -28, G + 1, 98, 2);
		place("town_trash_can", -38, G + 1, 93, 0);
		place("town_trash_can", -30, G + 1, 98, 0);
		place("town_fire_hydrant", -47, G + 1, 84, 0);
	}

	/** A house's object, given for the north side; on the south side it is mirrored across the street. */
	private static void house(boolean south, String id, int x, int y, int z, int facing) {
		if (!south) {
			place(id, x, y, z, facing);
			return;
		}
		// Mirrored: z flips, and so do south and north. A multi-block object then grows the other way to its right, so its
		// master moves to the far end of its width; backwards it still grows away from the street, so its depth stays.
		Type type = TYPES.get(id);
		int mz = MIRROR - z, mf = facing == 0 ? 2 : facing == 2 ? 0 : facing;
		int mx = x;
		if (type.multi() && (facing == 0 || facing == 2)) mx = x + (facing == 0 ? -(type.width() - 1) : type.width() - 1);
		place(id, mx, y, mz, mf);
	}

	private static void place(String id, int x, int y, int z, int facing) {
		Type type = TYPES.get(id);
		Place place = new Place(id, x, y, z, facing);
		int index = PLACES.size();
		PLACES.add(place);
		for (int[] cell : cells(place)) {
			Occupant was = FILLED.put(key(cell[0], cell[1], cell[2]), new Occupant(index, cell[0] == x && cell[1] == y && cell[2] == z));
			if (was != null) throw new IllegalStateException("town objects overlap: " + id + " at " + x + ", " + y + ", " + z);
		}
	}

	private static long key(int x, int y, int z) {
		return ((long) (y & 0xFFF) << 48) | ((long) (x & 0xFFFFFF) << 24) | (z & 0xFFFFFF);
	}

	private static int[] step(int facing) {
		return switch (facing & 3) {
			case 0 -> new int[] {0, 1};
			case 1 -> new int[] {-1, 0};
			case 2 -> new int[] {0, -1};
			default -> new int[] {1, 0};
		};
	}

	/** The blocks an object fills: {x, y, z}, growing to its right and backwards as props.MultiPartPropBlock does. */
	public static List<int[]> cells(Place place) {
		Type type = TYPES.get(place.id());
		List<int[]> cells = new ArrayList<>();
		if (!type.multi()) {
			cells.add(new int[] {place.x(), place.y(), place.z()});
			return cells;
		}
		int[] right = step(place.facing() + 1), back = step(place.facing() + 2);
		for (int dy = 0; dy < type.height(); dy++) {
			for (int i = 0; i < type.width(); i++) {
				for (int j = 0; j < type.depth(); j++) {
					cells.add(new int[] {place.x() + i * right[0] + j * back[0], place.y() + dy, place.z() + i * right[1] + j * back[1]});
				}
			}
		}
		return cells;
	}

	public static List<Place> places() {
		return PLACES;
	}

	public static Type type(String id) {
		return TYPES.get(id);
	}

	/** A house: its front door's lower block, and the room inside (x0..x1, z0..z1 on the floor y G + 1). */
	public record House(int doorX, int doorZ, int x0, int z0, int x1, int z1) {
		public boolean inside(double x, double y, double z) {
			return x >= x0 && x < x1 + 1 && z >= z0 && z < z1 + 1 && y >= G + 1 && y < G + 5;
		}
	}

	/** The eight houses (north side first). */
	public static List<House> houses() {
		List<House> houses = new ArrayList<>();
		for (int lot = 0; lot < LOTS.length * 2; lot++) {
			int x0 = LOTS[lot % LOTS.length];
			boolean south = lot >= LOTS.length;
			int door = south ? MIRROR - FRONT : FRONT;
			int z0 = south ? MIRROR - (FRONT - 2) : BACK + 2, z1 = south ? MIRROR - (BACK + 2) : FRONT - 2;
			houses.add(new House(x0 + 4, door, x0 + 2, z0, x0 + 10, z1));
		}
		return houses;
	}

	/** The street lamps' poles, {x, z}: along both sidewalks and in the park. */
	public static List<int[]> lamps() {
		List<int[]> lamps = new ArrayList<>();
		for (int x = MIN_X; x <= -9; x++) {
			if (Math.floorMod(x + 8, 16) == 0) lamps.add(new int[] {x, STREET_MIN_Z - 2});
			if (Math.floorMod(x, 16) == 0) lamps.add(new int[] {x, STREET_MAX_Z + 2});
		}
		for (int[] lamp : PARK_LAMPS) lamps.add(lamp);
		return lamps;
	}

	private static final int[][] PARK_LAMPS = {{-35, 86}, {-35, 105}, {-45, 94}, {-21, 97}};

	// The town's blocks -------------------------------------------------------------------------------------------

	/** Whether (x, z) is in the town's ground (kept flat). */
	public static boolean inside(int x, int z) {
		return x >= MIN_X && x <= MAX_X && z >= MIN_Z && z <= MAX_Z;
	}

	/** The highest block the town puts in a column. */
	public static int top(int x, int z) {
		return inside(x, z) || street(x, z) ? G + 12 : G;
	}

	/** Whether something of the town stands in or near a block area (no trees grow there). */
	public static boolean busy(int x0, int z0, int x1, int z1) {
		return x1 >= MIN_X - 4 && x0 <= MAX_X + 4 && z1 >= MIN_Z - 4 && z0 <= MAX_Z + 4;
	}

	/** Whether a block is part of something built in the town (which nobody may break). */
	public static boolean structure(int x, int y, int z) {
		Piece piece = piece(x, y, z);
		return piece != null && piece.kind() != Kind.AIR;
	}

	private static boolean street(int x, int z) {
		return z >= STREET_MIN_Z && z <= STREET_MAX_Z && x >= MIN_X && x <= -6;
	}

	/** What the town puts at a block, or null where it is up to the terrain. */
	public static Piece piece(int x, int y, int z) {
		if (y < G || y > G + 12) return null;
		if (inside(x, z)) {
			Occupant occupant = FILLED.get(key(x, y, z));
			if (occupant != null) return new Piece(occupant.master() ? Kind.PROP : Kind.PROP_PART, PLACES.get(occupant.index()).facing(), occupant.index());
		}
		return bare(x, y, z);
	}

	/** What the town puts at a block leaving its objects out: the buildings, paving, lamps and trees. */
	static Piece bare(int x, int y, int z) {
		if (y < G || y > G + 12) return null;
		if (street(x, z)) return y == G ? Piece.of(Kind.ASPHALT) : null;
		if (!inside(x, z)) return null;
		Piece piece;
		if ((piece = storeBlock(x, y, z)) != null) return piece;
		if ((piece = parkBlock(x, y, z)) != null) return piece;
		for (int lot = 0; lot < LOTS.length; lot++) {
			int x0 = LOTS[lot];
			if (x < x0 - 1 || x > x0 + WIDTH) continue;
			boolean south = z > MIRROR / 2;
			int nz = south ? MIRROR - z : z;
			Piece house = houseBlock(lot + (south ? LOTS.length : 0), x0, x - x0, y, nz);
			if (house != null) return south ? mirror(house) : house;
		}
		return sidewalk(x, y, z);
	}

	private static Piece mirror(Piece piece) {
		if (piece.kind() == Kind.ROOF_STAIR || piece.kind() == Kind.DOOR) {
			int f = piece.facing();
			return new Piece(piece.kind(), f == 0 ? 2 : f == 2 ? 0 : f, piece.variant());
		}
		return piece;
	}

	/** The sidewalks along both kerbs, the lamps along them. */
	private static Piece sidewalk(int x, int y, int z) {
		boolean north = z == STREET_MIN_Z - 1 || z == STREET_MIN_Z - 2, south = z == STREET_MAX_Z + 1 || z == STREET_MAX_Z + 2;
		if (!north && !south) return null;
		if (x > -9) return null;
		int lampZ = north ? STREET_MIN_Z - 2 : STREET_MAX_Z + 2;
		boolean lamp = z == lampZ && Math.floorMod(x + (north ? 8 : 0), 16) == 0;
		if (y == G) return Piece.of(Kind.SIDEWALK);
		if (lamp) return y <= G + 6 ? Piece.of(Kind.POLE) : y == G + 7 ? Piece.of(Kind.SODIUM) : null;
		return null;
	}

	/**
	 * One house, north side: lx 0..12 across the lot from its west wall, z from the back wall (60) to the front (69).
	 * Siding outside, Level 0's wallpaper and carpet inside, a plaster ceiling, a gable roof of shingles.
	 */
	private static Piece houseBlock(int lot, int x0, int lx, int y, int z) {
		int siding = lot % 3;
		boolean inX = lx >= 0 && lx <= WIDTH - 1, inZ = z >= BACK && z <= FRONT;
		// The front yard: the walk to the door, the driveway to the garage.
		if (z > FRONT && z <= STREET_MIN_Z - 1 && y == G) {
			if (lx == 4 && z < STREET_MIN_Z - 2) return Piece.of(Kind.SIDEWALK);
			if (lx >= 8 && lx <= 11) return Piece.of(Kind.ASPHALT);
			return null;
		}
		// The roof: one block over the walls on every side, rising a block a row to the ridge.
		int roofTop = G + 5;
		if (lx >= -1 && lx <= WIDTH && z >= BACK - 1 && z <= FRONT + 1) {
			int k = Math.min(z - (BACK - 1), FRONT + 1 - z);
			int surface = roofTop + Math.min(k, 5);
			if (y == surface) {
				if (k >= 5) return Piece.of(Kind.SHINGLES);
				// Climbing toward the ridge: the back slope climbs south, the front slope north.
				return new Piece(Kind.ROOF_STAIR, z <= (BACK + FRONT) / 2 ? 0 : 2, 0);
			}
			if (y > surface) return null;
			if (y > roofTop || y == roofTop && !(inX && inZ)) {
				// Under the roof: the gable ends are walls, the rest is the attic.
				if (!(inX && inZ)) return null;
				return lx == 0 || lx == WIDTH - 1 ? new Piece(Kind.SIDING, 0, siding) : Piece.of(Kind.AIR);
			}
		}
		if (!(inX && inZ)) return null;
		boolean outer = lx == 0 || lx == WIDTH - 1 || z == BACK || z == FRONT;
		boolean inner = !outer && (lx == 1 || lx == WIDTH - 2 || z == BACK + 1 || z == FRONT - 1);
		boolean corner = (lx == 0 || lx == WIDTH - 1) && (z == BACK || z == FRONT);
		int h = y - G;
		if (h == 0) return outer ? Piece.of(Kind.FOUNDATION) : Piece.of(Kind.CARPET);
		if (h == 5) return outer ? Piece.of(Kind.TRIM) : Piece.of(Kind.PLASTER);
		if (outer) {
			if (corner) return Piece.of(Kind.TRIM);
			if (z == FRONT) {
				if (lx == 4 && h <= 2) return new Piece(Kind.DOOR, 2, h == 2 ? 1 : 0);
				if ((lx == 2 || lx == 6) && (h == 2 || h == 3)) return Piece.of(Kind.WINDOW);
				if (lx >= 8 && lx <= 11 && h <= 2) return Piece.of(h == 1 ? Kind.GARAGE : Kind.GARAGE_TOP);
			}
			if (z == BACK && (lx == 3 || lx == 8) && (h == 2 || h == 3)) return Piece.of(Kind.WINDOW);
			if (lx == 0 && (z == 63 || z == 64) && (h == 2 || h == 3)) return Piece.of(Kind.WINDOW);
			return new Piece(Kind.SIDING, 0, siding);
		}
		if (inner) {
			// The inner skin of the walls is wallpaper; it opens where the door and the windows are.
			if (z == FRONT - 1 && lx == 4 && h <= 2) return Piece.of(Kind.AIR);
			if (z == FRONT - 1 && (lx == 2 || lx == 6) && (h == 2 || h == 3)) return Piece.of(Kind.AIR);
			if (z == BACK + 1 && (lx == 3 || lx == 8) && (h == 2 || h == 3)) return Piece.of(Kind.AIR);
			if (lx == 1 && (z == 63 || z == 64) && (h == 2 || h == 3)) return Piece.of(Kind.AIR);
			return Piece.of(Kind.WALLPAPER);
		}
		return Piece.of(Kind.AIR);
	}

	/** The corner store: a flat-roofed box with a shop front of windows, fluorescent tubes, a counter. */
	private static Piece storeBlock(int x, int y, int z) {
		if (x < STORE_X0 || x > STORE_X1 || z < BACK || z > STREET_MIN_Z - 1) return null;
		int h = y - G;
		if (z > FRONT) {
			// The walk from the door to the sidewalk.
			return h == 0 && (x == -38 || x == -37) && z < STREET_MIN_Z - 2 ? Piece.of(Kind.SIDEWALK) : null;
		}
		boolean wall = x == STORE_X0 || x == STORE_X1 || z == BACK || z == FRONT;
		if (h == 0) return wall ? Piece.of(Kind.FOUNDATION) : Piece.of(Kind.STORE_FLOOR);
		if (h == 5) {
			if (wall) return Piece.of(Kind.STORE_WALL);
			return Math.floorMod(x, 4) <= 1 && Math.floorMod(z, 3) == 0 ? Piece.of(Kind.STORE_LIGHT) : Piece.of(Kind.STORE_CEILING);
		}
		if (h == 6) return wall ? Piece.of(Kind.STORE_WALL) : Piece.of(Kind.FLAT_ROOF);
		if (h > 6) return null;
		if (wall) {
			if (z == FRONT && h <= 3) {
				if (x == -38 || x == -37) return Piece.of(Kind.AIR);
				if (x >= -44 && x <= -32 && h >= 1) return Piece.of(Kind.WINDOW);
			}
			return Piece.of(Kind.STORE_WALL);
		}
		if (z == 63 && x >= SHOPKEEPER[0] - 1 && x <= SHOPKEEPER[0] + 1) return h == 1 ? Piece.of(Kind.COUNTER) : Piece.of(Kind.AIR);
		return Piece.of(Kind.AIR);
	}

	/** The park: paths crossing, lamps, a few trees. */
	private static Piece parkBlock(int x, int y, int z) {
		if (x < PARK_X0 || x > PARK_X1 || z < PARK_Z0 || z > PARK_Z1) return null;
		int h = y - G;
		boolean path = x == -34 || x == -33 || z == 95 || z == 96;
		for (int[] tree : new int[][] {{-44, 104}, {-21, 93}, {-30, 105}, {-45, 99}}) {
			int dx = x - tree[0], dz = z - tree[1];
			if (dx == 0 && dz == 0 && h >= 1 && h <= 5) return Piece.of(Kind.LOG);
			double r = Math.sqrt(dx * dx + dz * dz + (h - 6) * (h - 6) * 1.4);
			if (h >= 4 && h <= 8 && r <= 2.7 && (dx != 0 || dz != 0 || h > 5)) return Piece.of(Kind.LEAVES);
		}
		for (int[] lamp : PARK_LAMPS) {
			if (x == lamp[0] && z == lamp[1]) return h <= 6 && h > 0 ? Piece.of(Kind.POLE) : h == 7 ? Piece.of(Kind.SODIUM) : h == 0 ? Piece.of(Kind.SIDEWALK) : null;
		}
		if (h == 0 && path) return Piece.of(Kind.SIDEWALK);
		return null;
	}
}
