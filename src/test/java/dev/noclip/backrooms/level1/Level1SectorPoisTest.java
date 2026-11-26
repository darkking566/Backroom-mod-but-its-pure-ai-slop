package dev.noclip.backrooms.level1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.noclip.backrooms.level1.Level1Elevation.Profile;
import dev.noclip.backrooms.level1.Level1Layout.Base;
import dev.noclip.backrooms.level1.Level1Layout.Sector;
import dev.noclip.backrooms.level1.Level1Layout.Tile;
import dev.noclip.backrooms.level1.Level1SectorPois.Kind;
import dev.noclip.backrooms.level1.Level1SectorPois.Poi;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class Level1SectorPoisTest {
	private static final int D = Level1Layout.DISTRICT;

	@Test
	void allSixSectorPoisGenerateAndMatchTheirSector() {
		EnumSet<Kind> seen = EnumSet.noneOf(Kind.class);
		for (int dx = -28; dx <= 28; dx++) {
			for (int dz = -28; dz <= 28; dz++) {
				Poi p = Level1SectorPois.poi(7L, dx, dz);
				if (p == null) continue;
				seen.add(p.kind());
				Sector expected = Level1Layout.sector(7L, dx * D + D / 2, dz * D + D / 2);
				assertEquals(expected, p.sector(), "sector mismatch for " + p);
			}
		}
		assertEquals(
				EnumSet.of(
						Kind.TALON_CRASH_SITE,
						Kind.GILD_TRADE_STALL,
						Kind.GOTHIC_RELIQUARY_SHRINE,
						Kind.GARDEN_OVERGROWN_CAMP,
						Kind.LEGEND_NEON_ARCADE,
						Kind.TAILCHASE_WORKSITE_SHACK),
				seen,
				"missing sector POI kinds: " + seen);
	}

	@Test
	void sectorPoisSkipProtectedDistrictsAndStayClearOfPillarsSpansAndFeatures() {
		for (long seed = 0; seed < 4; seed++) {
			assertNull(Level1SectorPois.poi(seed, 0, 0));
			for (int dx = -14; dx <= 14; dx++) {
				for (int dz = -14; dz <= 14; dz++) {
					Poi p = Level1SectorPois.poi(seed, dx, dz);
					boolean blocked = (dx == 0 && dz == 0)
							|| Level1Layout.kind(seed, dx, dz) == Level1Layout.Kind.CORRIDORS
							|| Level1Layout.base(seed, dx, dz) != Base.NONE
							|| Level1Landmarks.landmark(seed, dx, dz) != Level1Landmarks.Landmark.NONE
							|| Level1Elevation.profile(seed, dx, dz) == Profile.SUNKEN_BASIN;
					if (blocked) {
						assertNull(p, "unexpected sector POI in protected/basin district " + dx + "," + dz);
						continue;
					}
					if (p == null) continue;

					int x0 = dx * D, z0 = dz * D;
					assertInBounds(p.lootX(), p.lootZ(), x0, z0, p);
					assertTrue(Level1Layout.tile(seed, p.lootX(), p.lootZ()) != Tile.PILLAR, "loot hit pillar in " + p);
					assertFalse(Level1Elevation.isNearExitOrProp(seed, dx, dz, p.lootX(), p.lootZ()), "loot near feature in " + p);

					if (p.hasFuse()) {
						assertInBounds(p.fuseX(), p.fuseZ(), x0, z0, p);
						assertTrue(Level1Layout.tile(seed, p.fuseX(), p.fuseZ()) != Tile.PILLAR, "fuse hit pillar in " + p);
						assertFalse(Level1Elevation.isNearExitOrProp(seed, dx, dz, p.fuseX(), p.fuseZ()), "fuse near feature in " + p);
					}

					for (int lz = p.minLz() - 1; lz <= p.maxLz() + 1; lz++) {
						assertTrue(lz % 12 != 0, "reserved Z hit pipe gantry lz=" + lz);
						assertTrue(lz != 22 && lz != 42, "reserved Z hit unfinished beam lz=" + lz);
						assertTrue(lz < 28 || lz > 36, "reserved Z hit bridge lz=" + lz);
					}
				}
			}
		}
	}

	private static void assertInBounds(int x, int z, int x0, int z0, Poi p) {
		int lx = x - x0, lz = z - z0;
		assertTrue(lx >= p.minLx() && lx <= p.maxLx() && lz >= p.minLz() && lz <= p.maxLz(),
				"coord (" + lx + "," + lz + ") out of sector POI bounds " + p);
	}
}
