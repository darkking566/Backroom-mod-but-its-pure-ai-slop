package dev.noclip.backrooms.level0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class Level0PropsTest {
	@Test
	void protectsOriginLandingAreaAndFarTestPoint() {
		assertTrue(Level0Props.isProtectedRegion(0, 0));
		assertTrue(Level0Props.isProtectedRegion(-32, -32));
		assertTrue(Level0Props.isProtectedRegion(16, 16));
		assertTrue(Level0Props.isProtectedRegion(688, -704));
		assertFalse(Level0Props.isProtectedRegion(32, 0));
		assertFalse(Level0Props.isProtectedRegion(-48, 32));
		assertFalse(Level0Props.isProtectedRegion(128, 128));
	}

	@Test
	void allClusterThemesAppearAcrossZones() {
		Set<Level0Props.ClusterTheme> seen = EnumSet.noneOf(Level0Props.ClusterTheme.class);
		for (Maze.Zone z : Maze.Zone.values()) {
			for (long h = 0; h < 200; h++) {
				seen.add(Level0Props.chooseClusterTheme(z, h));
			}
		}
		assertEquals(Level0Props.ClusterTheme.values().length, seen.size(), "All 5 Level 0 cluster themes must occur");
	}
}
