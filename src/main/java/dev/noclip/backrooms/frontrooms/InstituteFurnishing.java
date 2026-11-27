package dev.noclip.backrooms.frontrooms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The institute's objects (tools/film/institute_list.py) and where each stands (tools/make_institute_plans.py writes both
 * into {@link InstitutePlans}), as plain data: which block of which floor each object fills. A multi-block object grows
 * to its right and backwards from its master block, as props.MultiPartPropBlock does.
 */
public final class InstituteFurnishing {
	/** A kind of object: its size in blocks, how it is mounted (multi, floor, small, low, wall, ceiling), its material, its light. */
	public record Type(String id, int width, int height, int depth, String mount, String material, int light) {
		public boolean multi() {
			return mount.equals("multi");
		}
	}

	/** One object in the building: its floor (plan index), master block, the way its front looks (2D data), its kind. */
	public record Place(int floor, int x, int z, int facing, Type type) {
	}

	/** A block an object fills: which object, and whether it is the master block (the one that draws it). */
	public record Occupant(int index, boolean master) {
	}

	private static final Map<String, Type> TYPES = new LinkedHashMap<>();
	private static final List<Place> PLACES = new ArrayList<>();
	/** floor, x, y above the floor, z -> what fills it. */
	private static final Map<Long, Occupant> FILLED = new HashMap<>();

	static {
		for (String line : InstitutePlans.PROP_TYPES) {
			String[] p = line.split(" ");
			TYPES.put(p[0], new Type(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), p[4], p[5], Integer.parseInt(p[6])));
		}
		for (String line : InstitutePlans.PROPS) {
			String[] p = line.split(" ");
			int facing = "SWNE".indexOf(p[3]);
			Type type = TYPES.get(p[4]);
			if (type == null || facing < 0) throw new IllegalStateException("bad institute object: " + line);
			Place place = new Place(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), facing, type);
			int index = PLACES.size();
			PLACES.add(place);
			for (int[] cell : cells(place)) {
				Occupant was = FILLED.put(key(place.floor(), cell[0], cell[1], cell[2]), new Occupant(index, cell[1] == 0 && cell[0] == place.x() && cell[2] == place.z()));
				if (was != null) throw new IllegalStateException("institute objects overlap at " + line);
			}
		}
	}

	private InstituteFurnishing() {
	}

	private static long key(int floor, int x, int dy, int z) {
		return ((long) floor << 56) | ((long) (dy & 0xFF) << 48) | ((long) (x & 0xFFFFFF) << 24) | (z & 0xFFFFFF);
	}

	/** The 2D step of a 2D data value (0 south, 1 west, 2 north, 3 east). */
	private static int[] step(int facing) {
		return switch (facing & 3) {
			case 0 -> new int[] {0, 1};
			case 1 -> new int[] {-1, 0};
			case 2 -> new int[] {0, -1};
			default -> new int[] {1, 0};
		};
	}

	/** The blocks an object fills: {x, height above the floor (0 the first), z}. */
	public static List<int[]> cells(Place place) {
		Type type = place.type();
		List<int[]> cells = new ArrayList<>();
		if (!type.multi()) {
			cells.add(new int[] {place.x(), 0, place.z()});
			return cells;
		}
		// To its right (clockwise from the way it faces) and backwards (the opposite way).
		int[] right = step(place.facing() + 1), back = step(place.facing() + 2);
		for (int dy = 0; dy < type.height(); dy++) {
			for (int i = 0; i < type.width(); i++) {
				for (int j = 0; j < type.depth(); j++) {
					cells.add(new int[] {place.x() + i * right[0] + j * back[0], dy, place.z() + i * right[1] + j * back[1]});
				}
			}
		}
		return cells;
	}

	public static Map<String, Type> types() {
		return TYPES;
	}

	public static List<Place> places() {
		return PLACES;
	}

	/** What fills a block of a floor, {@code dy} blocks above the floor block's top (0 the first), or null. */
	public static Occupant at(int floor, int x, int dy, int z) {
		return FILLED.get(key(floor, x, dy, z));
	}
}
