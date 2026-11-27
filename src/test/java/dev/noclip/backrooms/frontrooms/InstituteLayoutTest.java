package dev.noclip.backrooms.frontrooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.noclip.backrooms.frontrooms.InstituteLayout.Kind;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The institute's plans (InstituteLayout) and the ground around it (FrontroomsTerrain). */
final class InstituteLayoutTest {
	private static final int GROUND = 2, B1 = 1, B2 = 0;

	/** Every cell someone can walk to on a floor from a start, with blast doors open up to a clearance. */
	private static Set<Long> walk(int floor, int startX, int startZ, int clearance) {
		Set<Long> seen = new HashSet<>();
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		queue.add(new int[] {startX, startZ});
		seen.add(key(startX, startZ));
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int x = at[0] + step[0], z = at[1] + step[1];
				if (!InstituteLayout.inside(x, z) || seen.contains(key(x, z))) continue;
				char ch = InstituteLayout.plan(floor, x, z);
				boolean door = ch == '1' || ch == '3' || ch == '5';
				if (!(InstituteLayout.open(ch) || door && ch - '0' <= clearance)) continue;
				seen.add(key(x, z));
				queue.add(new int[] {x, z});
			}
		}
		return seen;
	}

	private static long key(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	/** Whether someone in the reached cells can get within reach (two blocks) of a spot: across a counter is fine. */
	private static boolean near(Set<Long> reached, int x, int z) {
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) if (reached.contains(key(x + dx, z + dz))) return true;
		return false;
	}

	private static boolean nearAny(Set<Long> reached, int floor, char ch) {
		for (int x = InstituteLayout.MIN_X; x <= InstituteLayout.MAX_X; x++) {
			for (int z = InstituteLayout.MIN_Z; z <= InstituteLayout.MAX_Z; z++) {
				if (InstituteLayout.plan(floor, x, z) == ch && near(reached, x, z)) return true;
			}
		}
		return false;
	}

	@Test
	void eachQuotaOpensItsWingAndNothingMore() {
		InstituteLayout.Spot a = InstituteLayout.spot('a');
		assertNotNull(a);
		Set<Long> zero = walk(GROUND, a.x(), a.z(), 0);
		for (char who : "kyu".toCharArray()) {
			InstituteLayout.Spot s = InstituteLayout.spot(who);
			assertTrue(near(zero, s.x(), s.z()), "the lobby does not reach " + who);
		}
		assertTrue(nearAny(zero, GROUND, 'L'), "no locker from the lobby");
		assertTrue(nearAny(zero, GROUND, ','), "no work area from the lobby");
		assertTrue(nearAny(zero, GROUND, 'g'), "the gate is not reachable");
		assertFalse(InstituteLayout.lobbyPads().isEmpty(), "no 1F lobby return pad to Level 0");
		for (InstituteLayout.Spot lp : InstituteLayout.lobbyPads()) {
			assertTrue(zero.contains(key(lp.x(), lp.z())), "1F lobby pad is not reachable before any quota");
			assertEquals(Kind.PAD, InstituteLayout.cell(lp.x(), InstituteLayout.FLOOR, lp.z()).kind(), "1F lobby pad missing PAD cell");
		}
		InstituteLayout.Spot medic = InstituteLayout.spot('n'), quartermaster = InstituteLayout.spot('q');
		assertFalse(near(zero, medic.x(), medic.z()), "the medic is reachable before any quota");
		assertFalse(near(zero, quartermaster.x(), quartermaster.z()), "the armoury is reachable before any quota");
		assertFalse(nearAny(zero, GROUND, 's'), "the stairs to B1 are reachable before any quota");

		Set<Long> one = walk(GROUND, a.x(), a.z(), 1);
		assertTrue(near(one, medic.x(), medic.z()), "one quota does not open the medical wing");
		assertFalse(near(one, quartermaster.x(), quartermaster.z()), "one quota opens the armoury");
		Set<Long> three = walk(GROUND, a.x(), a.z(), 3);
		assertTrue(near(three, quartermaster.x(), quartermaster.z()), "three quotas do not open the armoury");
		assertFalse(nearAny(three, GROUND, 's'), "three quotas open B1");
		assertTrue(nearAny(walk(GROUND, a.x(), a.z(), 5), GROUND, 's'), "five quotas do not open the stairs to B1");
	}

	@Test
	void theStairsGoDownOneBlockAStepToB1AndB1ReachesItsPads() {
		int stairX = -1, top = Integer.MAX_VALUE, bottom = Integer.MIN_VALUE;
		for (int x = InstituteLayout.MIN_X; x <= InstituteLayout.MAX_X; x++) {
			for (int z = InstituteLayout.MIN_Z; z <= InstituteLayout.MAX_Z; z++) {
				if (InstituteLayout.plan(GROUND, x, z) != 's') continue;
				stairX = x;
				top = Math.min(top, z);
				bottom = Math.max(bottom, z);
			}
		}
		assertEquals(InstituteLayout.FLOOR - InstituteLayout.B1_FLOOR, bottom - top + 1, "the stairs are not as long as the drop");
		for (int z = top; z <= bottom; z++) {
			int surface = InstituteLayout.FLOOR - 1 - (z - top);
			assertEquals(Kind.STAIR, InstituteLayout.cell(stairX, surface, z).kind(), "no step at " + z);
			for (int y = surface + 1; y < surface + 4; y++) assertEquals(Kind.AIR, InstituteLayout.cell(stairX, y, z).kind(), "no headroom over step " + z);
		}
		// The foot of the stairs opens onto B1's floor.
		assertTrue(InstituteLayout.open(InstituteLayout.plan(B1, stairX, bottom + 1)), "the stairs end in a wall");
		Set<Long> b1 = walk(B1, stairX, bottom + 1, 0);
		for (InstituteLayout.Spot pad : InstituteLayout.pads()) assertTrue(b1.contains(key(pad.x(), pad.z())), "a pad is out of reach");
		InstituteLayout.Spot hatch = InstituteLayout.hatch();
		assertTrue(b1.contains(key(hatch.x(), hatch.z())), "B2's hatch is out of reach");
		assertTrue(nearAny(b1, B1, 'U') && nearAny(b1, B1, 'Z'), "B1's machines are out of reach");
	}

	@Test
	void b2IsOnlyUnderTheHatchAndItsFilesAreReachable() {
		InstituteLayout.Spot hatch = InstituteLayout.hatch();
		assertEquals('l', InstituteLayout.plan(B2, hatch.x(), hatch.z()), "the ladder is not under the hatch");
		for (int y = InstituteLayout.B2_FLOOR + 1; y < InstituteLayout.B1_FLOOR; y++) {
			assertEquals(Kind.LADDER, InstituteLayout.cell(hatch.x(), y, hatch.z()).kind(), "the ladder breaks at y " + y);
		}
		assertEquals(Kind.HATCH, InstituteLayout.cell(hatch.x(), InstituteLayout.B1_FLOOR, hatch.z()).kind());
		// The ladder hangs on the block north of it.
		assertEquals('#', InstituteLayout.plan(B2, hatch.x(), hatch.z() - 1), "nothing for the ladder to hang on");
		Set<Long> b2 = walk(B2, hatch.x(), hatch.z(), 0);
		assertFalse(InstituteLayout.files().isEmpty());
		for (InstituteLayout.Spot file : InstituteLayout.files()) assertTrue(near(b2, file.x(), file.z()), "a file is out of reach");
	}

	@Test
	void doorsHaveTwoHalvesAndALampAndArrivalsStandOnFloor() {
		assertEquals(6, InstituteLayout.doors().size(), "three blast doors, two columns each");
		for (InstituteLayout.Door door : InstituteLayout.doors()) {
			assertEquals(Kind.DOOR, InstituteLayout.cell(door.x(), door.y(), door.z()).kind());
			assertTrue(InstituteLayout.cell(door.x(), door.y() + 1, door.z()).upper());
			assertEquals(Kind.DOOR_LAMP, InstituteLayout.cell(door.x(), door.y() + 2, door.z()).kind());
		}
		InstituteLayout.Spot a = InstituteLayout.spot('a');
		assertEquals(Kind.FLOOR, InstituteLayout.cell(a.x(), InstituteLayout.FLOOR, a.z()).kind());
		assertEquals(Kind.AIR, InstituteLayout.cell(a.x(), InstituteLayout.FLOOR + 1, a.z()).kind());
		assertEquals(Kind.AIR, InstituteLayout.cell(a.x(), InstituteLayout.FLOOR + 2, a.z()).kind());
		assertTrue(InstituteLayout.workArea(-20, InstituteLayout.FLOOR + 1, 10), "the west work area does not take building");
		assertFalse(InstituteLayout.workArea(a.x(), InstituteLayout.FLOOR + 1, a.z()), "the lobby takes building");
		assertTrue(InstituteLayout.building(a.x(), InstituteLayout.FLOOR, a.z()), "the lobby floor can be broken");
	}

	@Test
	void theGroundIsLevelAroundTheBuildingAndWalledIn() {
		long seed = 12345L;
		for (int x = InstituteLayout.MIN_X - 6; x <= InstituteLayout.MAX_X + 6; x += 3) {
			for (int z = InstituteLayout.MIN_Z - 6; z <= InstituteLayout.YARD_MAX_Z + 6; z += 3) {
				assertEquals(FrontroomsTerrain.GROUND, FrontroomsTerrain.height(seed, x, z), "the ground is not level at " + x + ", " + z);
			}
		}
		assertTrue(FrontroomsTerrain.grounds(0, 0));
		assertTrue(FrontroomsTerrain.grounds(98, 0));
		assertTrue(FrontroomsTerrain.wall(100, 0) && FrontroomsTerrain.wall(0, -101));
		assertTrue(FrontroomsTerrain.beyond(110, 0) && FrontroomsTerrain.beyond(80, 80));
		int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
		for (int x = -95; x <= 95; x += 5) {
			for (int z = -95; z <= 95; z += 5) {
				if (!FrontroomsTerrain.grounds(x, z)) continue;
				int h = FrontroomsTerrain.height(seed, x, z);
				min = Math.min(min, h);
				max = Math.max(max, h);
			}
		}
		assertTrue(max - min >= 4, "the grounds are flat: " + min + ".." + max);
		assertTrue(max < FrontroomsTerrain.WALL_TOP - 20, "a hill climbs the wall");
	}
}
