package dev.noclip.backrooms.level3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.noclip.backrooms.level3.GammaLayout.Thing;
import dev.noclip.backrooms.level3.Level3Layout.Kind;
import dev.noclip.backrooms.level3.Level3Layout.Tile;
import dev.noclip.backrooms.level3.Level3Plan.HallKind;
import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;

class GammaTest {
	private static final long SEED = 12345;
	private static final int W = GammaLayout.W, D = GammaLayout.D, Y = GammaLayout.Y;

	/** One Gamma in every 4x4 block of regions, never overlapping another hall. */
	@Test
	void oneGammaPerBlockOfRegions() {
		for (int bx = -3; bx < 3; bx++) {
			for (int bz = -3; bz < 3; bz++) {
				int count = 0;
				for (int rx = bx * 4; rx < bx * 4 + 4; rx++) {
					for (int rz = bz * 4; rz < bz * 4 + 4; rz++) {
						Level3Plan plan = Level3Plan.of(SEED, rx, rz);
						boolean gamma = GammaPlan.in(SEED, rx, rz);
						assertEquals(gamma, plan.halls.stream().anyMatch(h -> h.kind() == HallKind.GAMMA));
						if (!gamma) continue;
						count++;
						for (int c = 0; c < Level3Plan.N * Level3Plan.N; c++) {
							int i = c % Level3Plan.N, j = c / Level3Plan.N;
							boolean inside = i >= GammaPlan.I && i < GammaPlan.I + GammaPlan.W && j >= GammaPlan.J && j < GammaPlan.J + GammaPlan.H;
							if (inside) assertEquals(0, plan.hallOf[c], "Gamma's cells belong to Gamma");
							else if (plan.hallOf[c] >= 0) assertTrue(plan.halls.get(plan.hallOf[c]).kind() != HallKind.GAMMA);
						}
					}
				}
				assertEquals(1, count, "block " + bx + ", " + bz);
			}
		}
	}

	/** The gate: a wide maze passage in front, a gap two wide and three high, and a way into the checkpoint. */
	@Test
	void theGateOpensOntoTheMaze() {
		int[] region = gammaRegion();
		Level3Plan plan = Level3Plan.of(SEED, region[0], region[1]);
		assertEquals(Level3Layout.Profile.WIDE, plan.profile(GammaPlan.FRONT_I, GammaPlan.FRONT_J));
		assertEquals(Level3Plan.Edge.HALL, plan.edge(Level3Plan.cell(GammaPlan.FRONT_I, GammaPlan.FRONT_J), Level3Plan.SOUTH));
		int x0 = GammaPlan.x0(region[0]), z0 = GammaPlan.z0(region[1]);
		for (int u = GammaLayout.GATE_U; u <= GammaLayout.GATE_U + 1; u++) {
			Tile gap = Level3Layout.tile(SEED, x0 + u, z0);
			assertEquals(Kind.HALL_GAP, gap.kind());
			assertEquals(Kind.FLOOR, Level3Layout.tile(SEED, x0 + u, z0 - 1).kind(), "the maze passage reaches the gate");
			for (int y = 33; y <= 35; y++) assertEquals(Thing.AIR, GammaLayout.thing(u, y, 0));
		}
		// Regions without Gamma keep the profile the hash gives.
		for (int rx = -6; rx <= 6; rx++) {
			if (GammaPlan.in(SEED, rx, 0)) continue;
			assertTrue(Level3Plan.of(SEED, rx, 0).halls.stream().noneMatch(h -> h.kind() == HallKind.GAMMA));
		}
	}

	/** From the gate, the stairs reach every floor, and everyone working there can be walked up to. */
	@Test
	void everyFloorAndEveryoneCanBeReached() {
		boolean[] seen = walk();
		for (int floor : GammaLayout.FLOORS) {
			boolean any = false;
			for (int u = 0; u < W && !any; u++) {
				for (int v = 0; v < D && !any; v++) any = seen[index(u, floor + 1, v)];
			}
			assertTrue(any, "floor " + floor + " cannot be reached");
		}
		for (GammaLayout.Spot spot : GammaLayout.PEOPLE) {
			assertTrue(stand(spot.u(), spot.y(), spot.v()), spot.id() + " has nowhere to stand");
			assertTrue(near(seen, spot.u(), spot.y(), spot.v(), 3), spot.id() + " cannot be walked up to");
		}
		for (int u = 0; u < W; u++) {
			for (int v = 0; v < D; v++) {
				for (int y = 1; y < Y - 1; y++) {
					Thing thing = GammaLayout.thing(u, y, v);
					boolean reach = switch (thing) {
						case COT, ELEVATOR, EXIT_DOOR, CHECKPOINT, LOCKER, WATER -> true;
						default -> false;
					};
					if (reach && GammaLayout.at(u, y, v).part() == 0) {
						assertTrue(near(seen, u, y - 1, v, 1) || near(seen, u, y, v, 1) || near(seen, u, y + 1, v, 1), thing + " out of reach at " + u + ", " + y + ", " + v);
					}
				}
			}
		}
	}

	/** Only the gate goes through the outer wall. */
	@Test
	void theOuterWallIsWhole() {
		for (int y = 1; y < Y - 1; y++) {
			for (int u = 0; u < W; u++) {
				for (int v = 0; v < D; v++) {
					if (u != 0 && v != 0 && u != W - 1 && v != D - 1) continue;
					boolean gate = v == 0 && u >= GammaLayout.GATE_U && u <= GammaLayout.GATE_U + 1 && y >= 33 && y <= 35;
					if (!gate) assertEquals(Thing.BRICK, GammaLayout.thing(u, y, v), "a hole in the wall at " + u + ", " + y + ", " + v);
				}
			}
		}
	}

	private static int[] gammaRegion() {
		for (int rx = 0; rx < 4; rx++) {
			for (int rz = 0; rz < 4; rz++) if (GammaPlan.in(SEED, rx, rz)) return new int[] {rx, rz};
		}
		throw new AssertionError("no Gamma near the origin");
	}

	private static int index(int u, int y, int v) {
		return (y * D + v) * W + u;
	}

	private static boolean passable(int u, int y, int v) {
		Thing thing = GammaLayout.thing(u, y, v);
		// The admin door stays locked; the metal detector lets you through.
		return thing.open() && thing != Thing.IRON_DOOR;
	}

	/** Feet at y: room for feet and head, something to stand on. */
	private static boolean stand(int u, int y, int v) {
		return passable(u, y, v) && passable(u, y + 1, v) && GammaLayout.thing(u, y - 1, v).footing();
	}

	private static boolean near(boolean[] seen, int u, int y, int v, int reach) {
		for (int du = -reach; du <= reach; du++) {
			for (int dv = -reach; dv <= reach; dv++) {
				int a = u + du, b = v + dv;
				if (a >= 0 && b >= 0 && a < W && b < D && seen[index(a, y, b)]) return true;
			}
		}
		return false;
	}

	/** Walking from the gate: along a floor, a step of one up, any drop down. */
	private static boolean[] walk() {
		boolean[] seen = new boolean[W * D * Y];
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		int[] start = {GammaLayout.GATE_U, GammaLayout.RESIDENTIAL + 1, 0};
		assertTrue(stand(start[0], start[1], start[2]));
		seen[index(start[0], start[1], start[2])] = true;
		queue.add(start);
		int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			for (int[] way : ways) {
				int u = at[0] + way[0], v = at[2] + way[1];
				if (u < 0 || v < 0 || u >= W || v >= D) continue;
				// The checkpoint: the guards put you on the far side of it.
				if (GammaLayout.thing(u, at[1], v) == Thing.CHECKPOINT) {
					u += way[0];
					v += way[1];
				}
				int y = -1;
				if (passable(at[0], at[1] + 2, at[2]) && stand(u, at[1] + 1, v)) y = at[1] + 1;
				else {
					for (int down = at[1]; down >= 1 && passable(u, down, v) && passable(u, down + 1, v); down--) {
						if (stand(u, down, v)) {
							y = down;
							break;
						}
					}
				}
				if (y < 0 || seen[index(u, y, v)]) continue;
				seen[index(u, y, v)] = true;
				queue.add(new int[] {u, y, v});
			}
		}
		return seen;
	}
}
