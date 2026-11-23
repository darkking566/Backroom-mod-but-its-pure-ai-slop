package dev.noclip.backrooms.level1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.noclip.backrooms.level1.Level1Layout.Base;
import dev.noclip.backrooms.level1.Level1Layout.Kind;
import dev.noclip.backrooms.level1.Level1Layout.Sector;
import dev.noclip.backrooms.level1.Level1SectorArchitecture.Variant;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class Level1SectorArchitectureTest {
	@Test
	void allEighteenSectorVariantsGenerate() {
		EnumSet<Variant> seen = EnumSet.noneOf(Variant.class);
		for (int dx = -24; dx <= 24; dx++) {
			for (int dz = -24; dz <= 24; dz++) {
				seen.add(Level1SectorArchitecture.variant(7L, dx, dz));
			}
		}
		assertEquals(EnumSet.allOf(Variant.class), seen);
	}

	@Test
	void variantsMatchTheirSectorAndSkipProtectedDistricts() {
		int D = Level1Layout.DISTRICT;
		for (long seed = 0; seed < 5; seed++) {
			assertEquals(Variant.NONE, Level1SectorArchitecture.variant(seed, 0, 0));
			for (int dx = -12; dx <= 12; dx++) {
				for (int dz = -12; dz <= 12; dz++) {
					Variant v = Level1SectorArchitecture.variant(seed, dx, dz);
					if (dx == 0 && dz == 0 || Level1Layout.kind(seed, dx, dz) == Kind.CORRIDORS
							|| Level1Layout.base(seed, dx, dz) != Base.NONE) {
						assertEquals(Variant.NONE, v);
						continue;
					}
					Sector sector = Level1Layout.sector(seed, dx * D + D / 2, dz * D + D / 2);
					assertTrue(v.name().startsWith(sector.name() + "_"),
							"variant " + v + " did not match sector " + sector);
				}
			}
		}
	}
}
