package dev.noclip.backrooms.level1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.noclip.backrooms.level1.Level1EasterEggs.Egg;
import dev.noclip.backrooms.level1.Level1EasterEggs.Kind;
import dev.noclip.backrooms.level1.Level1Elevation.Profile;
import dev.noclip.backrooms.level1.Level1Layout.Base;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class Level1EasterEggsTest {
	private static final long SEED = 0x5EED_CAFEL;

	@Test
	void allThreeKindsSpawnAndAreRarePerRegion() {
		Set<Kind> seen = EnumSet.noneOf(Kind.class);
		for (int rx = -4; rx <= 4; rx++) {
			for (int rz = -4; rz <= 4; rz++) {
				int count = 0;
				for (int ox = 0; ox < 4; ox++) {
					for (int oz = 0; oz < 4; oz++) {
						Egg e = Level1EasterEggs.egg(SEED, rx * 4 + ox, rz * 4 + oz);
						if (e != null) {
							count++;
							seen.add(e.kind());
						}
					}
				}
				assertTrue(count <= 1, "more than one easter egg in region (" + rx + ", " + rz + "): " + count);
			}
		}
		assertEquals(EnumSet.of(Kind.PARTYGOER_DOOR_EGG, Kind.ANOMALY_RIFT_ARCHIVE, Kind.VOID_RELAY_POST), seen);
	}

	@Test
	void footprintsAreSafeAndDisjointFromOtherPois() {
		int checked = 0;
		for (int dx = -16; dx <= 19; dx++) {
			for (int dz = -16; dz <= 19; dz++) {
				Egg e = Level1EasterEggs.egg(SEED, dx, dz);
				if (e == null) continue;
				checked++;
				assertFalse(dx == 0 && dz == 0);
				assertNotEquals(Level1Layout.Kind.CORRIDORS, Level1Layout.kind(SEED, dx, dz));
				assertEquals(Base.NONE, Level1Layout.base(SEED, dx, dz));
				assertEquals(Level1Landmarks.Landmark.NONE, Level1Landmarks.landmark(SEED, dx, dz));
				assertNotEquals(Profile.SUNKEN_BASIN, Level1Elevation.profile(SEED, dx, dz));

				int x0 = dx * Level1Layout.DISTRICT;
				int z0 = dz * Level1Layout.DISTRICT;
				for (int lx = e.minLx() - 1; lx <= e.maxLx() + 1; lx++) {
					for (int lz = e.minLz() - 1; lz <= e.maxLz() + 1; lz++) {
						assertTrue(Level1EasterEggs.isReserved(SEED, dx, dz, lx, lz));
						int bz = Math.floorMod(z0 + lz, Level1Layout.BAY_Z);
						assertTrue(bz >= 2, "pillar risk at lx=" + lx + ", lz=" + lz + " (bz=" + bz + ")");
						assertFalse(Level1SectorPois.isReserved(SEED, dx, dz, lx, lz),
								"overlaps sector POI at dx=" + dx + ", dz=" + dz + ", lx=" + lx + ", lz=" + lz);
						assertFalse(Level1Vignettes.isReserved(SEED, dx, dz, lx, lz),
								"overlaps vignette at dx=" + dx + ", dz=" + dz + ", lx=" + lx + ", lz=" + lz);
					}
				}

				assertEquals(33, e.focalY());
				assertEquals(33, e.lootY());
				assertTrue(e.focalX() >= x0 + e.minLx() && e.focalX() <= x0 + e.maxLx());
				assertTrue(e.focalZ() >= z0 + e.minLz() && e.focalZ() <= z0 + e.maxLz());
				assertTrue(e.lootX() >= x0 + e.minLx() && e.lootX() <= x0 + e.maxLx());
				assertTrue(e.lootZ() >= z0 + e.minLz() && e.lootZ() <= z0 + e.maxLz());
			}
		}
		assertTrue(checked >= 10, "too few easter eggs checked: " + checked);
	}

	@Test
	void deterministicLookup() {
		for (int dx = -8; dx <= 8; dx++) {
			for (int dz = -8; dz <= 8; dz++) {
				Egg a = Level1EasterEggs.egg(SEED, dx, dz);
				Egg b = Level1EasterEggs.egg(SEED, dx, dz);
				assertEquals(a, b);
				if (a != null) {
					assertNotNull(a.kind());
				}
			}
		}
	}
}
