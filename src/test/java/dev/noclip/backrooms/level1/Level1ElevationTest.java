package dev.noclip.backrooms.level1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.noclip.backrooms.level1.Level1Elevation.Profile;
import dev.noclip.backrooms.level1.Level1Layout.Base;
import dev.noclip.backrooms.level1.Level1Layout.Kind;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class Level1ElevationTest {
	@Test
	void allVerticalProfilesGenerate() {
		EnumSet<Profile> seen = EnumSet.noneOf(Profile.class);
		for (int dx = -12; dx <= 12; dx++) {
			for (int dz = -12; dz <= 12; dz++) {
				seen.add(Level1Elevation.profile(7L, dx, dz));
			}
		}
		assertEquals(EnumSet.allOf(Profile.class), seen);
	}

	@Test
	void originCorridorsAndBasesStayStandard() {
		for (long seed = 0; seed < 10; seed++) {
			assertEquals(Profile.STANDARD, Level1Elevation.profile(seed, 0, 0));
			for (int dx = -10; dx <= 10; dx++) {
				for (int dz = -10; dz <= 10; dz++) {
					if (Level1Layout.kind(seed, dx, dz) == Kind.CORRIDORS || Level1Layout.base(seed, dx, dz) != Base.NONE) {
						assertEquals(Profile.STANDARD, Level1Elevation.profile(seed, dx, dz),
								"non-standard profile on corridor/base at " + dx + "," + dz);
					}
				}
			}
		}
	}

	@Test
	void ceilingHeightsStayWithinDimensionBounds() {
		for (long seed = 0; seed < 8; seed++) {
			for (int dx = -10; dx <= 10; dx++) {
				for (int dz = -10; dz <= 10; dz++) {
					int ceil = Level1Elevation.ceilingY(seed, dx, dz);
					assertTrue(ceil >= Level1ChunkGenerator.CEILING_Y && ceil < Level1ChunkGenerator.HEIGHT - 2,
							"ceiling out of bounds: " + ceil);
				}
			}
		}
	}
}
