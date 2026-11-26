package dev.noclip.backrooms.level1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.noclip.backrooms.level1.Level1Elevation.Profile;
import dev.noclip.backrooms.level1.Level1ElevationPois.Kind;
import dev.noclip.backrooms.level1.Level1ElevationPois.Poi;
import dev.noclip.backrooms.level1.Level1Layout.Base;
import dev.noclip.backrooms.level1.Level1Layout.Tile;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class Level1ElevationPoisTest {
	private static final int D = Level1Layout.DISTRICT;

	@Test
	void allFourElevationPoiKindsAndDeepVariantsGenerate() {
		EnumSet<Kind> seen = EnumSet.noneOf(Kind.class);
		boolean sawBasinShallow = false, sawBasinDeep = false;
		boolean sawTunnelShallow = false, sawTunnelDeep = false;

		for (int dx = -24; dx <= 24; dx++) {
			for (int dz = -24; dz <= 24; dz++) {
				Poi p = Level1ElevationPois.poi(7L, dx, dz);
				if (p == null) continue;
				seen.add(p.kind());
				if (p.kind() == Kind.BASIN_PUMP_STATION) {
					if (p.deep()) sawBasinDeep = true;
					else sawBasinShallow = true;
				}
				if (p.kind() == Kind.TUNNEL_SERVICE_CACHE) {
					if (p.deep()) sawTunnelDeep = true;
					else sawTunnelShallow = true;
				}
			}
		}

		assertEquals(
				EnumSet.of(Kind.SKYWALK_WATCHPOST, Kind.B1_GARAGE_CHECKPOINT, Kind.BASIN_PUMP_STATION, Kind.TUNNEL_SERVICE_CACHE),
				seen,
				"missing elevation POI kinds: " + seen);
		assertTrue(sawBasinShallow && sawBasinDeep, "missing shallow/deep BASIN_PUMP_STATION variants");
		assertTrue(sawTunnelShallow && sawTunnelDeep, "missing shallow/deep TUNNEL_SERVICE_CACHE variants");
	}

	@Test
	void elevationPoisSkipProtectedDistrictsAndStayClearOfPillarsAndFeatures() {
		for (long seed = 0; seed < 4; seed++) {
			assertNull(Level1ElevationPois.poi(seed, 0, 0));
			for (int dx = -14; dx <= 14; dx++) {
				for (int dz = -14; dz <= 14; dz++) {
					Poi p = Level1ElevationPois.poi(seed, dx, dz);
					boolean blocked = (dx == 0 && dz == 0)
							|| Level1Layout.kind(seed, dx, dz) == Level1Layout.Kind.CORRIDORS
							|| Level1Layout.base(seed, dx, dz) != Base.NONE
							|| Level1Landmarks.landmark(seed, dx, dz) != Level1Landmarks.Landmark.NONE
							|| Level1Elevation.profile(seed, dx, dz) == Profile.STANDARD;
					if (blocked) {
						assertNull(p, "unexpected POI in protected/standard district " + dx + "," + dz);
						continue;
					}
					if (p == null) continue;

					assertEquals(dx, p.dx());
					assertEquals(dz, p.dz());
					int x0 = dx * D, z0 = dz * D;
					assertInBounds(p.fuseX(), p.fuseZ(), x0, z0, p);
					assertInBounds(p.lampX(), p.lampZ(), x0, z0, p);
					assertInBounds(p.chestX(), p.chestZ(), x0, z0, p);

					assertTrue(Level1Layout.tile(seed, p.fuseX(), p.fuseZ()) != Tile.PILLAR, "fuse hit pillar in " + p);
					assertTrue(Level1Layout.tile(seed, p.lampX(), p.lampZ()) != Tile.PILLAR, "lamp hit pillar in " + p);
					assertTrue(Level1Layout.tile(seed, p.chestX(), p.chestZ()) != Tile.PILLAR, "chest hit pillar in " + p);

					assertFalse(Level1Elevation.isNearExitOrProp(seed, dx, dz, p.fuseX(), p.fuseZ()), "fuse near feature in " + p);
					assertFalse(Level1Elevation.isNearExitOrProp(seed, dx, dz, p.lampX(), p.lampZ()), "lamp near feature in " + p);
					assertFalse(Level1Elevation.isNearExitOrProp(seed, dx, dz, p.chestX(), p.chestZ()), "chest near feature in " + p);
				}
			}
		}
	}

	@Test
	void sublevel1AlcoveStaysOutsideMainTunnelsAndOpensSouth() {
		int checked = 0;
		for (long seed = 0; seed < 4; seed++) {
			for (int dx = -16; dx <= 16; dx++) {
				for (int dz = -16; dz <= 16; dz++) {
					Poi p = Level1ElevationPois.poi(seed, dx, dz);
					if (p == null || p.kind() != Kind.TUNNEL_SERVICE_CACHE || p.deep()) continue;
					assertEquals(28, p.minLx());
					assertEquals(34, p.maxLx());
					assertEquals(15, p.minLz());
					assertEquals(21, p.maxLz());
					assertEquals(22, p.floorY());
					checked++;
				}
			}
		}
		assertTrue(checked >= 5, "too few Sub1 alcoves checked: " + checked);
	}

	private static void assertInBounds(int x, int z, int x0, int z0, Poi p) {
		int lx = x - x0, lz = z - z0;
		assertTrue(lx >= p.minLx() && lx <= p.maxLx() && lz >= p.minLz() && lz <= p.maxLz(),
				"coord (" + lx + "," + lz + ") out of POI bounds " + p);
	}
}
