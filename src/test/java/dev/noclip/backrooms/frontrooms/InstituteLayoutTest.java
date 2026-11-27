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
				// The institute's objects stand in the way.
				if (InstituteLayout.furnished(floor, x, z)) continue;
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
		assertFalse(nearB1Stairs(zero), "the stairs to B1 are reachable before any quota");

		Set<Long> one = walk(GROUND, a.x(), a.z(), 1);
		assertTrue(near(one, medic.x(), medic.z()), "one quota does not open the medical wing");
		assertFalse(near(one, quartermaster.x(), quartermaster.z()), "one quota opens the armoury");
		Set<Long> three = walk(GROUND, a.x(), a.z(), 3);
		assertTrue(near(three, quartermaster.x(), quartermaster.z()), "three quotas do not open the armoury");
		assertFalse(nearB1Stairs(three), "three quotas open B1");
		assertTrue(nearB1Stairs(walk(GROUND, a.x(), a.z(), 5)), "five quotas do not open the stairs to B1");
	}

	/** The flight down to B1 is the one whose low floor is B1. */
	private static InstituteLayout.Flight b1Stairs() {
		return InstituteLayout.flights().stream().filter(f -> f.low() == InstituteLayout.B1_FLOOR).findFirst().orElseThrow();
	}

	private static boolean nearB1Stairs(Set<Long> reached) {
		InstituteLayout.Flight f = b1Stairs();
		for (int x = f.minX(); x <= f.maxX(); x++) for (int z = f.minZ(); z <= f.maxZ(); z++) if (reached.contains(key(x, z))) return true;
		return false;
	}

	/** The floor (plan index) whose floor block is at a height. */
	private static int floorAt(int y) {
		for (int f = 0; f < InstituteLayout.floors(); f++) if (InstituteLayout.floorY(f) == y) return f;
		throw new AssertionError("no floor at " + y);
	}

	@Test
	void everyFlightClimbsOneBlockAStepWithHeadroomAndJoinsBothFloors() {
		for (InstituteLayout.Flight f : InstituteLayout.flights()) {
			for (int x = f.minX(); x <= f.maxX(); x++) {
				for (int z = f.minZ(); z <= f.maxZ(); z++) {
					int step = f.step(x, z);
					assertTrue(step >= 0 && step < f.steps(), "a column of the stairs is off the flight at " + x + ", " + z);
					int surface = f.low() + 1 + step;
					Kind kind = InstituteLayout.cell(x, surface, z).kind();
					assertTrue(kind == Kind.STAIR || kind == Kind.FLOOR, "no step at " + x + ", " + surface + ", " + z);
					assertEquals(Kind.SOLID, InstituteLayout.cell(x, surface - 1, z).kind(), "a step hangs in the air at " + x + ", " + z);
					for (int y = surface + 1; y <= surface + 3; y++) {
						assertEquals(Kind.AIR, InstituteLayout.cell(x, y, z).kind(), "no headroom over the step at " + x + ", " + y + ", " + z);
					}
				}
			}
			// Someone walks onto the foot from the low floor and off the top onto the high floor.
			int low = floorAt(f.low()), high = floorAt(f.high());
			int fx = f.dx() != 0 ? f.foot() - f.dx() : f.minX(), fz = f.dz() != 0 ? f.foot() - f.dz() : f.minZ();
			assertTrue(InstituteLayout.open(InstituteLayout.plan(low, fx, fz)), "the foot of a flight starts in a wall at " + fx + ", " + fz);
			int top = f.foot() + (f.steps()) * (f.dx() != 0 ? f.dx() : f.dz());
			int tx = f.dx() != 0 ? top : f.minX(), tz = f.dz() != 0 ? top : f.minZ();
			assertTrue(InstituteLayout.open(InstituteLayout.plan(high, tx, tz)), "a flight ends in a wall at " + tx + ", " + tz);
		}
	}

	@Test
	void b1ReachesItsPadsHatchAndMachinesFromTheStairs() {
		InstituteLayout.Flight f = b1Stairs();
		assertEquals(InstituteLayout.FLOOR - InstituteLayout.B1_FLOOR, f.steps(), "the stairs are not as long as the drop");
		int fx = f.foot() - f.dx(), fz = f.minZ();
		assertTrue(InstituteLayout.open(InstituteLayout.plan(B1, fx, fz)), "the stairs end in a wall");
		Set<Long> b1 = walk(B1, fx, fz, 0);
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
		assertTrue(InstituteLayout.workArea(-36, InstituteLayout.FLOOR + 1, -25), "the west work area does not take building");
		assertFalse(InstituteLayout.workArea(a.x(), InstituteLayout.FLOOR + 1, a.z()), "the lobby takes building");
		assertTrue(InstituteLayout.building(a.x(), InstituteLayout.FLOOR, a.z()), "the lobby floor can be broken");
	}

	@Test
	void everyFloorAboveGroundIsReachedFromTheLobbyAndTheGroundsKeepClearOfTheBuilding() {
		InstituteLayout.Spot a = InstituteLayout.spot('a');
		Set<Long> lobby = walk(GROUND, a.x(), a.z(), 0);
		for (InstituteLayout.Flight f : InstituteLayout.flights()) {
			if (f.low() != InstituteLayout.FLOOR) continue;
			assertTrue(lobby.contains(key(f.minX(), f.foot() - f.dz())), "a staircase up is out of reach of the lobby");
		}
		for (int x = InstituteLayout.MIN_X; x <= InstituteLayout.MAX_X; x++) {
			for (int z = InstituteLayout.MIN_Z; z <= InstituteLayout.MAX_Z; z++) {
				for (int y = InstituteLayout.FLOOR; y <= InstituteLayout.TOP; y += 4) {
					assertEquals(FrontroomsCampus.Part.NONE, FrontroomsCampus.part(x, y, z), "the grounds build into the institute at " + x + ", " + y + ", " + z);
				}
			}
		}
	}

	@Test
	void objectsStandOnFloorsAndLeaveSpotsFree() {
		for (InstituteFurnishing.Place place : InstituteFurnishing.places()) {
			for (int[] cell : InstituteFurnishing.cells(place)) {
				char ch = InstituteLayout.plan(place.floor(), cell[0], cell[2]);
				assertTrue(".,;+_".indexOf(ch) >= 0, place.type().id() + " stands on '" + ch + "' at " + cell[0] + ", " + cell[2]);
				int y = InstituteLayout.floorY(place.floor()) + 1 + cell[1];
				assertTrue(y < InstituteLayout.ceilingY(place.floor()), place.type().id() + " goes through the ceiling");
				Kind kind = InstituteLayout.cell(cell[0], y, cell[2]).kind();
				assertTrue(kind == Kind.PROP || kind == Kind.PROP_PART, place.type().id() + " is not in the building at " + cell[0] + ", " + y + ", " + cell[2]);
			}
		}
		for (char who : "akyunq".toCharArray()) {
			InstituteLayout.Spot s = InstituteLayout.spot(who);
			assertFalse(InstituteLayout.furnished(GROUND, s.x(), s.z()), "an object stands on " + who);
		}
		assertEquals(18, InstituteFurnishing.types().size(), "the institute's kinds of objects");
	}

	@Test
	void theGroundIsLevelAroundTheBuildingAndWalledIn() {
		long seed = 12345L;
		for (int x = InstituteLayout.MIN_X - 6; x <= InstituteLayout.MAX_X + 6; x += 3) {
			for (int z = InstituteLayout.MIN_Z - 6; z <= InstituteLayout.MAX_Z + 30; z += 3) {
				assertEquals(FrontroomsTerrain.GROUND, FrontroomsTerrain.height(seed, x, z), "the ground is not level at " + x + ", " + z);
			}
		}
		assertTrue(FrontroomsTerrain.grounds(0, 0));
		assertTrue(FrontroomsTerrain.grounds(194, 0));
		assertTrue(FrontroomsTerrain.wall(196, 0) && FrontroomsTerrain.wall(0, -197));
		assertTrue(FrontroomsTerrain.beyond(205, 0) && FrontroomsTerrain.beyond(150, 150));
		int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
		for (int x = -190; x <= 190; x += 5) {
			for (int z = -190; z <= 190; z += 5) {
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
