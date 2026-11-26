package dev.noclip.backrooms.level0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

class MazeTest {
	private static final int SIZE = Maze.CELL * Maze.SECTOR * 2;

	@Test
	void everyOpenBlockIsReachable() {
		Random random = new Random(0);
		int[] origins = {0, -SIZE, -29_999_984 / SIZE * SIZE, 29_000_000 / SIZE * SIZE};

		for (int run = 0; run < 200; run++) {
			long seed = random.nextLong();
			int origin = origins[run % origins.length];
			int[] floorY = new int[SIZE * SIZE];
			boolean[] unvisited = new boolean[SIZE * SIZE];
			int open = 0;
			for (int i = 0; i < unvisited.length; i++) {
				int x = origin + i % SIZE, z = origin + i / SIZE;
				floorY[i] = Maze.walkableFloorY(seed, x, z, Maze.EpochSource.ZERO);
				unvisited[i] = floorY[i] != Integer.MIN_VALUE;
				if (unvisited[i]) open++;
			}

			ArrayDeque<Integer> queue = new ArrayDeque<>();
			int start = 0;
			while (start < unvisited.length && !unvisited[start]) start++;
			assertTrue(start < unvisited.length, "no walkable block in window, seed " + seed);
			unvisited[start] = false;
			queue.add(start);
			int reached = 0;
			while (!queue.isEmpty()) {
				int at = queue.poll(), x = at % SIZE, z = at / SIZE;
				int curY = floorY[at];
				reached++;
				int[] next = {x > 0 ? at - 1 : -1, x < SIZE - 1 ? at + 1 : -1, z > 0 ? at - SIZE : -1, z < SIZE - 1 ? at + SIZE : -1};
				for (int n : next) {
					if (n >= 0 && unvisited[n] && Math.abs(floorY[n] - curY) <= 1) {
						unvisited[n] = false;
						queue.add(n);
					}
				}
			}
			if (open != reached) {
				for (int i = 0; i < unvisited.length; i++) {
					if (unvisited[i]) {
						int ux = origin + i % SIZE, uz = origin + i / SIZE;
						assertEquals(open, reached, "unreachable area at (" + ux + ", " + uz + ") zone="
								+ Maze.zoneAt(seed, ux, uz) + " floorY=" + floorY[i] + ", seed " + seed);
					}
				}
			}
		}
	}

	@Test
	void landingSpotsAreNeverWalls() {
		for (int cx = -200; cx < 200; cx++) {
			for (int cz = -200; cz < 200; cz++) {
				assertFalse(Maze.isWall(42, cx * Maze.CELL + Maze.CENTER, cz * Maze.CELL + Maze.CENTER));
			}
		}
	}

	@Test
	void fiveBlockHeadroomAndWideThreePlusDoorways() {
		assertEquals(5, Level0ChunkGenerator.CEILING_Y - Level0ChunkGenerator.FLOOR_Y - 1,
				"Standard Level 0 headroom must be 5 clear blocks");
		assertTrue(Maze.CELL >= 10, "Cell scale must be >= 10 for wide corridors");
		long seed = 12345L;
		for (int cx = -20; cx <= 20; cx++) {
			for (int cz = -20; cz <= 20; cz++) {
				Maze.Zone zone = Maze.zoneAt(seed, cx * Maze.CELL + Maze.CENTER, cz * Maze.CELL + Maze.CENTER);
				if (zone == Maze.Zone.CHASM || zone == Maze.Zone.MEGATHALL) continue;
				if (ManilaRoomPlan.isProtectedCell(seed, cx, cz) || StaircasePlan.isProtectedCell(seed, cx, cz)) continue;
				// Check west doorway opening width (either closed 0 or >= 3 blocks wide)
				int run = 0;
				int minNonZeroRun = Integer.MAX_VALUE;
				for (int iz = 1; iz < Maze.CELL; iz++) {
					if (!Maze.isWall(seed, cx * Maze.CELL, cz * Maze.CELL + iz, false)) {
						run++;
					} else {
						if (run > 0) minNonZeroRun = Math.min(minNonZeroRun, run);
						run = 0;
					}
				}
				if (run > 0) minNonZeroRun = Math.min(minNonZeroRun, run);
				if (minNonZeroRun != Integer.MAX_VALUE) {
					assertTrue(minNonZeroRun >= 3, "cramped doorway width " + minNonZeroRun + " in " + zone);
				}
			}
		}
	}

	@Test
	void everyStageGuaranteesAllTenZonesAndMultiFormAnomalyExits() {
		long seed = 12345L;
		EnumSet<AnomalyExitPlan.EntryKind> allSeenExits = EnumSet.noneOf(AnomalyExitPlan.EntryKind.class);

		for (int stx = -2; stx <= 2; stx++) {
			for (int stz = -2; stz <= 2; stz++) {
				EnumSet<Maze.Zone> stageZones = EnumSet.noneOf(Maze.Zone.class);
				for (int lx = 0; lx < Maze.STAGE_SECTORS; lx++) {
					for (int lz = 0; lz < Maze.STAGE_SECTORS; lz++) {
						int sx = stx * Maze.STAGE_SECTORS + lx;
						int sz = stz * Maze.STAGE_SECTORS + lz;
						stageZones.add(Maze.baseZoneForSector(seed, sx, sz));
					}
				}
				assertEquals(EnumSet.allOf(Maze.Zone.class), stageZones,
						"Stage (" + stx + "," + stz + ") missing zones: " + stageZones);

				List<AnomalyExitPlan.Placement> exits = AnomalyExitPlan.placementsInStage(seed, stx, stz);
				assertTrue(exits.size() >= 6, "Stage (" + stx + "," + stz + ") has too few anomaly exits: " + exits.size());
				boolean hasOilVent = false, hasSteamGrate = false, hasPoster161 = false;
				for (AnomalyExitPlan.Placement p : exits) {
					allSeenExits.add(p.kind());
					if (p.kind() == AnomalyExitPlan.EntryKind.OIL_VENT_1) hasOilVent = true;
					if (p.kind() == AnomalyExitPlan.EntryKind.STEAM_GRATE_2) hasSteamGrate = true;
					if (p.kind() == AnomalyExitPlan.EntryKind.POSTER_161) hasPoster161 = true;
					assertFalse(ManilaRoomPlan.isProtectedCell(seed, p.cx(), p.cz()));
					assertFalse(StaircasePlan.isProtectedCell(seed, p.cx(), p.cz()));
				}
				assertTrue(hasOilVent, "Stage missing guaranteed Level 1 oil vent");
				assertTrue(hasSteamGrate, "Stage missing guaranteed Level 2 steam grate");
				assertTrue(hasPoster161, "Stage missing guaranteed Level 161 mountain poster");
			}
		}
		assertEquals(EnumSet.allOf(AnomalyExitPlan.EntryKind.class), allSeenExits,
				"Not all 13 anomaly exit forms generated across sample stages: " + allSeenExits);
	}

	@Test
	void pitfallsHaveLargeOpeningsEscapableUndercroftAndLadderShaft() {
		long seed = 12345L;
		int pitSx = Integer.MIN_VALUE, pitSz = Integer.MIN_VALUE;
		for (int sx = 0; sx < Maze.STAGE_SECTORS && pitSx == Integer.MIN_VALUE; sx++) {
			for (int sz = 0; sz < Maze.STAGE_SECTORS; sz++) {
				if (Maze.baseZoneForSector(seed, sx, sz) == Maze.Zone.PITS) {
					pitSx = sx;
					pitSz = sz;
					break;
				}
			}
		}
		assertTrue(pitSx != Integer.MIN_VALUE, "no PITS sector in origin stage");
		int baseCx = pitSx * Maze.SECTOR, baseCz = pitSz * Maze.SECTOR;
		int pitBlocks = 0;
		for (int lx = 0; lx < Maze.SECTOR; lx++) {
			for (int lz = 0; lz < Maze.SECTOR; lz++) {
				int ox = (baseCx + lx) * Maze.CELL, oz = (baseCz + lz) * Maze.CELL;
				for (int ix = 0; ix < Maze.CELL; ix++) {
					for (int iz = 0; iz < Maze.CELL; iz++) {
						if (Maze.isPit(seed, ox + ix, oz + iz)) {
							pitBlocks++;
							assertFalse(Maze.isAbyss(seed, ox + ix, oz + iz), "Pitfalls pit should not be lethal void abyss");
						}
					}
				}
			}
		}
		assertTrue(pitBlocks >= 90, "Expected large 3x3..5x3 pit openings in PITS sector, got " + pitBlocks);

		// Escape shaft cell at (SHAFT_LX, SHAFT_LZ) must have continuous ladder rungs from lower floor to upper floor
		int shaftCx = baseCx + PitfallsPlan.SHAFT_LX;
		int shaftCz = baseCz + PitfallsPlan.SHAFT_LZ;
		assertTrue(PitfallsPlan.isEscapeShaftOpening(shaftCx, shaftCz, 4, 4), "Escape shaft opening missing");
		assertFalse(Maze.isPitCell(seed, shaftCx, shaftCz), "Escape shaft cell must not also be a pit cell");
		for (int y = PitfallsPlan.LOWER_FLOOR_Y + 1; y <= Level0ChunkGenerator.FLOOR_Y; y++) {
			assertTrue(PitfallsPlan.isEscapeLadderAt(y, 4, 4), "Missing escape ladder rung at y=" + y);
		}
	}

	@Test
	void seedChangesTheFloorPlan() {
		int different = 0;
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				if (Maze.isWall(1, x, z) != Maze.isWall(2, x, z)) different++;
			}
		}
		assertTrue(different > SIZE);
	}

	@Test
	void allZoneVariantsGenerateWithExpectedTraits() {
		int sectorBlocks = Maze.CELL * Maze.SECTOR;
		EnumSet<Maze.Zone> seen = EnumSet.noneOf(Maze.Zone.class);
		boolean sawArchOverhang = false;
		boolean checkedPillarHall = false;
		boolean checkedDarkened = false;
		boolean sawPuddle = false;
		boolean sawPit = false;
		boolean sawRedWarningWall = false;
		boolean sawThickCarpet = false;

		assertEquals(Maze.Zone.CLASSIC, Maze.zoneAt(12345L, Maze.CENTER, Maze.CENTER));

		for (int sx = -5; sx <= 5; sx++) {
			for (int sz = -5; sz <= 5; sz++) {
				int ox = sx * sectorBlocks;
				int oz = sz * sectorBlocks;
				Maze.Zone zone = Maze.zoneAt(12345L, ox + Maze.CENTER, oz + Maze.CENTER);
				seen.add(zone);

				if (zone == Maze.Zone.DARKENED) {
					checkedDarkened = true;
					for (int x = ox; x < ox + sectorBlocks; x++) {
						for (int z = oz; z < oz + sectorBlocks; z++) {
							assertFalse(Maze.lampWorks(12345L, x, z), "darkened zone had a lit lamp");
							if (Maze.isPuddle(12345L, x, z)) {
								sawPuddle = true;
								assertFalse(Maze.isWall(12345L, x, z), "puddle overlapped a wall");
							}
						}
					}
				} else if (zone == Maze.Zone.PILLARS) {
					checkedPillarHall = true;
					for (int cx = 0; cx < Maze.SECTOR; cx++) {
						for (int cz = 0; cz < Maze.SECTOR; cz++) {
							assertTrue(Maze.isWall(12345L, ox + cx * Maze.CELL, oz + cz * Maze.CELL),
									"pillar hall missing cruciform corner pillar");
						}
					}
				} else if (zone == Maze.Zone.ARCHES) {
					for (int x = ox; x < ox + sectorBlocks; x++) {
						for (int z = oz; z < oz + sectorBlocks; z++) {
							if (!Maze.isWall(12345L, x, z, false) && Maze.isWall(12345L, x, z, true)) {
								sawArchOverhang = true;
							}
							if (Maze.isThickCarpet(12345L, x, z)) sawThickCarpet = true;
						}
					}
				} else if (zone == Maze.Zone.PITS) {
					for (int x = ox; x < ox + sectorBlocks; x++) {
						for (int z = oz; z < oz + sectorBlocks; z++) {
							if (Maze.isPit(12345L, x, z)) {
								sawPit = true;
								assertFalse(Maze.isWall(12345L, x, z), "pit overlapped a wall");
							}
						}
					}
				}

				if (zone != Maze.Zone.RED_ROOM) {
					for (int x = ox; x < ox + sectorBlocks; x++) {
						for (int z = oz; z < oz + sectorBlocks; z++) {
							if (Maze.isWall(12345L, x, z) && Maze.isRedWarningWall(12345L, x, z)) {
								sawRedWarningWall = true;
							}
						}
					}
				}
			}
		}

		assertEquals(EnumSet.allOf(Maze.Zone.class), seen, "not all zone variants generated");
		assertTrue(checkedDarkened, "never verified darkened zone");
		assertTrue(checkedPillarHall, "never verified pillar hall");
		assertTrue(sawArchOverhang, "arches zone never produced an arch overhang");
		assertTrue(sawPuddle, "darkened zone never produced a sunken puddle");
		assertTrue(sawPit, "pits zone never produced a pit");
		assertTrue(sawRedWarningWall, "never saw peeling red warning wall beside a red room");
		assertTrue(sawThickCarpet, "never saw thick carpet");
	}

	@Test
	void everyOpenBlockRemainsReachableUnderRandomEdgeShifts() {
		Random random = new Random(99);
		int[] origins = {0, -SIZE};

		for (int run = 0; run < 120; run++) {
			long seed = random.nextLong();
			int origin = origins[run % origins.length];
			Maze.EpochSource epochs = (cx, cz) -> {
				long mix = seed ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL);
				return Math.floorMod(mix, 6);
			};

			int[] floorY = new int[SIZE * SIZE];
			boolean[] unvisited = new boolean[SIZE * SIZE];
			int open = 0;
			for (int i = 0; i < unvisited.length; i++) {
				int x = origin + i % SIZE, z = origin + i / SIZE;
				floorY[i] = Maze.walkableFloorY(seed, x, z, epochs);
				unvisited[i] = floorY[i] != Integer.MIN_VALUE;
				if (unvisited[i]) open++;
			}

			ArrayDeque<Integer> queue = new ArrayDeque<>();
			int start = 0;
			while (start < unvisited.length && !unvisited[start]) start++;
			assertTrue(start < unvisited.length, "no walkable block in window, seed " + seed);
			unvisited[start] = false;
			queue.add(start);
			int reached = 0;
			while (!queue.isEmpty()) {
				int at = queue.poll(), x = at % SIZE, z = at / SIZE;
				int curY = floorY[at];
				reached++;
				int[] next = {x > 0 ? at - 1 : -1, x < SIZE - 1 ? at + 1 : -1, z > 0 ? at - SIZE : -1, z < SIZE - 1 ? at + SIZE : -1};
				for (int n : next) {
					if (n >= 0 && unvisited[n] && Math.abs(floorY[n] - curY) <= 1) {
						unvisited[n] = false;
						queue.add(n);
					}
				}
			}
			if (open != reached) {
				for (int i = 0; i < unvisited.length; i++) {
					if (unvisited[i]) {
						int ux = origin + i % SIZE, uz = origin + i / SIZE;
						assertEquals(open, reached, "unreachable area after edge shifts at (" + ux + ", " + uz
								+ ") zone=" + Maze.zoneAt(seed, ux, uz) + " floorY=" + floorY[i] + ", seed " + seed);
					}
				}
			}
		}
	}

	@Test
	void archesStayImmutableWhilePillarsLoseSymmetryWhenShifted() {
		long seed = 12345L;
		int sectorBlocks = Maze.CELL * Maze.SECTOR;
		boolean checkedArches = false;
		boolean sawShiftedPillarDiff = false;
		Maze.EpochSource shifted = (cx, cz) -> 3;

		for (int sx = -5; sx <= 5; sx++) {
			for (int sz = -5; sz <= 5; sz++) {
				int ox = sx * sectorBlocks, oz = sz * sectorBlocks;
				Maze.Zone zone = Maze.zoneAt(seed, ox + Maze.CENTER, oz + Maze.CENTER);
				if (zone == Maze.Zone.ARCHES) {
					checkedArches = true;
					assertEquals(0, Maze.shiftWeight(zone));
					for (int x = ox + Maze.CELL; x < ox + sectorBlocks - Maze.CELL; x++) {
						for (int z = oz + Maze.CELL; z < oz + sectorBlocks - Maze.CELL; z++) {
							assertEquals(Maze.isWall(seed, x, z, false), Maze.isWall(seed, x, z, false, shifted));
							assertEquals(Maze.isWall(seed, x, z, true), Maze.isWall(seed, x, z, true, shifted));
						}
					}
				} else if (zone == Maze.Zone.PILLARS) {
					for (int x = ox; x < ox + sectorBlocks; x++) {
						for (int z = oz; z < oz + sectorBlocks; z++) {
							if (Maze.isWall(seed, x, z, false) != Maze.isWall(seed, x, z, false, shifted)) {
								sawShiftedPillarDiff = true;
							}
						}
					}
				}
			}
		}

		assertTrue(checkedArches, "never found ARCHES sector");
		assertTrue(sawShiftedPillarDiff, "shifted PILLARS sector did not change layout");
	}

	@Test
	void edgeShiftObservationGeometryChecksAllCorners() {
		assertFalse(EdgeShift.isOutOfObservation(0.5, 0.5, 0.0, 1.0, 0, 21));
		assertFalse(EdgeShift.isOutOfObservation(0.5, 0.5, 0.0, 1.0, 14, 14));
		assertTrue(EdgeShift.isOutOfObservation(0.5, 0.5, 0.0, 1.0, -3, -28));
		assertTrue(EdgeShift.isOutOfObservation(0.5, 0.5, 0.0, 1.0, 0, -28));
		assertFalse(EdgeShift.isOutOfObservation(0.5, 0.5, 0.0, 1.0, 14, -2));
	}

	@Test
	void stairwellsAndCruciformPillarsGenerateCleanly() {
		long seed = 12345L;
		EnumSet<net.minecraft.core.Direction> seenStairDirs = EnumSet.noneOf(net.minecraft.core.Direction.class);
		boolean sawCruciformPillar = false;

		for (int cx = -60; cx < 60; cx++) {
			for (int cz = -60; cz < 60; cz++) {
				int dir = Maze.stairwellDir(seed, cx, cz);
				if (dir >= 0) {
					seenStairDirs.add(switch (dir) {
						case 0 -> net.minecraft.core.Direction.EAST;
						case 1 -> net.minecraft.core.Direction.WEST;
						case 2 -> net.minecraft.core.Direction.SOUTH;
						default -> net.minecraft.core.Direction.NORTH;
					});
					Maze.Zone zone = Maze.zoneAt(seed, cx * Maze.CELL, cz * Maze.CELL);
					assertTrue(zone == Maze.Zone.CLASSIC || zone == Maze.Zone.CORRIDORS,
							"unexpected zone for stairwell: " + zone);
					assertFalse(Maze.isWall(seed, cx * Maze.CELL + Maze.CENTER, cz * Maze.CELL + Maze.CENTER));
				}
				int ox = cx * Maze.CELL, oz = cz * Maze.CELL;
				if (Maze.zoneAt(seed, ox + Maze.CENTER, oz + Maze.CENTER) == Maze.Zone.PILLARS
						&& Maze.zoneAt(seed, ox - Maze.CENTER, oz + Maze.CENTER) == Maze.Zone.PILLARS
						&& Maze.zoneAt(seed, ox + Maze.CENTER, oz - Maze.CENTER) == Maze.Zone.PILLARS) {
					if (Maze.isWall(seed, ox, oz) && Maze.isWall(seed, ox, oz + 1) && Maze.isWall(seed, ox + 1, oz)) {
						sawCruciformPillar = true;
					}
				}
			}
		}

		assertEquals(4, seenStairDirs.size(), "expected all 4 stairwell directions, saw " + seenStairDirs);
		assertTrue(sawCruciformPillar, "never verified Kane Pixels cruciform pillar");
	}

	@Test
	void newRoomAndCeilingVariantsGenerateCleanly() {
		long seed = 12345L;
		EnumSet<Maze.CeilingStyle> seenCeilings = EnumSet.noneOf(Maze.CeilingStyle.class);
		EnumSet<Maze.FloorAccent> seenFloors = EnumSet.noneOf(Maze.FloorAccent.class);
		EnumSet<Maze.RoomLayout> seenLayouts = EnumSet.noneOf(Maze.RoomLayout.class);
		boolean sawHalfWall = false;
		boolean sawFramedDoor = false;
		boolean sawSunken = false;

		for (int cx = -60; cx < 60; cx++) {
			for (int cz = -60; cz < 60; cz++) {
				Maze.Zone zone = Maze.zoneAt(seed, cx * Maze.CELL, cz * Maze.CELL);
				seenCeilings.add(Maze.ceilingStyle(seed, cx, cz));
				seenFloors.add(Maze.floorAccent(seed, cx, cz));
				seenLayouts.add(Maze.roomLayout(seed, cx, cz, zone, 0));

				if (Maze.isSunkenCell(seed, cx, cz)) {
					sawSunken = true;
					assertFalse(Maze.isStairwellCell(seed, cx, cz));
					assertFalse(Maze.isWall(seed, cx * Maze.CELL + Maze.CENTER, cz * Maze.CELL + Maze.CENTER));
				}

				int ox = cx * Maze.CELL, oz = cz * Maze.CELL;
				for (int i = 1; i < Maze.CELL; i++) {
					if (!Maze.isWall(seed, ox, oz + i, false) && Maze.isWall(seed, ox, oz + i, true)
							&& zone != Maze.Zone.ARCHES) {
						sawFramedDoor = true;
					}
					for (int j = 1; j < Maze.CELL; j++) {
						if (Maze.isWall(seed, ox + i, oz + j, false) && !Maze.isWall(seed, ox + i, oz + j, true)) {
							sawHalfWall = true;
						}
					}
				}
			}
		}

		assertEquals(EnumSet.allOf(Maze.CeilingStyle.class), seenCeilings, "missing ceiling styles");
		assertEquals(EnumSet.allOf(Maze.FloorAccent.class), seenFloors, "missing floor accents");
		assertTrue(seenLayouts.size() >= 10, "too few room layouts seen: " + seenLayouts);
		assertTrue(sawHalfWall, "never generated a half-height cubicle partition");
		assertTrue(sawFramedDoor, "never generated a framed doorway");
		assertTrue(sawSunken, "never generated a sunken split-level room");
	}

	@Test
	void redRoomAndDarkenedZonesSpanSectorsWithInteriorLoopRings() {
		long seed = 12345L;
		int sectorBlocks = Maze.CELL * Maze.SECTOR;
		boolean sawDarkened = false;
		boolean sawRedRoom = false;

		for (int sx = -5; sx <= 5; sx++) {
			for (int sz = -5; sz <= 5; sz++) {
				Maze.Zone zone = Maze.zoneAt(seed, sx * sectorBlocks + Maze.CENTER, sz * sectorBlocks + Maze.CENTER);
				if (zone == Maze.Zone.DARKENED) sawDarkened = true;
				if (zone == Maze.Zone.RED_ROOM) {
					sawRedRoom = true;
					int baseCx = sx * Maze.SECTOR;
					int baseCz = sz * Maze.SECTOR;
					assertTrue(Maze.isRedRoomInteriorCell(seed, baseCx + 1, baseCz + 1));
					assertTrue(Maze.isRedRoomInteriorCell(seed, baseCx + 3, baseCz + 3));
					if (Maze.zoneAt(seed, (sx - 1) * sectorBlocks + Maze.CENTER, sz * sectorBlocks + Maze.CENTER) != Maze.Zone.RED_ROOM) {
						assertFalse(Maze.isRedRoomInteriorCell(seed, baseCx, baseCz + 1));
					}
				}
			}
		}

		assertTrue(sawDarkened, "never verified darkened zone");
		assertTrue(sawRedRoom, "never verified red room zone");
	}

	@Test
	void chasmMegahallAndAtriumGenerateMultiScaleDepths() {
		long seed = 12345L;
		int minChasmFloor = Integer.MAX_VALUE;
		int maxMegaCeil = 0;
		boolean sawChasmAbyss = false;
		boolean sawChasmStair = false;
		boolean sawChasmBuriedRoom = false;
		boolean sawMegaRaisedOrSunken = false;
		boolean sawAtrium = false;

		for (int cx = -60; cx <= 60; cx++) {
			for (int cz = -60; cz <= 60; cz++) {
				int ox = cx * Maze.CELL, oz = cz * Maze.CELL;
				Maze.Zone zone = Maze.zoneAt(seed, ox + Maze.CENTER, oz + Maze.CENTER);
				int ceilY = Maze.cellCeilingY(seed, cx, cz);

				if (zone == Maze.Zone.CHASM) {
					if (ceilY <= Level0ChunkGenerator.FLOOR_Y + 6 && ChasmPlan.floorYAt(seed, cx, cz, 5, 5) < Level0ChunkGenerator.FLOOR_Y) {
						sawChasmBuriedRoom = true;
					}
					for (int ix = 0; ix < Maze.CELL; ix++) {
						for (int iz = 0; iz < Maze.CELL; iz++) {
							int fy = Maze.walkableFloorY(seed, ox + ix, oz + iz, Maze.EpochSource.ZERO);
							if (fy != Integer.MIN_VALUE) {
								minChasmFloor = Math.min(minChasmFloor, fy);
							}
							if (Maze.isAbyss(seed, ox + ix, oz + iz)) sawChasmAbyss = true;
							if (ChasmPlan.stairFacingAt(seed, ox + ix, oz + iz) != null) sawChasmStair = true;
						}
					}
				} else if (zone == Maze.Zone.MEGATHALL) {
					maxMegaCeil = Math.max(maxMegaCeil, ceilY);
					int fy = Maze.walkableFloorY(seed, ox + 5, oz + 5, Maze.EpochSource.ZERO);
					if (fy != Level0ChunkGenerator.FLOOR_Y) sawMegaRaisedOrSunken = true;
				} else if (Maze.isAtriumCell(seed, cx, cz)) {
					sawAtrium = true;
					assertTrue(ceilY >= 44, "atrium ceiling too low: " + ceilY);
				}
			}
		}

		assertTrue(minChasmFloor <= 16, "chasm never descended deep enough: " + minChasmFloor);
		assertTrue(sawChasmAbyss, "chasm never generated sheer abyss drops");
		assertTrue(sawChasmStair, "chasm never generated descending stairs");
		assertTrue(sawChasmBuriedRoom, "chasm never generated buried sub-level rooms");
		assertTrue(maxMegaCeil >= 70, "megahall ceiling not soaring high enough: " + maxMegaCeil);
		assertTrue(sawMegaRaisedOrSunken, "megahall never generated sunken/raised floor bays");
		assertTrue(sawAtrium, "never generated 2x2 high-ceiling atrium in CLASSIC/ARCHES");
	}

	@Test
	void megahallStylesAllAppearAndInteriorStaysWalkable() {
		int cell = Maze.CELL, span = MegahallPlan.MACRO_CELLS;
		EnumSet<MegahallPlan.Style> seen = EnumSet.noneOf(MegahallPlan.Style.class);

		for (long seed = 1; seed <= 6; seed++) {
			for (int mx = -3; mx <= 3; mx++) {
				for (int mz = -3; mz <= 3; mz++) {
					int baseCx = mx * span, baseCz = mz * span;
					MegahallPlan.Style style = MegahallPlan.styleAt(seed, baseCx, baseCz);
					seen.add(style);
					if (style == MegahallPlan.Style.BAYS) continue;

					int ox = (baseCx + 1) * cell, oz = (baseCz + 1) * cell, n = (span - 2) * cell;
					boolean[] visited = new boolean[n * n];
					ArrayDeque<int[]> queue = new ArrayDeque<>();
					int startX = (baseCx + 1) * cell + Maze.CENTER, startZ = (baseCz + 1) * cell + Maze.CENTER;
					assertFalse(MegahallPlan.isWallAt(seed, startX, startZ, false), style + " start is a wall");
					visited[(startX - ox) * n + (startZ - oz)] = true;
					queue.add(new int[] {startX, startZ});
					int reached = 1, open = 0;
					int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
					for (int i = 0; i < n; i++) {
						for (int j = 0; j < n; j++) {
							if (!MegahallPlan.isWallAt(seed, ox + i, oz + j, false)) open++;
						}
					}
					while (!queue.isEmpty()) {
						int[] p = queue.poll();
						int fy = MegahallPlan.walkableFloorY(seed, p[0], p[1]);
						for (int[] s : steps) {
							int nx = p[0] + s[0], nz = p[1] + s[1];
							if (nx < ox || nz < oz || nx >= ox + n || nz >= oz + n) continue;
							if (MegahallPlan.isWallAt(seed, nx, nz, false)) continue;
							if (Math.abs(MegahallPlan.walkableFloorY(seed, nx, nz) - fy) > 1) continue;
							int k = (nx - ox) * n + (nz - oz);
							if (visited[k]) continue;
							visited[k] = true;
							reached++;
							queue.add(new int[] {nx, nz});
						}
					}
					assertEquals(open, reached, style + " interior split at macro " + mx + "," + mz + " seed " + seed);
				}
			}
		}
		assertEquals(EnumSet.allOf(MegahallPlan.Style.class), seen, "not every megahall style was generated");
	}

	@Test
	void manilaRoomGeneratesAndIsFullyConnected() {
		long seed = 12345L;
		int found = 0;
		ManilaRoomPlan.Room sample = null;

		for (int rx = -4; rx <= 4; rx++) {
			for (int rz = -4; rz <= 4; rz++) {
				ManilaRoomPlan.Room room = ManilaRoomPlan.roomInRegion(seed, rx, rz);
				if (room != null) {
					found++;
					if (sample == null) sample = room;
				}
			}
		}

		assertNotNull(sample, "no Manila Room generated");
		assertTrue(found > 0, "expected Manila Room placement");

		int bx = sample.baseX();
		int bz = sample.baseZ();
		int da = ManilaRoomPlan.DOOR_A;
		int span = ManilaRoomPlan.SPAN_BLOCKS;
		assertTrue(ManilaRoomPlan.isRoomCell(seed, sample.cx0(), sample.cz0()));
		assertTrue(ManilaRoomPlan.isProtectedCell(seed, sample.cx0() - 1, sample.cz0()));
		assertFalse(Maze.isWall(seed, bx + da, bz, false));
		assertTrue(Maze.isWall(seed, bx + da, bz, true));
		assertFalse(Maze.isWall(seed, bx + span, bz + da, false));
		assertFalse(Maze.isWall(seed, bx + da, bz + span, false));
		assertFalse(Maze.isWall(seed, bx + 5, bz + 5, false));
		assertTrue(Maze.isWall(seed, bx + 1, bz + 3, false));
		assertTrue(sample.isCoreVoidLayer(1, 3));

		int sx = Math.floorDiv(sample.cx0(), Maze.SECTOR);
		int sz = Math.floorDiv(sample.cz0(), Maze.SECTOR);
		int winOriginX = (sx - 1) * Maze.SECTOR * Maze.CELL;
		int winOriginZ = (sz - 1) * Maze.SECTOR * Maze.CELL;
		int winSize = Maze.SECTOR * Maze.CELL * 3;
		int[] floorY = new int[winSize * winSize];
		boolean[] unvisited = new boolean[winSize * winSize];
		int open = 0;
		for (int i = 0; i < unvisited.length; i++) {
			int x = winOriginX + i % winSize, z = winOriginZ + i / winSize;
			floorY[i] = Maze.walkableFloorY(seed, x, z, Maze.EpochSource.ZERO);
			unvisited[i] = floorY[i] != Integer.MIN_VALUE;
			if (unvisited[i]) open++;
		}

		ArrayDeque<Integer> queue = new ArrayDeque<>();
		int startIdx = (bz + 5 - winOriginZ) * winSize + (bx + 5 - winOriginX);
		assertTrue(unvisited[startIdx], "Manila Room interior should be walkable");
		unvisited[startIdx] = false;
		queue.add(startIdx);
		int reached = 0;
		while (!queue.isEmpty()) {
			int at = queue.poll(), x = at % winSize, z = at / winSize;
			int curY = floorY[at];
			reached++;
			int[] next = {x > 0 ? at - 1 : -1, x < winSize - 1 ? at + 1 : -1, z > 0 ? at - winSize : -1, z < winSize - 1 ? at + winSize : -1};
			for (int n : next) {
				if (n >= 0 && unvisited[n] && Math.abs(floorY[n] - curY) <= 1) {
					unvisited[n] = false;
					queue.add(n);
				}
			}
		}
		assertEquals(open, reached, "Manila Room window has unreachable blocks");
	}

	@Test
	void staircaseComplexesGenerateAllTypesWithoutOverlap() {
		long seed = 12345L;
		EnumSet<StaircasePlan.Type> seenTypes = EnumSet.noneOf(StaircasePlan.Type.class);
		int[] seenRots = new int[4];

		for (int sx = -20; sx <= 20; sx++) {
			for (int sz = -20; sz <= 20; sz++) {
				StaircasePlan.Complex complex = StaircasePlan.complexInSector(seed, sx, sz);
				if (complex == null) continue;
				seenTypes.add(complex.type());
				seenRots[complex.rot()]++;
				assertFalse(sx == 0 && sz == 0, "origin sector must stay clean");
				Maze.Zone zone = Maze.baseZoneForSector(seed, sx, sz);
				assertTrue(zone == Maze.Zone.CLASSIC || zone == Maze.Zone.CORRIDORS);
				for (int cu = 0; cu < StaircasePlan.SPAN_CELLS; cu++) {
					for (int cv = 0; cv < StaircasePlan.SPAN_CELLS; cv++) {
						int cx = complex.cx0() + cu, cz = complex.cz0() + cv;
						assertFalse(ManilaRoomPlan.isRoomCell(seed, cx, cz), "staircase overlapped Manila Room");
						assertFalse(Maze.isAtriumCell(seed, cx, cz), "staircase overlapped atrium");
						assertFalse(Maze.isStandardFloorCell(seed, cx, cz), "staircase cell must block Exits");
					}
				}
			}
		}

		assertEquals(EnumSet.allOf(StaircasePlan.Type.class), seenTypes, "missing staircase types: " + seenTypes);
		for (int r = 0; r < 4; r++) {
			assertTrue(seenRots[r] > 0, "missing rotation " + r);
		}
	}
}
