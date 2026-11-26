package dev.noclip.backrooms.level1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.noclip.backrooms.level1.Level1Elevation.Profile;
import dev.noclip.backrooms.level1.Level1Layout.Base;
import dev.noclip.backrooms.level1.Level1Layout.Tile;
import dev.noclip.backrooms.level1.Level1Vignettes.Kind;
import dev.noclip.backrooms.level1.Level1Vignettes.Vignette;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class Level1VignettesTest {
	private static final int D = Level1Layout.DISTRICT;

	@Test
	void allFiveVignetteKindsGenerate() {
		EnumSet<Kind> seen = EnumSet.noneOf(Kind.class);
		for (int dx = -24; dx <= 24; dx++) {
			for (int dz = -24; dz <= 24; dz++) {
				Vignette v = Level1Vignettes.vignette(7L, dx, dz);
				if (v != null) seen.add(v.kind());
			}
		}
		assertEquals(
				EnumSet.of(
						Kind.CORRIDOR_SECURITY_NOOK,
						Kind.CORRIDOR_SHELTER_CACHE,
						Kind.CORRIDOR_VENDING_ALCOVE,
						Kind.HALL_BREAKDOWN_SPOT,
						Kind.HALL_VENDING_BREAK_CORNER),
				seen,
				"missing vignette kinds: " + seen);
	}

	@Test
	void corridorVignettesSitInDeadEndNonRoomCellsClearOfExitAndGlitch() {
		int checked = 0;
		for (long seed = 0; seed < 4; seed++) {
			for (int dx = -14; dx <= 14; dx++) {
				for (int dz = -14; dz <= 14; dz++) {
					if (Level1Layout.kind(seed, dx, dz) != Level1Layout.Kind.CORRIDORS) continue;
					Vignette v = Level1Vignettes.vignette(seed, dx, dz);
					if (v == null) continue;
					checked++;
					assertTrue(
							v.kind() == Kind.CORRIDOR_SECURITY_NOOK
									|| v.kind() == Kind.CORRIDOR_SHELTER_CACHE
									|| v.kind() == Kind.CORRIDOR_VENDING_ALCOVE,
							"unexpected hall vignette in corridor: " + v);

					int ci = (v.minLx() - 1) / Level1Layout.CELL;
					int cj = (v.minLz() - 1) / Level1Layout.CELL;
					assertTrue(ci >= 2 && ci <= 13 && cj >= 2 && cj <= 13, "cell out of inner bounds: " + v);

					CorridorPlan plan = CorridorPlan.of(seed, dx, dz);
					int midLx = ci * Level1Layout.CELL + 2, midLz = cj * Level1Layout.CELL + 2;
					assertNull(plan.roomAt(midLx, midLz), "corridor vignette inside room: " + v);
					assertEquals(1, Integer.bitCount(plan.openMask(ci, cj)), "corridor vignette not in dead-end: " + v);
					assertTrue(Math.abs(midLx - plan.doorX()) > 6 || Math.abs(midLz - plan.doorZ()) > 6,
							"corridor vignette too close to exit door: " + v);
					assertTrue(Level1Layout.glitchZone(seed, dx * D + midLx, dz * D + midLz) < 0,
							"corridor vignette inside glitch zone: " + v);
				}
			}
		}
		assertTrue(checked >= 20, "too few corridor vignettes checked: " + checked);
	}

	@Test
	void hallVignettesSkipProtectedDistrictsAndStayClearOfPillarsSpansAndSectorPois() {
		int checked = 0;
		for (long seed = 0; seed < 4; seed++) {
			assertNull(Level1Vignettes.vignette(seed, 0, 0));
			for (int dx = -14; dx <= 14; dx++) {
				for (int dz = -14; dz <= 14; dz++) {
					if (Level1Layout.kind(seed, dx, dz) == Level1Layout.Kind.CORRIDORS) continue;
					Vignette v = Level1Vignettes.vignette(seed, dx, dz);
					boolean blocked = (dx == 0 && dz == 0)
							|| Level1Layout.base(seed, dx, dz) != Base.NONE
							|| Level1Landmarks.landmark(seed, dx, dz) != Level1Landmarks.Landmark.NONE
							|| Level1Elevation.profile(seed, dx, dz) == Profile.SUNKEN_BASIN;
					if (blocked) {
						assertNull(v, "unexpected hall vignette in protected/basin district " + dx + "," + dz);
						continue;
					}
					if (v == null) continue;
					checked++;
					assertTrue(v.kind() == Kind.HALL_BREAKDOWN_SPOT || v.kind() == Kind.HALL_VENDING_BREAK_CORNER,
							"unexpected corridor vignette in hall: " + v);

					int x0 = dx * D, z0 = dz * D;
					int lx = v.keyX() - x0, lz = v.keyZ() - z0;
					assertTrue(lx >= v.minLx() && lx <= v.maxLx() && lz >= v.minLz() && lz <= v.maxLz(),
							"key out of hall vignette bounds: " + v);
					assertTrue(Level1Layout.tile(seed, v.keyX(), v.keyZ()) != Tile.PILLAR, "key hit pillar in " + v);
					assertFalse(Level1Elevation.isNearExitOrProp(seed, dx, dz, v.keyX(), v.keyZ()), "key near feature in " + v);

					for (int z = v.minLz() - 1; z <= v.maxLz() + 1; z++) {
						assertTrue(z % 12 != 0, "reserved Z hit pipe gantry lz=" + z);
						assertTrue(z != 22 && z != 42, "reserved Z hit beam lz=" + z);
						assertTrue(z < 28 || z > 36, "reserved Z hit bridge lz=" + z);
						assertTrue(z < 50 || z > 58, "reserved Z overlapped sector POI lz=" + z);
					}
				}
			}
		}
		assertTrue(checked >= 20, "too few hall vignettes checked: " + checked);
	}
}
