package dev.noclip.backrooms.level1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.noclip.backrooms.level1.Level1Landmarks.Landmark;
import dev.noclip.backrooms.level1.Level1Layout.Base;
import dev.noclip.backrooms.level1.Level1Layout.Kind;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class Level1LandmarksTest {
	@Test
	void allSixRareLandmarksGenerate() {
		EnumSet<Landmark> seen = EnumSet.noneOf(Landmark.class);
		for (int dx = -40; dx <= 40; dx++) {
			for (int dz = -40; dz <= 40; dz++) {
				seen.add(Level1Landmarks.landmark(7L, dx, dz));
			}
		}
		assertEquals(EnumSet.allOf(Landmark.class), seen);
	}

	@Test
	void landmarksStayRareAndSkipProtectedDistricts() {
		int totalHalls = 0, landmarkCount = 0;
		for (long seed = 0; seed < 4; seed++) {
			assertEquals(Landmark.NONE, Level1Landmarks.landmark(seed, 0, 0));
			for (int dx = -20; dx <= 20; dx++) {
				for (int dz = -20; dz <= 20; dz++) {
					Landmark lm = Level1Landmarks.landmark(seed, dx, dz);
					if (dx == 0 && dz == 0 || Level1Layout.kind(seed, dx, dz) == Kind.CORRIDORS
							|| Level1Layout.base(seed, dx, dz) != Base.NONE) {
						assertEquals(Landmark.NONE, lm);
						continue;
					}
					totalHalls++;
					if (lm != Landmark.NONE) {
						landmarkCount++;
						int ceil = Level1Landmarks.ceilingY(lm);
						assertTrue(ceil > Level1ChunkGenerator.CEILING_Y && ceil < Level1ChunkGenerator.HEIGHT - 2);
					}
				}
			}
		}
		double rate = (double) landmarkCount / totalHalls;
		assertTrue(rate > 0.005 && rate < 0.12, "unexpected landmark rate: " + rate + " (" + landmarkCount + "/" + totalHalls + ")");
	}
}
