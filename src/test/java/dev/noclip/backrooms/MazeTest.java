package dev.noclip.backrooms;

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
			boolean[] unvisited = new boolean[SIZE * SIZE];
			int open = 0;
			for (int i = 0; i < unvisited.length; i++) {
				int x = origin + i % SIZE, z = origin + i / SIZE;
				unvisited[i] = !Maze.isWall(seed, x, z) && !Maze.isPit(seed, x, z);
				if (unvisited[i]) open++;
			}

			ArrayDeque<Integer> queue = new ArrayDeque<>();
			int start = 1 + SIZE;
			assertTrue(unvisited[start]);
			unvisited[start] = false;
			queue.add(start);
			int reached = 0;
			while (!queue.isEmpty()) {
				int at = queue.poll(), x = at % SIZE, z = at / SIZE;
				reached++;
				int[] next = {x > 0 ? at - 1 : -1, x < SIZE - 1 ? at + 1 : -1, z > 0 ? at - SIZE : -1, z < SIZE - 1 ? at + SIZE : -1};
				for (int n : next) {
					if (n >= 0 && unvisited[n]) {
						unvisited[n] = false;
						queue.add(n);
					}
				}
			}
			assertEquals(open, reached, "unreachable area, seed " + seed);
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

			boolean[] unvisited = new boolean[SIZE * SIZE];
			int open = 0;
			for (int i = 0; i < unvisited.length; i++) {
				int x = origin + i % SIZE, z = origin + i / SIZE;
				unvisited[i] = !Maze.isWall(seed, x, z, false, epochs) && !Maze.isPit(seed, x, z);
				if (unvisited[i]) open++;
			}

			ArrayDeque<Integer> queue = new ArrayDeque<>();
			int start = 1 + SIZE;
			assertTrue(unvisited[start]);
			unvisited[start] = false;
			queue.add(start);
			int reached = 0;
			while (!queue.isEmpty()) {
				int at = queue.poll(), x = at % SIZE, z = at / SIZE;
				reached++;
				int[] next = {x > 0 ? at - 1 : -1, x < SIZE - 1 ? at + 1 : -1, z > 0 ? at - SIZE : -1, z < SIZE - 1 ? at + SIZE : -1};
				for (int n : next) {
					if (n >= 0 && unvisited[n]) {
						unvisited[n] = false;
						queue.add(n);
					}
				}
			}
			assertEquals(open, reached, "unreachable area after edge shifts, seed " + seed);
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
		boolean sawStairwell = false;
		boolean sawMacroPillar = false;

		for (int cx = -64; cx < 64; cx++) {
			for (int cz = -64; cz < 64; cz++) {
				if (Maze.isStairwellCell(seed, cx, cz)) {
					sawStairwell = true;
					assertEquals(Maze.Zone.CLASSIC, Maze.zoneAt(seed, cx * Maze.CELL, cz * Maze.CELL));
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

		assertTrue(sawStairwell, "never generated a classic stairwell cell");
		assertTrue(sawMacroPillar, "never generated a 2x2 macro pillar hall");
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
}
