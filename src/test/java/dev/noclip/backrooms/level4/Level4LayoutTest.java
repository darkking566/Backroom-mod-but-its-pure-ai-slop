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
			for (int rx = -4; rx <= 4; rx++) {
				for (int rz = -4; rz <= 4; rz++) {
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
			for (int rx = -4; rx <= 4; rx++) {
				for (int rz = -4; rz <= 4; rz++) {
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
			for (int rx = -2; rx <= 2; rx++) {
				for (int rz = -2; rz <= 2; rz++) {
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
			for (int rx = -4; rx <= 4; rx++) {
				for (int rz = -4; rz <= 4; rz++) {
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

	@Test
	void batch4StructuresMezzaninePlenumAndSubTunnelAppear() {
		boolean sawMezzanine = false, sawMezzStair = false, sawMezzOverlook = false;
		boolean sawPlenum = false, sawLadder = false, sawDuct = false;
		boolean sawTunnel = false, sawTunnelStair = false, sawTunnelCorridor = false;
		for (long seed = 10; seed <= 16; seed++) {
			for (int rx = -1; rx <= 1; rx++) {
				for (int rz = -1; rz <= 1; rz++) {
					Level4Plan plan = Level4Plan.of(seed, rx, rz);
					for (ZoneKind z : plan.zones) {
						if (z == ZoneKind.MEZZANINE) sawMezzanine = true;
					}
					if (plan.tunnelJ >= 0) sawTunnel = true;
					for (int lx = 0; lx < Level4Plan.SIZE; lx++) {
						for (int lz = 0; lz < Level4Plan.SIZE; lz++) {
							int x = rx * Level4Plan.SIZE + lx, z = rz * Level4Plan.SIZE + lz;
							Level4Layout.MezzanineInfo mezz = Level4Layout.mezzanineInfo(seed, x, z);
							if (mezz != null) {
								if (mezz.stairStep() > 0) sawMezzStair = true;
								if (mezz.overlookOpen()) sawMezzOverlook = true;
							}
							Level4Layout.PlenumInfo plenum = Level4Layout.plenumInfo(seed, x, z);
							if (plenum != null) {
								sawPlenum = true;
								if (plenum.ladderDir() >= 0) sawLadder = true;
								if (plenum.duct()) sawDuct = true;
							}
							Level4Layout.SubTunnelInfo sub = Level4Layout.subTunnelInfo(seed, x, z);
							if (sub != null) {
								if (sub.stair()) sawTunnelStair = true;
								if (sub.corridor()) sawTunnelCorridor = true;
							}
						}
					}
				}
			}
		}
		assertTrue(sawMezzanine && sawMezzStair && sawMezzOverlook, "expected two-story mezzanine with stair and overlook");
		assertTrue(sawPlenum && sawLadder && sawDuct, "expected ceiling plenum crawlway above dark wing");
		assertTrue(sawTunnel && sawTunnelStair && sawTunnelCorridor, "expected connected sub-level MEP tunnel");
	}

	@Test
	void factionBasesAreRareAndMutuallyExclusive() {
		int omegaCount = 0, amorCount = 0, tbdCount = 0;
		for (int rx = 0; rx < 16; rx++) {
			for (int rz = 0; rz < 16; rz++) {
				Level4Plan plan = Level4Plan.of(42L, rx, rz);
				int basesInRegion = (plan.hasOmega ? 1 : 0) + (plan.hasAmor ? 1 : 0) + (plan.hasTbd ? 1 : 0);
				assertTrue(basesInRegion <= 1, "expected at most one faction base per region at " + rx + "," + rz);
				if (plan.hasOmega) omegaCount++;
				if (plan.hasAmor) amorCount++;
				if (plan.hasTbd) tbdCount++;
				assertTrue(Level4Events.nearestOmega(42L, rx * Level4Plan.SIZE, rz * Level4Plan.SIZE) != null,
						"expected nearestOmega to find a base from " + rx + "," + rz);
			}
		}
		assertEquals(4, omegaCount, "expected exactly 1 Omega base per 8x8 super-block across 16x16 regions");
		assertTrue(amorCount >= 1 && amorCount <= 20, "expected rare Amor camps (~3%), got " + amorCount);
		assertTrue(tbdCount >= 1 && tbdCount <= 24, "expected rare T.B.D. rooms (~4%), got " + tbdCount);
	}

	@Test
	void atriumZeroMegastructureIsRareConnectedAndSafe() {
		long testSeed = 42L;
		int count = 0;
		int foundRx = 0, foundRz = 0;
		for (int rx = 0; rx < 24; rx++) {
			for (int rz = 0; rz < 24; rz++) {
				Level4Plan plan = Level4Plan.of(testSeed, rx, rz);
				if (!plan.hasAtriumZero) continue;
				count++;
				foundRx = rx;
				foundRz = rz;
				assertTrue(Math.abs(rx) > 1 || Math.abs(rz) > 1, "Atrium Zero must avoid origin radius 1");
				assertTrue(!plan.hasOmega && !plan.hasAmor && !plan.hasTbd, "Atrium Zero must be mutually exclusive with faction bases");
				assertTrue(Level4PoiPlan.of(testSeed, rx, rz).pois.isEmpty(), "Atrium Zero must not generate room POIs");

				// Verify full BFS connectivity across all walkable cells including all 8 skybridges
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
				assertEquals(walkableCount, tail, "Atrium Zero region must be 100% connected");
			}
		}
		assertEquals(4, count, "expected exactly 1 Atrium Zero per 12x12 block across 24x24 regions");

		// Verify safety and 3D megastructure geometry in the found Atrium Zero region
		int baseX = foundRx * Level4Plan.SIZE;
		int baseZ = foundRz * Level4Plan.SIZE;
		assertTrue(!Level4Wanderers.isWanderZone(ZoneKind.ATRIUM_ZERO));
		for (int lx = 0; lx < Level4Plan.SIZE; lx++) {
			for (int lz = 0; lz < Level4Plan.SIZE; lz++) {
				Tile t = Level4Layout.tile(testSeed, baseX + lx, baseZ + lz);
				assertTrue(t.kind() != Kind.WINDOW_TRAP, "Atrium Zero must never generate Entity 2 trap windows");
			}
		}

		// Open-sky roof & deep abyssal pool at shaft interior
		Tile midShaftTile = Level4Layout.tile(testSeed, baseX + 35, baseZ + 35);
		assertEquals(Level4AtriumZero.Part.AIR, Level4AtriumZero.partAt(testSeed, 35, 127, 35, midShaftTile));
		assertEquals(Level4AtriumZero.Part.AIR, Level4AtriumZero.partAt(testSeed, 35, -62, 35, midShaftTile));
		assertEquals(Level4AtriumZero.Part.ABYSS_WATER, Level4AtriumZero.partAt(testSeed, 35, -63, 35, midShaftTile));

		// North rain bridge connects perimeter ring (lz=21) to central octagonal basin (lz=44)
		for (int lz = 21; lz <= 44; lz++) {
			Tile bt = Level4Layout.tile(testSeed, baseX + Level4AtriumZero.CENTER, baseZ + lz);
			Level4AtriumZero.Part floor = Level4AtriumZero.partAt(testSeed, Level4AtriumZero.CENTER, 32, lz, bt);
			assertTrue(floor == Level4AtriumZero.Part.GLASS_WALL || floor == Level4AtriumZero.Part.POLISHED_ANDESITE,
					"Expected continuous bridge floor at lz=" + lz + ", got " + floor);
			assertEquals(Level4AtriumZero.Part.AIR,
					Level4AtriumZero.partAt(testSeed, Level4AtriumZero.CENTER, 33, lz, bt));
		}

		// Central basin features: observation plaque, Almond Water pool, skyfall tower, and 4 abyss waterfalls
		Tile centerTile = Level4Layout.tile(testSeed, baseX + Level4AtriumZero.CENTER, baseZ + Level4AtriumZero.CENTER);
		assertEquals(Level4AtriumZero.Part.POLISHED_ANDESITE,
				Level4AtriumZero.partAt(testSeed, Level4AtriumZero.CENTER, 32, Level4AtriumZero.CENTER, centerTile));
		assertEquals(Level4AtriumZero.Part.ATRIUM_PLAQUE,
				Level4AtriumZero.partAt(testSeed, Level4AtriumZero.CENTER, 33, Level4AtriumZero.CENTER, centerTile));
		Tile poolTile = Level4Layout.tile(testSeed, baseX + Level4AtriumZero.CENTER + 7, baseZ + Level4AtriumZero.CENTER + 6);
		assertEquals(Level4AtriumZero.Part.ALMOND_POOL,
				Level4AtriumZero.partAt(testSeed, Level4AtriumZero.CENTER + 7, 32, Level4AtriumZero.CENTER + 6, poolTile));
		Tile poolRimTile = Level4Layout.tile(testSeed, baseX + Level4AtriumZero.CENTER + 7, baseZ + Level4AtriumZero.CENTER + 7);
		assertEquals(Level4AtriumZero.Part.ALMOND_BASIN,
				Level4AtriumZero.partAt(testSeed, Level4AtriumZero.CENTER + 7, 32, Level4AtriumZero.CENTER + 7, poolRimTile));
		Tile towerFallTile = Level4Layout.tile(testSeed, baseX + Level4AtriumZero.CENTER + 5, baseZ + Level4AtriumZero.CENTER + 2);
		assertEquals(Level4AtriumZero.Part.WATERFALL,
				Level4AtriumZero.partAt(testSeed, Level4AtriumZero.CENTER + 5, 82, Level4AtriumZero.CENTER + 2, towerFallTile));
		Tile abyssFallTile = Level4Layout.tile(testSeed, baseX + Level4AtriumZero.CENTER + 10, baseZ + Level4AtriumZero.CENTER + 9);
		assertEquals(Level4AtriumZero.Part.WATERFALL,
				Level4AtriumZero.partAt(testSeed, Level4AtriumZero.CENTER + 10, 0, Level4AtriumZero.CENTER + 9, abyssFallTile));

		// Multi-story ghost office curtain wall both high above (y=60) and deep below (y=-10)
		Tile rimWallTile = Level4Layout.tile(testSeed, baseX + 21, baseZ + Level4AtriumZero.CENTER + 1);
		assertEquals(Level4AtriumZero.Part.RAIN_WINDOW, Level4AtriumZero.partAt(testSeed, 21, 60, Level4AtriumZero.CENTER + 1, rimWallTile));
		assertEquals(Level4AtriumZero.Part.RAIN_WINDOW, Level4AtriumZero.partAt(testSeed, 21, -10, Level4AtriumZero.CENTER + 1, rimWallTile));

		// Skybridge doorway & outer perimeter ring stay open and carpeted
		Level4Plan foundPlan = Level4Plan.of(testSeed, foundRx, foundRz);
		int bridgeJ = -1;
		for (int j = Level4Plan.MARGIN; j < Level4Plan.N - Level4Plan.MARGIN; j++) {
			if (foundPlan.zones[Level4Plan.cell(1, j)] == ZoneKind.OPEN_OFFICE) {
				bridgeJ = j;
				break;
			}
		}
		assertTrue(bridgeJ >= 0, "expected a west skybridge connecting to Atrium Zero");
		int bridgeLz = bridgeJ * Level4Plan.CELL + 3;
		Tile bridgeDoorTile = Level4Layout.tile(testSeed, baseX + 14, baseZ + bridgeLz);
		assertEquals(Kind.AIR, bridgeDoorTile.kind());
		assertEquals(Level4AtriumZero.Part.CARPET, Level4AtriumZero.partAt(testSeed, 14, 32, bridgeLz, bridgeDoorTile));
		for (int y = 33; y <= 36; y++) {
			assertEquals(Level4AtriumZero.Part.AIR, Level4AtriumZero.partAt(testSeed, 14, y, bridgeLz, bridgeDoorTile));
		}
		Tile eastRingTile = Level4Layout.tile(testSeed, baseX + 97, baseZ + Level4AtriumZero.CENTER);
		assertEquals(Level4AtriumZero.Part.CARPET, Level4AtriumZero.partAt(testSeed, 97, 32, Level4AtriumZero.CENTER, eastRingTile));
		assertEquals(Level4AtriumZero.Part.AIR, Level4AtriumZero.partAt(testSeed, 97, 33, Level4AtriumZero.CENTER, eastRingTile));

		// Stage 2: Upper y=48 observation gallery & NE survey camp
		assertEquals(Level4AtriumZero.Part.CARPET, Level4AtriumZero.partAt(testSeed, 97, 48, Level4AtriumZero.CENTER, eastRingTile));
		assertEquals(Level4AtriumZero.Part.AIR, Level4AtriumZero.partAt(testSeed, 97, 49, Level4AtriumZero.CENTER, eastRingTile));
		assertEquals(Level4AtriumZero.Part.GLASS_WALL, Level4AtriumZero.partAt(testSeed, 21, 49, Level4AtriumZero.CENTER + 1, rimWallTile));
		Tile campTile = Level4Layout.tile(testSeed, baseX + 95, baseZ + 16);
		assertEquals(Level4AtriumZero.Part.SUPPLY_CRATE_SOUTH, Level4AtriumZero.partAt(testSeed, 95, 49, 16, campTile));
		assertEquals(Level4AtriumZero.Part.STEEL_SAFE_SOUTH, Level4AtriumZero.partAt(testSeed, 96, 49, 16, campTile));
		assertEquals(Level4AtriumZero.Part.SLEEPING_BAG_WEST, Level4AtriumZero.partAt(testSeed, 96, 49, 18, campTile));

		// Stage 2: NW & SE 5x5 spiral stairs (all 16 steps y=32..48 clear headroom) + flat bypasses
		int[][] nwPath = {
				{15, 19, 32}, {16, 19, 33}, {17, 19, 34}, {18, 19, 35},
				{19, 19, 36}, {19, 18, 37}, {19, 17, 38}, {19, 16, 39},
				{19, 15, 40}, {18, 15, 41}, {17, 15, 42}, {16, 15, 43},
				{15, 15, 44}, {15, 16, 45}, {15, 17, 46}, {15, 18, 47},
				{15, 19, 48}
		};
		for (int idx = 0; idx < nwPath.length; idx++) {
			int slx = nwPath[idx][0], slz = nwPath[idx][1], sy = nwPath[idx][2];
			Tile st = Level4Layout.tile(testSeed, baseX + slx, baseZ + slz);
			Level4AtriumZero.Part stepPart = Level4AtriumZero.partAt(testSeed, slx, sy, slz, st);
			assertTrue(stepPart != Level4AtriumZero.Part.AIR, "NW spiral step missing at idx=" + idx);
			if (idx > 0 && idx < nwPath.length - 1) {
				assertEquals(Level4AtriumZero.Part.AIR, Level4AtriumZero.partAt(testSeed, slx, sy + 1, slz, st));
				assertEquals(Level4AtriumZero.Part.AIR, Level4AtriumZero.partAt(testSeed, slx, sy + 2, slz, st));
			}
		}
		int[][] sePath = {
				{97, 93, 32}, {96, 93, 33}, {95, 93, 34}, {94, 93, 35},
				{93, 93, 36}, {93, 94, 37}, {93, 95, 38}, {93, 96, 39},
				{93, 97, 40}, {94, 97, 41}, {95, 97, 42}, {96, 97, 43},
				{97, 97, 44}, {97, 96, 45}, {97, 95, 46}, {97, 94, 47},
				{97, 93, 48}
		};
		for (int idx = 0; idx < sePath.length; idx++) {
			int slx = sePath[idx][0], slz = sePath[idx][1], sy = sePath[idx][2];
			Tile st = Level4Layout.tile(testSeed, baseX + slx, baseZ + slz);
			Level4AtriumZero.Part stepPart = Level4AtriumZero.partAt(testSeed, slx, sy, slz, st);
			assertTrue(stepPart != Level4AtriumZero.Part.AIR, "SE spiral step missing at idx=" + idx);
			if (idx > 0 && idx < sePath.length - 1) {
				assertEquals(Level4AtriumZero.Part.AIR, Level4AtriumZero.partAt(testSeed, slx, sy + 1, slz, st));
				assertEquals(Level4AtriumZero.Part.AIR, Level4AtriumZero.partAt(testSeed, slx, sy + 2, slz, st));
			}
		}
		Tile nwBypass = Level4Layout.tile(testSeed, baseX + 20, baseZ + 20);
		assertEquals(Level4AtriumZero.Part.CARPET, Level4AtriumZero.partAt(testSeed, 20, 32, 20, nwBypass));
		assertEquals(Level4AtriumZero.Part.AIR, Level4AtriumZero.partAt(testSeed, 20, 33, 20, nwBypass));
		Tile seBypass = Level4Layout.tile(testSeed, baseX + 92, baseZ + 92);
		assertEquals(Level4AtriumZero.Part.CARPET, Level4AtriumZero.partAt(testSeed, 92, 48, 92, seBypass));
		assertEquals(Level4AtriumZero.Part.AIR, Level4AtriumZero.partAt(testSeed, 92, 49, 92, seBypass));

		// Stage 2: Lower y=18 catwalk, access ladder, safe, and permanent City Door (Level 11) exit
		Tile ladderTile = Level4Layout.tile(testSeed, baseX + 60, baseZ + 67);
		for (int y = 19; y <= 32; y++) {
			assertEquals(Level4AtriumZero.Part.LADDER_NORTH, Level4AtriumZero.partAt(testSeed, 60, y, 67, ladderTile));
		}
		for (int lz = 45; lz <= 66; lz++) {
			Tile cwTile = Level4Layout.tile(testSeed, baseX + 56, baseZ + lz);
			Level4AtriumZero.Part cwFloor = Level4AtriumZero.partAt(testSeed, 56, 18, lz, cwTile);
			assertTrue(cwFloor == Level4AtriumZero.Part.POLISHED_ANDESITE || cwFloor == Level4AtriumZero.Part.ELEVATOR_FRAME,
					"Lower catwalk spine broken at lz=" + lz);
		}
		Tile exitTile = Level4Layout.tile(testSeed, baseX + 56, baseZ + 44);
		assertEquals(Level4AtriumZero.Part.EXIT_DOOR_11_LOWER, Level4AtriumZero.partAt(testSeed, 56, 19, 44, exitTile));
		assertEquals(Level4AtriumZero.Part.EXIT_DOOR_11_UPPER, Level4AtriumZero.partAt(testSeed, 56, 20, 44, exitTile));
		Tile safeTile = Level4Layout.tile(testSeed, baseX + 58, baseZ + 46);
		assertEquals(Level4AtriumZero.Part.STEEL_SAFE_WEST, Level4AtriumZero.partAt(testSeed, 58, 19, 46, safeTile));

		// Stage 2: Four suspended observation elevators with glass walls, ceiling lights, and full cables
		int[][] elevators = {
				{34, 54, 34}, {78, 43, 34}, {34, 19, 78}, {78, 66, 78}
		};
		for (int[] ev : elevators) {
			int ex = ev[0], ey = ev[1], ez = ev[2];
			Tile evCenter = Level4Layout.tile(testSeed, baseX + ex, baseZ + ez);
			Tile evWall = Level4Layout.tile(testSeed, baseX + ex + 2, baseZ + ez);
			assertEquals(Level4AtriumZero.Part.GLASS_WALL, Level4AtriumZero.partAt(testSeed, ex + 2, ey + 2, ez, evWall));
			Level4AtriumZero.Part lightPart = Level4AtriumZero.partAt(testSeed, ex, ey + 4, ez, evCenter);
			assertTrue(lightPart == Level4AtriumZero.Part.TROFFER_LIT
					|| lightPart == Level4AtriumZero.Part.TROFFER_DIM
					|| lightPart == Level4AtriumZero.Part.TROFFER_FLICKER);
			assertEquals(Level4AtriumZero.Part.IRON_BARS, Level4AtriumZero.partAt(testSeed, ex, 100, ez, evCenter));
			assertEquals(Level4AtriumZero.Part.IRON_BARS, Level4AtriumZero.partAt(testSeed, ex, -20, ez, evCenter));
		}
	}

	@Test
	void walkableSkybridgeJunctionsToBuildingPerimeterStayOpen() {
		for (long seed = 1; seed <= 20; seed++) {
			for (int rx = -1; rx <= 1; rx++) {
				for (int rz = -1; rz <= 1; rz++) {
					Level4Plan plan = Level4Plan.of(seed, rx, rz);
					for (int k = Level4Plan.MARGIN; k < Level4Plan.N - Level4Plan.MARGIN; k++) {
						int[][] pairs = {
								{Level4Plan.MARGIN - 1, k, Level4Plan.EAST},
								{Level4Plan.N - Level4Plan.MARGIN, k, Level4Plan.WEST},
								{k, Level4Plan.MARGIN - 1, Level4Plan.SOUTH},
								{k, Level4Plan.N - Level4Plan.MARGIN, Level4Plan.NORTH}
						};
						for (int[] p : pairs) {
							int si = p[0], sj = p[1], dir = p[2];
							if (!plan.walkable(si, sj)) continue;
							int bi = si + Level4Plan.DX[dir], bj = sj + Level4Plan.DZ[dir];
							if (!plan.walkable(bi, bj)) continue;
							assertEquals(Edge.OPEN, plan.edge(Level4Plan.cell(si, sj), dir),
									"skybridge junction should be OPEN at seed=" + seed + " rx=" + rx + " rz=" + rz
											+ " (" + si + "," + sj + ") dir=" + dir);
						}
					}
				}
			}
		}
	}

	@Test
	void subTunnelOverheadPipesClearBothStairwellEntrances() {
		boolean checkedAnyTunnel = false;
		for (long seed = 1; seed <= 25; seed++) {
			for (int rx = -1; rx <= 1; rx++) {
				for (int rz = -1; rz <= 1; rz++) {
					Level4Plan plan = Level4Plan.of(seed, rx, rz);
					if (plan.tunnelJ < 0) continue;
					checkedAnyTunnel = true;
					int baseX = rx * Level4Plan.SIZE;
					int baseZ = rz * Level4Plan.SIZE;
					int[][] blockedCols = {
							{plan.tunnelI0 + 1, 0},
							{plan.tunnelI0 + 1, 1},
							{plan.tunnelI1 - 1, 6},
							{plan.tunnelI1, 0}
					};
					for (int[] col : blockedCols) {
						for (int cz = 2; cz <= 4; cz++) {
							int x = baseX + col[0] * Level4Plan.CELL + col[1];
							int z = baseZ + plan.tunnelJ * Level4Plan.CELL + cz;
							Level4Layout.SubTunnelInfo sub = Level4Layout.subTunnelInfo(seed, x, z);
							assertTrue(sub != null && sub.corridor(), "expected corridor at stair-adjacent col (" + col[0] + "," + col[1] + ")");
							assertTrue(!sub.pipeAllowed(), "overhead pipe must stay clear near stairwell bottom at (" + col[0] + "," + col[1] + ")");
						}
					}
					int midX = baseX + (plan.tunnelI0 + 2) * Level4Plan.CELL + 3;
					int midZ = baseZ + plan.tunnelJ * Level4Plan.CELL + 3;
					Level4Layout.SubTunnelInfo midSub = Level4Layout.subTunnelInfo(seed, midX, midZ);
					assertTrue(midSub != null && midSub.pipeAllowed(), "expected overhead pipes allowed in mid-tunnel corridor");
				}
			}
		}
		assertTrue(checkedAnyTunnel, "expected at least one sub-tunnel across tested seeds/regions");
	}
}
