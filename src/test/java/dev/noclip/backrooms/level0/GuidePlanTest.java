package dev.noclip.backrooms.level0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GuidePlanTest {
	/** A made-up maze: 10-block cells with 1-block walls, 4-block gaps in some of them, like Level 0's corridors. */
	private static GuidePlan.Ground maze(long seed) {
		return new GuidePlan.Ground() {
			@Override
			public boolean walkable(int x, int z) {
				return !wall(x, z);
			}

			@Override
			public boolean wall(int x, int z) {
				int ix = Math.floorMod(x, 10), iz = Math.floorMod(z, 10);
				int cx = Math.floorDiv(x, 10), cz = Math.floorDiv(z, 10);
				if (ix == 0 && iz == 0) return true;
				if (ix == 0) return !(gap(seed, cx, cz, 1) && iz >= 3 && iz <= 6);
				if (iz == 0) return !(gap(seed, cx, cz, 2) && ix >= 3 && ix <= 6);
				return false;
			}
		};
	}

	private static boolean gap(long seed, int cx, int cz, int salt) {
		long h = (seed ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL) ^ salt) * 0xBF58476D1CE4E5B9L;
		return ((h >>> 40) & 3) != 0;
	}

	@Test
	void theCableRunsUnbrokenFromTheLandingToADoorAgainstAWall() {
		for (long seed = 1; seed <= 6; seed++) {
			GuidePlan.Ground ground = maze(seed);
			GuidePlan.Plan plan = GuidePlan.plan(ground, 5, 5);
			assertNotNull(plan, "no route in maze " + seed);
			List<int[]> path = plan.path();
			assertEquals(5, path.get(0)[0]);
			assertEquals(5, path.get(0)[1]);
			Set<Long> seen = new HashSet<>();
			for (int i = 0; i < path.size(); i++) {
				int[] p = path.get(i);
				assertTrue(ground.walkable(p[0], p[1]), "cable over a wall at " + p[0] + "," + p[1]);
				assertTrue(seen.add(((long) p[0] << 32) | (p[1] & 0xFFFFFFFFL)), "the cable crosses itself");
				if (i > 0) assertEquals(1, Math.abs(p[0] - path.get(i - 1)[0]) + Math.abs(p[1] - path.get(i - 1)[1]), "a gap in the cable");
			}
			int[] last = path.get(path.size() - 1);
			assertEquals(1, Math.abs(last[0] - plan.doorX()) + Math.abs(last[1] - plan.doorZ()), "the cable stops short of the door");
			double distance = Math.hypot(plan.doorX() - 5, plan.doorZ() - 5);
			assertTrue(distance >= GuidePlan.MIN_DISTANCE && distance <= GuidePlan.MAX_DISTANCE, "door " + distance + " away");
			int[] back = GuidePlan.side(plan.doorBack());
			assertTrue(ground.wall(plan.doorX() + back[0], plan.doorZ() + back[1]), "the door's back is not against a wall");
			assertTrue(ground.walkable(plan.doorX() - back[0], plan.doorZ() - back[1]), "the door faces a wall");
			assertTrue(plan.writing().size() >= path.size() / GuidePlan.WRITING_EVERY / 2, "too little writing: " + plan.writing().size());
			for (GuidePlan.Face face : plan.writing()) {
				assertTrue(ground.wall(face.x(), face.z()), "writing on thin air");
				int[] out = GuidePlan.side(face.side());
				assertTrue(ground.walkable(face.x() + out[0], face.z() + out[1]), "writing faces into a wall");
			}
			for (int[] reel : plan.reels()) {
				assertTrue(ground.walkable(reel[0], reel[1]));
				for (int[] p : path) assertTrue(p[0] != reel[0] || p[1] != reel[1], "a reel on the cable");
			}
		}
	}

	@Test
	void noRouteFromInsideAWallOrAClosedRoom() {
		assertNull(GuidePlan.plan(maze(1), 0, 0));
		GuidePlan.Ground room = new GuidePlan.Ground() {
			@Override
			public boolean walkable(int x, int z) {
				return Math.abs(x) < 20 && Math.abs(z) < 20;
			}

			@Override
			public boolean wall(int x, int z) {
				return !walkable(x, z);
			}
		};
		assertNull(GuidePlan.plan(room, 0, 0));
	}

	@Test
	void theWritingGoesFromDespairToDirectionsToDoubts() {
		for (int count : new int[] {1, 4, 9, 20, 30, 42, 60}) {
			List<String> lines = GuidePlan.lines(count, new Random(count));
			assertEquals(Math.min(count, 42), lines.size(), "lines for " + count + " walls");
			assertEquals(lines.size(), new HashSet<>(lines).size(), "a line written twice");
			assertEquals("guide.0", lines.get(0));
			if (count >= 3) assertEquals("thing.0", lines.get(2));
			if (count >= 9) {
				assertTrue(lines.get(lines.size() - 1).startsWith("hint."), "the last line by the door should make you wonder");
				int almost = lines.indexOf("guide.9"), team = lines.indexOf("team.0");
				assertTrue(almost > 0 && (team < 0 || team < almost), "'almost there' before the researchers' lines");
			}
		}
	}
}
