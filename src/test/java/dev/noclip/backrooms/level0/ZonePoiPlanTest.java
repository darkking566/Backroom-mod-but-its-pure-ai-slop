package dev.noclip.backrooms.level0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class ZonePoiPlanTest {
	@Test
	void chasmOutpostsSitOnDeepFlatNonStairCells() {
		long seed = 12345L;
		int checked = 0;

		for (int mx = -16; mx <= 16 && checked < 16; mx++) {
			for (int mz = -16; mz <= 16 && checked < 16; mz++) {
				int baseCx = mx * ChasmPlan.MACRO_CELLS;
				int baseCz = mz * ChasmPlan.MACRO_CELLS;
				if (Maze.zoneAt(seed, baseCx * Maze.CELL + Maze.CENTER, baseCz * Maze.CELL + Maze.CENTER) != Maze.Zone.CHASM) {
					continue;
				}
				ZonePoiPlan.ChasmOutpost outpost = ZonePoiPlan.chasmOutpostInMacro(seed, baseCx, baseCz);
				assertNotNull(outpost, "CHASM macro missing outpost at " + mx + "," + mz);
				assertTrue(outpost.tier() >= 2, "outpost too shallow: " + outpost.tier());
				assertFalse(ChasmPlan.isFlatRimCell(seed, outpost.cx(), outpost.cz()), "outpost placed on flat rim");

				int ox = outpost.cx() * Maze.CELL;
				int oz = outpost.cz() * Maze.CELL;
				for (int ix = 1; ix < Maze.CELL; ix++) {
					for (int iz = 1; iz < Maze.CELL; iz++) {
						assertEquals(outpost.floorY(), ChasmPlan.walkableFloorY(seed, ox + ix, oz + iz),
								"non-flat floor inside chasm outpost");
						assertEquals(null, ChasmPlan.stairFacingAt(seed, ox + ix, oz + iz),
								"stair overlapped chasm outpost");
					}
				}
				checked++;
			}
		}

		assertTrue(checked >= 6, "too few CHASM zones checked: " + checked);
	}

	@Test
	void megahallLoungesSitOnWallFreeFlatBaysAcrossAllStyles() {
		int span = MegahallPlan.MACRO_CELLS;
		EnumSet<MegahallPlan.Style> seen = EnumSet.noneOf(MegahallPlan.Style.class);

		for (long seed = 1; seed <= 8; seed++) {
			for (int mx = -3; mx <= 3; mx++) {
				for (int mz = -3; mz <= 3; mz++) {
					int baseCx = mx * span;
					int baseCz = mz * span;
					MegahallPlan.Style style = MegahallPlan.styleAt(seed, baseCx, baseCz);
					seen.add(style);

					ZonePoiPlan.MegahallLounge lounge = ZonePoiPlan.megahallLoungeInMacro(seed, baseCx, baseCz);
					assertNotNull(lounge, "missing Megahall lounge for " + style + " at " + mx + "," + mz);
					assertFalse(MegahallPlan.isRimCell(lounge.cx(), lounge.cz()), "lounge placed on rim");

					int ox = lounge.cx() * Maze.CELL;
					int oz = lounge.cz() * Maze.CELL;
					for (int ix = 1; ix < Maze.CELL; ix++) {
						for (int iz = 1; iz < Maze.CELL; iz++) {
							assertFalse(MegahallPlan.isWallAt(seed, ox + ix, oz + iz, false),
									"wall inside Megahall lounge for " + style);
							assertEquals(lounge.floorY(), MegahallPlan.walkableFloorY(seed, ox + ix, oz + iz),
									"uneven floor inside Megahall lounge for " + style);
						}
					}
				}
			}
		}

		assertEquals(EnumSet.allOf(MegahallPlan.Style.class), seen);
	}

	@Test
	void redRoomAndDarkenedPoisStayOpenProtectedAndPuddleFree() {
		long seed = 12345L;
		int sectorSpan = Maze.LARGE_ZONE_SPAN;
		boolean sawRed = false;
		boolean sawDarkened = false;

		for (int sx = -24; sx <= 24; sx += sectorSpan) {
			for (int sz = -24; sz <= 24; sz += sectorSpan) {
				int osx = Maze.largeZoneOriginSector(sx);
				int osz = Maze.largeZoneOriginSector(sz);
				if (osx == 0 && osz == 0) continue;
				Maze.Zone zone = Maze.baseZoneForSector(seed, osx, osz);
				if (zone != Maze.Zone.RED_ROOM && zone != Maze.Zone.DARKENED) continue;

				int cx = osx * Maze.SECTOR + 12;
				int cz = osz * Maze.SECTOR + 12;
				assertTrue(ZonePoiPlan.isProtectedCell(seed, cx, cz));

				int ox = cx * Maze.CELL;
				int oz = cz * Maze.CELL;
				for (int ix = 1; ix < Maze.CELL; ix++) {
					for (int iz = 1; iz < Maze.CELL; iz++) {
						assertFalse(Maze.isWall(seed, ox + ix, oz + iz, false),
								"interior wall inside POI at " + cx + "," + cz);
						assertFalse(Maze.isPuddle(seed, ox + ix, oz + iz),
								"puddle inside POI at " + cx + "," + cz);
					}
				}

				if (zone == Maze.Zone.RED_ROOM) {
					sawRed = true;
					assertTrue(ZonePoiPlan.isRedRoomPoiCell(seed, cx, cz));
					assertTrue(Maze.isRedRoomInteriorCell(seed, cx, cz));
					assertTrue(Maze.isLamp(seed, ox + 3, oz + 3, Maze.EpochSource.ZERO));
					assertTrue(Maze.lampWorks(seed, ox + 3, oz + 3, Maze.EpochSource.ZERO));
				} else {
					sawDarkened = true;
					assertTrue(ZonePoiPlan.isDarkenedPoiCell(seed, cx, cz));
					assertFalse(Maze.lampWorks(seed, ox + 3, oz + 3, Maze.EpochSource.ZERO));
				}
			}
		}

		assertTrue(sawRed, "never verified RED_ROOM POI");
		assertTrue(sawDarkened, "never verified DARKENED POI");
	}

	@Test
	void sectorVignettesGenerateCleanlyAndStayOpen() {
		long seed = 12345L;
		EnumSet<ZonePoiPlan.VignetteType> seen = EnumSet.noneOf(ZonePoiPlan.VignetteType.class);

		for (int sx = -24; sx <= 24; sx++) {
			for (int sz = -24; sz <= 24; sz++) {
				ZonePoiPlan.Vignette v = ZonePoiPlan.vignetteInSector(seed, sx, sz);
				if (v == null) continue;
				seen.add(v.type());
				int cx = v.cx(), cz = v.cz();
				assertFalse(sx == 0 && sz == 0, "origin sector must stay clean");
				assertTrue(ZonePoiPlan.isProtectedCell(seed, cx, cz));
				assertFalse(ManilaRoomPlan.isProtectedCell(seed, cx, cz), "vignette overlapped Manila Room");
				assertFalse(StaircasePlan.isProtectedCell(seed, cx, cz), "vignette overlapped staircase");
				assertFalse(Maze.isAtriumCell(seed, cx, cz), "vignette overlapped atrium");
				assertFalse(Maze.isSunkenCell(seed, cx, cz), "vignette overlapped sunken basin");
				assertEquals(Maze.CeilingStyle.FLAT, Maze.ceilingStyle(seed, cx, cz));

				int ox = cx * Maze.CELL, oz = cz * Maze.CELL;
				for (int ix = 1; ix < Maze.CELL; ix++) {
					for (int iz = 1; iz < Maze.CELL; iz++) {
						assertFalse(Maze.isWall(seed, ox + ix, oz + iz, false),
								"interior wall inside vignette at " + cx + "," + cz);
					}
				}
			}
		}

		assertEquals(EnumSet.allOf(ZonePoiPlan.VignetteType.class), seen, "missing vignette types: " + seen);
	}
}
