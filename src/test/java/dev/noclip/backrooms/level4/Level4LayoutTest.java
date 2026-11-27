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
						if (plan.zones[c] != ZoneKind.SOLID_CORE && plan.zones[c] != ZoneKind.OUTSIDE) {
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
		assertTrue(!Level4Wanderers.isWanderZone(ZoneKind.OUTSIDE));
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
	void windowsBlackedNearBasesAndMostlyOpenRainOutside() {
		int wildRain = 0, wildTrap = 0, wildBlacked = 0, baseBlacked = 0, baseNonBlacked = 0;
		boolean sawOutside = false;
		for (long seed = 1; seed <= 8; seed++) {
			for (int rx = -1; rx <= 1; rx++) {
				for (int rz = -1; rz <= 1; rz++) {
					Level4Plan plan = Level4Plan.of(seed, rx, rz);
					for (int i = 0; i < Level4Plan.N; i++) {
						for (int j = 0; j < Level4Plan.N; j++) {
							int c = Level4Plan.cell(i, j);
							if (plan.zones[c] == ZoneKind.OUTSIDE) sawOutside = true;
							int wxBase = rx * Level4Plan.SIZE + i * Level4Plan.CELL;
							int wzBase = rz * Level4Plan.SIZE + j * Level4Plan.CELL;
							for (int cx = 0; cx < Level4Plan.CELL; cx++) {
								for (int cz = 0; cz < Level4Plan.CELL; cz++) {
									Tile t = Level4Layout.tile(seed, wxBase + cx, wzBase + cz);
									Kind k = t.kind();
									if (k != Kind.WINDOW_BLACKED && k != Kind.WINDOW_TRAP && k != Kind.WINDOW_RAIN) continue;
									int refI = i, refJ = j;
									if (plan.zones[c] == ZoneKind.OUTSIDE) {
										if (cz == 0 && j > 0 && plan.zones[Level4Plan.cell(i, j - 1)] != ZoneKind.OUTSIDE) refJ = j - 1;
										else if (cx == 0 && i > 0 && plan.zones[Level4Plan.cell(i - 1, j)] != ZoneKind.OUTSIDE) refI = i - 1;
									}
									if (plan.nearHumanBase(refI, refJ)) {
										if (k == Kind.WINDOW_BLACKED) baseBlacked++;
										else baseNonBlacked++;
									} else {
										if (k == Kind.WINDOW_RAIN) wildRain++;
										else if (k == Kind.WINDOW_TRAP) wildTrap++;
										else wildBlacked++;
									}
								}
							}
						}
					}
				}
			}
		}
		int wildTotal = wildRain + wildTrap + wildBlacked;
		assertTrue(sawOutside, "expected outdoor misty canyon cells");
		assertTrue(baseBlacked > 0 && baseNonBlacked == 0,
				"expected all windows near human bases blacked out, got blacked=" + baseBlacked + " non=" + baseNonBlacked);
		assertTrue(wildTotal > 0 && wildRain * 100 >= wildTotal * 65,
				"expected mostly transparent rain windows outside bases, got rain=" + wildRain + "/" + wildTotal);
		assertTrue(wildTrap > 0 && wildTrap * 100 <= wildTotal * 35,
				"expected ~18% Entity 2 trap windows outside bases, got trap=" + wildTrap + "/" + wildTotal);
	}

	@Test
	void physicalExitStructuresHaveValidFootprints() {
		Set<Level4Exit> seenStructs = EnumSet.noneOf(Level4Exit.class);
		for (long seed = 10; seed <= 16; seed++) {
			for (int rx = -2; rx <= 2; rx++) {
				for (int rz = -2; rz <= 2; rz++) {
					Level4Plan plan = Level4Plan.of(seed, rx, rz);
					for (PlacedExit pe : plan.exits) {
						if (!pe.exit().hasStructure()) continue;
						int cx = pe.cell() % Level4Plan.N, cz = pe.cell() / Level4Plan.N;
						int baseX = rx * Level4Plan.SIZE + cx * Level4Plan.CELL;
						int baseZ = rz * Level4Plan.SIZE + cz * Level4Plan.CELL;
						int doors = 0, structs = 0;
						for (int x = baseX; x < baseX + Level4Plan.CELL; x++) {
							for (int z = baseZ; z < baseZ + Level4Plan.CELL; z++) {
								Level4Layout.ExitStruct es = Level4Layout.exitStructure(seed, x, z);
								if (es == null) continue;
								Tile t = Level4Layout.tile(seed, x, z);
								if (t.kind() == Kind.EXIT_DOOR) doors++;
								else if (t.kind() == Kind.EXIT_STRUCTURE) structs++;
							}
						}
						assertEquals(1, doors, "expected 1 exit door column for " + pe.exit());
						assertTrue(structs >= 5, "expected multi-block structure for " + pe.exit());
						seenStructs.add(pe.exit());
					}
				}
			}
		}
		assertTrue(seenStructs.contains(Level4Exit.ELEVATOR_3));
		assertTrue(seenStructs.contains(Level4Exit.OMEGA_STAIRS_5));
		assertTrue(seenStructs.contains(Level4Exit.OMEGA_STAIRS_6));
		assertTrue(seenStructs.contains(Level4Exit.STAIRS_5));
		assertTrue(seenStructs.contains(Level4Exit.BASEMENT_71));
	}

	@Test
	void indoorWalkableSpacesNeverTouchOutsideAir() {
		for (long seed = 1; seed <= 6; seed++) {
			for (int x = -Level4Plan.SIZE; x < Level4Plan.SIZE * 2; x++) {
				for (int z = -Level4Plan.SIZE; z < Level4Plan.SIZE * 2; z++) {
					Tile center = Level4Layout.tile(seed, x, z);
					if (!center.walkable()) continue;
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							if (dx == 0 && dz == 0) continue;
							Tile neighbor = Level4Layout.tile(seed, x + dx, z + dz);
							assertTrue(neighbor.kind() != Kind.OUTSIDE_AIR,
									"wall gap between indoor walkable tile and OUTSIDE_AIR at seed=" + seed
											+ " (" + x + "," + z + ") -> (" + (x + dx) + "," + (z + dz) + ")");
						}
					}
				}
			}
		}
	}

	@Test
	void batch1StructuresCubiclesUnfinishedAndVariedSkybridgesAppear() {
		boolean sawCubicleWall = false, sawCubicleGlass = false;
		boolean sawUnfinished = false, sawStub = false;
		boolean sawElevatedBridge = false, sawFlatBridge = false;
		for (long seed = 10; seed <= 16; seed++) {
			for (int rx = -1; rx <= 1; rx++) {
				for (int rz = -1; rz <= 1; rz++) {
					Level4Plan plan = Level4Plan.of(seed, rx, rz);
					for (ZoneKind z : plan.zones) {
						if (z == ZoneKind.UNFINISHED_WING) sawUnfinished = true;
					}
					for (int lx = 0; lx < Level4Plan.SIZE; lx++) {
						for (int lz = 0; lz < Level4Plan.SIZE; lz++) {
							int x = rx * Level4Plan.SIZE + lx, z = rz * Level4Plan.SIZE + lz;
							Tile t = Level4Layout.tile(seed, x, z);
							if (t.kind() == Kind.CUBICLE_WALL) sawCubicleWall = true;
							if (t.kind() == Kind.CUBICLE_GLASS) sawCubicleGlass = true;
							if (t.kind() == Kind.UNFINISHED_STUB) sawStub = true;
							Level4Layout.BridgeInfo bi = Level4Layout.skybridgeInfo(seed, x, z);
							if (bi != null && t.walkable()) {
								if (bi.elevated()) {
									sawElevatedBridge = true;
									assertTrue(bi.spanPos() >= 0 && bi.spanPos() <= 27,
											"expected bridge spanPos in 0..27, got " + bi.spanPos());
								} else {
									sawFlatBridge = true;
								}
							}
						}
					}
				}
			}
		}
		assertTrue(sawCubicleWall && sawCubicleGlass, "expected cubicle partition mazes");
		assertTrue(sawUnfinished && sawStub, "expected unfinished concrete wing");
		assertTrue(sawElevatedBridge && sawFlatBridge, "expected both elevated and flat skybridges");
	}

	@Test
	void batch2StructuresLightwellAndRainBalconiesAppear() {
		boolean sawLightwell = false, sawShaft = false;
		boolean sawBalconyFloor = false, sawBalconyRail = false, sawBalconyCanopy = false;
		for (long seed = 10; seed <= 16; seed++) {
			for (int rx = -1; rx <= 1; rx++) {
				for (int rz = -1; rz <= 1; rz++) {
					Level4Plan plan = Level4Plan.of(seed, rx, rz);
					for (ZoneKind z : plan.zones) {
						if (z == ZoneKind.LIGHTWELL) sawLightwell = true;
					}
					for (int lx = 0; lx < Level4Plan.SIZE; lx++) {
						for (int lz = 0; lz < Level4Plan.SIZE; lz++) {
							int x = rx * Level4Plan.SIZE + lx, z = rz * Level4Plan.SIZE + lz;
							Tile t = Level4Layout.tile(seed, x, z);
							if (Level4Layout.isLightwellShaft(seed, x, z)) sawShaft = true;
							if (t.kind() == Kind.BALCONY_FLOOR) sawBalconyFloor = true;
							if (t.kind() == Kind.BALCONY_RAIL) sawBalconyRail = true;
							if (t.kind() == Kind.BALCONY_CANOPY) sawBalconyCanopy = true;
						}
					}
				}
			}
		}
		assertTrue(sawLightwell && sawShaft, "expected open-sky central lightwell shaft");
		assertTrue(sawBalconyFloor && sawBalconyRail && sawBalconyCanopy, "expected recessed rain balconies");
	}

	@Test
	void batch3StructuresElevatorShaftsAndAuditoriumAppear() {
		boolean sawElevatorLobby = false, sawShaft = false, sawCable = false, sawThreshold = false;
		boolean sawAuditorium = false, sawSunkenStage = false, sawStair = false, sawScreen = false;
		for (long seed = 10; seed <= 16; seed++) {
			for (int rx = -1; rx <= 1; rx++) {
				for (int rz = -1; rz <= 1; rz++) {
					Level4Plan plan = Level4Plan.of(seed, rx, rz);
					for (ZoneKind z : plan.zones) {
						if (z == ZoneKind.ELEVATOR_LOBBY) sawElevatorLobby = true;
						if (z == ZoneKind.AUDITORIUM) sawAuditorium = true;
					}
					for (int lx = 0; lx < Level4Plan.SIZE; lx++) {
						for (int lz = 0; lz < Level4Plan.SIZE; lz++) {
							int x = rx * Level4Plan.SIZE + lx, z = rz * Level4Plan.SIZE + lz;
							Tile t = Level4Layout.tile(seed, x, z);
							if (t.kind() == Kind.ELEVATOR_SHAFT) sawShaft = true;
							if (t.kind() == Kind.ELEVATOR_CABLE) sawCable = true;
							if (t.kind() == Kind.ELEVATOR_THRESHOLD) sawThreshold = true;
							Level4Layout.AuditoriumInfo aud = Level4Layout.auditoriumInfo(seed, x, z);
							if (aud != null) {
								if (aud.floorY() == Level4Layout.FLOOR_Y - 3) sawSunkenStage = true;
								if (aud.stair()) sawStair = true;
								if (aud.screenWall()) sawScreen = true;
							}
						}
					}
				}
			}
		}
		assertTrue(sawElevatorLobby && sawShaft && sawCable && sawThreshold, "expected elevator lobby with open shafts");
		assertTrue(sawAuditorium && sawSunkenStage && sawStair && sawScreen, "expected sunken tiered auditorium");
	}
}
