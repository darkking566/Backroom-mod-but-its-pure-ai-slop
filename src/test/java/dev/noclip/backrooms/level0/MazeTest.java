package dev.noclip.backrooms.level0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MazeTest {
	private static final int SIZE = Maze.CELL * Maze.SECTOR * 3;

	@Test
	void everyOpenBlockIsReachable() {
		Random random = new Random(0);
		// Includes negative coordinates and positions near the world border.
		int[] origins = {0, -SIZE, -29_999_984 / SIZE * SIZE, 29_000_000 / SIZE * SIZE};

		for (int run = 0; run < 1000; run++) {
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
		for (int cx = -300; cx < 300; cx++) {
			for (int cz = -300; cz < 300; cz++) {
				assertFalse(Maze.isWall(42, cx * Maze.CELL + Maze.CENTER, cz * Maze.CELL + Maze.CENTER));
			}
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

		for (int sx = -10; sx <= 10; sx++) {
			for (int sz = -10; sz <= 10; sz++) {
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
									"pillar hall missing corner pillar");
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

		for (int run = 0; run < 500; run++) {
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

		for (int sx = -8; sx <= 8; sx++) {
			for (int sz = -8; sz <= 8; sz++) {
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
		// Player at (0.5, 0.5) looking toward +Z (0, 1).
		assertFalse(EdgeShift.isOutOfObservation(0.5, 0.5, 0.0, 1.0, 0, 21));
		assertFalse(EdgeShift.isOutOfObservation(0.5, 0.5, 0.0, 1.0, 14, 14));
		assertTrue(EdgeShift.isOutOfObservation(0.5, 0.5, 0.0, 1.0, -3, -28));
		assertTrue(EdgeShift.isOutOfObservation(0.5, 0.5, 0.0, 1.0, 0, -28));
		// Side cell whose southern corner peeks forward must count as observed.
		assertFalse(EdgeShift.isOutOfObservation(0.5, 0.5, 0.0, 1.0, 14, -2));
	}

	@Test
	void stairwellsAndMacroPillarHallsGenerateCleanly() {
		long seed = 12345L;
		EnumSet<net.minecraft.core.Direction> seenStairDirs = EnumSet.noneOf(net.minecraft.core.Direction.class);
		boolean sawMacroPillar = false;

		for (int cx = -64; cx < 64; cx++) {
			for (int cz = -64; cz < 64; cz++) {
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
			}
		}

		for (int sx = -10; sx < 10; sx += 2) {
			for (int sz = -10; sz < 10; sz += 2) {
				if (sx == 0 && sz == 0) continue;
				int baseX = sx * Maze.SECTOR * Maze.CELL + Maze.CENTER;
				int baseZ = sz * Maze.SECTOR * Maze.CELL + Maze.CENTER;
				if (Maze.zoneAt(seed, baseX, baseZ) == Maze.Zone.PILLARS) {
					sawMacroPillar = true;
					// All four sectors of the 2x2 macro-block should share PILLARS.
					assertEquals(Maze.Zone.PILLARS, Maze.zoneAt(seed, (sx + 1) * Maze.SECTOR * Maze.CELL + Maze.CENTER, baseZ));
					assertEquals(Maze.Zone.PILLARS, Maze.zoneAt(seed, baseX, (sz + 1) * Maze.SECTOR * Maze.CELL + Maze.CENTER));
					assertEquals(Maze.Zone.PILLARS, Maze.zoneAt(seed,
							(sx + 1) * Maze.SECTOR * Maze.CELL + Maze.CENTER,
							(sz + 1) * Maze.SECTOR * Maze.CELL + Maze.CENTER));
				}
			}
		}

		assertEquals(4, seenStairDirs.size(), "expected all 4 stairwell directions, saw " + seenStairDirs);
		assertTrue(sawMacroPillar, "never generated a 2x2 macro pillar hall");
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

		for (int cx = -64; cx < 64; cx++) {
			for (int cz = -64; cz < 64; cz++) {
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
		assertTrue(sawFramedDoor, "never generated a framed 2-high doorway");
		assertTrue(sawSunken, "never generated a sunken split-level room");
	}

	@Test
	void macroDarkenedAndRedRoomZonesSpanThreeByThreeSectors() {
		long seed = 12345L;
		int sectorBlocks = Maze.CELL * Maze.SECTOR;
		boolean sawMacroDarkened = false;
		boolean sawMacroRedRoom = false;

		for (int sx = -12; sx <= 12; sx += Maze.LARGE_ZONE_SPAN) {
			for (int sz = -12; sz <= 12; sz += Maze.LARGE_ZONE_SPAN) {
				int osx = Maze.largeZoneOriginSector(sx);
				int osz = Maze.largeZoneOriginSector(sz);
				Maze.Zone zone = Maze.zoneAt(seed, osx * sectorBlocks + Maze.CENTER, osz * sectorBlocks + Maze.CENTER);
				if (zone == Maze.Zone.DARKENED || zone == Maze.Zone.RED_ROOM) {
					if (zone == Maze.Zone.DARKENED) sawMacroDarkened = true;
					if (zone == Maze.Zone.RED_ROOM) sawMacroRedRoom = true;
					for (int dx = 0; dx < Maze.LARGE_ZONE_SPAN; dx++) {
						for (int dz = 0; dz < Maze.LARGE_ZONE_SPAN; dz++) {
							assertEquals(zone, Maze.zoneAt(seed,
									(osx + dx) * sectorBlocks + Maze.CENTER,
									(osz + dz) * sectorBlocks + Maze.CENTER));
						}
					}
					if (zone == Maze.Zone.RED_ROOM) {
						int baseCx = osx * Maze.SECTOR;
						int baseCz = osz * Maze.SECTOR;
						int spanCells = Maze.LARGE_ZONE_SPAN * Maze.SECTOR;
						assertTrue(Maze.isRedRoomInteriorCell(seed, baseCx + 1, baseCz + 1));
						assertTrue(Maze.isRedRoomInteriorCell(seed, baseCx + Maze.SECTOR, baseCz + Maze.SECTOR));
						if (Maze.zoneAt(seed, (osx - 1) * sectorBlocks + Maze.CENTER, osz * sectorBlocks + Maze.CENTER) != Maze.Zone.RED_ROOM) {
							assertFalse(Maze.isRedRoomInteriorCell(seed, baseCx, baseCz + 1));
						}
						if (Maze.zoneAt(seed, (osx + Maze.LARGE_ZONE_SPAN) * sectorBlocks + Maze.CENTER, osz * sectorBlocks + Maze.CENTER) != Maze.Zone.RED_ROOM) {
							assertFalse(Maze.isRedRoomInteriorCell(seed, baseCx + spanCells - 1, baseCz + 1));
						}
					}
				}
			}
		}

		assertTrue(sawMacroDarkened, "never verified 3x3 macro darkened zone");
		assertTrue(sawMacroRedRoom, "never verified 3x3 macro red room zone");
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

		for (int cx = -120; cx <= 120; cx++) {
			for (int cz = -120; cz <= 120; cz++) {
				int ox = cx * Maze.CELL, oz = cz * Maze.CELL;
				Maze.Zone zone = Maze.zoneAt(seed, ox + Maze.CENTER, oz + Maze.CENTER);
				int ceilY = Maze.cellCeilingY(seed, cx, cz);

				if (zone == Maze.Zone.CHASM) {
					if (ceilY <= Level0ChunkGenerator.FLOOR_Y + 6 && ChasmPlan.floorYAt(seed, cx, cz, 3, 3) < Level0ChunkGenerator.FLOOR_Y) {
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
					int fy = Maze.walkableFloorY(seed, ox + 5, oz + 3, Maze.EpochSource.ZERO);
					if (fy != Level0ChunkGenerator.FLOOR_Y) sawMegaRaisedOrSunken = true;
				} else if (Maze.isAtriumCell(seed, cx, cz)) {
					sawAtrium = true;
					assertTrue(ceilY >= 44, "atrium ceiling too low: " + ceilY);
				}
			}
		}

		assertTrue(minChasmFloor <= 12, "chasm never descended deep enough: " + minChasmFloor);
		assertTrue(sawChasmAbyss, "chasm never generated sheer abyss drops");
		assertTrue(sawChasmStair, "chasm never generated descending stairs");
		assertTrue(sawChasmBuriedRoom, "chasm never generated buried sub-level rooms");
		assertTrue(maxMegaCeil >= 70, "megahall ceiling not soaring high enough: " + maxMegaCeil);
		assertTrue(sawMegaRaisedOrSunken, "megahall never generated sunken/raised floor bays");
		assertTrue(sawAtrium, "never generated 3x3 high-ceiling atrium in CLASSIC/ARCHES");
	}

	@Test
	void manilaRoomGeneratesRarelyAndIsFullyConnected() {
		long seed = 12345L;
		int found = 0;
		int checkedRegions = 0;
		ManilaRoomPlan.Room sample = null;

		for (int rx = -6; rx <= 6; rx++) {
			for (int rz = -6; rz <= 6; rz++) {
				checkedRegions++;
				ManilaRoomPlan.Room room = ManilaRoomPlan.roomInRegion(seed, rx, rz);
				if (room != null) {
					found++;
					if (sample == null) sample = room;
				}
			}
		}

		assertTrue(found > 0 && found < checkedRegions / 2,
				"expected rare Manila Room placement, found " + found + " in " + checkedRegions + " regions");

		int bx = sample.baseX();
		int bz = sample.baseZ();
		assertTrue(ManilaRoomPlan.isRoomCell(seed, sample.cx0(), sample.cz0()));
		assertTrue(ManilaRoomPlan.isProtectedCell(seed, sample.cx0() - 1, sample.cz0()));
		assertFalse(Maze.isWall(seed, bx + 6, bz, false));
		assertTrue(Maze.isWall(seed, bx + 6, bz, true));
		assertFalse(Maze.isWall(seed, bx + 14, bz + 6, false));
		assertFalse(Maze.isWall(seed, bx + 6, bz + 14, false));
		assertFalse(Maze.isWall(seed, bx + 4, bz + 4, false));
		assertTrue(Maze.isWall(seed, bx + 1, bz + 3, false));
		assertTrue(sample.isCoreVoidLayer(1, 3));

		// Verify 100% cardinal reachability in the 3x3-sector window centered on the Manila Room's sector.
		int sx = Math.floorDiv(sample.cx0(), Maze.SECTOR);
		int sz = Math.floorDiv(sample.cz0(), Maze.SECTOR);
		int winOriginX = (sx - 1) * Maze.SECTOR * Maze.CELL;
		int winOriginZ = (sz - 1) * Maze.SECTOR * Maze.CELL;
		int[] floorY = new int[SIZE * SIZE];
		boolean[] unvisited = new boolean[SIZE * SIZE];
		int open = 0;
		for (int i = 0; i < unvisited.length; i++) {
			int x = winOriginX + i % SIZE, z = winOriginZ + i / SIZE;
			floorY[i] = Maze.walkableFloorY(seed, x, z, Maze.EpochSource.ZERO);
			unvisited[i] = floorY[i] != Integer.MIN_VALUE;
			if (unvisited[i]) open++;
		}

		ArrayDeque<Integer> queue = new ArrayDeque<>();
		int startIdx = (bz + 4 - winOriginZ) * SIZE + (bx + 4 - winOriginX);
		assertTrue(unvisited[startIdx], "Manila Room interior should be walkable");
		unvisited[startIdx] = false;
		queue.add(startIdx);
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
		assertEquals(open, reached, "Manila Room window has unreachable blocks");
	}

	@Test
	void staircaseComplexesGenerateAllTypesWithoutOverlap() {
		long seed = 12345L;
		EnumSet<StaircasePlan.Type> seenTypes = EnumSet.noneOf(StaircasePlan.Type.class);
		int[] seenRots = new int[4];

		for (int sx = -24; sx <= 24; sx++) {
			for (int sz = -24; sz <= 24; sz++) {
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
