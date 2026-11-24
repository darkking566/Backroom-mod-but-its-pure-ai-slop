package dev.noclip.backrooms.level0p1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.noclip.backrooms.level0p1.ZenithLayout.Kind;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ZenithLayoutTest {
	private static final long[] SEEDS = {1, 12345, -987654321L};
	private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private static boolean passable(long seed, int x, int y, int z) {
		return ZenithLayout.passable(ZenithLayout.cell(seed, x, y, z));
	}

	/** Feet can be here: room for the body, and something solid to stand on that is not water or a hanging cable. */
	private static boolean standable(long seed, int x, int y, int z) {
		return passable(seed, x, y, z) && passable(seed, x, y + 1, z) && !passable(seed, x, y - 1, z);
	}

	/** A player's walk: one block at a time, up one block with a jump, down up to three. Doors open, so they pass. */
	private static boolean reaches(long seed, int[] start, int[] box, java.util.function.Predicate<int[]> goal) {
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		Set<Long> seen = new HashSet<>();
		queue.add(start);
		seen.add(key(start[0], start[1], start[2]));
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			if (goal.test(at)) return true;
			for (int[] step : STEPS) {
				int nx = at[0] + step[0], nz = at[2] + step[1];
				if (nx < box[0] || nx > box[1] || nz < box[2] || nz > box[3]) continue;
				for (int dy = 1; dy >= -3; dy--) {
					int ny = at[1] + dy;
					if (ny < box[4] || ny > box[5] || !standable(seed, nx, ny, nz)) continue;
					// Stairs are climbed by walking; anything else by jumping, which needs room above.
					boolean stair = ZenithLayout.kind(ZenithLayout.cell(seed, nx, ny - 1, nz)) == Kind.STAIR;
					if (dy == 1 && !stair && !passable(seed, at[0], at[1] + 2, at[2])) continue;
					if (dy <= 0 && !(passable(seed, nx, at[1], nz) && passable(seed, nx, at[1] + 1, nz))) continue;
					boolean clear = true;
					for (int yy = ny + 2; yy <= at[1] + 1 && clear; yy++) clear = passable(seed, nx, yy, nz);
					if (!clear || !seen.add(key(nx, ny, nz))) continue;
					queue.add(new int[] {nx, ny, nz});
					break;
				}
			}
		}
		return false;
	}

	private static long key(int x, int y, int z) {
		return ((long) (x + 2048) << 40) | ((long) (y + 2048) << 20) | (z + 2048);
	}

	/** The spiral stair in the corner of each 48x48 block takes you up a whole deck, again and again. */
	@Test
	void outerWindowsLookOutOnASeaOfRooms() {
		long seed = SEEDS[0];
		int windows = 0, sea = 0, lamps = 0;
		for (int i = 0; i < 20000; i++) {
			double yaw = i * 2.399963, pitch = Math.asin(((i * 0.618034) % 1.0) * 2 - 1);
			int x = (int) (478.5 * Math.cos(pitch) * Math.cos(yaw)), y = (int) (478.5 * Math.sin(pitch)), z = (int) (478.5 * Math.cos(pitch) * Math.sin(yaw));
			if (ZenithLayout.kind(ZenithLayout.cell(seed, x, y, z)) == Kind.WINDOW) windows++;
		}
		for (int x = 545; x < 680; x += 2) {
			for (int y = -30; y < 30; y++) {
				Kind kind = ZenithLayout.kind(ZenithLayout.cell(seed, x, y, 17));
				if (kind == Kind.SEA_WALL || kind == Kind.SEA_CARPET) sea++;
				if (kind == Kind.SEA_LIGHT) lamps++;
			}
		}
		assertTrue(sea > 500, "sea blocks " + sea);
		assertTrue(lamps > 0, "no lamps in the sea");
		assertTrue(windows > 50, "outer windows " + windows);
	}

	@Test
	void speakersAndZoneDetailsAppear() {
		java.util.EnumSet<ZenithLayout.Kind> seen = java.util.EnumSet.noneOf(ZenithLayout.Kind.class);
		for (int x = -400; x < 400; x += 2) {
			for (int z = -400; z < 400; z += 2) {
				for (int y : new int[] {33, 34, 38, 39, 41, 42, 65, 70}) seen.add(ZenithLayout.kind(ZenithLayout.cell(SEEDS[0], x, y, z)));
			}
		}
		for (ZenithLayout.Kind kind : new ZenithLayout.Kind[] {ZenithLayout.Kind.SPEAKER, ZenithLayout.Kind.ICICLES, ZenithLayout.Kind.DRIFT,
				ZenithLayout.Kind.SLAG, ZenithLayout.Kind.VENT, ZenithLayout.Kind.SCREEN, ZenithLayout.Kind.COIL, ZenithLayout.Kind.NOTES}) {
			assertTrue(seen.contains(kind), kind + " never appears");
		}
	}

	@Test
	void stairsClimbDeckByDeck() {
		for (long seed : SEEDS) {
			for (int deck : new int[] {-30, 25, 40, 52}) {
				int[] start = {1, deck * 8 + 1, -6};
				assertTrue(standable(seed, start[0], start[1], start[2]), "start " + deck);
				int goalY = (deck + 3) * 8 + 1;
				assertTrue(reaches(seed, start, new int[] {-24, 28, -24, 28, deck * 8 - 8, goalY + 8}, at -> at[1] >= goalY),
						"seed " + seed + " deck " + deck);
			}
		}
	}

	/** From the corridor out in the shell all the way along a spoke to the ring walkway round the singularity. */
	@Test
	void spokeLeadsToTheMiddle() {
		for (long seed : SEEDS) {
			int[] start = {1, 1, 250};
			assertTrue(standable(seed, 1, 1, 250));
			assertTrue(reaches(seed, start, new int[] {-6, 10, 30, 260, -2, 9}, at -> at[2] <= 44 && at[2] >= 42), "seed " + seed);
		}
	}

	@Test
	void arrivalIsInAnAirlockRoomOnTheFloor() {
		for (long seed : SEEDS) {
			for (int i = 0; i < 12; i++) {
				int[] spot = ZenithLayout.landing(seed, i * 700 - 3000, i * 91);
				assertTrue(standable(seed, spot[0], spot[1], spot[2]), "seed " + seed + " " + java.util.Arrays.toString(spot));
				double radius = Math.sqrt((double) spot[0] * spot[0] + (double) spot[1] * spot[1] + (double) spot[2] * spot[2]);
				assertTrue(radius > ZenithLayout.RO - 60 && radius < ZenithLayout.RO, "near the outer hull: " + radius);
			}
		}
	}

	/** The way back to Level 0 is there to find: an airlock holds a portal block. */
	@Test
	void airlocksHoldAWayOut() {
		long seed = SEEDS[0];
		int[] spot = ZenithLayout.landing(seed, 0, 0);
		int mx = Math.floorDiv(spot[0], 16) * 16, mz = Math.floorDiv(spot[2], 16) * 16, y = spot[1];
		assertEquals(Kind.PORTAL, ZenithLayout.kind(ZenithLayout.cell(seed, mx + 9, y, mz + 9)));
	}

	@Test
	void everyRoomHasADoorAndTheOutsideIsSealed() {
		for (int i = 0; i < 400; i++) {
			assertTrue(ZenithLayout.doors(7, i, i * 3 - 100, i % 17) != 0);
		}
		// Hull: nothing but hull blocks at the radius, from the equator to the poles.
		long seed = SEEDS[1];
		for (int[] p : new int[][] {{0, 478, 0}, {478, 0, 0}, {0, 0, 478}, {0, -478, 0}, {250, 200, 355}, {-250, -200, -355}}) {
			Kind kind = ZenithLayout.kind(ZenithLayout.cell(seed, p[0], p[1], p[2]));
			assertTrue(kind == Kind.HULL || kind == Kind.HULL_SEAM || kind == Kind.WINDOW || kind == Kind.AIR, kind.name());
		}
	}

	@Test
	void theSameSeedBuildsTheSameStation() {
		for (int i = 0; i < 2000; i++) {
			int x = i * 37 % 900 - 450, y = i * 53 % 900 - 450, z = i * 71 % 900 - 450;
			assertEquals(ZenithLayout.cell(5, x, y, z), ZenithLayout.cell(5, x, y, z));
		}
	}

	/** A chunk of the shell is a quarter of a million blocks; the generator must manage a few of them per tick. */
	@Test
	void aChunkOfShellIsQuickToWorkOut() throws Exception {
		long best = Long.MAX_VALUE;
		int air = 0;
		for (int round = 0; round < 4; round++) {
			long start = System.nanoTime();
			air = 0;
			for (int chunk = 0; chunk < 4; chunk++) {
				int minX = 160 + chunk * 16, minZ = 100;
				for (int x = minX; x < minX + 16; x++) {
					for (int z = minZ; z < minZ + 16; z++) {
						int reach = (int) Math.sqrt(ZenithLayout.RO * (double) ZenithLayout.RO - (double) x * x - (double) z * z);
						for (int y = -reach; y <= reach; y++) {
							if (ZenithLayout.kind(ZenithLayout.cell(7, x, y, z)) == Kind.AIR) air++;
						}
					}
				}
			}
			best = Math.min(best, System.nanoTime() - start);
		}
		java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "zenith_bench.txt"), (best / 4 / 1_000_000) + " ms per chunk, air " + air);
		assertTrue(best / 4 / 1_000_000 < 400, "too slow: " + best / 4 / 1_000_000 + " ms per chunk");
	}

	@Test
	void allRoomShapesAndMergedHallsAppear() {
		long seed = SEEDS[0];
		java.util.EnumSet<ZenithLayout.Shape> shapes = java.util.EnumSet.noneOf(ZenithLayout.Shape.class);
		boolean sawWide = false, sawDeep = false, sawQuad = false;
		for (int mx = -18; mx <= 18; mx++) {
			for (int mz = -18; mz <= 18; mz++) {
				for (int k = 1; k <= 12; k++) {
					double d = Math.hypot(Math.hypot(mx * 16 + 8, mz * 16 + 8), k * 8 + 4);
					if (d < ZenithLayout.RI + 30 || d > ZenithLayout.RO - 30) continue;
					if (ZenithLayout.atrium(seed, Math.floorDiv(mx, 3), Math.floorDiv(mz, 3), Math.floorDiv(k, 6))) continue;
					shapes.add(ZenithLayout.shape(seed, mx, mz, k));
					int[] b = ZenithLayout.roomBounds(seed, mx, mz, k);
					if (b[2] > b[0] && b[3] == b[1]) sawWide = true;
					if (b[3] > b[1] && b[2] == b[0]) sawDeep = true;
					if (b[2] > b[0] && b[3] > b[1]) sawQuad = true;
				}
			}
		}
		for (ZenithLayout.Shape s : ZenithLayout.Shape.values()) {
			assertTrue(shapes.contains(s), "missing shape: " + s);
		}
		assertTrue(sawWide, "no wide 2x1 merged hall");
		assertTrue(sawDeep, "no deep 1x2 merged hall");
		assertTrue(sawQuad, "no quad 2x2 merged hall");
	}

	@Test
	void doubleHeightStairsReachMezzanine() {
		for (long seed : SEEDS) {
			int[] found = findShape(seed, ZenithLayout.Shape.DOUBLE_LOWER, false);
			assertTrue(found != null, "no DOUBLE_LOWER room for seed " + seed);
			int[] b = ZenithLayout.roomBounds(seed, found[0], found[1], found[2]);
			int x0 = b[0] * 16 + 4, z0 = b[1] * 16 + 4;
			int x1 = b[2] * 16 + 15, z1 = b[3] * 16 + 15;
			int y0 = found[2] * 8;
			int[] start = {x0 + 6, y0 + 1, z0 + 6};
			assertTrue(standable(seed, start[0], start[1], start[2]), "double lower start not standable");
			assertTrue(reaches(seed, start, new int[] {x0, x1, z0, z1, y0, y0 + 14}, at -> at[1] == y0 + 9),
					"cannot climb stairs to mezzanine for seed " + seed);
		}
	}

	@Test
	void sunkenPitAndRaisedDaisAreWalkableAndSealed() {
		long seed = SEEDS[0];
		int[] sunken = findShape(seed, ZenithLayout.Shape.SUNKEN_PIT, false);
		assertTrue(sunken != null, "no SUNKEN_PIT found");
		int[] sb = ZenithLayout.roomBounds(seed, sunken[0], sunken[1], sunken[2]);
		int sx0 = sb[0] * 16 + 4, sz0 = sb[1] * 16 + 4, sx1 = sb[2] * 16 + 15, sz1 = sb[3] * 16 + 15;
		int sy0 = sunken[2] * 8;
		int[] pitStart = {sx0 + 1, sy0 + 1, sz0 + 5};
		assertTrue(standable(seed, pitStart[0], pitStart[1], pitStart[2]), "sunken perimeter not standable");
		assertTrue(reaches(seed, pitStart, new int[] {sx0, sx1, sz0, sz1, sy0 - 1, sy0 + 6}, at -> at[1] == sy0),
				"cannot walk down into sunken pit");
		// Ceiling of deck below (sy0 - 1) under the pit must stay solid.
		for (int x = sx0 + 3; x <= sx1 - 3; x++) {
			for (int z = sz0 + 3; z <= sz1 - 3; z++) {
				assertTrue(!passable(seed, x, sy0 - 1, z), "sunken pit punched hole into deck below at " + x + "," + z);
			}
		}

		int[] dais = findShape(seed, ZenithLayout.Shape.RAISED_DAIS, false);
		assertTrue(dais != null, "no RAISED_DAIS found");
		int[] db = ZenithLayout.roomBounds(seed, dais[0], dais[1], dais[2]);
		int dx0 = db[0] * 16 + 4, dz0 = db[1] * 16 + 4, dx1 = db[2] * 16 + 15, dz1 = db[3] * 16 + 15;
		int dy0 = dais[2] * 8;
		int[] daisStart = {dx0 + 6, dy0 + 1, dz0 + 6};
		assertTrue(standable(seed, daisStart[0], daisStart[1], daisStart[2]), "dais center not standable");
		assertTrue(reaches(seed, daisStart, new int[] {dx0, dx1, dz0, dz1, dy0, dy0 + 6}, at -> at[1] == dy0 + 2),
				"cannot walk up onto raised dais");
	}

	@Test
	void mergedAndSubdividedRoomsStayTraversable() {
		long seed = SEEDS[0];
		int[] quad = findShape(seed, null, true);
		assertTrue(quad != null, "no merged hall found");
		int[] b = ZenithLayout.roomBounds(seed, quad[0], quad[1], quad[2]);
		int x0 = b[0] * 16 + 4, z0 = b[1] * 16 + 4, x1 = b[2] * 16 + 15, z1 = b[3] * 16 + 15;
		int y0 = quad[2] * 8;
		int startY = ZenithLayout.shape(seed, quad[0], quad[1], quad[2]) == ZenithLayout.Shape.SUNKEN_PIT ? y0 : y0 + 1;
		int[] start = {x0 + 6, startY, z0 + 6};
		assertTrue(standable(seed, start[0], start[1], start[2]), "merged hall start not standable");
		assertTrue(reaches(seed, start, new int[] {x0, x1, z0, z1, y0 - 1, y0 + 14},
				at -> (b[2] > b[0] ? at[0] >= x1 - 6 : at[2] >= z1 - 6)),
				"cannot cross merged hall from end to end");

		for (ZenithLayout.Shape s : new ZenithLayout.Shape[] {
				ZenithLayout.Shape.NARROW_HALL, ZenithLayout.Shape.L_CORNER, ZenithLayout.Shape.CUBICLES, ZenithLayout.Shape.ALCOVE_BAYS}) {
			int[] r = findShape(seed, s, false);
			assertTrue(r != null, "no " + s + " room found");
			int[] rb = ZenithLayout.roomBounds(seed, r[0], r[1], r[2]);
			int rx0 = rb[0] * 16 + 4, rz0 = rb[1] * 16 + 4, rx1 = rb[2] * 16 + 15, rz1 = rb[3] * 16 + 15;
			int ry0 = r[2] * 8;
			int[] rs = {rx0 + 6, ry0 + 1, rz0 + 6};
			assertTrue(standable(seed, rs[0], rs[1], rs[2]), s + " center not standable");
			// Must be able to step out through an outer door to the corridor outside.
			assertTrue(reaches(seed, rs, new int[] {rx0 - 2, rx1 + 2, rz0 - 2, rz1 + 2, ry0 - 1, ry0 + 6},
					at -> at[0] < rx0 || at[0] > rx1 || at[2] < rz0 || at[2] > rz1),
					"trapped inside " + s);
		}
	}

	private static int[] findShape(long seed, ZenithLayout.Shape target, boolean requireMerged) {
		for (int mx = -18; mx <= 18; mx++) {
			for (int mz = -18; mz <= 18; mz++) {
				for (int k = 2; k <= 12; k++) {
					double d = Math.hypot(Math.hypot(mx * 16 + 8, mz * 16 + 8), k * 8 + 4);
					if (d < ZenithLayout.RI + 40 || d > ZenithLayout.RO - 40) continue;
					if (ZenithLayout.atrium(seed, Math.floorDiv(mx, 3), Math.floorDiv(mz, 3), Math.floorDiv(k, 6))) continue;
					if (ZenithLayout.room(seed, mx, mz, k) == ZenithLayout.Room.AIRLOCK) continue;
					if (requireMerged && (!ZenithLayout.isMerged(seed, mx, mz, k) || ZenithLayout.shape(seed, mx, mz, k) == ZenithLayout.Shape.DOUBLE_UPPER)) continue;
					if (target != null && ZenithLayout.shape(seed, mx, mz, k) != target) continue;
					if (target != null && ZenithLayout.isMerged(seed, mx, mz, k)) continue;
					return new int[] {mx, mz, k};
				}
			}
		}
		return null;
	}
}
