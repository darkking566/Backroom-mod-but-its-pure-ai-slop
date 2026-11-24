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
	void speakersAndZoneDetailsAppear() {
		java.util.EnumSet<ZenithLayout.Kind> seen = java.util.EnumSet.noneOf(ZenithLayout.Kind.class);
		for (int x = -400; x < 400; x += 2) {
			for (int z = -400; z < 400; z += 2) {
				for (int y : new int[] {33, 38, 39, 41, 65, 70}) seen.add(ZenithLayout.kind(ZenithLayout.cell(SEEDS[0], x, y, z)));
			}
		}
		for (ZenithLayout.Kind kind : new ZenithLayout.Kind[] {ZenithLayout.Kind.SPEAKER, ZenithLayout.Kind.ICICLES, ZenithLayout.Kind.DRIFT,
				ZenithLayout.Kind.SLAG, ZenithLayout.Kind.VENT}) {
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
			assertTrue(kind == Kind.HULL || kind == Kind.HULL_SEAM || kind == Kind.AIR, kind.name());
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
}
