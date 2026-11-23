package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.level1.Level1Exit.Shape;
import dev.noclip.backrooms.level1.Level1Layout.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which exits a hall district holds, and where. Exits sit in parking bays: the 9x16 rectangles between
 * pillar lines, in bay coordinates (bx, bz) from the bay's own pillar. Every footprint stays two blocks
 * clear of the district's edge lines and never touches a pillar, rack or another exit, and everything
 * solid is surrounded by open floor, so the halls stay one connected space.
 */
final class HallPlan {
	static final int[][] FACES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}}; // north, east, south, west
	private static final int D = Level1Layout.DISTRICT;
	private static final int PLAN = 31;
	private static final ConcurrentHashMap<Key, HallPlan> CACHE = new ConcurrentHashMap<>();

	private record Key(long seed, int dx, int dz) {
	}

	/** One exit: its kind, the bay it is in (by the bay's pillar corner) and, for a service room, its face. */
	record Placed(Level1Exit exit, int x0, int z0, int face) {
		/** The block a player uses, at floor level: the door, painting, outlets, crack, hatch or frame. */
		int[] spot() {
			return switch (exit.shape) {
				case CORE_DOOR, CORE_PAINTING, CORE_OUTLETS -> new int[] {x0 + 5 + FACES[face][0] * 2, z0 + 8 + FACES[face][1] * 2};
				case PILLAR -> new int[] {x0, z0 + 1};
				case HATCH -> new int[] {x0 + 2, z0 + 7};
				case FRAME -> new int[] {x0 + 5, z0 + 8};
				case STAIRS_DOWN, STAIRS_UP -> new int[] {x0 + 4, z0 + 3};
			};
		}
	}

	/** A column inside an exit's footprint, in bay coordinates. */
	record Cell(Placed placed, int bx, int bz) {
	}

	private final List<Placed> placed = new ArrayList<>();

	static HallPlan of(long seed, int dx, int dz) {
		if (CACHE.size() > 1024) CACHE.clear();
		return CACHE.computeIfAbsent(new Key(seed, dx, dz), key -> new HallPlan(seed, dx, dz));
	}

	private HallPlan(long seed, int dx, int dz) {
		Kind kind = Level1Layout.kind(seed, dx, dz);
		if (kind == Kind.CORRIDORS) return;
		SplittableRandom random = new SplittableRandom(Level1Layout.hash(seed, dx, dz, PLAN));

		List<int[]> bays = new ArrayList<>();
		for (int x0 = Math.floorDiv(dx * D, Level1Layout.BAY_X) * Level1Layout.BAY_X; x0 < dx * D + D; x0 += Level1Layout.BAY_X) {
			for (int z0 = dz * D; z0 < dz * D + D; z0 += Level1Layout.BAY_Z) bays.add(new int[] {x0, z0});
		}
		boolean[] used = new boolean[bays.size()];
		boolean[] pillarUsed = new boolean[bays.size()];

		for (Level1Exit exit : Level1Exit.values()) {
			if (kind == Kind.WAREHOUSE && !exit.shape.fitsWarehouse()) continue;
			if (random.nextDouble() >= exit.chance) continue;
			boolean pillar = exit.shape == Shape.PILLAR;
			int first = random.nextInt(bays.size());
			for (int i = 0; i < bays.size(); i++) {
				int b = (first + i) % bays.size();
				if ((pillar ? pillarUsed : used)[b]) continue;
				int[] bay = bays.get(b);
				Placed candidate = new Placed(exit, bay[0], bay[1], random.nextInt(4));
				if (!inside(candidate, dx, dz)) continue;
				(pillar ? pillarUsed : used)[b] = true;
				placed.add(candidate);
				break;
			}
		}
	}

	List<Placed> placed() {
		return placed;
	}

	/** The exit footprint covering (x, z), or null. */
	Cell cell(int x, int z) {
		for (Placed p : placed) {
			int bx = x - p.x0(), bz = z - p.z0();
			if (covers(p.exit().shape, bx, bz)) return new Cell(p, bx, bz);
		}
		return null;
	}

	static boolean covers(Shape shape, int bx, int bz) {
		return switch (shape) {
			case CORE_DOOR, CORE_PAINTING, CORE_OUTLETS -> bx >= 3 && bx <= 7 && bz >= 6 && bz <= 10;
			case PILLAR -> bx == 0 && bz == 1;
			case HATCH -> bx == 2 && bz == 7;
			case FRAME -> bx == 5 && bz == 8;
			case STAIRS_DOWN, STAIRS_UP -> bx >= 3 && bx <= 6 && bz >= 2 && bz <= 10;
		};
	}

	/** The whole footprint, and the floor in front of a service room's exit, within the district and clear of its edges. */
	private static boolean inside(Placed p, int dx, int dz) {
		int[] box = switch (p.exit().shape) {
			case CORE_DOOR, CORE_PAINTING, CORE_OUTLETS -> new int[] {1, 5, 9, 11};
			case PILLAR -> new int[] {0, 0, 1, 1};
			case HATCH -> new int[] {2, 7, 2, 7};
			case FRAME -> new int[] {5, 7, 5, 9};
			case STAIRS_DOWN, STAIRS_UP -> new int[] {3, 2, 6, 10};
		};
		int minX = dx * D + 2, maxX = dx * D + D - 3, minZ = dz * D + 2, maxZ = dz * D + D - 3;
		return p.x0() + box[0] >= minX && p.x0() + box[2] <= maxX && p.z0() + box[1] >= minZ && p.z0() + box[3] <= maxZ;
	}
}
