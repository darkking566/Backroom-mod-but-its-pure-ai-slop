package dev.noclip.backrooms.level0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.noclip.backrooms.frontrooms.InstituteLayout;
import org.junit.jupiter.api.Test;

/**
 * Pure JUnit checks for Level 0 bounds constants and Institute staircase flush top-step geometry.
 * Bit-for-bit BlockState equality between {@link Level0ChunkGenerator#fillColumnStates} and
 * {@link Level0ChunkGenerator#worldBlockAt} is verified inside the bootstrapped game environment via
 * {@link Level0MissionHelper#verifyColumnMatchesWorld}.
 */
final class Level0ChunkFastPathTest {
	@Test
	void chunkBoundsRemainConsistent() {
		assertEquals(Level0ChunkGenerator.MAX_Y, Level0ChunkGenerator.MIN_Y + Level0ChunkGenerator.HEIGHT - 1);
		assertEquals(128, Level0ChunkGenerator.HEIGHT);
	}

	@Test
	void instituteStaircasesAreFlushAtTopLanding() {
		for (InstituteLayout.Flight f : InstituteLayout.flights()) {
			boolean sawTopStair = false;
			for (int x = f.minX(); x <= f.maxX(); x++) {
				for (int z = f.minZ(); z <= f.maxZ(); z++) {
					boolean hasStair = false;
					for (int y = f.low() + 1; y <= f.high(); y++) {
						if (InstituteLayout.cell(x, y, z).kind() == InstituteLayout.Kind.STAIR) {
							hasStair = true;
							assertEquals(InstituteLayout.Kind.AIR, InstituteLayout.cell(x, y + 1, z).kind(),
									"Expected clear headroom over stair at (" + x + "," + y + "," + z + ")");
							if (y == f.high()) {
								sawTopStair = true;
								assertEquals(InstituteLayout.Kind.AIR,
										InstituteLayout.cell(x + f.dx(), f.high() + 1, z + f.dz()).kind(),
										"Expected clear landing headroom after top step");
							}
						}
					}
					assertTrue(hasStair, "Expected stair step in column (" + x + "," + z + ")");
				}
			}
			assertTrue(sawTopStair, "Expected top step flush with f.high() = " + f.high());
		}
	}
}
