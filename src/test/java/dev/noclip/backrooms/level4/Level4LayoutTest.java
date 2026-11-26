package dev.noclip.backrooms.level4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.noclip.backrooms.level4.Level4Layout.Kind;
import dev.noclip.backrooms.level4.Level4Layout.Tile;
import dev.noclip.backrooms.level4.Level4Plan.Edge;
import dev.noclip.backrooms.level4.Level4Plan.PlacedExit;
import dev.noclip.backrooms.level4.Level4Plan.ZoneKind;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class Level4LayoutTest {
	@Test
	void everyWalkableCellInARegionReachesEveryOther() {
		for (long seed = 1; seed <= 12; seed++) {
			for (int rx = -1; rx <= 1; rx++) {
				for (int rz = -1; rz <= 1; rz++) {
					Level4Plan plan = Level4Plan.of(seed, rx, rz);
					boolean[] seen = new boolean[Level4Plan.N * Level4Plan.N];
					int[] queue = new int[Level4Plan.N * Level4Plan.N];
					int start = -1, walkableCount = 0;
					for (int c = 0; c < Level4Plan.N * Level4Plan.N; c++) {
						if (plan.zones[c] != ZoneKind.SOLID_CORE) {
							walkableCount++;
							if (start < 0) start = c;
						}
					}
					int head = 0, tail = 0;
					queue[tail++] = start;
					seen[start] = true;
					while (head < tail) {
						int c = queue[head++];
						int i = c % Level4Plan.N, j = c / Level4Plan.N;
						for (int d = 0; d < 4; d++) {
							Edge e = plan.edge(c, d);
							if (e != Edge.OPEN && e != Edge.DOOR) continue;
							int a = i + Level4Plan.DX[d], b = j + Level4Plan.DZ[d];
							if (!plan.walkable(a, b)) continue;
							int nc = Level4Plan.cell(a, b);
							if (!seen[nc]) {
								seen[nc] = true;
								queue[tail++] = nc;
							}
						}
					}
					assertEquals(walkableCount, tail, "disconnected cell in seed=" + seed + " region " + rx + "," + rz);
				}
			}
		}
	}

	@Test
	void allThreeFactionsAtriumAndExitsAppear() {
		Set<ZoneKind> zonesSeen = EnumSet.noneOf(ZoneKind.class);
		Set<Level4Exit> exitsSeen = EnumSet.noneOf(Level4Exit.class);
		for (long seed = 10; seed <= 14; seed++) {
			for (int rx = -2; rx <= 2; rx++) {
				for (int rz = -2; rz <= 2; rz++) {
					Level4Plan plan = Level4Plan.of(seed, rx, rz);
					for (ZoneKind z : plan.zones) zonesSeen.add(z);
					for (PlacedExit pe : plan.exits) exitsSeen.add(pe.exit());
				}
			}
		}
		assertTrue(zonesSeen.contains(ZoneKind.OPEN_OFFICE));
		assertTrue(zonesSeen.contains(ZoneKind.SUITE));
		assertTrue(zonesSeen.contains(ZoneKind.ATRIUM));
		assertTrue(zonesSeen.contains(ZoneKind.DARK_WING));
		assertTrue(zonesSeen.contains(ZoneKind.OMEGA_BASE));
		assertTrue(zonesSeen.contains(ZoneKind.AMOR_CAMP));
		assertTrue(zonesSeen.contains(ZoneKind.TBD_ROOM));
		assertTrue(exitsSeen.contains(Level4Exit.ELEVATOR_3));
		assertTrue(exitsSeen.contains(Level4Exit.OMEGA_STAIRS_5));
		assertTrue(exitsSeen.contains(Level4Exit.OMEGA_STAIRS_6));
		assertTrue(exitsSeen.contains(Level4Exit.BASEMENT_71));
		assertTrue(exitsSeen.contains(Level4Exit.WHITE_DOOR_4P3));
	}

	@Test
	void landingIsAlwaysWalkableAndOutsideOmega() {
		for (long seed = 1; seed <= 30; seed++) {
			int[] spot = Level4Layout.landing(seed, (int) seed * 113, (int) -seed * 89);
			Tile tile = Level4Layout.tile(seed, spot[0], spot[1]);
			assertEquals(Kind.AIR, tile.kind(), "landing blocked at " + spot[0] + "," + spot[1]);
			assertTrue(tile.zone() == ZoneKind.OPEN_OFFICE || tile.zone() == ZoneKind.SUITE);
		}
	}

	@Test
	void wanderZonesOnlyAllowOpenSpacesAndAtrium() {
		assertTrue(Level4Wanderers.isWanderZone(ZoneKind.OPEN_OFFICE));
		assertTrue(Level4Wanderers.isWanderZone(ZoneKind.SUITE));
		assertTrue(Level4Wanderers.isWanderZone(ZoneKind.ATRIUM));
		assertTrue(!Level4Wanderers.isWanderZone(ZoneKind.OMEGA_BASE));
		assertTrue(!Level4Wanderers.isWanderZone(ZoneKind.AMOR_CAMP));
		assertTrue(!Level4Wanderers.isWanderZone(ZoneKind.TBD_ROOM));
		assertTrue(!Level4Wanderers.isWanderZone(ZoneKind.DARK_WING));
		assertTrue(!Level4Wanderers.isWanderZone(ZoneKind.SOLID_CORE));
	}

	@Test
	void factionSizesAndDistinctFixturesMatchWiki() {
		boolean omegaOk = false, amorOk = false, tbdOk = false;
		Set<Kind> amorKinds = EnumSet.noneOf(Kind.class);
		Set<Kind> tbdKinds = EnumSet.noneOf(Kind.class);
		for (long seed = 10; seed <= 16; seed++) {
			for (int rx = -2; rx <= 2; rx++) {
				for (int rz = -2; rz <= 2; rz++) {
					Level4Plan plan = Level4Plan.of(seed, rx, rz);
					for (Level4Plan.RoomBox room : plan.rooms) {
						int x0 = rx * Level4Plan.SIZE + room.i() * Level4Plan.CELL;
						int z0 = rz * Level4Plan.SIZE + room.j() * Level4Plan.CELL;
						int x1 = x0 + room.w() * Level4Plan.CELL;
						int z1 = z0 + room.h() * Level4Plan.CELL;
						if (room.kind() == ZoneKind.OMEGA_BASE) {
							assertEquals(5, room.w());
							assertEquals(5, room.h());
							omegaOk = true;
						} else if (room.kind() == ZoneKind.AMOR_CAMP) {
							assertEquals(3, room.w());
							assertEquals(3, room.h());
							for (int x = x0; x < x1; x++) {
								for (int z = z0; z < z1; z++) {
									amorKinds.add(Level4Layout.tile(seed, x, z).kind());
								}
							}
							amorOk = true;
						} else if (room.kind() == ZoneKind.TBD_ROOM) {
							assertEquals(2, room.w());
							assertEquals(3, room.h());
							for (int x = x0; x < x1; x++) {
								for (int z = z0; z < z1; z++) {
									tbdKinds.add(Level4Layout.tile(seed, x, z).kind());
								}
							}
							tbdOk = true;
						}
					}
				}
			}
		}
		assertTrue(omegaOk && amorOk && tbdOk);
		assertTrue(amorKinds.contains(Kind.AMOR_PLANTER));
		assertTrue(amorKinds.contains(Kind.AMOR_PLANTER_L1));
		assertTrue(amorKinds.contains(Kind.AMOR_PLANTER_VEIL));
		assertTrue(tbdKinds.contains(Kind.TBD_SHELF));
		assertTrue(tbdKinds.contains(Kind.TBD_HOARD));
	}

	@Test
	void mostWindowsAreBlackedOut() {
		int blacked = 0, total = 0;
		for (long seed = 1; seed <= 8; seed++) {
			for (int rx = -1; rx <= 1; rx++) {
				for (int rz = -1; rz <= 1; rz++) {
					int x0 = rx * Level4Plan.SIZE, z0 = rz * Level4Plan.SIZE;
					for (int x = x0; x < x0 + Level4Plan.SIZE; x++) {
						for (int z = z0; z < z0 + Level4Plan.SIZE; z++) {
							Kind k = Level4Layout.tile(seed, x, z).kind();
							if (k == Kind.WINDOW_BLACKED || k == Kind.WINDOW_TRAP || k == Kind.WINDOW_RAIN) {
								total++;
								if (k == Kind.WINDOW_BLACKED) blacked++;
							}
						}
					}
				}
			}
		}
		assertTrue(total > 0, "expected windows in Level 4");
		assertTrue(blacked * 100 >= total * 70, "expected >= 70% blacked-out windows, got " + blacked + "/" + total);
	}
}
